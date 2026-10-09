package dev.life.client.fabric.mixin;

import dev.life.client.core.Life;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @org.spongepowered.asm.mixin.Shadow
    protected abstract void applyBlur(net.minecraft.client.gui.DrawContext context);

    /** "Blur other screens: Off" — no vanilla blur behind menus (Life's own menu has its own switch). */
    @Inject(method = "applyBlur", at = @At("HEAD"), cancellable = true, require = 0)
    private void life$noBlur(net.minecraft.client.gui.DrawContext context, CallbackInfo ci) {
        if (Life.platform == null || (Object) this instanceof dev.life.client.fabric.LifeMenuScreen) return;
        if (Life.get(dev.life.client.core.module.Features.Client.class).screenBlur.is("Off")
                && !Life.get(dev.life.client.core.module.Features.MenuBlur.class).isEnabled()) ci.cancel();
    }

    /** "Menus + inventory": blur the world behind inventories and other in-game screens too. */
    @Inject(method = "renderInGameBackground", at = @At("HEAD"), require = 0)
    private void life$inventoryBlur(net.minecraft.client.gui.DrawContext context, CallbackInfo ci) {
        if (Life.platform == null || (Object) this instanceof dev.life.client.fabric.LifeMenuScreen) return;
        boolean menuBlur = Life.get(dev.life.client.core.module.Features.MenuBlur.class).isEnabled()
                && Life.get(dev.life.client.core.module.Features.MenuBlur.class).inventory.on();
        if (!menuBlur && !Life.get(dev.life.client.core.module.Features.Client.class).screenBlur.is("Menus + inventory")) return;
        try {
            applyBlur(context);
        } catch (Throwable ignored) {
            // already blurred this frame
        }
    }
    /** Launcher wallpaper (animated too) instead of the panorama behind Singleplayer, Multiplayer and co. */
    @Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true)
    private void life$wallpaper(net.minecraft.client.gui.DrawContext context, float delta, CallbackInfo ci) {
        if (Life.platform == null) return;
        Screen self = (Screen) (Object) this;
        if (dev.life.client.core.ui.TitleMenu.backdrop(dev.life.client.fabric.LifeFabric.RENDER.with(context), self.width, self.height)) ci.cancel();
    }

    /** Mouse Trail: drawn over every menu. */
    @Inject(method = "renderWithTooltip", at = @At("TAIL"))
    private void life$mouseTrail(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (Life.platform == null) return;
        dev.life.client.core.module.Features.MouseTrail t = Life.get(dev.life.client.core.module.Features.MouseTrail.class);
        if (t.isEnabled()) t.render(dev.life.client.fabric.LifeFabric.RENDER.with(context), mouseX, mouseY);
    }

    @org.spongepowered.asm.mixin.Shadow
    @org.spongepowered.asm.mixin.Final
    private java.util.List<net.minecraft.client.gui.Drawable> drawables;

    /**
     * Joining a server / loading a world: only Life's star animation over the wallpaper, instead
     * of Minecraft's loading screens (the Cancel button stays while connecting).
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void life$loading(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (Life.platform == null) return;
        Object self = this;
        boolean connect = self instanceof net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
        if (!connect && !(self instanceof net.minecraft.client.gui.screen.world.LevelLoadingScreen)
                && !(self instanceof net.minecraft.client.gui.screen.ProgressScreen)) return;
        Screen sc = (Screen) self;
        dev.life.client.fabric.FabricRender r = dev.life.client.fabric.LifeFabric.RENDER.with(context);
        if (!dev.life.client.core.ui.TitleMenu.backdrop(r, sc.width, sc.height)) context.fill(0, 0, sc.width, sc.height, 0xFF0B0B0D);
        dev.life.client.core.ui.TitleMenu.loading(r, sc.width, sc.height);
        if (connect) for (net.minecraft.client.gui.Drawable d : drawables) d.render(context, mouseX, mouseY, delta);   // Cancel
        ci.cancel();
    }
}

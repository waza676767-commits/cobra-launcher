package dev.cobra.client.fabric.mixin;

import dev.cobra.client.core.Cobra;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @org.spongepowered.asm.mixin.Shadow
    protected abstract void applyBlur(net.minecraft.client.gui.DrawContext context);

    /** "Blur other screens: Off" — no vanilla blur behind menus (Cobra's own menu has its own switch). */
    @Inject(method = "applyBlur", at = @At("HEAD"), cancellable = true, require = 0)
    private void cobra$noBlur(net.minecraft.client.gui.DrawContext context, CallbackInfo ci) {
        if (Cobra.platform == null || (Object) this instanceof dev.cobra.client.fabric.CobraMenuScreen) return;
        if (Cobra.get(dev.cobra.client.core.module.Features.Client.class).screenBlur.is("Off")
                && !Cobra.get(dev.cobra.client.core.module.Features.MenuBlur.class).isEnabled()) ci.cancel();
    }

    /** "Menus + inventory": blur the world behind inventories and other in-game screens too. */
    @Inject(method = "renderInGameBackground", at = @At("HEAD"), require = 0)
    private void cobra$inventoryBlur(net.minecraft.client.gui.DrawContext context, CallbackInfo ci) {
        if (Cobra.platform == null || (Object) this instanceof dev.cobra.client.fabric.CobraMenuScreen) return;
        boolean menuBlur = Cobra.get(dev.cobra.client.core.module.Features.MenuBlur.class).isEnabled()
                && Cobra.get(dev.cobra.client.core.module.Features.MenuBlur.class).inventory.on();
        if (!menuBlur && !Cobra.get(dev.cobra.client.core.module.Features.Client.class).screenBlur.is("Menus + inventory")) return;
        try {
            applyBlur(context);
        } catch (Throwable ignored) {
            // already blurred this frame
        }
    }
    /** Launcher wallpaper (animated too) instead of the panorama behind Singleplayer, Multiplayer and co. */
    @Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true)
    private void cobra$wallpaper(net.minecraft.client.gui.DrawContext context, float delta, CallbackInfo ci) {
        if (Cobra.platform == null) return;
        Screen self = (Screen) (Object) this;
        if (dev.cobra.client.core.ui.TitleMenu.backdrop(dev.cobra.client.fabric.CobraFabric.RENDER.with(context), self.width, self.height)) ci.cancel();
    }

    /** Mouse Trail: drawn over every menu. */
    @Inject(method = "renderWithTooltip", at = @At("TAIL"))
    private void cobra$mouseTrail(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (Cobra.platform == null) return;
        dev.cobra.client.core.module.Features.MouseTrail t = Cobra.get(dev.cobra.client.core.module.Features.MouseTrail.class);
        if (t.isEnabled()) t.render(dev.cobra.client.fabric.CobraFabric.RENDER.with(context), mouseX, mouseY);
    }

    @org.spongepowered.asm.mixin.Shadow
    @org.spongepowered.asm.mixin.Final
    private java.util.List<net.minecraft.client.gui.Drawable> drawables;

    /**
     * Joining a server / loading a world: only Cobra's star animation over the wallpaper, instead
     * of Minecraft's loading screens (the Cancel button stays while connecting).
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void cobra$loading(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (Cobra.platform == null) return;
        Object self = this;
        boolean connect = self instanceof net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
        if (!connect && !(self instanceof net.minecraft.client.gui.screen.world.LevelLoadingScreen)
                && !(self instanceof net.minecraft.client.gui.screen.ProgressScreen)) return;
        Screen sc = (Screen) self;
        dev.cobra.client.fabric.FabricRender r = dev.cobra.client.fabric.CobraFabric.RENDER.with(context);
        if (!dev.cobra.client.core.ui.TitleMenu.backdrop(r, sc.width, sc.height)) context.fill(0, 0, sc.width, sc.height, 0xFF0B0B0D);
        dev.cobra.client.core.ui.TitleMenu.loading(r, sc.width, sc.height);
        if (connect) for (net.minecraft.client.gui.Drawable d : drawables) d.render(context, mouseX, mouseY, delta);   // Cancel
        ci.cancel();
    }
}

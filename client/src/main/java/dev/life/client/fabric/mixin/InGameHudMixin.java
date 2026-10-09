package dev.life.client.fabric.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.life.client.core.Life;
import dev.life.client.core.module.Features;
import dev.life.client.core.module.HudModules;
import dev.life.client.fabric.LifeFabric;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    /** Custom Crosshair replaces the vanilla one. */
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void life$crosshair(CallbackInfo ci, @Local(argsOnly = true) DrawContext context) {
        if (Life.platform == null) return;
        Features.Crosshair c = Life.get(Features.Crosshair.class);
        if (!c.isEnabled()) return;
        ci.cancel();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!mc.options.getPerspective().isFirstPerson()) return;
        if (mc.interactionManager != null && mc.interactionManager.getCurrentGameMode() == GameMode.SPECTATOR) return;
        c.draw(LifeFabric.RENDER.with(context));
    }

    /** Scoreboard module draws its own movable sidebar. */
    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("HEAD"), cancellable = true)
    private void life$scoreboard(CallbackInfo ci) {
        if (Life.platform != null && Life.get(HudModules.Scoreboard.class).isEnabled()) ci.cancel();
    }

    /** Potion Effects module replaces the top-right effect icons. */
    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void life$effects(CallbackInfo ci) {
        if (Life.platform != null && Life.get(HudModules.Potions.class).isEnabled()) ci.cancel();
    }

    /** Visual Tweaks: no pumpkin blur. */
    @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true)
    private void life$pumpkin(DrawContext context, net.minecraft.util.Identifier texture, float opacity, CallbackInfo ci) {
        if (Life.platform != null && texture != null && texture.getPath().contains("pumpkinblur")
                && Life.get(Features.Tweaks.class).noPumpkin()) ci.cancel();
    }
}

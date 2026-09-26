package dev.cobra.client.fabric.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.cobra.client.core.Cobra;
import dev.cobra.client.core.module.Features;
import dev.cobra.client.core.module.HudModules;
import dev.cobra.client.fabric.CobraFabric;
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
    private void cobra$crosshair(CallbackInfo ci, @Local(argsOnly = true) DrawContext context) {
        if (Cobra.platform == null) return;
        Features.Crosshair c = Cobra.get(Features.Crosshair.class);
        if (!c.isEnabled()) return;
        ci.cancel();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!mc.options.getPerspective().isFirstPerson()) return;
        if (mc.interactionManager != null && mc.interactionManager.getCurrentGameMode() == GameMode.SPECTATOR) return;
        c.draw(CobraFabric.RENDER.with(context));
    }

    /** Scoreboard module draws its own movable sidebar. */
    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("HEAD"), cancellable = true)
    private void cobra$scoreboard(CallbackInfo ci) {
        if (Cobra.platform != null && Cobra.get(HudModules.Scoreboard.class).isEnabled()) ci.cancel();
    }

    /** Potion Effects module replaces the top-right effect icons. */
    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void cobra$effects(CallbackInfo ci) {
        if (Cobra.platform != null && Cobra.get(HudModules.Potions.class).isEnabled()) ci.cancel();
    }

    /** Visual Tweaks: no pumpkin blur. */
    @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true)
    private void cobra$pumpkin(DrawContext context, net.minecraft.util.Identifier texture, float opacity, CallbackInfo ci) {
        if (Cobra.platform != null && texture != null && texture.getPath().contains("pumpkinblur")
                && Cobra.get(Features.Tweaks.class).noPumpkin()) ci.cancel();
    }
}

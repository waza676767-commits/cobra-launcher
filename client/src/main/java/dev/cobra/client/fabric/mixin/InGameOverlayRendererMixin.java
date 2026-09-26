package dev.cobra.client.fabric.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.cobra.client.core.Cobra;
import dev.cobra.client.core.module.Features;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {
    /** Visual Tweaks: fire offset (moves the burning overlay down so you can see). */
    @Inject(method = "renderFireOverlay", at = @At("HEAD"))
    private static void cobra$fireIn(CallbackInfo ci, @Local(argsOnly = true) MatrixStack matrices) {
        matrices.push();
        if (Cobra.platform != null) matrices.translate(0, Cobra.get(Features.ViewModel.class).fireOffset(), 0);
    }

    @Inject(method = "renderFireOverlay", at = @At("RETURN"))
    private static void cobra$fireOut(CallbackInfo ci, @Local(argsOnly = true) MatrixStack matrices) {
        matrices.pop();
    }
}

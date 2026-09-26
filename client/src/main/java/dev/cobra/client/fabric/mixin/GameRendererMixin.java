package dev.cobra.client.fabric.mixin;

import dev.cobra.client.core.Cobra;
import dev.cobra.client.core.module.Features;
import dev.cobra.client.fabric.FabricPlatform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /** Zoom and FOV Modifier. */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void cobra$fov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        if (Cobra.platform == null || !changingFov) return;
        double fov = cir.getReturnValue();
        Features.FovModifier fm = Cobra.get(Features.FovModifier.class);
        if (fm.isEnabled()) fov = fov * fm.fov.get() / MinecraftClient.getInstance().options.getFov().getValue();
        fov /= Cobra.get(Features.Zoom.class).divisor();
        FabricPlatform.lastFov = fov;
        cir.setReturnValue((float) fov);
        dev.cobra.client.fabric.CobraFabric.motionFrame(camera, fov);   // velocity motion blur, once per frame
    }

    /** No Hurt Camera. */
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void cobra$hurtCam(CallbackInfo ci) {
        if (Cobra.platform != null && Cobra.get(Features.HurtCam.class).isEnabled()) ci.cancel();
    }

    /** Motion blur runs right after the world is drawn, before the HUD. */

    /** Screen Recorder: after the whole frame (world + HUD + menus) is drawn, grab it. */
    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void cobra$record(CallbackInfo ci) {
        dev.cobra.client.fabric.CobraFabric.captureFrame();
    }
}

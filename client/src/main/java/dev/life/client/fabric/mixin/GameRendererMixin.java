package dev.life.client.fabric.mixin;

import dev.life.client.core.Life;
import dev.life.client.core.module.Features;
import dev.life.client.fabric.FabricPlatform;
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
    private void life$fov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        if (Life.platform == null || !changingFov) return;
        double fov = cir.getReturnValue();
        Features.FovModifier fm = Life.get(Features.FovModifier.class);
        if (fm.isEnabled()) fov = fov * fm.fov.get() / MinecraftClient.getInstance().options.getFov().getValue();
        fov /= Life.get(Features.Zoom.class).divisor();
        FabricPlatform.lastFov = fov;
        cir.setReturnValue((float) fov);
        dev.life.client.fabric.LifeFabric.motionFrame(camera, fov);   // velocity motion blur, once per frame
    }

    /** No Hurt Camera. */
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void life$hurtCam(CallbackInfo ci) {
        if (Life.platform != null && Life.get(Features.HurtCam.class).isEnabled()) ci.cancel();
    }

    /** Motion blur runs right after the world is drawn, before the HUD. */

    /** Screen Recorder: after the whole frame (world + HUD + menus) is drawn, grab it. */
    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void life$record(CallbackInfo ci) {
        dev.life.client.fabric.LifeFabric.captureFrame();
    }
}

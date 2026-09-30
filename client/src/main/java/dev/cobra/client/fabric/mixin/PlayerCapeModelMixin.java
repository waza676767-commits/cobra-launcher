package dev.cobra.client.fabric.mixin;

import dev.cobra.client.core.Cobra;
import dev.cobra.client.core.module.Features;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerCapeModel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Wavy Capes: after the cape is posed, a gentle wind ripple is added (bigger when you move). */
@Mixin(PlayerCapeModel.class)
public abstract class PlayerCapeModelMixin {
    @Shadow @Final private ModelPart cape;

    @Inject(method = "setAngles", at = @At("TAIL"), require = 0)
    private void cobra$wave(CallbackInfo ci) {
        if (Cobra.platform == null) return;
        Features.WavyCapes w = Cobra.get(Features.WavyCapes.class);
        if (!w.isEnabled()) return;
        double t = System.currentTimeMillis() / 1000.0;
        float k = w.wind.f();
        cape.pitch += (float) ((Math.sin(t * 3.1) * 0.05 + Math.sin(t * 5.3) * 0.025 + 0.05) * k);
        cape.roll += (float) (Math.sin(t * 2.3) * 0.03 * k);
    }
}

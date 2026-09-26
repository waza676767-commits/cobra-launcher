package dev.cobra.client.fabric.mixin;

import dev.cobra.client.core.Cobra;
import dev.cobra.client.core.module.Features;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {
    /** FOV Modifier: static FOV (no sprint/speed zoom). */
    @Inject(method = "getFovMultiplier", at = @At("RETURN"), cancellable = true)
    private void cobra$staticFov(CallbackInfoReturnable<Float> cir) {
        if (Cobra.platform == null) return;
        Features.FovModifier f = Cobra.get(Features.FovModifier.class);
        if (f.isEnabled() && !f.dynamic.on()) cir.setReturnValue(1.0f);
    }
}

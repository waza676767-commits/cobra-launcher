package dev.life.client.fabric.mixin;

import dev.life.client.core.Life;
import dev.life.client.core.module.Features;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {
    /** FOV Modifier: static FOV (no sprint/speed zoom). */
    @Inject(method = "getFovMultiplier", at = @At("RETURN"), cancellable = true)
    private void life$staticFov(CallbackInfoReturnable<Float> cir) {
        if (Life.platform == null) return;
        Features.FovModifier f = Life.get(Features.FovModifier.class);
        if (f.isEnabled() && !f.dynamic.on()) cir.setReturnValue(1.0f);
    }
}

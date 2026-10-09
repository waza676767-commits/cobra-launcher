package dev.life.client.fabric.mixin;

import dev.life.client.core.Life;
import dev.life.client.core.module.Features;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Life Settings → "Disable all particles". */
@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {
    private static boolean life$off() {
        return Life.platform != null && Life.get(Features.Client.class).noParticles.on();
    }

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void life$noParticle(ParticleEffect effect, double x, double y, double z, double vx, double vy, double vz,
                                  CallbackInfoReturnable<Particle> cir) {
        if (life$off()) cir.setReturnValue(null);
    }

    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void life$noParticle2(Particle particle, CallbackInfo ci) {
        if (life$off()) ci.cancel();
    }
}

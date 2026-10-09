package dev.life.client.fabric.mixin;

import dev.life.client.core.Life;
import dev.life.client.core.module.Features;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.particle.TotemParticle;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Totem module: totem particles in your colour, or a rainbow. */
@Mixin(TotemParticle.class)
public abstract class TotemParticleMixin {
    @Inject(method = "<init>", at = @At("TAIL"), require = 0)
    private void life$colour(ClientWorld world, double x, double y, double z, double vx, double vy, double vz,
                              SpriteProvider sprites, CallbackInfo ci) {
        if (Life.platform == null) return;
        Features.Totem t = Life.get(Features.Totem.class);
        if (!t.isEnabled() || t.particles.is("Default")) return;
        int c;
        if (t.particles.is("Rainbow")) {
            c = java.awt.Color.HSBtoRGB((float) Math.random(), 0.85f, 1f);
        } else {
            c = t.colour.argb();
            float k = 0.8f + (float) Math.random() * 0.4f;                 // a little variety, like vanilla
            int r = Math.min(255, Math.round((c >> 16 & 255) * k)), g = Math.min(255, Math.round((c >> 8 & 255) * k)), b = Math.min(255, Math.round((c & 255) * k));
            c = r << 16 | g << 8 | b;
        }
        ((BillboardParticle) (Object) this).setColor((c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f);
    }
}

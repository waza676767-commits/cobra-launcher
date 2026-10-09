package dev.life.client.fabric.mixin;

import dev.life.client.core.Life;
import dev.life.client.core.module.Features;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sky module: your own weather, on your screen only (the server's world is untouched). */
@Mixin(World.class)
public abstract class WorldWeatherMixin {
    @Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true, require = 0)
    private void life$rain(float delta, CallbackInfoReturnable<Float> cir) {
        if (Life.platform == null || !((Object) this instanceof ClientWorld)) return;
        float v = Life.get(Features.Sky.class).rain();
        if (v >= 0) cir.setReturnValue(v);
    }

    @Inject(method = "getThunderGradient", at = @At("HEAD"), cancellable = true, require = 0)
    private void life$thunder(float delta, CallbackInfoReturnable<Float> cir) {
        if (Life.platform == null || !((Object) this instanceof ClientWorld)) return;
        float v = Life.get(Features.Sky.class).thunder();
        if (v >= 0) cir.setReturnValue(v);
    }
}

package dev.cobra.client.fabric.mixin;

import dev.cobra.client.core.Cobra;
import dev.cobra.client.core.module.Features;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.Properties.class)
public abstract class ClientWorldPropertiesMixin {
    /** Time Changer: client-side time of day (sky and lighting only). */
    @Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true)
    private void cobra$time(CallbackInfoReturnable<Long> cir) {
        if (Cobra.platform == null) return;
        long sky = Cobra.get(Features.Sky.class).timeTicks();
        if (sky >= 0) {
            cir.setReturnValue(sky);
            return;
        }
        Features.TimeChanger t = Cobra.get(Features.TimeChanger.class);
        if (t.isEnabled()) cir.setReturnValue(t.time());
    }
}

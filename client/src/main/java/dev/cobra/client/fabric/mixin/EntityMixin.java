package dev.cobra.client.fabric.mixin;

import dev.cobra.client.core.Cobra;
import dev.cobra.client.core.module.Features;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    /** Freelook: mouse movement turns the camera, not the player. */
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void cobra$freelook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (Cobra.platform == null || (Object) this != MinecraftClient.getInstance().player) return;
        Features.Freelook fl = Cobra.get(Features.Freelook.class);
        if (fl.active) {
            fl.turn(cursorDeltaX, cursorDeltaY);
            ci.cancel();
        }
    }
}

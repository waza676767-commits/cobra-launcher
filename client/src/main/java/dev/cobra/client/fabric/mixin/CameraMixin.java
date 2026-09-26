package dev.cobra.client.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cobra.client.core.Cobra;
import dev.cobra.client.core.module.Features;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Freelook: the camera takes its own yaw/pitch instead of the player's. Hooked two ways (the
 * player's rotation as the camera reads it, and the rotation call itself), so it keeps working
 * if one of them moves in a Minecraft update.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    private static Features.Freelook cobra$fl() {
        if (Cobra.platform == null) return null;
        Features.Freelook fl = Cobra.get(Features.Freelook.class);
        return fl.active ? fl : null;
    }

    @ModifyExpressionValue(method = "update", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw(F)F"))
    private float cobra$yaw(float yaw) {
        Features.Freelook fl = cobra$fl();
        return fl != null ? fl.yaw : yaw;
    }

    @ModifyExpressionValue(method = "update", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPitch(F)F"))
    private float cobra$pitch(float pitch) {
        Features.Freelook fl = cobra$fl();
        return fl != null ? fl.pitch : pitch;
    }

    @Redirect(method = "update", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V", ordinal = 0))
    private void cobra$freelook(Camera camera, float yaw, float pitch) {
        Features.Freelook fl = cobra$fl();
        if (fl != null) this.setRotation(fl.yaw, fl.pitch);
        else this.setRotation(yaw, pitch);
    }
}

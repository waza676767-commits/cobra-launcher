package dev.life.client.fabric.mixin;

import dev.life.client.core.Life;
import dev.life.client.core.module.Features;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Rescale (stretched resolution): the world is projected for another shape (e.g. 4:3) and then
 * fills your whole screen, so everything looks wider, like a stretched resolution. The HUD and
 * menus are untouched.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererRescaleMixin {
    @Inject(method = "getBasicProjectionMatrix", at = @At("RETURN"), require = 0)
    private void life$stretch(float fov, CallbackInfoReturnable<Matrix4f> cir) {
        if (Life.platform == null) return;
        Features.Rescale r = Life.get(Features.Rescale.class);
        if (!r.isEnabled()) return;
        var win = MinecraftClient.getInstance().getWindow();
        if (win.getFramebufferHeight() <= 0) return;
        float actual = (float) win.getFramebufferWidth() / win.getFramebufferHeight();
        Matrix4f m = cir.getReturnValue();
        if (m != null) m.m00(m.m00() * actual / r.target());
    }
}

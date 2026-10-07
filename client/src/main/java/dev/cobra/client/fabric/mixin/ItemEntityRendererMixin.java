package dev.cobra.client.fabric.mixin;

import dev.cobra.client.core.Cobra;
import dev.cobra.client.core.module.Features;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Item Beams: a soft column of light rising from every dropped item (fading towards the top). */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    private static final Identifier WHITE = Identifier.of("cobra", "textures/misc/white.png");

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"), require = 0)
    private void cobra$beam(ItemEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera, CallbackInfo ci) {
        if (Cobra.platform == null) return;
        Features.ItemBeams b = Cobra.get(Features.ItemBeams.class);
        if (!b.isEnabled()) return;
        int c = b.argb() & 0xFFFFFF;
        float hgt = b.height.f(), hw = b.width.f() / 2;
        queue.submitCustom(matrices, dev.cobra.client.fabric.compat.Layers.translucent(WHITE), (e, vc) -> {
            int bottom = 0xB0 << 24 | c, top = c;                    // fades out towards the top
            float[][] sides = {{-hw, -hw, hw, -hw}, {hw, -hw, hw, hw}, {hw, hw, -hw, hw}, {-hw, hw, -hw, -hw}};
            for (float[] sd : sides) {
                vc.vertex(e, sd[0], 0.1f, sd[1]).color(bottom).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(e, 0, 1, 0);
                vc.vertex(e, sd[2], 0.1f, sd[3]).color(bottom).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(e, 0, 1, 0);
                vc.vertex(e, sd[2], hgt, sd[3]).color(top).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(e, 0, 1, 0);
                vc.vertex(e, sd[0], hgt, sd[1]).color(top).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(e, 0, 1, 0);
            }
        });
    }
}

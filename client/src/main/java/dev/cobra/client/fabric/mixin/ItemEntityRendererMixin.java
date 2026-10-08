package dev.cobra.client.fabric.mixin;

import dev.cobra.client.core.Cobra;
import dev.cobra.client.core.module.Features;
import dev.cobra.client.fabric.ItemPhysicsFx;
import dev.cobra.client.fabric.ItemPhysicsState;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dropped items:
 * <ul>
 *   <li>Item Beams: a soft column of light rising from every dropped item (fading towards the top).</li>
 *   <li>Item Physics: the item is drawn lying / tumbling like a real object (see {@link ItemPhysicsFx})
 *   instead of floating, bobbing and spinning.</li>
 * </ul>
 */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    private static final Identifier WHITE = Identifier.of("cobra", "textures/misc/white.png");

    /** Item Physics: read the real item (on the ground? in water? speed?) into its render state. */
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V",
            at = @At("TAIL"), require = 0)
    private void cobra$physicsState(ItemEntity entity, ItemEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (Cobra.platform == null) return;
        try {
            ItemPhysicsFx.update(entity, state, Cobra.get(Features.ItemPhysics.class));
        } catch (Throwable t) {
            if (((Object) state) instanceof ItemPhysicsState ps) ps.cobra$setPhysics(false, 0, 0, 0);
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void cobra$render(ItemEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera, CallbackInfo ci) {
        if (Cobra.platform == null) return;
        beam(state, matrices, queue);
        if (!(((Object) state) instanceof ItemPhysicsState ps) || !ps.cobra$physics() || state.itemRenderState.isEmpty()) return;
        if (!Cobra.get(Features.ItemPhysics.class).isEnabled()) return;
        try {
            ItemPhysicsFx.render(state, ps, matrices, queue);
            ci.cancel();                                   // drawn: skip the floating vanilla item
        } catch (Throwable ignored) {
            // something unexpected: fall back to the vanilla item rather than drawing nothing
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private static void beam(ItemEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue) {
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

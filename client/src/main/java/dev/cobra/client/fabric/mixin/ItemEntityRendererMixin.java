package dev.cobra.client.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.cobra.client.core.Cobra;
import dev.cobra.client.core.module.Features;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Item Physics: dropped items stop bobbing and spinning in the air. Flat items (most items) lie
 * down on the ground, blocks sit on it; with a spin speed above 0 they slowly turn in place.
 */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    @Unique
    private static final String RENDER = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;"
            + "Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;"
            + "Lnet/minecraft/client/render/state/CameraRenderState;)V";

    @Unique
    private static Features.ItemPhysics cobra$physics() {
        if (Cobra.platform == null) return null;
        Features.ItemPhysics p = Cobra.get(Features.ItemPhysics.class);
        return p.isEnabled() ? p : null;
    }

    /** No bobbing: sin(...) = -1 makes vanilla's bob offset 0. */
    @ModifyExpressionValue(method = RENDER, require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;sin(D)F"))
    private float cobra$noBob(float sin) {
        return cobra$physics() != null ? -1f : sin;
    }

    /** No vanilla spin: our own rotation is applied when the item is placed (below). */
    @ModifyExpressionValue(method = RENDER, require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/ItemEntity;getRotation(FF)F"))
    private float cobra$noSpin(float spin) {
        return cobra$physics() != null ? 0f : spin;
    }

    /** Places the item: flat on the ground (thin items) or resting on it (blocks), facing its own way. */
    @WrapOperation(method = RENDER, require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V", ordinal = 0))
    private void cobra$place(MatrixStack matrices, float x, float y, float z, Operation<Void> original,
                             @Local(argsOnly = true) ItemEntityRenderState state) {
        Features.ItemPhysics p = cobra$physics();
        if (p == null) {
            original.call(matrices, x, y, z);
            return;
        }
        Box box = state.itemRenderState.getModelBoundingBox();
        double depth = box.maxZ - box.minZ;
        float yaw = state.uniqueOffset * 57.3f + state.age * 2f * p.speed.f();
        if (depth < 0.2) {
            // a flat sprite: lie down, just above the ground
            float thick = (float) (depth / 2 + Math.abs((box.maxZ + box.minZ) / 2)) + 0.01f;
            original.call(matrices, x, thick, z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
        } else {
            // a block: sits on the ground (vanilla's height without the bob), turned its own way
            original.call(matrices, x, y, z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        }
    }
}

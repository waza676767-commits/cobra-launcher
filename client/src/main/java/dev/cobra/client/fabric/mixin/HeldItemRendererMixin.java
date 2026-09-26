package dev.cobra.client.fabric.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.cobra.client.core.Cobra;
import dev.cobra.client.core.module.Features;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    /** View Model: per item type position, rotation and size of what you hold (and the empty hand). */
    @Inject(method = "renderFirstPersonItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = At.Shift.AFTER, ordinal = 0))
    private void cobra$viewModel(CallbackInfo ci, @Local(argsOnly = true) MatrixStack matrices,
                                 @Local(argsOnly = true) ItemStack item, @Local(argsOnly = true) Hand hand) {
        if (Cobra.platform == null) return;
        Features.ViewModel vm = Cobra.get(Features.ViewModel.class);
        if (!vm.isEnabled()) return;
        boolean empty = item == null || item.isEmpty();
        String id = empty ? "" : Registries.ITEM.getId(item.getItem()).toString();
        boolean block = !empty && item.getItem() instanceof BlockItem;
        boolean food = !empty && (item.contains(DataComponentTypes.FOOD) || item.contains(DataComponentTypes.POTION_CONTENTS));
        float[] t = vm.transform(Features.ViewModel.typeOf(id, empty, block, food));
        if (t == null) return;
        float side = hand == Hand.OFF_HAND && vm.mirror.on() ? -1 : 1;
        matrices.translate(t[0] * side, t[1], t[2]);
        if (t[3] != 0) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(t[3]));
        if (t[4] != 0) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(t[4] * side));
        if (t[5] != 0) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(t[5] * side));
        if (t[6] != 1) matrices.scale(t[6], t[6], t[6]);
    }
}

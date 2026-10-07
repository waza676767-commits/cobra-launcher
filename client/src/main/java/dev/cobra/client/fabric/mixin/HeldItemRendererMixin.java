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
    @org.spongepowered.asm.mixin.Unique
    private static final java.util.Map<net.minecraft.item.Item, String> TYPES = new java.util.IdentityHashMap<>();

    /** View Model: hide the empty hand and/or the off-hand item. */
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void cobra$hide(CallbackInfo ci, @Local(argsOnly = true) ItemStack item, @Local(argsOnly = true) Hand hand) {
        if (Cobra.platform == null) return;
        Features.ViewModel vm = Cobra.get(Features.ViewModel.class);
        if (!vm.isEnabled()) return;
        boolean empty = item == null || item.isEmpty();
        if (empty && vm.hideHand.on() || hand == Hand.OFF_HAND && !empty && vm.hideOffhand.on()) ci.cancel();
    }

    /** View Model: per item type position, rotation and size of what you hold (and the empty hand). */
    @Inject(method = "renderFirstPersonItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = At.Shift.AFTER, ordinal = 0))
    private void cobra$viewModel(CallbackInfo ci, @Local(argsOnly = true) MatrixStack matrices,
                                 @Local(argsOnly = true) ItemStack item, @Local(argsOnly = true) Hand hand) {
        if (Cobra.platform == null) return;
        cobra$oldVisuals(matrices, item, hand);
        Features.ViewModel vm = Cobra.get(Features.ViewModel.class);
        cobra$size = 1f;
        if (!vm.isEnabled()) return;
        boolean empty = item == null || item.isEmpty();
        String type;
        if (empty) {
            type = "hand";
        } else {
            // the item's type never changes: work it out once per item, not every frame
            type = TYPES.get(item.getItem());
            if (type == null) {
                String id = Registries.ITEM.getId(item.getItem()).toString();
                boolean block = item.getItem() instanceof BlockItem;
                boolean food = item.contains(DataComponentTypes.FOOD) || item.contains(DataComponentTypes.POTION_CONTENTS);
                type = Features.ViewModel.typeOf(id, false, block, food);
                TYPES.put(item.getItem(), type);
            }
        }
        float[] t = vm.transform(type);
        if (t == null) return;
        float side = hand == Hand.OFF_HAND && vm.mirror.on() ? -1 : 1;
        matrices.translate(t[0] * side, t[1], t[2]);
        if (t[3] != 0) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(t[3]));
        if (t[4] != 0) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(t[4] * side));
        if (t[5] != 0) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(t[5] * side));
        cobra$size = t[6];                                   // applied right before the item itself (below)
    }

    @org.spongepowered.asm.mixin.Unique
    private static float cobra$size = 1f;

    /**
     * View Model size: scaled around the item itself, just before it's drawn. (Scaling at the start
     * scaled around your eye, which in first person looks exactly the same size, just further away.)
     */
    @Inject(method = "renderFirstPersonItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V"),
            require = 0)
    private void cobra$sizeItem(CallbackInfo ci, @Local(argsOnly = true) MatrixStack matrices, @Local(argsOnly = true) ItemStack item) {
        cobra$applySize(matrices);
        // Totem module: the totem in your hand gets its own size and place
        if (item != null && !item.isEmpty() && Cobra.platform != null) {
            Features.Totem tm = Cobra.get(Features.Totem.class);
            if (tm.isEnabled() && Registries.ITEM.getId(item.getItem()).getPath().equals("totem_of_undying")) {
                matrices.translate(tm.heldX.f() * 0.5f, tm.heldY.f() * 0.5f, tm.heldZ.f() * 0.5f);
                float k = tm.heldSize.f();
                matrices.scale(k, k, k);
            }
        }
    }

    @Inject(method = "renderFirstPersonItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderArmHoldingItem(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;IFFLnet/minecraft/util/Arm;)V"),
            require = 0)
    private void cobra$sizeHand(CallbackInfo ci, @Local(argsOnly = true) MatrixStack matrices) {
        cobra$applySize(matrices);
    }

    @org.spongepowered.asm.mixin.Unique
    private static void cobra$applySize(MatrixStack matrices) {
        float k = cobra$size;
        cobra$size = 1f;                                     // once per hand
        if (Cobra.platform == null || !Cobra.get(Features.ViewModel.class).isEnabled() || Math.abs(k - 1f) < 0.001f) return;
        matrices.scale(k, k, k);
    }

    /**
     * 1.7 Visuals: items sit a little lower and closer like in 1.7/1.8, and a sword held while you
     * right-click takes the old "blocking" pose (block-hitting).
     */
    @org.spongepowered.asm.mixin.Unique
    private static void cobra$oldVisuals(MatrixStack matrices, ItemStack item, Hand hand) {
        Features.OldVisuals ov = Cobra.get(Features.OldVisuals.class);
        if (!ov.isEnabled() || item == null || item.isEmpty()) return;
        float side = hand == Hand.OFF_HAND ? -1 : 1;
        if (ov.oldPositions.on()) matrices.translate(0.02f * side, -0.04f, 0.05f);
        if (ov.blockHit.on() && hand == Hand.MAIN_HAND && net.minecraft.client.MinecraftClient.getInstance().options.useKey.isPressed()
                && Registries.ITEM.getId(item.getItem()).getPath().endsWith("_sword")) {
            matrices.translate(-0.12f, 0.08f, 0f);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(30f));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-80f));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(60f));
        }
    }

    /** View Model: no item-switch (equip) animation. */
    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), argsOnly = true, ordinal = 3, require = 0)
    private float cobra$noEquip(float equipProgress) {
        if (Cobra.platform == null) return equipProgress;
        Features.ViewModel vm = Cobra.get(Features.ViewModel.class);
        Features.OldVisuals ov = Cobra.get(Features.OldVisuals.class);
        return vm.isEnabled() && vm.noEquip.on() || ov.isEnabled() && ov.noEquip.on() ? 0f : equipProgress;
    }
}

package dev.cobra.client.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cobra.client.core.Cobra;
import dev.cobra.client.core.module.Features;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Totem module: the totem pop animation's size, where it shows, or no animation at all. */
@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {
    @Shadow private float floatingItemOffsetX;
    @Shadow private float floatingItemOffsetY;

    @Inject(method = "setFloatingItem", at = @At("TAIL"), require = 0)
    private void cobra$place(ItemStack stack, Random random, CallbackInfo ci) {
        if (Cobra.platform == null) return;
        Features.Totem t = Cobra.get(Features.Totem.class);
        if (!t.isEnabled()) return;
        float[] o = t.popOffset();
        if (o != null) {
            floatingItemOffsetX = o[0];
            floatingItemOffsetY = o[1];
        }
    }

    @Inject(method = "renderFloatingItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void cobra$hide(MatrixStack matrices, float tickDelta, OrderedRenderCommandQueue queue, CallbackInfo ci) {
        if (Cobra.platform == null) return;
        Features.Totem t = Cobra.get(Features.Totem.class);
        if (t.isEnabled() && t.hidePop.on()) ci.cancel();
    }

    @WrapOperation(method = "renderFloatingItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;scale(FFF)V"), require = 0)
    private void cobra$size(MatrixStack matrices, float x, float y, float z, Operation<Void> original) {
        float k = 1f;
        if (Cobra.platform != null) {
            Features.Totem t = Cobra.get(Features.Totem.class);
            if (t.isEnabled()) k = t.popSize.f();
        }
        original.call(matrices, x * k, y * k, z * k);
    }
}

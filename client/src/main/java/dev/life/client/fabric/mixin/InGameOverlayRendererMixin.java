package dev.life.client.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.life.client.core.Life;
import dev.life.client.core.module.Features;
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
    private void life$place(ItemStack stack, Random random, CallbackInfo ci) {
        if (Life.platform == null) return;
        Features.Totem t = Life.get(Features.Totem.class);
        if (!t.isEnabled()) return;
        float[] o = t.popOffset();
        if (o != null) {
            floatingItemOffsetX = o[0];
            floatingItemOffsetY = o[1];
        }
    }

    @Inject(method = "renderFloatingItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void life$hide(MatrixStack matrices, float tickDelta, OrderedRenderCommandQueue queue, CallbackInfo ci) {
        if (Life.platform == null) return;
        Features.Totem t = Life.get(Features.Totem.class);
        if (t.isEnabled() && t.hidePop.on()) ci.cancel();
    }

    @WrapOperation(method = "renderFloatingItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;scale(FFF)V"), require = 0)
    private void life$size(MatrixStack matrices, float x, float y, float z, Operation<Void> original) {
        float k = 1f;
        if (Life.platform != null) {
            Features.Totem t = Life.get(Features.Totem.class);
            if (t.isEnabled()) k = t.popSize.f();
        }
        original.call(matrices, x * k, y * k, z * k);
    }
}

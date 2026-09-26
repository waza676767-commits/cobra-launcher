package dev.cobra.client.fabric.mixin;

import dev.cobra.client.core.Cobra;
import dev.cobra.client.core.module.Features;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    /** Scroll while zoomed to change the zoom level instead of the hotbar slot. */
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void cobra$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (Cobra.platform == null || MinecraftClient.getInstance().currentScreen != null) return;
        if (Cobra.get(Features.Zoom.class).onScroll(vertical)) ci.cancel();
    }

    /** Zoom: lower mouse sensitivity so aiming feels the same zoomed in (scales the final turn speed). */
    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = "updateMouse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;", ordinal = 0))
    private Object cobra$zoomSensitivity(Object value) {
        if (Cobra.platform == null || !(value instanceof Double v)) return value;
        double k = Cobra.get(Features.Zoom.class).sensitivity();
        if (k >= 0.999) return value;
        // vanilla turns by 8 * (0.6 v + 0.2)^3; pick v' so that becomes k times as much
        double d = (0.6 * v + 0.2) * Math.cbrt(k);
        return (d - 0.2) / 0.6;
    }
}

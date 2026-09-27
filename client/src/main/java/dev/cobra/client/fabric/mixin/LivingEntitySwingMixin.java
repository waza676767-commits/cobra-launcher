package dev.cobra.client.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.cobra.client.core.Cobra;
import dev.cobra.client.core.module.Features;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** View Model: your own arm swing speed (only how it looks on your screen). */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySwingMixin {
    @ModifyReturnValue(method = "getHandSwingDuration", at = @At("RETURN"), require = 0)
    private int cobra$swing(int ticks) {
        if (Cobra.platform == null || (Object) this != MinecraftClient.getInstance().player) return ticks;
        Features.ViewModel vm = Cobra.get(Features.ViewModel.class);
        if (!vm.isEnabled() || Math.abs(vm.swingSpeed.f() - 1) < 0.01f) return ticks;
        return Math.max(1, Math.round(ticks / vm.swingSpeed.f()));
    }
}

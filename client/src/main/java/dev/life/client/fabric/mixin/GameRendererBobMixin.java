package dev.life.client.fabric.mixin;

import dev.life.client.core.Life;
import dev.life.client.core.module.Features;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** View Model: no view bobbing while walking. */
@Mixin(GameRenderer.class)
public abstract class GameRendererBobMixin {
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true, require = 0)
    private void life$noBob(MatrixStack matrices, float tickProgress, CallbackInfo ci) {
        if (Life.platform == null) return;
        Features.ViewModel vm = Life.get(Features.ViewModel.class);
        if (vm.isEnabled() && vm.noBob.on()) ci.cancel();
    }
}

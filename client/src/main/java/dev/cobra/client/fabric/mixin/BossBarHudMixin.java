package dev.cobra.client.fabric.mixin;

import dev.cobra.client.core.Cobra;
import dev.cobra.client.core.module.HudModules;
import net.minecraft.client.gui.hud.BossBarHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossBarHud.class)
public abstract class BossBarHudMixin {
    /** The Boss Bar module draws its own movable bar. */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void cobra$hide(CallbackInfo ci) {
        if (Cobra.platform != null && Cobra.get(HudModules.BossBar.class).isEnabled()) ci.cancel();
    }
}

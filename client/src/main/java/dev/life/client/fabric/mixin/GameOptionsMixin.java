package dev.life.client.fabric.mixin;

import dev.life.client.fabric.FabricPlatform;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameOptions.class)
public abstract class GameOptionsMixin {
    /** Fullbright: never write the boosted gamma into options.txt. */
    @Inject(method = "write", at = @At("HEAD"))
    private void life$beforeWrite(CallbackInfo ci) {
        if (FabricPlatform.savedGamma != null) gamma().life$setValue(FabricPlatform.savedGamma);
    }

    @Inject(method = "write", at = @At("RETURN"))
    private void life$afterWrite(CallbackInfo ci) {
        if (FabricPlatform.savedGamma != null) gamma().life$setValue(16.0);
    }

    private SimpleOptionAccessor gamma() {
        return (SimpleOptionAccessor) (Object) ((GameOptions) (Object) this).getGamma();
    }
}

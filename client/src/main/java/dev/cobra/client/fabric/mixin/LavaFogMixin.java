package dev.cobra.client.fabric.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.render.fog.LavaFogModifier")   // not public: by name
public abstract class LavaFogMixin {
    @Inject(method = "applyStartEndModifier", at = @At("TAIL"), require = 0)
    private void cobra$fog(FogData data, Camera camera, ClientWorld world, float viewDistance, RenderTickCounter tickCounter, CallbackInfo ci) {
        dev.cobra.client.fabric.FogTweaks.apply("lava", data);
    }
}

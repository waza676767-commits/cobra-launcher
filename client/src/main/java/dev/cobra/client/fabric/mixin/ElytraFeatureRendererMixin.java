package dev.cobra.client.fabric.mixin;

import dev.cobra.client.core.Cobra;
import dev.cobra.client.fabric.cosmetics.CobraCapes;
import dev.cobra.client.fabric.cosmetics.CosmeticsFeature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.feature.ElytraFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Your Abyss cape's design on your elytra too, like a real cape. */
@Mixin(ElytraFeatureRenderer.class)
public abstract class ElytraFeatureRendererMixin {
    @Inject(method = "getTexture", at = @At("HEAD"), cancellable = true, require = 0)
    private static void cobra$capeElytra(BipedEntityRenderState state, CallbackInfoReturnable<Identifier> cir) {
        if (Cobra.platform == null || !(state instanceof PlayerEntityRenderState ps)) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        Entity e = mc.world == null ? null : mc.world.getEntityById(ps.id);
        if (!(e instanceof PlayerEntity p)) return;
        boolean self = mc.player != null && p.getUuid().equals(mc.player.getUuid());
        Identifier tex = CobraCapes.texture(p.getUuid().toString(), CosmeticsFeature.wearingOf(p), self);
        if (tex != null) cir.setReturnValue(tex);
    }
}

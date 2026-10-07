package dev.cobra.client.fabric.mixin;

import dev.cobra.client.core.Cobra;
import dev.cobra.client.fabric.cosmetics.CobraCapes;
import dev.cobra.client.fabric.cosmetics.CosmeticsFeature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * An Abyss cape replaces the player's cape texture right before Minecraft draws the cape, so it
 * gets the real cape model and physics (and nothing clips into the player).
 */
@Mixin(CapeFeatureRenderer.class)
public abstract class CapeFeatureRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/PlayerEntityRenderState;FF)V",
            at = @At("HEAD"), require = 0)
    private void cobra$ownCape(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state,
                               float a, float b, CallbackInfo ci) {
        if (Cobra.platform == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        Entity e = mc.world == null ? null : mc.world.getEntityById(state.id);
        if (!(e instanceof PlayerEntity p)) return;
        boolean self = mc.player != null && p.getUuid().equals(mc.player.getUuid());
        AssetInfo.TextureAssetInfo cape = CobraCapes.asset(p.getUuid().toString(), CosmeticsFeature.wearingOf(p), self);
        if (cape == null) return;
        state.skinTextures = state.skinTextures.withOverride(SkinTextures.SkinOverride.create(Optional.empty(), Optional.of(cape), Optional.of(cape), Optional.empty()));
        state.capeVisible = true;
    }
}

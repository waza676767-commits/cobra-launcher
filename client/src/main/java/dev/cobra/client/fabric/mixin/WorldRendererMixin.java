package dev.cobra.client.fabric.mixin;

import dev.cobra.client.core.Cobra;
import dev.cobra.client.core.module.Features;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    /** Block Overlay: outline colour (the fill isn't on 1.21.11 yet). */
    @ModifyVariable(method = "drawBlockOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int cobra$outlineColor(int color) {
        if (Cobra.platform == null) return color;
        Features.BlockOverlay bo = Cobra.get(Features.BlockOverlay.class);
        if (!bo.isEnabled()) return color;
        return bo.outline.on() ? bo.outlineArgb() : 0;
    }
}

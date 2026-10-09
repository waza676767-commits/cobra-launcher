package dev.life.client.fabric.mixin;

import dev.life.client.core.Life;
import dev.life.client.core.module.Features;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    /** Block Overlay: outline colour (the fill isn't on 1.21.11 yet). */
    @ModifyVariable(method = "drawBlockOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int life$outlineColor(int color) {
        if (Life.platform == null) return color;
        Features.BlockOverlay bo = Life.get(Features.BlockOverlay.class);
        if (!bo.isEnabled()) return color;
        return bo.outline.on() ? bo.outlineArgb() : 0;
    }

    /** Block Overlay: outline width. */
    @ModifyVariable(method = "drawBlockOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private float life$outlineWidth(float width) {
        if (Life.platform == null) return width;
        Features.BlockOverlay bo = Life.get(Features.BlockOverlay.class);
        return bo.isEnabled() ? bo.width.f() : width;
    }
}

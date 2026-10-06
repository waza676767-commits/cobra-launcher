package dev.cobra.client.fabric.compat;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

/** Render layers on Minecraft 1.21.9 and 1.21.10 (still on RenderLayer itself there). */
public final class Layers {
    private Layers() {}

    public static RenderLayer cutout(Identifier tex) { return RenderLayer.getEntityCutoutNoCull(tex); }

    public static RenderLayer translucent(Identifier tex) { return RenderLayer.getEntityTranslucent(tex); }
}

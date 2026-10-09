package dev.life.client.fabric.compat;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.util.Identifier;

/** Render layers on Minecraft 1.21.11 (they moved to RenderLayers in this version). */
public final class Layers {
    private Layers() {}

    public static RenderLayer cutout(Identifier tex) { return RenderLayers.entityCutoutNoCull(tex); }

    public static RenderLayer translucent(Identifier tex) { return RenderLayers.entityTranslucent(tex); }
}

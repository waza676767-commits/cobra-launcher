package dev.life.client.fabric;

import dev.life.client.core.Life;
import dev.life.client.core.module.Features;
import net.minecraft.client.render.fog.FogData;

/** Visual Tweaks on 1.21.11: pushes a switched-off kind of fog out of sight. */
public final class FogTweaks {
    private FogTweaks() {}

    public static void apply(String kind, FogData data) {
        if (Life.platform == null) return;
        Features.Tweaks t = Life.get(Features.Tweaks.class);
        if (!t.isEnabled()) return;
        boolean terrain = kind.equals("terrain");
        if (t.noFog(kind) || terrain && t.noFog("thick")) {
            data.environmentalStart = 1.0E7f;
            data.environmentalEnd = 1.0E7f + 1;
        }
        if (terrain && t.noFog("terrain")) {
            data.renderDistanceStart = 1.0E7f;
            data.renderDistanceEnd = 1.0E7f + 1;
        }
        if (terrain && t.noFog("sky")) {
            data.skyEnd = 1.0E7f;
            data.cloudEnd = 1.0E7f;
        }
    }
}

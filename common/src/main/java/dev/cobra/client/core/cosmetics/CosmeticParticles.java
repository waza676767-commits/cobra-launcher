package dev.cobra.client.core.cosmetics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Cobra's own cosmetic particles (not Minecraft's, so they show even with Particles: Minimal):
 * glowing little sparkles, hearts, embers, snowflakes, petals… that drift off the wings (or the
 * halo / body) and fade out, plus a trail at your feet while you walk. Drawn in the player's body
 * space with full brightness and a soft glow.
 */
public final class CosmeticParticles {
    private static final class P {
        float x, y, z, vx, vy, vz, age, life, size, spin;
        int colour;
    }

    private static final Map<String, List<P>> BY_PLAYER = new HashMap<String, List<P>>();
    private static final Map<String, Long> LAST = new HashMap<String, Long>();
    private static final Random RND = new Random();

    private CosmeticParticles() {}

    /** Colours for each effect (a couple each, picked at random per particle). */
    private static int[] colours(String fx) {
        if ("hearts".equals(fx)) return new int[]{0xFFFF4D6D, 0xFFFF8FA3};
        if ("flames".equals(fx)) return new int[]{0xFFFFB347, 0xFFFF6B1A, 0xFFFFD27A};
        if ("soulfire".equals(fx)) return new int[]{0xFF5FE1F0, 0xFF2BB5D9, 0xFFB8F6FF};
        if ("snow".equals(fx)) return new int[]{0xFFFFFFFF, 0xFFDDEBFF};
        if ("magic".equals(fx)) return new int[]{0xFFB565FF, 0xFFE09BFF, 0xFF7A3CFF};
        if ("petals".equals(fx)) return new int[]{0xFFFFB7D5, 0xFFFF8CC6, 0xFFFFE0EE};
        if ("notes".equals(fx)) return new int[]{0xFF66E08A, 0xFF4CC3FF, 0xFFFFD84C};
        return new int[]{0xFFFFF4C2, 0xFFFFFFFF, 0xFFFFD86B};          // sparkles
    }

    /**
     * Advances this player's particles (spawning new ones) and draws them. Coordinates are the
     * body's (pixels; y down, back at +z). {@code walking} 0..1 adds the foot trail.
     */
    public static void render(String player, String fx, boolean wings, boolean halo, boolean trail, float walking,
                              CosmeticModels.M body, CosmeticModels.Out out) {
        long now = System.currentTimeMillis();
        Long last = LAST.get(player);
        float dt = last == null ? 0.016f : Math.min(0.1f, (now - last) / 1000f);
        LAST.put(player, now);
        List<P> list = BY_PLAYER.get(player);
        if (list == null) {
            list = new ArrayList<P>();
            BY_PLAYER.put(player, list);
        }
        if (BY_PLAYER.size() > 64) {                                    // forget players long gone
            BY_PLAYER.clear();
            LAST.clear();
        }
        int[] cols = colours(fx);
        boolean falling = "snow".equals(fx) || "petals".equals(fx);
        // spawn: about 14 a second around you, plus the trail while walking
        float rate = 14f * dt;
        while (rate > 0) {
            if (RND.nextFloat() < rate) list.add(spawn(wings, halo, falling, cols));
            rate -= 1f;
        }
        if (trail && walking > 0.15f && RND.nextFloat() < 20f * dt * walking) {
            P p = new P();
            p.x = (RND.nextFloat() - 0.5f) * 6f;
            p.y = 23f;                                                   // the feet
            p.z = (RND.nextFloat() - 0.5f) * 4f;
            p.vx = 0;
            p.vy = -2f;
            p.vz = 6f + RND.nextFloat() * 3f;                            // left behind you
            p.life = 1.2f;
            p.size = 0.7f + RND.nextFloat() * 0.5f;
            p.colour = cols[RND.nextInt(cols.length)];
            list.add(p);
        }
        Iterator<P> it = list.iterator();
        while (it.hasNext()) {
            P p = it.next();
            p.age += dt;
            if (p.age >= p.life) {
                it.remove();
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.z += p.vz * dt;
            p.vx *= 0.98f;
            p.vz *= 0.98f;
            p.spin += dt * 2.5f;
            draw(body, out, p, fx);
        }
        while (list.size() > 120) list.remove(0);
    }

    private static P spawn(boolean wings, boolean halo, boolean falling, int[] cols) {
        P p = new P();
        if (wings) {                                                     // off the wing area behind the shoulders
            float side = RND.nextBoolean() ? 1 : -1;
            p.x = side * (3f + RND.nextFloat() * 16f);
            p.y = -4f + RND.nextFloat() * 18f;
            p.z = 4f + RND.nextFloat() * 4f;
        } else if (halo) {
            double a = RND.nextDouble() * Math.PI * 2;
            p.x = (float) Math.cos(a) * 6f;
            p.y = -21f;
            p.z = (float) Math.sin(a) * 6f;
        } else {                                                         // around you
            double a = RND.nextDouble() * Math.PI * 2;
            p.x = (float) Math.cos(a) * 9f;
            p.y = -8f + RND.nextFloat() * 30f;
            p.z = (float) Math.sin(a) * 9f;
        }
        p.vx = (RND.nextFloat() - 0.5f) * 2f;
        p.vy = falling ? 3f + RND.nextFloat() * 2f : -(3f + RND.nextFloat() * 3f);   // y is down: negative rises
        p.vz = (RND.nextFloat() - 0.5f) * 2f + 1f;
        p.life = 1.4f + RND.nextFloat() * 1.2f;
        p.size = 0.6f + RND.nextFloat() * 0.7f;
        p.colour = cols[RND.nextInt(cols.length)];
        return p;
    }

    /** A sparkle: three crossed slivers (so it looks right from any side) in a soft glow. */
    private static void draw(CosmeticModels.M body, CosmeticModels.Out out, P p, String fx) {
        float t = p.age / p.life;
        float fade = t < 0.15f ? t / 0.15f : 1f - (t - 0.15f) / 0.85f;          // fade in, then out
        float s = p.size * (0.6f + 0.4f * fade);
        int a = Math.max(0, Math.min(255, Math.round(255 * fade)));
        int core = (a << 24) | (p.colour & 0xFFFFFF);
        int glow = (Math.round(a * 0.28f) << 24) | (p.colour & 0xFFFFFF);
        CosmeticModels.M m = body.copy().translate(p.x, p.y, p.z).rotY(p.spin).rotX(p.spin * 0.7f);
        int light = CosmeticModels.BRIGHT;
        if ("hearts".equals(fx)) {                                        // a little heart: two lobes and a point
            CosmeticModels.box(m, out, light, core, -s, -s * 0.6f, -0.12f, -0.05f, s * 0.2f, 0.12f);
            CosmeticModels.box(m, out, light, core, 0.05f, -s * 0.6f, -0.12f, s, s * 0.2f, 0.12f);
            CosmeticModels.box(m, out, light, core, -s * 0.55f, s * 0.2f, -0.12f, s * 0.55f, s * 0.9f, 0.12f);
        } else {
            CosmeticModels.box(m, out, light, core, -s, -0.12f, -0.12f, s, 0.12f, 0.12f);
            CosmeticModels.box(m, out, light, core, -0.12f, -s, -0.12f, 0.12f, s, 0.12f);
            CosmeticModels.box(m, out, light, core, -0.12f, -0.12f, -s, 0.12f, 0.12f, s);
        }
        float gs = s * 0.55f;
        CosmeticModels.box(m, out, light, glow, -gs, -gs, -gs, gs, gs, gs);
    }
}

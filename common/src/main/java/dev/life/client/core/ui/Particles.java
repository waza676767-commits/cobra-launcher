package dev.life.client.core.ui;

import dev.life.client.core.Life;
import dev.life.client.core.Render;
import dev.life.client.core.module.Features;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Click burst for Life's screens: a ring and a few dots, ivory on dark / charcoal on light. */
public final class Particles {
    private static final long LIFE = 480_000_000L;
    private static final Random RNG = new Random();
    private final List<float[]> bursts = new ArrayList<float[]>();

    public void burst(float x, float y) {
        if (!Life.animations || !Life.get(Features.Client.class).particles.on()) return;
        float[] b = new float[2 + 1 + 7 * 2];
        b[0] = x;
        b[1] = y;
        b[2] = Float.intBitsToFloat(0); // unused
        float base = RNG.nextFloat() * 6.283f;
        for (int i = 0; i < 7; i++) {
            float a = base + i * 6.283f / 7 + (RNG.nextFloat() - 0.5f) * 0.5f;
            b[3 + i * 2] = a;
            b[4 + i * 2] = 9 + RNG.nextFloat() * 7;
        }
        bursts.add(b);
        born.add(System.nanoTime());
    }

    private final List<Long> born = new ArrayList<Long>();

    public void render(Render r) {
        long now = System.nanoTime();
        Iterator<float[]> it = bursts.iterator();
        Iterator<Long> bt = born.iterator();
        while (it.hasNext()) {
            float[] b = it.next();
            long start = bt.next();
            float t = (now - start) / (float) LIFE;
            if (t >= 1) {
                it.remove();
                bt.remove();
                continue;
            }
            float e = 1 - (1 - t) * (1 - t) * (1 - t);
            int col = Draw.alpha(Draw.FG, (1 - t) * 0.9f);
            // ring
            float rr = 2 + 7 * e;
            for (int k = 0; k < 16; k++) {
                double a = k * Math.PI * 2 / 16;
                r.rect((int) Math.round(b[0] + Math.cos(a) * rr), (int) Math.round(b[1] + Math.sin(a) * rr), 1, 1, Draw.alpha(Draw.FG, (1 - t) * 0.5f));
            }
            // dots
            for (int i = 0; i < 7; i++) {
                float a = b[3 + i * 2], d = b[4 + i * 2] * e;
                int px = Math.round(b[0] + (float) Math.cos(a) * d), py = Math.round(b[1] + (float) Math.sin(a) * d);
                int size = t < 0.5f ? 2 : 1;
                Draw.round(r, px, py, size, size, 0, col);
            }
        }
    }
}

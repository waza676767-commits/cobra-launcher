package dev.life.launcher.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Random;

/**
 * The Life background: glossy liquid poured across deep water, in the logo's colours. Bands of
 * azure, cyan and foam white flow diagonally across the window (like the swirls behind the LIFE
 * letters), each pour with a bright wet rim and a soft shadow under its edge. The glass panels
 * blur it, so they glow with whatever liquid is behind them.
 *
 * <p>It's computed once per window size at a low resolution (about 640 px wide: a few tens of
 * milliseconds) and scaled up smoothly, which also softens it into a liquid blur.
 */
final class Liquid {
    private Liquid() {}

    private static final int G = 64;
    private static final float[] GRID = new float[(G + 1) * (G + 1)];

    static {
        Random r = new Random(3);
        for (int i = 0; i < GRID.length; i++) GRID[i] = r.nextFloat();
    }

    private static BufferedImage field;
    private static int fieldW, fieldH;

    /** Paints the liquid over the whole w x h area. */
    static void paint(Graphics2D g, int w, int h) {
        BufferedImage f = field(w, h);
        Graphics2D d = (Graphics2D) g.create();
        d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        d.drawImage(f, 0, 0, w, h, null);
        d.dispose();
    }

    private static synchronized BufferedImage field(int w, int h) {
        // always 640 wide; the height only steps every 24 px of shape change, so resizing the window
        // mostly reuses the same picture (it's simply scaled) instead of computing a new one
        int gw = 640, gh = Math.max(48, Math.round(gw * h / (float) Math.max(1, w) / 24f) * 24);
        if (field != null && fieldW == gw && fieldH == gh) return field;
        field = render(gw, gh);
        fieldW = gw;
        fieldH = gh;
        return field;
    }

    // ------------------------------------------------------------------ the picture

    private static final int BASE = 0x030C18;
    /** Each poured layer: where it starts (in the flow value), how sharp its edge is, its colour. */
    private static final float[][] LAYERS = {
            {0.50f, 0.010f, 0x0B3A72}, {0.60f, 0.008f, 0x1677D6}, {0.69f, 0.007f, 0x2BC3FA},
            {0.79f, 0.006f, 0xCFF6FF}, {0.86f, 0.005f, 0xFFFFFF}};

    static BufferedImage render(int W, int H) {
        float[] val = new float[W * H], qxs = new float[W * H], qys = new float[W * H], wvs = new float[W * H];
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        float ref = H * 1.6f;                                  // shapes keep their size whatever the window's shape
        for (int py = 0; py < H; py++) {
            for (int px = 0; px < W; px++) {
                float nx = px / ref, ny = py / (float) H;
                // stretched along a diagonal current: long, poured shapes instead of round blobs
                float u = nx * 0.8f + ny * 0.6f, wv = nx * 0.6f - ny * 0.8f;
                float x = u * 1.1f, y = wv * 3.2f;
                float qx = fbm(x, y, 3), qy = fbm(x + 5.2f, y + 1.3f, 3);
                float v = fbm(x + 1.8f * qx, y + 1.8f * qy, 4);
                int i = py * W + px;
                val[i] = v;
                qxs[i] = qx;
                qys[i] = qy;
                wvs[i] = wv;
                if (v < min) min = v;
                if (v > max) max = v;
            }
        }
        float range = Math.max(1e-4f, max - min);
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        int[] px = ((DataBufferInt) out.getRaster().getDataBuffer()).getData();
        float br = BASE >> 16 & 255, bg = BASE >> 8 & 255, bb = BASE & 255;
        for (int py = 0; py < H; py++) {
            for (int pxx = 0; pxx < W; pxx++) {
                int i = py * W + pxx;
                float v = (val[i] - min) / range, wv = wvs[i];
                // two currents of liquid across the window
                float a1 = wv + 0.1f + 0.2f * (qxs[i] - 0.5f), a2 = wv - 0.42f + 0.2f * (qys[i] - 0.5f);
                float flow = (float) (Math.exp(-a1 * a1 / 0.03) + 0.8 * Math.exp(-a2 * a2 / 0.025));
                v = clamp(v * 0.72f + flow * 0.36f);
                float r = br, g = bg, b = bb;
                for (float[] l : LAYERS) {
                    float t = l[0], wd = l[1];
                    int c = (int) l[2];
                    float edge = ss(t - wd, t + wd, v);
                    float shadow = ss(t - 0.05f, t, v) * (1 - edge);          // soft shadow just outside the pour
                    r *= 1 - 0.35f * shadow;
                    g *= 1 - 0.35f * shadow;
                    b *= 1 - 0.35f * shadow;
                    float rim = edge * (1 - ss(t + wd, t + 0.03f, v));         // bright wet rim just inside
                    float cr = c >> 16 & 255, cg = c >> 8 & 255, cb = c & 255;
                    cr += (255 - cr) * 0.45f * rim;
                    cg += (255 - cg) * 0.45f * rim;
                    cb += (255 - cb) * 0.45f * rim;
                    r = r * (1 - edge) + cr * edge;
                    g = g * (1 - edge) + cg * edge;
                    b = b * (1 - edge) + cb * edge;
                }
                // darker towards the edges of the window
                float vx = pxx / (float) W - 0.5f, vy = py / (float) H - 0.5f;
                float vig = 1 - 0.62f * clamp((vx * vx * 1.2f + vy * vy) * 2f);
                px[i] = (int) clamp255(r * vig) << 16 | (int) clamp255(g * vig) << 8 | (int) clamp255(b * vig);
            }
        }
        return out;
    }

    private static float vnoise(float x, float y) {
        float fx = (float) Math.floor(x), fy = (float) Math.floor(y);
        int xi = Math.floorMod((int) fx, G), yi = Math.floorMod((int) fy, G);
        float xf = x - fx, yf = y - fy;
        float u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf);
        int x1 = (xi + 1) % G, y1 = (yi + 1) % G;
        float a = GRID[yi * (G + 1) + xi], b = GRID[yi * (G + 1) + x1], c = GRID[y1 * (G + 1) + xi], d = GRID[y1 * (G + 1) + x1];
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
    }

    private static float fbm(float x, float y, int octaves) {
        float s = 0, amp = 0.5f, f = 1;
        for (int i = 0; i < octaves; i++) {
            s += amp * vnoise(x * f + i * 17.3f, y * f + i * 9.1f);
            amp *= 0.5f;
            f *= 2.03f;
        }
        return s;
    }

    private static float ss(float e0, float e1, float t) {
        float x = clamp((t - e0) / (e1 - e0));
        return x * x * (3 - 2 * x);
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static float clamp255(float v) {
        return v < 0 ? 0 : v > 255 ? 255 : v;
    }
}

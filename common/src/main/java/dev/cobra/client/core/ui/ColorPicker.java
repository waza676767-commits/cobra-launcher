package dev.cobra.client.core.ui;

import dev.cobra.client.core.Render;
import dev.cobra.client.core.module.Setting;

/** Colour wheel (hue around, saturation outward) + brightness and opacity bars + presets. */
public final class ColorPicker {
    public static final int W = 150, H = 118, R = 34;
    private static final int STEP = 2;

    private Setting.Color target;
    private float h, s, v = 1;
    private int a = 255;
    private int x, y;
    private int drag;              // 0 none, 1 wheel, 2 brightness, 3 opacity
    private int[] cache;
    private float cachedV = -1;

    public boolean isOpen() { return target != null; }

    public void open(Setting.Color c, int px, int py, int screenW, int screenH) {
        target = c;
        int argb = c.argb();
        float[] hsv = Draw.toHsv(argb);
        h = hsv[0];
        s = hsv[1];
        v = hsv[2];
        a = argb >>> 24;
        x = Math.max(4, Math.min(px, screenW - W - 4));
        y = Math.max(4, Math.min(py, screenH - H - 4));
    }

    public void close() {
        target = null;
        drag = 0;
    }

    private void apply() {
        if (target != null) target.set(Draw.hsv(h, s, v, a));
    }

    private int wheelX() { return x + 8 + R; }
    private int wheelY() { return y + 8 + R; }
    private int barX() { return x + 8 + R * 2 + 10; }
    private int alphaX() { return barX() + 14; }

    public void render(Render r, int mx, int my) {
        if (target == null) return;
        Draw.box(r, x, y, W, H, 4, Draw.PANEL, Draw.LINE_2);
        int cx = wheelX(), cy = wheelY();
        if (cache == null || cachedV != v) buildCache();
        int i = 0;
        for (int dy = -R; dy < R; dy += STEP) {
            for (int dx = -R; dx < R; dx += STEP) {
                int col = cache[i++];
                if (col != 0) r.rect(cx + dx, cy + dy, STEP, STEP, col);
            }
        }
        // marker
        double ang = h * Math.PI * 2;
        int mxp = (int) Math.round(cx + Math.cos(ang) * s * R), myp = (int) Math.round(cy + Math.sin(ang) * s * R);
        Draw.outline(r, mxp - 2, myp - 2, 5, 5, 0xFF000000);
        Draw.outline(r, mxp - 1, myp - 1, 3, 3, 0xFFFFFFFF);

        // brightness bar
        int bx = barX(), top = y + 8, bh = R * 2;
        for (int k = 0; k < bh; k += 2) r.rect(bx, top + k, 8, 2, Draw.hsv(h, s, 1 - k / (float) bh, 255));
        int vy = top + Math.round((1 - v) * (bh - 1));
        r.rect(bx - 1, vy - 1, 10, 3, 0xFFFFFFFF);
        r.rect(bx - 1, vy, 10, 1, 0xFF000000);
        // opacity bar over a checkerboard
        int ax = alphaX();
        Draw.checker(r, ax, top, 8, bh);
        for (int k = 0; k < bh; k += 2) r.rect(ax, top + k, 8, 2, Draw.hsv(h, s, v, Math.round(255 * (1 - k / (float) bh))));
        int ay = top + Math.round((1 - a / 255f) * (bh - 1));
        r.rect(ax - 1, ay - 1, 10, 3, 0xFFFFFFFF);
        r.rect(ax - 1, ay, 10, 1, 0xFF000000);

        // presets
        int[] pr = Setting.Color.PRESETS;
        int px = x + 8, py = y + 8 + bh + 6;
        for (int k = 0; k < pr.length; k++) {
            int sx = px + k * 10;
            if (sx + 8 > x + W - 8) break;
            r.rect(sx, py, 8, 8, pr[k]);
        }
        // preview + hex
        int col = Draw.hsv(h, s, v, a);
        Draw.checker(r, x + W - 44, y + H - 22, 12, 12);
        r.rect(x + W - 44, y + H - 22, 12, 12, col);
        String hex = String.format("#%08X", col);
        r.push();
        r.translate(x + 8, y + H - 19);
        r.scale(0.75f);
        r.text(hex, 0, 0, Draw.SOFT, false);
        r.pop();
        boolean hov = in(mx, my, x + W - 28, y + H - 22, 20, 12);
        Draw.round(r, x + W - 28, y + H - 22, 20, 12, 3, hov ? Draw.mix(Draw.FG, Draw.SOFT, 0.3f) : Draw.FG);
        Draw.label(r, "OK", x + W - 18, y + H - 16, 0.7f, 0.5f, Draw.BG);
    }

    private void buildCache() {
        int n = (R * 2 / STEP) * (R * 2 / STEP);
        cache = new int[n];
        int i = 0;
        for (int dy = -R; dy < R; dy += STEP) {
            for (int dx = -R; dx < R; dx += STEP) {
                double d = Math.sqrt((dx + 1) * (dx + 1) + (dy + 1) * (dy + 1));
                if (d > R) {
                    cache[i++] = 0;
                    continue;
                }
                float hue = (float) ((Math.atan2(dy + 1, dx + 1) / (Math.PI * 2) + 1) % 1);
                cache[i++] = Draw.hsv(hue, (float) Math.min(1, d / R), v, 255);
            }
        }
        cachedV = v;
    }

    /** @return true when the click was inside the picker */
    public boolean click(int mx, int my) {
        if (target == null) return false;
        if (!in(mx, my, x, y, W, H)) {
            close();
            return false;
        }
        if (in(mx, my, x + W - 28, y + H - 22, 20, 12)) {
            close();
            return true;
        }
        int[] pr = Setting.Color.PRESETS;
        int py = y + 8 + R * 2 + 6;
        for (int k = 0; k < pr.length; k++) {
            int sx = x + 8 + k * 10;
            if (in(mx, my, sx, py, 8, 8)) {
                float[] hsv = Draw.toHsv(pr[k]);
                h = hsv[0];
                s = hsv[1];
                v = hsv[2];
                a = 255;
                apply();
                return true;
            }
        }
        int dx = mx - wheelX(), dy = my - wheelY();
        if (dx * dx + dy * dy <= (R + 2) * (R + 2)) drag = 1;
        else if (in(mx, my, barX() - 2, y + 8, 12, R * 2)) drag = 2;
        else if (in(mx, my, alphaX() - 2, y + 8, 12, R * 2)) drag = 3;
        drag(mx, my);
        return true;
    }

    public void drag(int mx, int my) {
        if (target == null || drag == 0) return;
        if (drag == 1) {
            double dx = mx - wheelX(), dy = my - wheelY();
            h = (float) ((Math.atan2(dy, dx) / (Math.PI * 2) + 1) % 1);
            s = (float) Math.min(1, Math.sqrt(dx * dx + dy * dy) / R);
        } else if (drag == 2) {
            v = 1 - Math.max(0, Math.min(1, (my - y - 8) / (float) (R * 2)));
        } else {
            a = Math.round(255 * (1 - Math.max(0, Math.min(1, (my - y - 8) / (float) (R * 2)))));
        }
        apply();
    }

    public void release() {
        drag = 0;
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}

package dev.cobra.client.core.ui;

import dev.cobra.client.core.Render;

public final class Draw {
    public static final int WHITE = 0xFFFFFFFF, BLACK = 0xFF000000;
    /** Palette: dark by default, flipped by {@link #setLight(boolean)} (set from the launcher's Light mode). */
    public static int FG, BG, PANEL, CARD, CARD_HOVER, LINE, LINE_2, MUTED, SOFT, INPUT, BTN, BTN_HOVER, BTN_LINE, DIM, HOVER;
    public static boolean light;

    static {
        setLight(false);
    }

    public static void setLight(boolean on) {
        light = on;
        if (on) {
            // Ivory / charcoal
            FG = 0xFF2D2D2D; BG = 0xFFF8F5F2; PANEL = 0xF6F8F5F2; CARD = 0xFFEFEBE6; CARD_HOVER = 0xFFE6E1DB;
            LINE = 0xFFDDD7CF; LINE_2 = 0xFFCBC4BB; MUTED = 0xFF8C857C; SOFT = 0xFF5B5650; INPUT = 0xFFFFFFFF;
            BTN = 0xFFEBE6E0; BTN_HOVER = 0xFFE0DAD2; BTN_LINE = 0xFF9C958C; DIM = 0x66F8F5F2; HOVER = 0x10000000;
        } else {
            // Charcoal / ivory
            FG = 0xFFF8F5F2; BG = 0xFF121212; PANEL = 0xF01C1C1C; CARD = 0xFF2A2A2A; CARD_HOVER = 0xFF333333;
            LINE = 0xFF333333; LINE_2 = 0xFF454545; MUTED = 0xFF7C7A77; SOFT = 0xFFA8A5A1; INPUT = 0xFF141414;
            BTN = 0xFF262626; BTN_HOVER = 0xFF3A3A3A; BTN_LINE = 0xFF6A6A6A; DIM = 0x66000000; HOVER = 0x12FFFFFF;
        }
    }

    /**
     * Your own theme: a background colour and a text colour; everything else (cards, lines, muted
     * text, buttons) is mixed from those two so it always stays readable.
     */
    public static void setCustom(int bg, int fg) {
        bg |= 0xFF000000;
        fg |= 0xFF000000;
        int lum = ((bg >> 16 & 255) * 299 + (bg >> 8 & 255) * 587 + (bg & 255) * 114) / 1000;
        light = lum > 140;
        FG = fg;
        BG = bg;
        PANEL = (0xF0 << 24) | (bg & 0xFFFFFF);
        CARD = mix(bg, fg, 0.08f);
        CARD_HOVER = mix(bg, fg, 0.14f);
        LINE = mix(bg, fg, 0.14f);
        LINE_2 = mix(bg, fg, 0.22f);
        MUTED = mix(bg, fg, 0.45f);
        SOFT = mix(bg, fg, 0.68f);
        INPUT = mix(bg, 0xFF000000, light ? 0f : 0.25f);
        BTN = mix(bg, fg, 0.10f);
        BTN_HOVER = mix(bg, fg, 0.18f);
        BTN_LINE = mix(bg, fg, 0.38f);
        DIM = (0x66 << 24) | (bg & 0xFFFFFF);
        HOVER = (0x14 << 24) | (fg & 0xFFFFFF);
    }

    /** HUD glass strength (Cobra Settings → HUD glass strength), 1 = normal. */
    public static float glassK = 1f;

    private static int scaleAlpha(int argb, float k) {
        int a = Math.max(0, Math.min(255, Math.round((argb >>> 24) * k)));
        return a << 24 | (argb & 0xFFFFFF);
    }

    private Draw() {}

    public static void round(Render r, int x, int y, int w, int h, int rad, int c) {
        rad = Math.min(rad, Math.min(w, h) / 2);
        if (rad <= 0) {
            r.rect(x, y, w, h, c);
            return;
        }
        r.rect(x, y + rad, w, h - 2 * rad, c);
        for (int i = 0; i < rad; i++) {
            double dy = rad - i - 0.5;
            int inset = rad - (int) Math.round(Math.sqrt(rad * rad - dy * dy));
            r.rect(x + inset, y + i, w - 2 * inset, 1, c);
            r.rect(x + inset, y + h - 1 - i, w - 2 * inset, 1, c);
        }
    }

    /** Rounded box with a 1px border (fill should be near-opaque). */
    public static void box(Render r, int x, int y, int w, int h, int rad, int fill, int border) {
        round(r, x, y, w, h, rad, border);
        round(r, x + 1, y + 1, w - 2, h - 2, Math.max(0, rad - 1), fill);
    }

    public static void outline(Render r, int x, int y, int w, int h, int c) {
        r.rect(x, y, w, 1, c);
        r.rect(x, y + h - 1, w, 1, c);
        r.rect(x, y + 1, 1, h - 2, c);
        r.rect(x + w - 1, y + 1, 1, h - 2, c);
    }

    public static int alpha(int argb, float a) {
        int al = (int) (((argb >>> 24) & 0xFF) * Math.max(0, Math.min(1, a)));
        return (al << 24) | (argb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return (int) (aa + (ba - aa) * t) << 24 | (int) (ar + (br - ar) * t) << 16 | (int) (ag + (bg - ag) * t) << 8 | (int) (ab + (bb - ab) * t);
    }

    public static void scaledText(Render r, String s, float x, float y, float scale, int color, boolean shadow) {
        r.push();
        r.translate(x, y);
        r.scale(scale);
        r.text(s, 0, 0, color, shadow);
        r.pop();
    }

    public static void centered(Render r, String s, float cx, float y, int color, boolean shadow) {
        r.text(s, cx - r.textWidth(s) / 2f, y, color, shadow);
    }

    /** Pill switch like the launcher: off = outlined pill with white knob, on = white pill with black knob. t in [0,1]. */
    public static void toggle(Render r, int x, int y, float t, int bg) {
        int w = 18, h = 10;
        round(r, x, y, w, h, 5, mix(light ? 0xFF8A8B92 : 0xFF8A8B8F, FG, t));
        round(r, x + 1, y + 1, w - 2, h - 2, 4, mix(bg, FG, t));
        int kx = x + 2 + Math.round((w - 10) * t);
        round(r, kx, y + 2, 6, 6, 3, mix(FG, BG, t));
    }

    /** Letter-spaced text (Lunar-style labels). Returns the drawn width. */
    /** Letter-spaced text. Formatting codes (e.g. \u00a7l bold) carry over to every letter. */
    public static int spaced(Render r, String s, float x, float y, float spacing, int color, boolean shadow) {
        float cx = x;
        String codes = "";
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7' && i + 1 < s.length()) {
                codes += "\u00a7" + s.charAt(++i);
                continue;
            }
            String ch = codes + c;
            r.text(ch, cx, y, color, shadow);
            cx += r.textWidth(ch) + spacing;
        }
        return Math.round(cx - x - spacing);
    }

    public static int spacedWidth(Render r, String s, float spacing) {
        float w = 0;
        String codes = "";
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7' && i + 1 < s.length()) {
                codes += "\u00a7" + s.charAt(++i);
                continue;
            }
            w += r.textWidth(codes + c) + spacing;
        }
        return Math.round(w - spacing);
    }

    /** Scaled, letter-spaced label centred in a box. */
    public static void label(Render r, String s, float cx, float cy, float scale, float spacing, int color) {
        float w = spacedWidth(r, s, spacing) * scale;
        r.push();
        r.translate(cx - w / 2f, cy - 4 * scale);
        r.scale(scale);
        spaced(r, s, 0, 0, spacing, color, false);
        r.pop();
    }

    public static int hsv(float h, float s, float v, int alpha) {
        return (alpha & 255) << 24 | (java.awt.Color.HSBtoRGB(h, s, v) & 0xFFFFFF);
    }

    public static float[] toHsv(int argb) {
        return java.awt.Color.RGBtoHSB(argb >> 16 & 255, argb >> 8 & 255, argb & 255, null);
    }

    /** Transparency checkerboard (for colour swatches with alpha). */
    public static void checker(Render r, int x, int y, int w, int h) {
        for (int yy = 0; yy < h; yy += 3) {
            for (int xx = 0; xx < w; xx += 3) {
                r.rect(x + xx, y + yy, Math.min(3, w - xx), Math.min(3, h - yy), ((xx + yy) / 3 & 1) == 0 ? 0xFFBDBDBD : 0xFF7E7E7E);
            }
        }
    }

    /** Menu animation speed (Cobra Settings → Menu animation speed), kept here so it's cheap to read. */
    public static float speedK = 1f;

    public static float approach(float cur, float target, float speed) {
        if (!dev.cobra.client.core.Cobra.animations) return target;
        float d = target - cur;
        if (Math.abs(d) < 0.01f) return target;
        return cur + d * Math.min(1f, speed * speedK);
    }

    /**
     * Liquid Glass panel for HUD elements, built from plain GUI fills so it costs next to nothing:
     * a soft floating shadow, a see-through tint (your background colour), a lighter top half for
     * depth, and a 1 px specular edge along the top and left like light catching glass.
     */
    public static void glassPanel(Render r, int x, int y, int w, int h, int radius, int tint) {
        if (w <= 0 || h <= 0) return;
        // floating shadow: three widening, fading layers, offset down
        for (int i = 3; i >= 1; i--) {
            round(r, x - i + 1, y - i + 3, w + 2 * i - 2, h + 2 * i - 2, radius + i, (0x10 + 4 * (3 - i)) << 24);
        }
        float k = glassK;
        int a = tint >>> 24;
        int base = (Math.max(0x18, Math.min(0xE0, Math.round(Math.max(0x30, Math.min(0xB0, a)) * Math.max(0.3f, k)))) << 24) | (tint & 0xFFFFFF);
        round(r, x, y, w, h, radius, base);
        // depth: the top half a touch lighter, the bottom edge a touch darker
        int top = Math.max(1, h / 2);
        round(r, x, y, w, top, radius, scaleAlpha(0x14FFFFFF, k));
        r.rect(x + radius, y + h - 1, Math.max(0, w - 2 * radius), 1, scaleAlpha(0x33000000, k));
        // specular edge
        r.rect(x + radius, y, Math.max(0, w - 2 * radius), 1, scaleAlpha(light ? 0xBFFFFFFF : 0x40FFFFFF, k));
        r.rect(x, y + radius, 1, Math.max(0, h / 2 - radius), scaleAlpha(light ? 0x80FFFFFF : 0x26FFFFFF, k));
    }
}

package dev.cobra.launcher.ui;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class Theme {
    /** Dark: Royal Charcoal + grayscale ramp from the reference boards. Light: the same set inverted. */
    public static Color BLACK = new Color(0x000000);   // canvas / ink on accent
    public static Color PANEL = new Color(0x171717);
    public static Color PANEL_2 = new Color(0x0A0A0A);
    public static Color RAISED = new Color(0x262626);
    public static Color LINE = new Color(0x262626);
    public static Color LINE_2 = new Color(0x373737);
    public static Color MID = new Color(0x525252);
    public static Color STEEL = new Color(0x525252);
    public static Color MUTED = new Color(0x8A8A8A);
    public static Color SOFT = new Color(0xD4D4D4);
    public static Color TEXT = new Color(0xFAFAFA);    // accent / primary text
    public static Color DANGER = new Color(0xff6b6b);
    public static Color GLOW = new Color(0x1e1f22);    // centre of the background gradient
    public static Color EDGE = new Color(0x0a0a0b);    // outer ring of the background gradient

    /**
     * Colour themes for the whole launcher (and, if you want, the game). Each is a set of carefully
     * matched shades: a deep tinted black, panels a step lighter, lines, muted and soft text, near-white
     * text, and an accent that belongs to it. Values: base, panel, panel2, raised, line, line2, mid,
     * muted, soft, text, accent, glow (soft light in the background).
     */
    public static final String[] THEMES = {"Midnight", "Ocean", "Forest", "Sunset", "Rose", "Violet", "Graphite"};
    private static final int[][] PALETTES = {
            {0x000000, 0x171717, 0x0A0A0A, 0x262626, 0x262626, 0x373737, 0x525252, 0x8A8A8A, 0xD4D4D4, 0xFAFAFA, 0xFAFAFA, 0x3A3A3A},
            {0x050D17, 0x0E1B2C, 0x08121F, 0x15263C, 0x19304B, 0x24405F, 0x395A80, 0x7F97B5, 0xC9D6E6, 0xF2F7FC, 0x4DA3FF, 0x173A63},
            {0x04100A, 0x0C1D14, 0x07150E, 0x13291D, 0x183426, 0x234733, 0x3A6650, 0x83A593, 0xCFE0D6, 0xF1F8F4, 0x3DDC84, 0x14402A},
            {0x120806, 0x22120E, 0x180C09, 0x301A14, 0x3A2018, 0x502C22, 0x7A4535, 0xB48C80, 0xEAD6CF, 0xFFF6F2, 0xFF8A4C, 0x4A2012},
            {0x12060C, 0x221019, 0x180A11, 0x311724, 0x3B1C2C, 0x52283D, 0x7B3F5B, 0xB98AA0, 0xEFD5E1, 0xFFF4F8, 0xFF5C9A, 0x4A1430},
            {0x0A0714, 0x150F26, 0x0F0A1D, 0x1E1636, 0x251B42, 0x33265B, 0x4E3E86, 0x9A8FC2, 0xDDD7F2, 0xF7F5FF, 0x8B6CFF, 0x2C1E5E},
            {0x0B0C0E, 0x1A1C20, 0x121417, 0x24272C, 0x2A2D33, 0x3A3E45, 0x575C66, 0x8E949E, 0xD5D9DF, 0xF5F7FA, 0x9EC1E8, 0x2A3140}};

    /** The current theme's colours (index into THEMES). */
    public static int[] palette() {
        String t = dev.cobra.launcher.core.Settings.get().theme;
        for (int i = 0; i < THEMES.length; i++) if (THEMES[i].equals(t)) return PALETTES[i];
        return PALETTES[0];
    }

    public static int[] palette(int i) { return PALETTES[i]; }

    /** Accent (Launch button, selection pill, switches, sliders) and the ink drawn on top of it. */
    public static Color ACCENT = TEXT, ON_ACCENT = BLACK;

    /** Named accent presets for Settings → Appearance. "Classic" = ivory / charcoal. */
    public static final String[] ACCENTS = {"Classic", "Abyss green", "Ocean", "Violet", "Rose", "Sunset", "Gold", "Mint", "White", "Black", "Custom"};
    static final int[] ACCENT_RGB = {0, 0x3DDC84, 0x3EA6FF, 0x8B6CFF, 0xFF5C8A, 0xFF8A3D, 0xF5C542, 0x5EEAD4, 0xFFFFFF, 0x111111, 0};

    private static boolean light;

    /** Identifies the current custom gradient (for caches). */
    public static String gradientKey() {
        dev.cobra.launcher.core.Settings s = dev.cobra.launcher.core.Settings.get();
        return s.gradient ? s.gradientA + "/" + s.gradientB + "/" + s.gradientAngle : "off";
    }

    /**
     * Paints the custom background gradient (two colours at an angle, with a soft glow) if it's on.
     * Returns false when it's off, so the caller paints the normal background.
     */
    public static boolean paintGradient(Graphics2D g, int w, int h) {
        dev.cobra.launcher.core.Settings s = dev.cobra.launcher.core.Settings.get();
        if (!s.gradient) return false;
        double a = Math.toRadians(s.gradientAngle);
        double cx = w / 2.0, cy = h / 2.0, len = Math.abs(w * Math.cos(a)) / 2 + Math.abs(h * Math.sin(a)) / 2;
        float x1 = (float) (cx - Math.cos(a) * len), y1 = (float) (cy - Math.sin(a) * len);
        float x2 = (float) (cx + Math.cos(a) * len), y2 = (float) (cy + Math.sin(a) * len);
        Color ca = new Color(s.gradientA & 0xFFFFFF), cb = new Color(s.gradientB & 0xFFFFFF);
        g.setPaint(new LinearGradientPaint(x1, y1, x2, y2, new float[]{0f, 1f}, new Color[]{ca, cb}));
        g.fillRect(0, 0, w, h);
        // a little light in the upper middle so it doesn't look flat
        g.setPaint(new RadialGradientPaint(new java.awt.geom.Point2D.Double(w * 0.6, h * 0.3), (float) (h * 0.9),
                new float[]{0f, 1f}, new Color[]{new Color(255, 255, 255, 26), new Color(255, 255, 255, 0)}));
        g.fillRect(0, 0, w, h);
        return true;
    }

    /** Recomputes ACCENT / ON_ACCENT from the settings (call after setLight and when they change). */
    public static void applyAccent() {
        dev.cobra.launcher.core.Settings s = dev.cobra.launcher.core.Settings.get();
        int rgb = 0;
        if ("Kit green".equals(s.accent) || "Cobra green".equals(s.accent)) s.accent = "Abyss green";   // renamed
        for (int i = 0; i < ACCENTS.length; i++) if (ACCENTS[i].equals(s.accent)) rgb = ACCENT_RGB[i];
        if ("Custom".equals(s.accent)) rgb = s.accentCustom & 0xFFFFFF;
        if (rgb == 0) {                         // Classic: the theme's own accent
            int ta = palette()[10];
            if (light || ta == palette()[9]) {
                ACCENT = TEXT;
                ON_ACCENT = BLACK;
                return;
            }
            rgb = ta;
        }
        ACCENT = new Color(rgb);
        int lum = ((rgb >> 16 & 255) * 299 + (rgb >> 8 & 255) * 587 + (rgb & 255) * 114) / 1000;
        ON_ACCENT = lum > 150 ? new Color(0x121212) : new Color(0xFFFFFF);
    }

    public static final int LIGHT = 0, REGULAR = 1, MEDIUM = 2, BOLD = 3;
    private static final Font[] BASE = new Font[4];
    private static final Map<String, Font> FONTS = new HashMap<>();
    private static final Map<String, BufferedImage> IMAGES = new HashMap<>();
    private static final Map<String, BufferedImage> TINTED = new HashMap<>();

    static {
        String[] files = {"SpaceGrotesk-Light.ttf", "SpaceGrotesk-Regular.ttf", "SpaceGrotesk-Medium.ttf", "SpaceGrotesk-Bold.ttf"};
        for (int i = 0; i < files.length; i++) {
            try (InputStream in = Theme.class.getResourceAsStream("/fonts/" + files[i])) {
                BASE[i] = Font.createFont(Font.TRUETYPE_FONT, in);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(BASE[i]);
            } catch (Exception e) {
                BASE[i] = new Font(Font.SANS_SERIF, i == BOLD ? Font.BOLD : Font.PLAIN, 12);
            }
        }
    }

    private Theme() {}

    public static boolean isLight() { return light; }

    /** Swaps the whole palette. Call repaint on the window afterwards. */
    public static void setLight(boolean on) {
        light = on;
        // neutral greys, AAA contrast (the 50–950 scales): no warm tint
        if (on) {
            BLACK = new Color(0xFFFFFF);    // base
            PANEL = new Color(0xFAFAFA);    // 50
            PANEL_2 = new Color(0xF5F5F5);  // 100
            RAISED = new Color(0xE5E5E5);   // 200
            LINE = new Color(0xE5E5E5);     // 200
            LINE_2 = new Color(0xD4D4D4);   // 300
            MID = new Color(0xA3A3A3);      // 400
            STEEL = new Color(0xD4D4D4);
            MUTED = new Color(0x737373);    // 500
            SOFT = new Color(0x404040);     // 700
            TEXT = new Color(0x0A0A0A);     // 950
            DANGER = new Color(0xC8373F);
            GLOW = new Color(0xFFFFFF);
            EDGE = new Color(0xF5F5F5);
        } else {
            int[] p = palette();                // Midnight = the neutral scale; others are tinted to match
            BLACK = new Color(p[0]);
            PANEL = new Color(p[1]);
            PANEL_2 = new Color(p[2]);
            RAISED = new Color(p[3]);
            LINE = new Color(p[4]);
            LINE_2 = new Color(p[5]);
            MID = new Color(p[6]);
            STEEL = new Color(p[6]);
            MUTED = new Color(p[7]);
            SOFT = new Color(p[8]);
            TEXT = new Color(p[9]);
            DANGER = new Color(0xFF6B6B);
            GLOW = new Color(p[11]);
            EDGE = new Color(p[2]);
        }
        TINTED.clear();
        LOGOS.clear();
        Glass.clearSolid();
        applyAccent();
    }

    public static Font font(int weight, float size) {
        return FONTS.computeIfAbsent(weight + ":" + size, k -> BASE[weight].deriveFont(size));
    }

    public static Font tracked(int weight, float size, float tracking) {
        return FONTS.computeIfAbsent(weight + ":" + size + ":" + tracking,
                k -> BASE[weight].deriveFont(size).deriveFont(Map.of(TextAttribute.TRACKING, tracking)));
    }

    public static BufferedImage image(String name) {
        return IMAGES.computeIfAbsent(name, n -> {
            try (InputStream in = Theme.class.getResourceAsStream("/img/" + n)) {
                return in == null ? null : ImageIO.read(in);
            } catch (Exception e) {
                return null;
            }
        });
    }

    /** The logo artwork is white; in light mode it is recoloured so it stays visible. */
    private static BufferedImage tinted(String name, Color c) {
        return TINTED.computeIfAbsent(name + ":" + c.getRGB(), k -> {
            BufferedImage src = image(name);
            if (src == null) return null;
            BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.drawImage(src, 0, 0, null);
            g.setComposite(AlphaComposite.SrcAtop);
            g.setColor(c);
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
            g.dispose();
            return out;
        });
    }

    public static Graphics2D aa(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        return g2;
    }

    public static Color alpha(Color c, double a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.round(clamp(a) * 255));
    }

    public static Color mix(Color a, Color b, double t) {
        t = clamp(t);
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t), (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t), (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    public static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    /**
     * A see-through composite, always within 0..1. Spring animations overshoot a little (e.g. 1.01),
     * and AlphaComposite throws on anything outside 0..1, which made parts of the window vanish.
     */
    public static AlphaComposite fade(double a) {
        return AlphaComposite.SrcOver.derive((float) Math.max(0, Math.min(1, a)));
    }

    /** Settings → Customization → Corner roundness: every rounded corner is scaled by this. */
    public static double round(double r) {
        double k = dev.cobra.launcher.core.Settings.get().roundness / 100.0;
        return Math.max(0, Math.min(Math.max(0, r * k), r * 3));
    }

    public static void fill(Graphics2D g, double x, double y, double w, double h, double r, Paint p) {
        g.setPaint(p);
        double rr = Math.min(round(r), Math.min(w, h) / 2);
        g.fill(new RoundRectangle2D.Double(x, y, w, h, rr * 2, rr * 2));
    }

    /** Standard card/row/chip surface: solid charcoal, or liquid glass when that style is on. */
    public static void surface(Graphics2D g, Component c, double x, double y, double w, double h, double r) {
        surface(g, c, x, y, w, h, r, 0, 0);
    }

    /** @param hover 0..1 hover highlight, @param emphasis 0..1 raised look (glass) */
    /**
     * A card on a page: a soft see-through fill with no outline. The page itself sits on one glass
     * sheet, so cards don't stack glass on glass (that's what drew all the outlines).
     */
    public static void surface(Graphics2D g, Component c, double x, double y, double w, double h, double r, double hover, double emphasis) {
        if (Glass.on() && !Glass.lite() && Glass.frostedCards()) {
            // frosted glass card: the blurred wallpaper, a dark tint and a thin light border (the
            // reference widgets)
            Glass.surface(g, c, x, y, w, h, r, Math.min(1, emphasis * 0.4 + hover * 0.6));
            return;
        }
        double level = Math.min(1, emphasis * 0.6 + hover * 0.8);
        Color base = isLight() ? new Color(255, 255, 255, (int) (120 + 60 * level)) : new Color(255, 255, 255, (int) (9 + 12 * level));
        fill(g, x, y, w, h, r, base);
        // a hint of light on the top edge, fading out: depth without a border
        Graphics2D t = (Graphics2D) g.create();
        t.clip(new java.awt.Rectangle((int) x, (int) y, (int) Math.ceil(w), (int) Math.min(h, 2 + r)));
        t.setPaint(new GradientPaint(0, (float) y, new Color(255, 255, 255, isLight() ? 90 : 22), 0, (float) (y + Math.min(h, 2 + r)), new Color(255, 255, 255, 0)));
        t.setStroke(new BasicStroke(1f));
        t.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, w - 1, h - 1, r * 2 - 1, r * 2 - 1));
        t.dispose();
    }

    /** Buttons, inputs and switches: a flat tinted shape, stronger on hover/focus. No outline. */
    /**
     * Buttons, inputs and switches: a glossy dark pill like polished glass. A soft vertical
     * gradient, a thin light edge that's brighter on top and fades towards the bottom, and a
     * faint sheen across the upper half. Brighter on hover.
     */
    public static void chip(Graphics2D g, double x, double y, double w, double h, double r, double level) {
        level = Math.max(0, Math.min(1, level));
        if (Glass.lite()) {                                     // More optimization: flat and cheap
            fill(g, x, y, w, h, r, isLight() ? new Color(0, 0, 0, (int) (12 + 16 * level)) : new Color(255, 255, 255, (int) (14 + 20 * level)));
            return;
        }
        gloss(g, x, y, w, h, r, level);
    }

    /** The glossy surface used by {@link #chip} and the title pill / rail controls. */
    public static void gloss(Graphics2D g, double x, double y, double w, double h, double r, double level) {
        level = Math.max(0, Math.min(1, level));
        float y0 = (float) y, y1 = (float) (y + h);
        if (isLight()) {
            fill(g, x, y, w, h, r, new GradientPaint(0, y0, new Color(255, 255, 255, (int) (200 + 40 * level)), 0, y1, new Color(236, 236, 240, (int) (200 + 40 * level))));
            stroke(g, x, y, w, h, r, new Color(0, 0, 0, (int) (22 + 14 * level)), 1f);
            return;
        }
        int lift = (int) (18 * level);
        fill(g, x, y, w, h, r, new GradientPaint(0, y0, new Color(44 + lift, 44 + lift, 47 + lift, 235), 0, y1, new Color(14 + lift / 2, 14 + lift / 2, 15 + lift / 2, 235)));
        // the sheen: a faint light band over the top half
        Graphics2D s = (Graphics2D) g.create();
        double rr = Math.min(round(r), Math.min(w, h) / 2);
        s.clip(new RoundRectangle2D.Double(x, y, w, h, rr * 2, rr * 2));
        s.setPaint(new GradientPaint(0, y0, new Color(255, 255, 255, (int) (22 + 14 * level)), 0, (float) (y + h * 0.55), new Color(255, 255, 255, 0)));
        s.fill(new java.awt.geom.Rectangle2D.Double(x, y, w, h * 0.55));
        s.dispose();
        // the edge: bright at the top, fading to almost nothing at the bottom
        Graphics2D e = (Graphics2D) g.create();
        e.setPaint(new GradientPaint(0, y0, new Color(255, 255, 255, (int) (34 + 30 * level)), 0, y1, new Color(255, 255, 255, 0)));
        e.setStroke(new BasicStroke(1f));
        e.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, w - 1, h - 1, Math.max(0, rr * 2 - 1), Math.max(0, rr * 2 - 1)));
        e.dispose();
    }

    public static void stroke(Graphics2D g, double x, double y, double w, double h, double r, Color c, float width) {
        g.setColor(c);
        g.setStroke(new BasicStroke(width));
        double o = width / 2.0;
        r = Math.min(round(r), Math.min(w, h) / 2);
        g.draw(new RoundRectangle2D.Double(x + o, y + o, w - width, h - width, Math.max(0, r * 2 - width), Math.max(0, r * 2 - width)));
    }

    public static void center(Graphics2D g, String s, Font f, Color c, double x, double y, double w, double h) {
        g.setFont(f);
        g.setColor(c);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, (float) (x + (w - fm.stringWidth(s)) / 2.0), (float) (y + (h - fm.getAscent() - fm.getDescent()) / 2.0 + fm.getAscent()));
    }

    /** Draws text vertically centred in a row starting at x. */
    public static void left(Graphics2D g, String s, Font f, Color c, double x, double y, double h) {
        g.setFont(f);
        g.setColor(c);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, (float) x, (float) (y + (h - fm.getAscent() - fm.getDescent()) / 2.0 + fm.getAscent()));
    }

    public static int width(Graphics2D g, String s, Font f) {
        return g.getFontMetrics(f).stringWidth(s);
    }

    public static String ellipsize(String s, FontMetrics fm, int max) {
        if (s == null) return "";
        if (fm.stringWidth(s) <= max) return s;
        int end = s.length();
        while (end > 0 && fm.stringWidth(s.substring(0, end) + "…") > max) end--;
        return s.substring(0, end).trim() + "…";
    }

    private static BufferedImage wordmarkCache;
    private static int wordmarkW;

    /** The ABYSS wordmark (in its own colours), {@code w} wide, its top-left at (x, y). Returns its height. */
    public static int wordmark(Graphics2D g, double x, double y, int w) {
        BufferedImage src = image("wordmark.png");
        if (src == null || w <= 0) return 0;
        int h = (int) Math.round(src.getHeight() * (w / (double) src.getWidth()));
        double scale = Math.max(1, g.getTransform().getScaleX());
        int pw = (int) Math.round(w * scale);
        if (wordmarkCache == null || wordmarkW != pw) {
            int ph = (int) Math.round(src.getHeight() * (pw / (double) src.getWidth()));
            BufferedImage cur = src;
            while (cur.getWidth() / 2 >= pw) {
                BufferedImage half = new BufferedImage(cur.getWidth() / 2, cur.getHeight() / 2, BufferedImage.TYPE_INT_ARGB);
                Graphics2D hg = half.createGraphics();
                hg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                hg.drawImage(cur, 0, 0, half.getWidth(), half.getHeight(), null);
                hg.dispose();
                cur = half;
            }
            BufferedImage out = new BufferedImage(pw, Math.max(1, ph), BufferedImage.TYPE_INT_ARGB);
            Graphics2D og = out.createGraphics();
            og.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            og.drawImage(cur, 0, 0, pw, ph, null);
            og.dispose();
            wordmarkCache = out;
            wordmarkW = pw;
        }
        Graphics2D gl = (Graphics2D) g.create();
        gl.translate(x, y);
        gl.scale(1 / scale, 1 / scale);
        gl.drawImage(wordmarkCache, 0, 0, null);
        gl.dispose();
        return h;
    }

    public static void logo(Graphics2D g, double cx, double cy, double size, double alpha) {
        logo(g, cx, cy, size, alpha, TEXT);
    }

    private static final Map<String, BufferedImage> LOGOS = new HashMap<>();

    public static void logo(Graphics2D g, double cx, double cy, double size, double alpha, Color color) {
        if (alpha <= 0.001) return;
        // pre-scale the 512 px artwork once per size/colour instead of bicubic-scaling it every frame
        double scale = Math.max(1, g.getTransform().getScaleX());
        int px = (int) Math.round(size * scale);
        if (px <= 0) return;
        BufferedImage mine = dev.cobra.launcher.core.AppIcon.custom();   // your own logo, in its own colours
        String key = mine != null ? "custom|" + dev.cobra.launcher.core.AppIcon.version() + "|" + px : px + "|" + color.getRGB();
        BufferedImage img = LOGOS.get(key);
        if (img == null) {
            BufferedImage src = mine != null ? dev.cobra.launcher.core.AppIcon.rounded(mine)
                    : color.equals(Color.WHITE) ? image("logo.png") : tinted("logo.png", color);
            if (src == null) return;
            if (LOGOS.size() > 60) LOGOS.clear();
            img = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
            Graphics2D ig = img.createGraphics();
            ig.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            ig.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            // step down in halves for a clean result from 512 px
            BufferedImage cur = src;
            while (cur.getWidth() / 2 >= px) {
                BufferedImage half = new BufferedImage(cur.getWidth() / 2, cur.getHeight() / 2, BufferedImage.TYPE_INT_ARGB);
                Graphics2D hg = half.createGraphics();
                hg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                hg.drawImage(cur, 0, 0, half.getWidth(), half.getHeight(), null);
                hg.dispose();
                cur = half;
            }
            ig.drawImage(cur, 0, 0, px, px, null);
            ig.dispose();
            LOGOS.put(key, img);
        }
        Graphics2D gl = (Graphics2D) g.create();
        if (alpha < 0.999) gl.setComposite(AlphaComposite.SrcOver.derive((float) clamp(alpha)));
        gl.translate(cx - size / 2, cy - size / 2);
        gl.scale(size / px, size / px);
        gl.drawImage(img, 0, 0, null);
        gl.dispose();
    }

    private static final java.util.Map<Object, java.awt.image.BufferedImage> ROUNDED = new java.util.WeakHashMap<>();

    /**
     * Draws a picture with smooth (anti-aliased) rounded corners. Clipping to a rounded shape
     * leaves jagged corners in Java2D, so the corners are cut out of a cached copy instead.
     * @param smooth bicubic scaling (photos, icons) or nearest (pixel art like skin faces)
     */
    public static void roundedImage(Graphics2D g, Image img, double x, double y, double w, double h, double arc, boolean smooth) {
        if (img == null) return;
        double scale = Math.max(1, g.getTransform().getScaleX());
        int pw = Math.max(1, (int) Math.round(w * scale)), ph = Math.max(1, (int) Math.round(h * scale));
        Object key = java.util.List.of(System.identityHashCode(img), pw, ph, (int) Math.round(arc * scale), smooth);
        java.awt.image.BufferedImage out = ROUNDED.get(key);
        if (out == null) {
            out = new java.awt.image.BufferedImage(pw, ph, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D b = out.createGraphics();
            b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            b.fill(new RoundRectangle2D.Double(0, 0, pw, ph, arc * scale, arc * scale));
            b.setComposite(AlphaComposite.SrcIn);
            b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, smooth ? RenderingHints.VALUE_INTERPOLATION_BICUBIC
                    : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            b.drawImage(img, 0, 0, pw, ph, null);
            b.dispose();
            if (ROUNDED.size() > 256) ROUNDED.clear();
            ROUNDED.put(key, out);
        }
        Graphics2D d = (Graphics2D) g.create();
        d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        d.drawImage(out, (int) Math.round(x), (int) Math.round(y), (int) Math.round(w), (int) Math.round(h), null);
        d.dispose();
    }
}

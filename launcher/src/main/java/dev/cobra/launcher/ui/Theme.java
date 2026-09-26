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
    public static Color BLACK = new Color(0x121212);   // canvas / ink on accent
    public static Color PANEL = new Color(0x2D2D2D);
    public static Color PANEL_2 = new Color(0x1e2023);
    public static Color RAISED = new Color(0x2b2a2a);
    public static Color LINE = new Color(0x2b2a2a);
    public static Color LINE_2 = new Color(0x3d3d3d);
    public static Color MID = new Color(0x454444);
    public static Color STEEL = new Color(0x4c4e51);
    public static Color MUTED = new Color(0x646464);
    public static Color SOFT = new Color(0x9a9b9f);
    public static Color TEXT = new Color(0xF8F5F2);    // accent / primary text
    public static Color DANGER = new Color(0xff6b6b);
    public static Color GLOW = new Color(0x1e1f22);    // centre of the background gradient
    public static Color EDGE = new Color(0x0a0a0b);    // outer ring of the background gradient

    /** Accent (Launch button, selection pill, switches, sliders) and the ink drawn on top of it. */
    public static Color ACCENT = TEXT, ON_ACCENT = BLACK;

    /** Named accent presets for Settings → Appearance. "Classic" = ivory / charcoal. */
    public static final String[] ACCENTS = {"Classic", "Cobra green", "Ocean", "Violet", "Rose", "Sunset", "Gold", "Mint", "White", "Black", "Custom"};
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
        for (int i = 0; i < ACCENTS.length; i++) if (ACCENTS[i].equals(s.accent)) rgb = ACCENT_RGB[i];
        if ("Custom".equals(s.accent)) rgb = s.accentCustom & 0xFFFFFF;
        if (rgb == 0) {
            ACCENT = TEXT;
            ON_ACCENT = BLACK;
            return;
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
        if (on) {
            // Ivory #F8F5F2 with charcoal #2D2D2D ink
            BLACK = new Color(0xF8F5F2);
            PANEL = new Color(0xEFEBE6);
            PANEL_2 = new Color(0xE9E4DE);
            RAISED = new Color(0xE2DDD6);
            LINE = new Color(0xDDD7CF);
            LINE_2 = new Color(0xCBC4BB);
            MID = new Color(0xAFA89F);
            STEEL = new Color(0xC4BDB4);
            MUTED = new Color(0x8C857C);
            SOFT = new Color(0x5B5650);
            TEXT = new Color(0x2D2D2D);
            DANGER = new Color(0xC8373F);
            GLOW = new Color(0xFFFFFF);
            EDGE = new Color(0xEEE9E3);
        } else {
            // Charcoal #2D2D2D surfaces on near-black, ivory ink
            BLACK = new Color(0x121212);
            PANEL = new Color(0x2D2D2D);
            PANEL_2 = new Color(0x262626);
            RAISED = new Color(0x363636);
            LINE = new Color(0x333333);
            LINE_2 = new Color(0x444444);
            MID = new Color(0x555555);
            STEEL = new Color(0x5A5A5A);
            MUTED = new Color(0x7C7A77);
            SOFT = new Color(0xA8A5A1);
            TEXT = new Color(0xF8F5F2);
            DANGER = new Color(0xFF6B6B);
            GLOW = new Color(0x2A2A2A);
            EDGE = new Color(0x161616);
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

    public static void fill(Graphics2D g, double x, double y, double w, double h, double r, Paint p) {
        g.setPaint(p);
        g.fill(new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2));
    }

    /** Standard card/row/chip surface: solid charcoal, or liquid glass when that style is on. */
    public static void surface(Graphics2D g, Component c, double x, double y, double w, double h, double r) {
        surface(g, c, x, y, w, h, r, 0, 0);
    }

    /** @param hover 0..1 hover highlight, @param emphasis 0..1 raised look (glass) */
    public static void surface(Graphics2D g, Component c, double x, double y, double w, double h, double r, double hover, double emphasis) {
        if (Glass.on()) {
            Glass.surface(g, c, x, y, w, h, r, Math.min(1, emphasis + hover * 0.6));
            return;
        }
        Glass.solid(g, x, y, w, h, r, hover, emphasis);
    }

    public static void stroke(Graphics2D g, double x, double y, double w, double h, double r, Color c, float width) {
        g.setColor(c);
        g.setStroke(new BasicStroke(width));
        double o = width / 2.0;
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
}

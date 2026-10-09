package dev.life.launcher.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.WeakHashMap;

/** Solid surfaces and liquid glass (see the liquid glass section for how it stays cheap). */
public final class Glass {
    private static Component root;
    private static BufferedImage backdrop;
    private static int builtW, builtH, builtFrost = -1, builtWallpaper = -1;
    private static boolean builtLight;
    private static int stamp;

    private record SolidKey(int w, int h, int r, int hover, int emphasis, boolean light) {}

    private static final Map<SolidKey, BufferedImage> SOLID = new HashMap<>();

    /**
     * Solid (non-glass) surface, also cached: the translucent fill + border is rasterised once per
     * size/state instead of on every repaint.
     */
    public static void solid(Graphics2D g, double x, double y, double w, double h, double r, double hover, double emphasis) {
        int iw = (int) Math.round(w), ih = (int) Math.round(h);
        if (iw <= 0 || ih <= 0) return;
        SolidKey k = new SolidKey(iw, ih, (int) Math.round(r), (int) Math.round(Theme.clamp(hover) * 5), (int) Math.round(Theme.clamp(emphasis) * 5), Theme.isLight());
        BufferedImage img = SOLID.get(k);
        if (img == null) {
            if (SOLID.size() > 160) SOLID.clear();
            img = gc().createCompatibleImage(iw, ih, Transparency.TRANSLUCENT);
            Graphics2D b = Theme.aa(img.createGraphics());
            double hv = k.hover() / 5.0, em = k.emphasis() / 5.0;
            Theme.fill(b, 0, 0, iw, ih, k.r(), Theme.alpha(Theme.mix(Theme.PANEL, Theme.RAISED, hv * 0.6), 0.72 + 0.2 * em));
            Theme.stroke(b, 0, 0, iw, ih, k.r(), Theme.mix(Theme.LINE, Theme.MID, hv), 1f);
            b.dispose();
            SOLID.put(k, img);
        }
        g.drawImage(img, (int) Math.round(x), (int) Math.round(y), null);
    }

    /** Called on theme change so cached solid surfaces pick up the new palette. */
    public static void clearSolid() {
        SOLID.clear();
    }

    // ------------------------------------------------------------------ liquid glass

    /*
     * Liquid glass: clear glass over the wallpaper. Light blur, thin tint, and near the rim the
     * picture bends like it's seen through the thick edge of a lens; on top a bright specular rim,
     * a faint inner glow and a sheen.
     *
     * How it stays fast:
     *  - The blurred backdrop ("source") is built with Java2D's native scaling (down, tiny box blur,
     *    back up in doublings). For a video/GIF wallpaper it's rebuilt on the wallpaper's own
     *    background thread right after each frame is decoded, never on the UI thread.
     *  - Three source buffers rotate, so the UI thread never reads one that's being written.
     *  - Each surface keeps one ARGB buffer. A new frame costs one System.arraycopy per row plus a
     *    per-pixel pass over the rim band only (the bent/antialiased pixels, a few % of the area).
     *  - Mask/refraction and the highlight layer depend only on size, so they're shared and scrolling
     *    doesn't rebuild anything.
     *  - While the window is minimised or the game is running nothing is rebuilt at all.
     */
    private record Source(int[] px, int w, int h, int stamp) {}

    private static volatile Source src;
    private static String builtGradient = "";
    private static final BufferedImage[] RING = new BufferedImage[3];
    private static int ring, stampGen;

    private record SizeKey(int w, int h, int r, int q) {}

    private record ShapeKey(int w, int h, int r) {}

    private record LookKey(int w, int h, int r, int q, boolean light) {}

    /** Rim band of one shape: which pixels need per-pixel work, their coverage and refraction. */
    private record Band(int[] idx, byte[] a, byte[] dx, byte[] dy) {}

    private static final class Entry {
        final SizeKey key;
        final BufferedImage buf;
        final int[] px;
        Band band;
        BufferedImage overlay;
        int ax = Integer.MIN_VALUE, ay, stamp = -1;
        boolean light;

        Entry(SizeKey key) {
            this.key = key;
            buf = new BufferedImage(key.w(), key.h(), BufferedImage.TYPE_INT_ARGB);
            px = ((java.awt.image.DataBufferInt) buf.getRaster().getDataBuffer()).getData();
        }
    }

    private static final Map<Component, Map<SizeKey, Entry>> CACHE = new WeakHashMap<>();
    private static final Map<ShapeKey, Band> BANDS = new HashMap<>();
    private static final Map<LookKey, BufferedImage> LOOKS = new HashMap<>();

    private Glass() {}

    /** "Solid" style: see-through too, but a darker, heavily blurred pane without glass highlights. */
    static boolean solidLook() {
        return !"glass".equals(dev.life.launcher.core.Settings.get().style);
    }

    private static int a255(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    /** Page cards are separate pieces of frosted glass (not with Solid / Clear). */
    public static boolean frostedCards() {
        return !solidLook() && !"liquid".equals(dev.life.launcher.core.Settings.get().glassLook);
    }

    static boolean frosted() {
        return solidLook() || !"liquid".equals(dev.life.launcher.core.Settings.get().glassLook);
    }

    /** Panels are always drawn over the (blurred) wallpaper now; Solid only changes how they look. */
    public static boolean on() {
        return true;
    }

    public static void attach(Component rootPane) {
        root = rootPane;
    }

    public static void invalidate() {
        builtW = -1;
    }

    private static GraphicsConfiguration gc() {
        GraphicsConfiguration g = root != null ? root.getGraphicsConfiguration() : null;
        return g != null ? g : GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
    }

    /** Rebuilds the still backdrop and the glass source when size / theme / blur / wallpaper change. */
    private static void ensure() {
        if (root == null) return;
        int w = Math.max(1, root.getWidth()), h = Math.max(1, root.getHeight());
        int frost = dev.life.launcher.core.Settings.get().frost;
        int wp = Wallpaper.version();
        boolean light = Theme.isLight();
        var cs = dev.life.launcher.core.Settings.get();
        String grad = Theme.gradientKey() + "|" + cs.glassLook + "|" + cs.style + "|" + cs.roundness + "|" + cs.glassTint + "|" + cs.glassBorder + "|" + cs.theme;
        if (backdrop != null && w == builtW && h == builtH && frost == builtFrost && light == builtLight && wp == builtWallpaper
                && grad.equals(builtGradient)) return;
        builtGradient = grad;
        BANDS.clear();                 // look / size / theme changed: rebuild every surface's shape and highlights
        LOOKS.clear();
        CACHE.clear();
        builtW = w;
        builtH = h;
        builtFrost = frost;
        builtLight = light;
        builtWallpaper = wp;
        BufferedImage base = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = base.createGraphics();
        if (Wallpaper.active()) Wallpaper.paint(g, w, h);
        else paintDefault(g, w, h, light);
        g.dispose();
        backdrop = toCompatible(base, Transparency.OPAQUE);
        publish(base, frost);
    }

    /**
     * Called by the wallpaper thread with each new (cover-scaled, dimmed) video frame: rebuilds the
     * glass source off the UI thread.
     */
    static void onFrame(BufferedImage composed) {
        if (!on() || root == null || builtW < 0) return;
        if (composed.getWidth() != builtW || composed.getHeight() != builtH) return;   // ensure() will catch up
        long now = System.nanoTime();
        if (now - lastPublish < 75_000_000L) return;   // ~12 fps is plenty behind a blur, halves the CPU
        lastPublish = now;
        publish(composed, builtFrost);
    }

    private static volatile long lastPublish;

    /** Blurs {@code base} into the next ring buffer and makes it the current source. */
    private static synchronized void publish(BufferedImage base, int frost) {
        int w = base.getWidth(), h = base.getHeight();
        // Clear (default): almost no blur, the wallpaper stays sharp and bends at the edges
        double factor = solidLook() ? 5 + frost * 0.1 : frosted() ? 3 + frost * 0.1 : 1 + frost * 0.02;   // frosted: always soft; liquid: frost 100 = 7x
        // down: native bilinear halvings to about w / factor
        BufferedImage cur = base;
        int tw = Math.max(8, (int) (w / factor));
        boolean first = true;
        while (cur.getWidth() / 2 >= tw && cur.getWidth() > 16) {
            cur = scaled(cur, cur.getWidth() / 2, cur.getHeight() / 2, !first);   // first halving: nearest (fast, blurred later)
            first = false;
        }
        if (cur != base) cur = boxBlur(boxBlur(cur));                 // two soft passes: smooth, no blockiness
        // up: doublings (a single big bilinear jump looks blocky), last step straight into the ring
        while (cur.getWidth() * 2 < w) cur = scaled(cur, cur.getWidth() * 2, cur.getHeight() * 2, true);
        BufferedImage out = RING[ring];
        if (out == null || out.getWidth() != w || out.getHeight() != h) {
            out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);   // ARGB: rows copy with alpha 255
            RING[ring] = out;
        }
        Graphics2D g = out.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(cur, 0, 0, w, h, null);
        g.dispose();
        if (!frosted() && !Wallpaper.animated()) liquidize(((java.awt.image.DataBufferInt) out.getRaster().getDataBuffer()).getData(), w, h);
        else finish(((java.awt.image.DataBufferInt) out.getRaster().getDataBuffer()).getData(), w, h);
        ring = (ring + 1) % RING.length;
        src = new Source(((java.awt.image.DataBufferInt) out.getRaster().getDataBuffer()).getData(), w, h, ++stampGen);
        stamp++;
    }

    // ------------------------------------------------------------------ liquid (Clear) look

    private static int[] noiseDx, noiseDy, liquidTmp;
    private static int noiseW, noiseH;

    /**
     * The liquid part of Clear glass, like an SVG turbulence + displacement filter: the backdrop is
     * pushed around by a smooth, low-frequency noise field (organic, water-like wobble), then made a
     * little more saturated (x1.2) and brighter (x1.15). Done once per backdrop, not per panel.
     */
    private static void liquidize(int[] px, int w, int h) {
        if (noiseDx == null || noiseW != w || noiseH != h) {
            noiseDx = turbulence(w, h, 11, 22);
            noiseDy = turbulence(w, h, 29, 22);
            noiseW = w;
            noiseH = h;
            liquidTmp = new int[w * h];
        }
        int[] tmp = liquidTmp;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int i = row + x;
                int sx = Math.max(0, Math.min(w - 1, x + noiseDx[i])), sy = Math.max(0, Math.min(h - 1, y + noiseDy[i]));
                int c = px[sy * w + sx];
                int r = c >> 16 & 255, gg = c >> 8 & 255, b = c & 255;
                int l = (r * 77 + gg * 150 + b * 29) >> 8;            // luminance
                r = l + (r - l) * 6 / 5;                              // saturate 120%
                gg = l + (gg - l) * 6 / 5;
                b = l + (b - l) * 6 / 5;
                r = r * 23 / 20;                                      // brightness 115%
                gg = gg * 23 / 20;
                b = b * 23 / 20;
                tmp[i] = 0xFF000000 | clamp(r) << 16 | clamp(gg) << 8 | clamp(b);
            }
        }
        System.arraycopy(tmp, 0, px, 0, w * h);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(255, v);
    }

    /** Smooth 2-octave value noise (period ~125 px, like baseFrequency 0.008) scaled to +-amp pixels. */
    private static int[] turbulence(int w, int h, long seed, int amp) {
        java.util.Random rnd = new java.util.Random(seed);
        int cell = 125;
        int gw = w / cell + 3, gh = h / cell + 3;
        float[] g1 = new float[gw * gh], g2 = new float[(gw * 2 + 2) * (gh * 2 + 2)];
        for (int i = 0; i < g1.length; i++) g1[i] = rnd.nextFloat() * 2 - 1;
        for (int i = 0; i < g2.length; i++) g2[i] = rnd.nextFloat() * 2 - 1;
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float v = sample(g1, gw, x / (float) cell, y / (float) cell) + 0.5f * sample(g2, gw * 2 + 2, x * 2f / cell, y * 2f / cell);
                out[y * w + x] = Math.round(v / 1.5f * amp);
            }
        }
        return out;
    }

    private static float sample(float[] g, int gw, float fx, float fy) {
        int x0 = (int) fx, y0 = (int) fy;
        float tx = fx - x0, ty = fy - y0;
        tx = tx * tx * (3 - 2 * tx);                                  // smoothstep: no creases
        ty = ty * ty * (3 - 2 * ty);
        float a = g[y0 * gw + x0], b = g[y0 * gw + x0 + 1], c = g[(y0 + 1) * gw + x0], d = g[(y0 + 1) * gw + x0 + 1];
        return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * ty;
    }

    private static BufferedImage scaled(BufferedImage img, int w, int h, boolean smooth) {
        BufferedImage out = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        if (smooth) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, out.getWidth(), out.getHeight(), null);
        g.dispose();
        return out;
    }

    /** A small gaussian (5x5): softer and rounder than a box, so the frost has no square edges. */
    private static BufferedImage boxBlur(BufferedImage img) {
        float[] g1 = {1, 4, 6, 4, 1};
        float[] k = new float[25];
        for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) k[y * 5 + x] = g1[x] * g1[y] / 256f;
        BufferedImage src = img.getType() == BufferedImage.TYPE_INT_RGB ? img : copyRgb(img);
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        new java.awt.image.ConvolveOp(new java.awt.image.Kernel(5, 5, k), java.awt.image.ConvolveOp.EDGE_NO_OP, null).filter(src, out);
        return out;
    }

    private static BufferedImage copyRgb(BufferedImage img) {
        BufferedImage o = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = o.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return o;
    }

    private static int[] grain;

    /**
     * The finish on frosted glass: a touch more colour (vibrancy, like macOS) and a very fine grain
     * so smooth gradients never show bands.
     */
    private static void finish(int[] px, int w, int h) {
        if (grain == null) {
            java.util.Random r = new java.util.Random(7);
            grain = new int[64 * 64];
            for (int i = 0; i < grain.length; i++) grain[i] = r.nextInt(7) - 3;
        }
        for (int y = 0; y < h; y++) {
            int row = y * w, gr = (y & 63) * 64;
            for (int x = 0; x < w; x++) {
                int c = px[row + x];
                int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
                int l = (r * 77 + g * 151 + b * 28) >> 8;
                int n = grain[gr + (x & 63)];
                r = l + ((r - l) * 9 >> 3) + n;                   // saturation x1.125
                g = l + ((g - l) * 9 >> 3) + n;
                b = l + ((b - l) * 9 >> 3) + n;
                r = r < 0 ? 0 : r > 255 ? 255 : r;
                g = g < 0 ? 0 : g > 255 ? 255 : g;
                b = b < 0 ? 0 : b > 255 ? 255 : b;
                px[row + x] = 0xFF000000 | r << 16 | g << 8 | b;
            }
        }
    }

    /** No wallpaper: a calm charcoal (or ivory) field with soft light, so the glass has something to bend. */
    private static void paintDefault(Graphics2D g, int w, int h, boolean light) {
        Theme.aa(g);
        if (Theme.paintGradient(g, w, h)) return;
        if (!light && "Life".equals(dev.life.launcher.core.Settings.get().theme)) {   // Life: the liquid
            Liquid.paint(g, w, h);
            return;
        }
        int[] p = Theme.palette();
        Color base = light ? new Color(0xF5F5F5) : new Color(p[2]);
        g.setColor(base);
        g.fillRect(0, 0, w, h);
        // two soft pools of the theme's own light, top right and bottom left
        Color glow = light ? Color.WHITE : new Color(p[11]);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(w * 0.68, h * 0.26), (float) (h * 0.85), new float[]{0f, 1f},
                new Color[]{new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), light ? 255 : 200), new Color(base.getRed(), base.getGreen(), base.getBlue(), 0)}));
        g.fillRect(0, 0, w, h);
        Color glow2 = light ? new Color(0xE5E5E5) : Theme.mix(new Color(p[11]), new Color(p[3]), 0.5);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(w * 0.18, h * 0.9), (float) (h * 0.7), new float[]{0f, 1f},
                new Color[]{new Color(glow2.getRed(), glow2.getGreen(), glow2.getBlue(), light ? 200 : 170), new Color(base.getRed(), base.getGreen(), base.getBlue(), 0)}));
        g.fillRect(0, 0, w, h);
    }

    private static BufferedImage toCompatible(BufferedImage img, int transparency) {
        BufferedImage out = gc().createCompatibleImage(img.getWidth(), img.getHeight(), transparency);
        Graphics2D g = out.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return out;
    }

    /** Window background in glass mode. */
    /** Settings → More optimization: flat colours, no wallpaper, no blur (lightest possible). */
    public static boolean lite() {
        return dev.life.launcher.core.Settings.get().moreOptimization;
    }

    public static void paintBackground(Graphics2D g, int w, int h) {
        if (lite()) {
            g.setColor(Theme.isLight() ? new Color(0xEDEAE6) : new Color(Theme.palette()[2]));
            g.fillRect(0, 0, w, h);
            return;
        }
        ensure();
        if (Wallpaper.active() && Wallpaper.animated()) Wallpaper.paint(g, w, h); // live video frame
        else if (backdrop != null) g.drawImage(backdrop, 0, 0, null);
    }

    /**
     * Liquid glass surface in component {@code c}'s coordinates.
     *
     * @param emphasis 0 = quiet card, 1 = raised element (chips, focused inputs)
     */
    public static void surface(Graphics2D g, Component c, double x, double y, double w, double h, double r, double emphasis) {
        r = Math.min(Theme.round(r), Math.min(w, h) / 2);
        if (lite()) {                         // plain panel: one fill and a hairline, nothing else
            Graphics2D f = (Graphics2D) g.create();
            f.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            f.setColor(Theme.isLight() ? new Color(255, 255, 255, 235) : Theme.alpha(Theme.PANEL, 0.96));
            f.fill(new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2));
            f.setColor(Theme.isLight() ? new Color(0, 0, 0, 28) : new Color(255, 255, 255, 22));
            f.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, w - 1, h - 1, r * 2, r * 2));
            f.dispose();
            return;
        }
        ensure();
        Source s = src;
        if (root == null || s == null) return;
        int iw = (int) Math.round(w), ih = (int) Math.round(h);
        if (iw <= 0 || ih <= 0) return;
        Point o = SwingUtilities.convertPoint(c, 0, 0, root);
        int ax = o.x + (int) Math.round(x), ay = o.y + (int) Math.round(y);
        SizeKey key = new SizeKey(iw, ih, (int) Math.round(Math.min(r, Math.min(iw, ih) / 2.0)),
                (int) Math.round(Math.max(0, Math.min(1, emphasis)) * 4));
        Map<SizeKey, Entry> map = CACHE.computeIfAbsent(c, k -> new HashMap<>());
        Entry e = map.get(key);
        if (e == null) {
            if (map.size() > 16) map.clear();
            e = new Entry(key);
            map.put(key, e);
        }
        boolean light = Theme.isLight();
        if (e.band == null || e.light != light) {
            if (BANDS.size() > 96) BANDS.clear();
            if (LOOKS.size() > 128) LOOKS.clear();
            e.band = BANDS.computeIfAbsent(new ShapeKey(iw, ih, key.r()), sk -> band(sk.w(), sk.h(), sk.r()));
            e.overlay = LOOKS.computeIfAbsent(new LookKey(iw, ih, key.r(), key.q(), light), Glass::highlights);
            e.light = light;
            e.stamp = -1;
        }
        if (e.stamp != s.stamp() || e.ax != ax || e.ay != ay) compose(e, s, ax, ay);
        int dx = (int) Math.round(x), dy = (int) Math.round(y);
        g.drawImage(e.buf, dx, dy, null);
        g.drawImage(e.overlay, dx, dy, null);
        specular(g, c, x, y, w, h, r);
    }

    /**
     * Light that follows the pointer (glass-specular): a soft white spot where your mouse is over a
     * glass shape. Only on small glass parts (the big frame would need a repaint on every move).
     */
    private static void specular(Graphics2D g, Component c, double x, double y, double w, double h, double r) {
        if (c == null || c == root || lite() || frosted() || w * h > 250_000 || !c.isShowing()) return;
        PointerInfo pi = MouseInfo.getPointerInfo();
        if (pi == null) return;
        Point p = pi.getLocation();
        SwingUtilities.convertPointFromScreen(p, c);
        if (p.x < x || p.y < y || p.x > x + w || p.y > y + h) return;
        float rad = (float) Math.max(40, Math.min(w, h) * 0.9);
        Graphics2D s = (Graphics2D) g.create();
        s.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        s.setPaint(new RadialGradientPaint(new Point2D.Double(p.x, p.y), rad, new float[]{0f, 0.3f, 0.6f},
                new Color[]{new Color(255, 255, 255, 38), new Color(255, 255, 255, 13), new Color(255, 255, 255, 0)}));
        s.fill(new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2));
        s.dispose();
    }

    /** The rim band of a rounded rectangle: antialiased edge, cut corners and refraction offsets. */
    private static Band band(int w, int h, int ri) {
        double r = Math.max(0.5, ri);
        double bandW = Math.max(4, Math.min(Math.min(18, r + 8), Math.min(w, h) / 2.5));
        double strength = frosted() ? 0 : bandW * 0.85;  // frosted: no bending; clear: a clean lens at the rim
        double hw = w / 2.0, hh = h / 2.0;
        int cap = Math.min(w * h, (int) ((w + h) * 2 * (bandW + 2)) + 64);
        int[] idx = new int[cap];
        byte[] a = new byte[cap], dx = new byte[cap], dy = new byte[cap];
        int n = 0;
        int reach = (int) Math.ceil(Math.max(bandW, r)) + 2;
        for (int py = 0; py < h; py++) {
            boolean rowNearEdge = py < reach || py >= h - reach;
            double yy = py + 0.5 - hh, ay = Math.abs(yy);
            for (int px = 0; px < w; px++) {
                if (!rowNearEdge && px >= reach && px < w - reach) {   // interior: nothing to do
                    px = w - reach - 1;
                    continue;
                }
                double xx = px + 0.5 - hw, ax = Math.abs(xx);
                double qx = ax - (hw - r), qy = ay - (hh - r);
                double ox = Math.max(qx, 0), oy = Math.max(qy, 0);
                double sdf = Math.sqrt(ox * ox + oy * oy) + Math.min(Math.max(qx, qy), 0) - r;
                double cover = Math.max(0, Math.min(1, 0.5 - sdf));
                double d = -sdf;
                int ddx = 0, ddy = 0;
                if (cover > 0 && d < bandW) {
                    double nx, ny;
                    if (qx > 0 && qy > 0) {
                        double len = Math.sqrt(qx * qx + qy * qy);
                        nx = qx / len * Math.signum(xx);
                        ny = qy / len * Math.signum(yy);
                    } else if (qx > qy) {
                        nx = Math.signum(xx);
                        ny = 0;
                    } else {
                        nx = 0;
                        ny = Math.signum(yy);
                    }
                    double t = 1 - d / bandW;
                    double off = strength * t * t;
                    ddx = (int) Math.round(nx * off);
                    ddy = (int) Math.round(ny * off);
                }
                if (cover >= 1 && ddx == 0 && ddy == 0) continue;
                if (n == cap) {   // shouldn't happen; grow just in case
                    cap = Math.min(w * h, cap * 2);
                    idx = java.util.Arrays.copyOf(idx, cap);
                    a = java.util.Arrays.copyOf(a, cap);
                    dx = java.util.Arrays.copyOf(dx, cap);
                    dy = java.util.Arrays.copyOf(dy, cap);
                }
                idx[n] = py * w + px;
                a[n] = (byte) Math.round(cover * 255);
                dx[n] = (byte) ddx;
                dy[n] = (byte) ddy;
                n++;
            }
        }
        return new Band(java.util.Arrays.copyOf(idx, n), java.util.Arrays.copyOf(a, n),
                java.util.Arrays.copyOf(dx, n), java.util.Arrays.copyOf(dy, n));
    }

    /** Straight row copies from the source, then the rim band pixel by pixel. */
    private static void compose(Entry e, Source s, int ax, int ay) {
        int w = e.key.w(), h = e.key.h(), sw = s.w(), sh = s.h();
        int[] sp = s.px(), out = e.px;
        boolean inside = ax >= 0 && ax + w <= sw;
        for (int py = 0; py < h; py++) {
            int gy = ay + py;
            gy = gy < 0 ? 0 : gy >= sh ? sh - 1 : gy;
            int row = gy * sw;
            if (inside) {
                System.arraycopy(sp, row + ax, out, py * w, w);
            } else {
                int o = py * w;
                for (int px = 0; px < w; px++) {
                    int gx = ax + px;
                    gx = gx < 0 ? 0 : gx >= sw ? sw - 1 : gx;
                    out[o + px] = sp[row + gx];
                }
            }
        }
        Band b = e.band;
        int[] idx = b.idx();
        byte[] al = b.a(), bx = b.dx(), by = b.dy();
        for (int k = 0; k < idx.length; k++) {
            int i = idx[k], a = al[k] & 0xFF;
            if (a == 0) {
                out[i] = 0;
                continue;
            }
            int px = i % w, py = i / w;
            int gx = ax + px + bx[k], gy = ay + py + by[k];
            gx = gx < 0 ? 0 : gx >= sw ? sw - 1 : gx;
            gy = gy < 0 ? 0 : gy >= sh ? sh - 1 : gy;
            out[i] = a << 24 | (sp[gy * sw + gx] & 0xFFFFFF);
        }
        e.ax = ax;
        e.ay = ay;
        e.stamp = s.stamp();
    }

    /** Tint, inner glow, top sheen and the specular rim, in one translucent layer. */
    private static BufferedImage highlights(LookKey k) {
        boolean light = k.light();
        int w = k.w(), h = k.h();
        double r = k.r(), q = k.q() / 4.0;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = Theme.aa(img.createGraphics());
        // every layer is an anti-aliased fill of the rounded shape (a clip would give jagged corners)
        Shape shape = new RoundRectangle2D.Double(0, 0, w, h, r * 2, r * 2);
        boolean frost = frosted(), solid = solidLook();
        double tintK = dev.life.launcher.core.Settings.get().glassTint / 100.0;
        double borderK = dev.life.launcher.core.Settings.get().glassBorder / 100.0;
        g.setColor(light ? new Color(255, 255, 255, a255(((solid ? 150 : frost ? 125 : 60) + 50 * q) * tintK))
                : new Color(Theme.PANEL_2.getRed(), Theme.PANEL_2.getGreen(), Theme.PANEL_2.getBlue(), a255(((solid ? 150 : frost ? 120 : 46) + 30 * q) * tintK)));
        g.fill(shape);
        if (solid) {                   // plain: just a quiet hairline border, no glass shine
            g.setStroke(new BasicStroke(1f));
            g.setColor(light ? new Color(0, 0, 0, 30) : new Color(255, 255, 255, 34));
            g.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, Math.max(0, r * 2 - 1), Math.max(0, r * 2 - 1)));
            g.dispose();
            return img;
        }
        if (frost) {
            // frosted: a thin even border and a whisper of light at the top, nothing else
            g.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, light ? 60 : 14), 0, (float) Math.min(h, 80), new Color(255, 255, 255, 0)));
            g.fill(shape);
            g.setStroke(new BasicStroke(1f));
            g.setColor(light ? new Color(255, 255, 255, a255(190 * borderK)) : new Color(255, 255, 255, a255((54 + 30 * q) * borderK)));
            g.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, Math.max(0, r * 2 - 1), Math.max(0, r * 2 - 1)));
            g.dispose();
            return img;
        }
        // sheen: light falling on the top of the glass, fading out downwards
        g.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, light ? 70 : (int) (20 + 12 * q)), 0, (float) Math.min(h, 60), new Color(255, 255, 255, 0)));
        g.fill(shape);
        // depth: a soft darker band along the bottom inside edge
        g.setPaint(new GradientPaint(0, (float) (h - Math.min(h, 34)), new Color(0, 0, 0, 0), 0, h, new Color(0, 0, 0, light ? 10 : 26)));
        g.fill(shape);
        // one soft inner ring
        g.setStroke(new BasicStroke(6f));
        g.setColor(new Color(255, 255, 255, light ? 10 : 7));
        g.draw(new RoundRectangle2D.Double(3, 3, w - 6, h - 6, Math.max(0, r * 2 - 6), Math.max(0, r * 2 - 6)));
        // specular: a thin bright line hugging the top edge, strongest at the upper-left (liquid only)
        if (!frost && h > 12) {
            g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, light ? 150 : 95), (float) w * 0.6f, 0, new Color(255, 255, 255, 0)));
            double in = 1.6;
            g.draw(new java.awt.geom.Arc2D.Double(in, in, r * 2 - in * 2, r * 2 - in * 2, 90, 90, java.awt.geom.Arc2D.OPEN));
            g.draw(new java.awt.geom.Line2D.Double(r, in, w - r, in));
        }
        Paint rim = new LinearGradientPaint(0, 0, (float) (w * 0.75), (float) (h * 0.75 + 1),
                new float[]{0f, 0.28f, 0.55f, 1f},
                new Color[]{new Color(255, 255, 255, light ? 210 : 125), new Color(255, 255, 255, light ? 110 : 34),
                        new Color(255, 255, 255, light ? 80 : 12), new Color(255, 255, 255, light ? 170 : 70)});
        g.setPaint(rim);
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, Math.max(0, r * 2 - 1), Math.max(0, r * 2 - 1)));
        // hairline just inside the rim for depth
        g.setStroke(new BasicStroke(1f));
        g.setColor(light ? new Color(40, 40, 40, 26) : new Color(0, 0, 0, 60));
        g.draw(new RoundRectangle2D.Double(2, 2, w - 4, h - 4, Math.max(0, r * 2 - 4), Math.max(0, r * 2 - 4)));
        g.dispose();
        return img;
    }

    /** Soft outer glow (for the active sidebar tile). */
    public static void glow(Graphics2D g0, double x, double y, double w, double h, double r, Color c, int spread) {
        if (spread <= 0) return;
        Graphics2D g = (Graphics2D) g0.create();
        for (int i = spread; i > 0; i--) {
            double a = 0.06 * (1 - i / (double) (spread + 1));
            g.setColor(Theme.alpha(c, a));
            g.fill(new RoundRectangle2D.Double(x - i, y - i, w + 2 * i, h + 2 * i, (r + i) * 2, (r + i) * 2));
        }
        g.dispose();
    }

    @SuppressWarnings("unused")
    private static boolean same(Object a, Object b) { return Objects.equals(a, b); }
}

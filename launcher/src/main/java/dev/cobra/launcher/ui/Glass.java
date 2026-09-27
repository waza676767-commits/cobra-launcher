package dev.cobra.launcher.ui;

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
        return !"glass".equals(dev.cobra.launcher.core.Settings.get().style);
    }

    static boolean frosted() {
        return solidLook() || !"liquid".equals(dev.cobra.launcher.core.Settings.get().glassLook);
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
        int frost = dev.cobra.launcher.core.Settings.get().frost;
        int wp = Wallpaper.version();
        boolean light = Theme.isLight();
        String grad = Theme.gradientKey() + "|" + dev.cobra.launcher.core.Settings.get().glassLook + "|" + dev.cobra.launcher.core.Settings.get().style;
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
        double factor = solidLook() ? 5 + frost * 0.1 : frosted() ? 3 + frost * 0.1 : 1 + frost * 0.06;   // frosted: always soft; liquid: frost 100 = 7x
        // down: native bilinear halvings to about w / factor
        BufferedImage cur = base;
        int tw = Math.max(8, (int) (w / factor));
        boolean first = true;
        while (cur.getWidth() / 2 >= tw && cur.getWidth() > 16) {
            cur = scaled(cur, cur.getWidth() / 2, cur.getHeight() / 2, !first);   // first halving: nearest (fast, blurred later)
            first = false;
        }
        if (cur != base) cur = boxBlur(cur);
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
        ring = (ring + 1) % RING.length;
        src = new Source(((java.awt.image.DataBufferInt) out.getRaster().getDataBuffer()).getData(), w, h, ++stampGen);
        stamp++;
    }

    private static BufferedImage scaled(BufferedImage img, int w, int h, boolean smooth) {
        BufferedImage out = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        if (smooth) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, out.getWidth(), out.getHeight(), null);
        g.dispose();
        return out;
    }

    private static BufferedImage boxBlur(BufferedImage img) {
        float[] k = new float[9];
        java.util.Arrays.fill(k, 1f / 9f);
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        new java.awt.image.ConvolveOp(new java.awt.image.Kernel(3, 3, k), java.awt.image.ConvolveOp.EDGE_NO_OP, null).filter(img, out);
        return out;
    }

    /** No wallpaper: a calm charcoal (or ivory) field with soft light, so the glass has something to bend. */
    private static void paintDefault(Graphics2D g, int w, int h, boolean light) {
        Theme.aa(g);
        if (Theme.paintGradient(g, w, h)) return;
        g.setColor(light ? new Color(0xF8F5F2) : new Color(0x101012));
        g.fillRect(0, 0, w, h);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(w * 0.68, h * 0.26), (float) (h * 0.85), new float[]{0f, 1f},
                light ? new Color[]{new Color(255, 255, 255, 255), new Color(236, 231, 225, 0)}
                        : new Color[]{new Color(70, 70, 74, 170), new Color(18, 18, 18, 0)}));
        g.fillRect(0, 0, w, h);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(w * 0.18, h * 0.9), (float) (h * 0.7), new float[]{0f, 1f},
                light ? new Color[]{new Color(225, 220, 212, 200), new Color(236, 231, 225, 0)}
                        : new Color[]{new Color(44, 44, 48, 150), new Color(18, 18, 18, 0)}));
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
        return dev.cobra.launcher.core.Settings.get().moreOptimization;
    }

    public static void paintBackground(Graphics2D g, int w, int h) {
        if (lite()) {
            g.setColor(Theme.isLight() ? new Color(0xEDEAE6) : new Color(0x0E0E11));
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
        if (lite()) {                         // plain panel: one fill and a hairline, nothing else
            Graphics2D f = (Graphics2D) g.create();
            f.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            f.setColor(Theme.isLight() ? new Color(255, 255, 255, 235) : new Color(26, 26, 31, 245));
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
    }

    /** The rim band of a rounded rectangle: antialiased edge, cut corners and refraction offsets. */
    private static Band band(int w, int h, int ri) {
        double r = Math.max(0.5, ri);
        double bandW = Math.max(4, Math.min(Math.min(18, r + 8), Math.min(w, h) / 2.5));
        double strength = frosted() ? 0 : bandW * 0.6;   // frosted: no bending at all; liquid: a gentle bend at the rim
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
        Shape shape = new RoundRectangle2D.Double(0, 0, w, h, r * 2, r * 2);
        g.setClip(shape);
        // thin tint: keeps text readable without hiding what's behind
        boolean frost = frosted(), solid = solidLook();
        // Solid: a smoky pane, not a black one (the wallpaper still shows through, just calmer)
        g.setColor(light ? new Color(255, 255, 255, (int) ((solid ? 150 : frost ? 125 : 95) + 50 * q))
                : new Color(24, 24, 30, (int) ((solid ? 112 : frost ? 120 : 78) + 30 * q)));
        g.fillRect(0, 0, w, h);
        if (solid) {                   // plain: just a quiet hairline border, no glass shine
            g.setClip(null);
            g.setStroke(new BasicStroke(1f));
            g.setColor(light ? new Color(0, 0, 0, 30) : new Color(255, 255, 255, 34));
            g.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, Math.max(0, r * 2 - 1), Math.max(0, r * 2 - 1)));
            g.dispose();
            return img;
        }
        // sheen: light falling on the top of the glass
        g.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, light ? 70 : (int) (20 + 12 * q)), 0, (float) Math.min(h, 60), new Color(255, 255, 255, 0)));
        g.fillRect(0, 0, w, h);
        // inner glow along the rim (the glass is thicker there)
        for (int i = 0; i < 1; i++) {      // one soft ring (was three): cleaner edge
            float sw = 6;
            g.setStroke(new BasicStroke(sw));
            g.setColor(new Color(255, 255, 255, light ? 10 : 7 + i * 3));
            double o = sw / 2.0;
            g.draw(new RoundRectangle2D.Double(o, o, w - sw, h - sw, Math.max(0, r * 2 - sw), Math.max(0, r * 2 - sw)));
        }
        g.setClip(null);
        // specular rim: bright where the light hits (top-left), a second catch-light bottom-right
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

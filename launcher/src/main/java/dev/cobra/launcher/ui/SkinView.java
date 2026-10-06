package dev.cobra.launcher.ui;

import dev.cobra.launcher.core.Accessories;
import dev.cobra.launcher.core.Paths;
import dev.cobra.launcher.core.Settings;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Your Minecraft skin as the real player model: every part textured from the skin itself (front,
 * sides, top, plus the hat / jacket / sleeve / trouser layers), pixel-sharp like in the game. It
 * turns slowly on its own; drag to spin it. No skin yet: Steve, taken from the game files.
 */
public final class SkinView extends JComponent {
    private double yaw = 25, velocity = 0.5;
    private int lastX = -1;
    private BufferedImage skin;
    private boolean slim;
    private long loadedAt;
    private final Timer spin;

    public SkinView() {
        setOpaque(false);
        spin = new Timer(33, e -> {
            if (!isShowing()) return;
            if (lastX < 0) {
                yaw += velocity;
                velocity += (0.5 - velocity) * 0.03;          // settles back to a slow turn
            }
            repaint();
        });
        MouseAdapter m = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { lastX = e.getX(); }

            @Override public void mouseDragged(MouseEvent e) {
                double d = e.getX() - lastX;
                yaw += d * 0.9;
                velocity = d * 0.9;
                lastX = e.getX();
                repaint();
            }

            @Override public void mouseReleased(MouseEvent e) { lastX = -1; }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (Anim.enabled && !Glass.lite()) spin.start();
    }

    @Override
    public void removeNotify() {
        spin.stop();
        super.removeNotify();
    }

    /** Re-reads your skin (call when it changes). */
    public void reload() {
        loadedAt = 0;
        repaint();
    }

    private void ensureSkin() {
        long now = System.currentTimeMillis();
        if (skin != null && now - loadedAt < 4000) return;
        loadedAt = now;
        BufferedImage s = Accessories.skin();
        slim = Settings.get().skinSlim;
        if (s == null) {
            s = steve();
            slim = false;
        }
        skin = s == null ? null : to64(s);
    }

    // ------------------------------------------------------------------ Steve

    private static BufferedImage steveCache;
    private static boolean steveLooked;

    /** The default Steve skin, read from a Minecraft client jar the launcher already downloaded. */
    private static synchronized BufferedImage steve() {
        if (steveCache != null || steveLooked) return steveCache;
        steveLooked = true;
        Path cached = Paths.CACHE.resolve("steve.png");
        try {
            if (Files.isRegularFile(cached)) return steveCache = ImageIO.read(cached.toFile());
        } catch (Exception ignored) {}
        try (var dirs = Files.list(Paths.VERSIONS)) {
            for (Path d : dirs.toList()) {
                Path jar = d.resolve(d.getFileName() + ".jar");
                if (!Files.isRegularFile(jar)) continue;
                try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(jar.toFile())) {
                    for (String name : new String[]{"assets/minecraft/textures/entity/player/wide/steve.png", "assets/minecraft/textures/entity/steve.png"}) {
                        var e = z.getEntry(name);
                        if (e == null) continue;
                        byte[] png = z.getInputStream(e).readAllBytes();
                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
                        if (img == null) continue;
                        Files.createDirectories(Paths.CACHE);
                        Files.write(cached, png);
                        return steveCache = img;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Old 64x32 skins get their left arm and leg mirrored from the right, like the game does. */
    private static BufferedImage to64(BufferedImage s) {
        BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        if (s.getWidth() != 64 && s.getWidth() == s.getHeight()) {           // HD skin: scale down
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(s, 0, 0, 64, 64, null);
        } else {
            g.drawImage(s, 0, 0, null);
        }
        g.dispose();
        if (s.getHeight() == 32 || s.getHeight() * 2 == s.getWidth()) {
            mirror(out, 0, 16, 16, 48);                                      // right leg -> left leg
            mirror(out, 40, 16, 32, 48);                                     // right arm -> left arm
        }
        return out;
    }

    private static void mirror(BufferedImage img, int sx, int sy, int dx, int dy) {
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) img.setRGB(dx + 15 - x, dy + y, img.getRGB(sx + x, sy + y));
    }

    // ------------------------------------------------------------------ the model

    /** A textured box: size w,h,d at (x,y,z) [feet at y=0], texture at (u,v), optional swing. */
    private record Box(double x, double y, double z, int w, int h, int d, int u, int v, double inflate, double swing, double pivotY) {}

    private record Face(double[] xs, double[] ys, double depth, BufferedImage tex) {}

    private List<Box> boxes(double t) {
        int aw = slim ? 3 : 4;
        double swing = Math.sin(t) * 0.12;                                     // a gentle arm sway
        List<Box> b = new ArrayList<>();
        // base layer
        b.add(new Box(-4, 24, -4, 8, 8, 8, 0, 0, 0, 0, 0));                    // head
        b.add(new Box(-4, 12, -2, 8, 12, 4, 16, 16, 0, 0, 0));                 // body
        b.add(new Box(-4 - aw, 12, -2, aw, 12, 4, 40, 16, 0, swing, 22));      // right arm
        b.add(new Box(4, 12, -2, aw, 12, 4, 32, 48, 0, -swing, 22));           // left arm
        b.add(new Box(-4, 0, -2, 4, 12, 4, 0, 16, 0, -swing * 0.6, 12));       // right leg
        b.add(new Box(0, 0, -2, 4, 12, 4, 16, 48, 0, swing * 0.6, 12));        // left leg
        // second layer (hat, jacket, sleeves, trousers), slightly bigger
        b.add(new Box(-4, 24, -4, 8, 8, 8, 32, 0, 0.5, 0, 0));
        b.add(new Box(-4, 12, -2, 8, 12, 4, 16, 32, 0.25, 0, 0));
        b.add(new Box(-4 - aw, 12, -2, aw, 12, 4, 40, 32, 0.25, swing, 22));
        b.add(new Box(4, 12, -2, aw, 12, 4, 48, 48, 0.25, -swing, 22));
        b.add(new Box(-4, 0, -2, 4, 12, 4, 0, 32, 0.25, -swing * 0.6, 12));
        b.add(new Box(0, 0, -2, 4, 12, 4, 0, 48, 0.25, swing * 0.6, 12));
        return b;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        ensureSkin();
        Graphics2D g = Theme.aa(g0.create());
        int w = getWidth(), h = getHeight();
        // a soft shadow on the floor
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new java.awt.geom.Ellipse2D.Double(w / 2.0 - w * 0.16, h - 22, w * 0.32, 14));
        if (skin == null) {                                                     // no skin and no game files yet
            Icons.paint(g, "user", w / 2.0 - 40, h / 2.0 - 40, 80, Theme.alpha(Theme.TEXT, 0.5));
            g.dispose();
            return;
        }
        double scale = Math.min((h - 30) / 34.0, w / 20.0);
        double cx = w / 2.0, base = h - 16;
        double yawR = Math.toRadians(yaw), pitch = Math.toRadians(8);
        double cos = Math.cos(yawR), sin = Math.sin(yawR), cp = Math.cos(pitch), sp = Math.sin(pitch);
        double t = System.currentTimeMillis() / 900.0;
        List<Face> faces = new ArrayList<>();
        for (Box bx : boxes(t)) {
            if (bx.inflate() > 0 && !hasPixels(bx)) continue;                   // empty overlay
            addFaces(faces, bx, cos, sin, cp, sp, scale, cx, base);
        }
        faces.sort((a, b) -> Double.compare(a.depth(), b.depth()));
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        for (Face f : faces) {
            double e1x = f.xs()[1] - f.xs()[0], e1y = f.ys()[1] - f.ys()[0];
            double e2x = f.xs()[2] - f.xs()[0], e2y = f.ys()[2] - f.ys()[0];
            int tw = f.tex().getWidth(), th = f.tex().getHeight();
            // a hair bigger so faces meet without seams
            AffineTransform at = new AffineTransform(e1x / tw * 1.02, e1y / tw * 1.02, e2x / th * 1.02, e2y / th * 1.02,
                    f.xs()[0] - (e1x + e2x) * 0.01, f.ys()[0] - (e1y + e2y) * 0.01);
            g.drawImage(f.tex(), at, null);
        }
        g.dispose();
    }

    private boolean hasPixels(Box b) {
        int x0 = b.u(), y0 = b.v(), x1 = Math.min(64, b.u() + 2 * (b.d() + b.w())), y1 = Math.min(64, b.v() + b.d() + b.h());
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) if ((skin.getRGB(x, y) >>> 24) > 16) return true;
        return false;
    }

    private void addFaces(List<Face> out, Box b, double cos, double sin, double cp, double sp, double scale, double cx, double base) {
        double i = b.inflate();
        double x0 = b.x() - i, x1 = b.x() + b.w() + i, y0 = b.y() - i, y1 = b.y() + b.h() + i, z0 = b.z() - i, z1 = b.z() + b.d() + i;
        int u = b.u(), v = b.v(), w = b.w(), h = b.h(), d = b.d();
        // each face: texture rect, then its top-left, top-right and bottom-left corners seen from outside
        double[][][] f = {
                {{u + d, v + d, w, h}, {x0, y1, z1}, {x1, y1, z1}, {x0, y0, z1}},                 // front (+z)
                {{u + d + w + d, v + d, w, h}, {x1, y1, z0}, {x0, y1, z0}, {x1, y0, z0}},         // back
                {{u, v + d, d, h}, {x0, y1, z0}, {x0, y1, z1}, {x0, y0, z0}},                     // right side
                {{u + d + w, v + d, d, h}, {x1, y1, z1}, {x1, y1, z0}, {x1, y0, z1}},             // left side
                {{u + d, v, w, d}, {x0, y1, z0}, {x1, y1, z0}, {x0, y1, z1}},                     // top
                {{u + d + w, v, w, d}, {x0, y0, z1}, {x1, y0, z1}, {x0, y0, z0}}};                // bottom
        for (double[][] face : f) {
            double[] xs = new double[3], ys = new double[3];
            double depth = 0;
            for (int k = 0; k < 3; k++) {
                double px = face[k + 1][0], py = face[k + 1][1], pz = face[k + 1][2];
                // limb swing round the shoulder / hip
                if (b.swing() != 0) {
                    double dy = py - b.pivotY(), cs = Math.cos(b.swing()), sn = Math.sin(b.swing());
                    double ny = dy * cs - pz * sn, nz = dy * sn + pz * cs;
                    py = b.pivotY() + ny;
                    pz = nz;
                }
                double rx = px * cos + pz * sin, rz = -px * sin + pz * cos;     // turn
                double ry = py * cp - rz * sp, rz2 = py * sp + rz * cp;        // look slightly down
                xs[k] = cx + rx * scale;
                ys[k] = base - ry * scale;
                depth += rz2;
            }
            double cross = (xs[1] - xs[0]) * (ys[2] - ys[0]) - (ys[1] - ys[0]) * (xs[2] - xs[0]);
            if (cross <= 0) continue;                                          // facing away
            int tu = (int) face[0][0], tv = (int) face[0][1], tw = (int) face[0][2], th = (int) face[0][3];
            if (tw <= 0 || th <= 0 || tu + tw > 64 || tv + th > 64) continue;
            out.add(new Face(xs, ys, depth / 3 + i * 0.01, skin.getSubimage(tu, tv, tw, th)));
        }
    }
}

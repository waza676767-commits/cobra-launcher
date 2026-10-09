package dev.life.launcher.ui.pages;

import dev.life.client.core.cosmetics.CosmeticModels;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A little 3D renderer for the launcher: a player (plain, or wearing your skin's colours) with the
 * same cosmetic models the game uses, drawn with flat shading from any angle. Used for the cards
 * and the big turntable preview on the Accessories page.
 */
final class CosmeticPreview {
    private CosmeticPreview() {}

    private record Face(double[] xs, double[] ys, double depth, Color colour, BufferedImage tex, float shade, Color[] corners) {
        Face(double[] xs, double[] ys, double depth, Color colour) { this(xs, ys, depth, colour, null, 1f, null); }
        Face(double[] xs, double[] ys, double depth, Color colour, BufferedImage tex, float shade) { this(xs, ys, depth, colour, tex, shade, null); }
    }

    /** A skin face waiting to be projected: its top-left, top-right and bottom-left corners + texture. */
    private record SkinFace(double[][] p, BufferedImage tex, double[] normal) {}

    /** Renders {@code code} (e.g. "wings:angel,halo:angel") on a player, turned {@code yawDeg} degrees. */
    static BufferedImage render(String code, int w, int h, double yawDeg, boolean withPlayer, BufferedImage skin) {
        Map<String, String> wear = parse(code);
        List<double[][]> quads = new ArrayList<>();
        List<int[]> colours = new ArrayList<>();
        List<double[]> normals = new ArrayList<>();
        CosmeticModels.Out out = new CosmeticModels.Out() {
            @Override
            public void quad(float[] p, float nx, float ny, float nz, int argb, int light) {
                quad4(p, nx, ny, nz, new int[]{argb, argb, argb, argb}, light);
            }

            @Override
            public void quad4(float[] p, float nx, float ny, float nz, int[] argb, int light) {
                double[][] q = new double[4][3];
                for (int i = 0; i < 4; i++) {
                    q[i][0] = p[i * 3];
                    q[i][1] = p[i * 3 + 1];
                    q[i][2] = p[i * 3 + 2];
                }
                quads.add(q);
                colours.add(argb.clone());
                normals.add(new double[]{nx, ny, nz});
            }
        };
        List<SkinFace> skinFaces = new ArrayList<>();
        BufferedImage sk = skin != null ? dev.life.launcher.ui.SkinView.to64(skin) : null;
        if (sk == null && withPlayer) {
            BufferedImage steve = dev.life.launcher.ui.SkinView.steve();
            if (steve != null) sk = dev.life.launcher.ui.SkinView.to64(steve);
        }
        if (withPlayer) {
            if (sk != null) skinFaces(skinFaces, sk, dev.life.launcher.core.Settings.get().skinSlim && skin != null);
            else player(out, skin);                                // no skin and no game files yet: plain shapes
        }
        float t = 40;
        float k = wear.containsKey("size") ? parseSize(wear.get("size")) : 1f;
        CosmeticModels.M head = CosmeticModels.M.identity(), body = CosmeticModels.M.identity();
        CosmeticModels.M hk = head.copy(), bk = body.copy();
        scale(hk, k);
        scale(bk, k);
        if (wear.containsKey("wings")) CosmeticModels.wings(bk, out, 0, t, CosmeticModels.colour(wear.get("wings")), wear.getOrDefault("wingstyle", "feather"), false, 0);
        if (wear.containsKey("tail")) CosmeticModels.tail(bk, out, 0, t, wear.get("tail"));
        if (wear.containsKey("backpack")) CosmeticModels.backpack(body, out, 0, CosmeticModels.colour(wear.get("backpack")));
        if (wear.containsKey("katana")) CosmeticModels.katana(bk, out, 0);
        if (wear.containsKey("halo")) CosmeticModels.halo(hk, out, 0, t, CosmeticModels.haloColour(wear.get("halo")), 0.45f, 0xFF);
        if (wear.containsKey("hat")) CosmeticModels.hat(hk, out, 0, wear.get("hat"));
        if (wear.containsKey("ears")) CosmeticModels.catEars(hk, out, 0, CosmeticModels.colour(wear.get("ears")));
        if (wear.containsKey("bunny")) CosmeticModels.bunnyEars(hk, out, 0, CosmeticModels.colour(wear.get("bunny")), t);
        if (wear.containsKey("horns")) CosmeticModels.horns(hk, out, 0, CosmeticModels.colour(wear.get("horns")));
        if (wear.containsKey("antlers")) CosmeticModels.antlers(hk, out, 0, CosmeticModels.colour(wear.get("antlers")));
        if (wear.containsKey("orbit")) CosmeticModels.orbit(hk, out, 0, t, CosmeticModels.colour(wear.get("orbit")));
        if (wear.containsKey("flowers")) CosmeticModels.flowers(head, out, 0, CosmeticModels.colour(wear.get("flowers")));
        if (wear.containsKey("bowtie")) CosmeticModels.bowtie(body, out, 0, CosmeticModels.colour(wear.get("bowtie")));
        if (wear.containsKey("spikes")) CosmeticModels.spikes(bk, out, 0, CosmeticModels.colour(wear.get("spikes")));
        if (wear.containsKey("scarf")) CosmeticModels.scarf(body, out, 0, t, CosmeticModels.colour(wear.get("scarf")));
        if (wear.containsKey("glasses")) CosmeticModels.glasses(head, out, 0, CosmeticModels.colour(wear.get("glasses")));
        if (wear.containsKey("headphones")) CosmeticModels.headphones(head, out, 0, CosmeticModels.colour(wear.get("headphones")));
        if (wear.containsKey("gloves")) {
            int c = CosmeticModels.colour(wear.get("gloves"));
            CosmeticModels.glove(CosmeticModels.M.identity().translate(-6f, 2f, 0), out, 0, c, -1);
            CosmeticModels.glove(CosmeticModels.M.identity().translate(6f, 2f, 0), out, 0, c, 1);
        }
        if (wear.containsKey("feet")) {
            CosmeticModels.foot(CosmeticModels.M.identity().translate(-2f, 12f, 0), out, 0);
            CosmeticModels.foot(CosmeticModels.M.identity().translate(2f, 12f, 0), out, 0);
        }

        // project: the game draws the model mirrored in x and y (y up on screen), turned by yaw
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(-14);
        double minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9;
        List<Face> faces = new ArrayList<>();
        for (int i = 0; i < quads.size(); i++) {
            double[][] q = quads.get(i);
            double[] xs = new double[4], ys = new double[4];
            double depth = 0;
            for (int j = 0; j < 4; j++) {
                double x = -q[j][0], y = -q[j][1], z = q[j][2];
                double rx = x * Math.cos(yaw) + z * Math.sin(yaw), rz = -x * Math.sin(yaw) + z * Math.cos(yaw);
                double ry = y * Math.cos(pitch) - rz * Math.sin(pitch), rz2 = y * Math.sin(pitch) + rz * Math.cos(pitch);
                xs[j] = -rx;                                           // a real camera: their right arm on your left
                ys[j] = -ry;
                depth += rz2;
                minX = Math.min(minX, -rx);
                maxX = Math.max(maxX, -rx);
                minY = Math.min(minY, -ry);
                maxY = Math.max(maxY, -ry);
            }
            double[] n = normals.get(i);
            double wx = -n[0], wy = -n[1], wz = n[2];
            double nz = -wx * Math.sin(yaw) + wz * Math.cos(yaw), nx = wx * Math.cos(yaw) + wz * Math.sin(yaw);
            double light = 0.62 + 0.38 * Math.max(0, -nz * 0.55 + wy * 0.6 + nx * 0.25);
            int[] cs = colours.get(i);
            Color[] lits = new Color[4];
            boolean same = true;
            for (int j = 0; j < 4; j++) {
                Color c = new Color(cs[j], true);
                lits[j] = new Color(clamp(c.getRed() * light), clamp(c.getGreen() * light), clamp(c.getBlue() * light), c.getAlpha());
                if (cs[j] != cs[0]) same = false;
            }
            faces.add(new Face(xs, ys, depth / 4, lits[0], null, 1f, same ? null : lits));
        }
        // the skin's faces: real textures (projected the same way)
        for (SkinFace sf : skinFaces) {
            double[] xs = new double[3], ys = new double[3], ds = new double[3];
            double depth = 0;
            for (int j = 0; j < 3; j++) {
                double x = -sf.p()[j][0], y = -sf.p()[j][1], z = sf.p()[j][2];
                double rx = x * Math.cos(yaw) + z * Math.sin(yaw), rz = -x * Math.sin(yaw) + z * Math.cos(yaw);
                double ry = y * Math.cos(pitch) - rz * Math.sin(pitch), rz2 = y * Math.sin(pitch) + rz * Math.cos(pitch);
                xs[j] = -rx;                                           // a real camera: their right arm on your left
                ys[j] = -ry;
                ds[j] = rz2;
                minX = Math.min(minX, -rx);
                maxX = Math.max(maxX, -rx);
                minY = Math.min(minY, -ry);
                maxY = Math.max(maxY, -ry);
            }
            depth = (ds[1] + ds[2]) * 1.5;                              // the face's centre (TR-BL midpoint) x3
            double[] n = sf.normal();
            double wx = -n[0], wy = -n[1], wz = n[2];
            double nz = -wx * Math.sin(yaw) + wz * Math.cos(yaw), nx = wx * Math.cos(yaw) + wz * Math.sin(yaw);
            float light = (float) (0.70 + 0.30 * Math.max(0, -nz * 0.55 + wy * 0.6 + nx * 0.25));
            // the fourth corner sits opposite the first: use the middle of the face for sorting
            double cDepth = depth / 3 + 0.0;
            faces.add(new Face(xs, ys, cDepth, null, sf.tex(), light));
        }
        faces.sort((a, b) -> Double.compare(b.depth(), a.depth()));
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        if (faces.isEmpty()) return img;
        double scale = Math.min((w - 16) / (maxX - minX), (h - 16) / (maxY - minY));
        double ox = w / 2.0 - (minX + maxX) / 2 * scale, oy = h / 2.0 - (minY + maxY) / 2 * scale;
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (Face f : faces) {
            if (f.tex() != null) {                                       // a skin face: the texture, mapped exactly
                double ax = ox + f.xs()[0] * scale, ay = oy + f.ys()[0] * scale;
                double e1x = (f.xs()[1] - f.xs()[0]) * scale, e1y = (f.ys()[1] - f.ys()[0]) * scale;
                double e2x = (f.xs()[2] - f.xs()[0]) * scale, e2y = (f.ys()[2] - f.ys()[0]) * scale;
                int tw = f.tex().getWidth(), th = f.tex().getHeight();
                java.awt.geom.AffineTransform at = new java.awt.geom.AffineTransform(e1x / tw * 1.02, e1y / tw * 1.02, e2x / th * 1.02, e2y / th * 1.02,
                        ax - (e1x + e2x) * 0.01, ay - (e1y + e2y) * 0.01);
                Graphics2D tg = (Graphics2D) g.create();
                tg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                tg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                tg.drawImage(f.tex(), at, null);
                if (f.shade() < 0.99f) {                                   // side faces a little darker
                    Path2D sp = new Path2D.Double();
                    sp.moveTo(ax, ay);
                    sp.lineTo(ax + e1x, ay + e1y);
                    sp.lineTo(ax + e1x + e2x, ay + e1y + e2y);
                    sp.lineTo(ax + e2x, ay + e2y);
                    sp.closePath();
                    tg.setComposite(AlphaComposite.SrcAtop);
                    tg.setColor(new Color(0, 0, 0, Math.round((1 - f.shade()) * 255)));
                    tg.setClip(sp);
                    tg.setComposite(AlphaComposite.SrcOver);
                    tg.fill(sp);
                }
                tg.dispose();
                continue;
            }
            Path2D p = new Path2D.Double();
            p.moveTo(ox + f.xs()[0] * scale, oy + f.ys()[0] * scale);
            for (int j = 1; j < 4; j++) p.lineTo(ox + f.xs()[j] * scale, oy + f.ys()[j] * scale);
            p.closePath();
            if (f.corners() != null) {                     // colours blending across the face
                int lo = 0, hi = 0;
                for (int j = 1; j < 4; j++) {
                    if (lum(f.corners()[j]) < lum(f.corners()[lo])) lo = j;
                    if (lum(f.corners()[j]) > lum(f.corners()[hi])) hi = j;
                }
                double x0 = ox + f.xs()[lo] * scale, y0 = oy + f.ys()[lo] * scale, x1 = ox + f.xs()[hi] * scale, y1 = oy + f.ys()[hi] * scale;
                if (Math.hypot(x1 - x0, y1 - y0) > 0.5) g.setPaint(new GradientPaint((float) x0, (float) y0, f.corners()[lo], (float) x1, (float) y1, f.corners()[hi]));
                else g.setColor(f.colour());
            } else g.setColor(f.colour());
            g.fill(p);
            g.draw(p);                                  // closes hairline gaps between faces
        }
        g.dispose();
        return img;
    }

    private static void scale(CosmeticModels.M m, float k) {
        if (k == 1f) return;
        // scale by building the geometry smaller: a uniform scale is the same as moving the camera,
        // so for the preview we only need relative size against the player
        m.scale(k);
    }

    private static float parseSize(String s) {
        try {
            return Math.max(0.6f, Math.min(1.6f, Integer.parseInt(s) / 100f));
        } catch (NumberFormatException e) {
            return 1f;
        }
    }

    private static int lum(Color c) {
        return c.getRed() * 299 + c.getGreen() * 587 + c.getBlue() * 114;
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    /**
     * The player as the real skin: every part textured (with the hat, jacket, sleeve and trouser
     * layers), in the same space as the cosmetics (y down from the neck, face at -z).
     */
    private static void skinFaces(List<SkinFace> out, BufferedImage skin, boolean slim) {
        int aw = slim ? 3 : 4;
        int[][] boxes = {           // x, yTop(model, y down), z, w, h, d, u, v, inflate*100
                {-4, -8, -4, 8, 8, 8, 0, 0, 0}, {-4, 0, -2, 8, 12, 4, 16, 16, 0},
                {-4 - aw, 0, -2, aw, 12, 4, 40, 16, 0}, {4, 0, -2, aw, 12, 4, 32, 48, 0},
                {-4, 12, -2, 4, 12, 4, 0, 16, 0}, {0, 12, -2, 4, 12, 4, 16, 48, 0},
                {-4, -8, -4, 8, 8, 8, 32, 0, 50}, {-4, 0, -2, 8, 12, 4, 16, 32, 25},
                {-4 - aw, 0, -2, aw, 12, 4, 40, 32, 25}, {4, 0, -2, aw, 12, 4, 48, 48, 25},
                {-4, 12, -2, 4, 12, 4, 0, 32, 25}, {0, 12, -2, 4, 12, 4, 0, 48, 25}};
        for (int[] b : boxes) {
            double i = b[8] / 100.0;
            double x0 = b[0] - i, x1 = b[0] + b[3] + i, y0 = b[1] - i, y1 = b[1] + b[4] + i, z0 = b[2] - i, z1 = b[2] + b[5] + i;
            int u = b[6], v = b[7], w = b[3], h = b[4], d = b[5];
            // y here runs down (y0 = top); the face is at -z (z0)
            double[][][] f = {
                    {{u + d, v + d, w, h}, {x0, y0, z0}, {x1, y0, z0}, {x0, y1, z0}, {0, 0, -1}},              // front
                    {{u + d + w + d, v + d, w, h}, {x1, y0, z1}, {x0, y0, z1}, {x1, y1, z1}, {0, 0, 1}},       // back
                    {{u, v + d, d, h}, {x0, y0, z1}, {x0, y0, z0}, {x0, y1, z1}, {-1, 0, 0}},                 // right side
                    {{u + d + w, v + d, d, h}, {x1, y0, z0}, {x1, y0, z1}, {x1, y1, z0}, {1, 0, 0}},          // left side
                    {{u + d, v, w, d}, {x0, y0, z1}, {x1, y0, z1}, {x0, y0, z0}, {0, -1, 0}},                 // top
                    {{u + d + w, v, w, d}, {x0, y1, z0}, {x1, y1, z0}, {x0, y1, z1}, {0, 1, 0}}};             // bottom
            for (double[][] face : f) {
                int tu = (int) face[0][0], tv = (int) face[0][1], tw = (int) face[0][2], th = (int) face[0][3];
                if (tw <= 0 || th <= 0 || tu + tw > 64 || tv + th > 64) continue;
                BufferedImage tex = skin.getSubimage(tu, tv, tw, th);
                if (b[8] > 0 && empty(tex)) continue;                 // no second layer drawn there
                out.add(new SkinFace(new double[][]{face[1], face[2], face[3]}, tex, face[4]));
            }
        }
    }

    private static boolean empty(BufferedImage t) {
        for (int y = 0; y < t.getHeight(); y++) for (int x = 0; x < t.getWidth(); x++) if ((t.getRGB(x, y) >>> 24) > 16) return false;
        return true;
    }

    /** A player made of boxes, coloured from the skin's main areas when there is one. */
    private static void player(CosmeticModels.Out out, BufferedImage skin) {
        CosmeticModels.M m = CosmeticModels.M.identity();
        int face = skinColour(skin, 8, 8, 8, 8, 0xFFC89A74), hair = skinColour(skin, 8, 0, 8, 8, 0xFF4A3426);
        int shirt = skinColour(skin, 20, 20, 8, 12, 0xFF3A6EA5), arm = skinColour(skin, 44, 20, 4, 12, 0xFFC89A74);
        int legs = skinColour(skin, 4, 20, 4, 12, 0xFF2F3B66);
        CosmeticModels.box(m, out, 0, face, -4, -8, -4, 4, 0, 4);
        CosmeticModels.box(m, out, 0, hair, -4.05f, -8.05f, -4.05f, 4.05f, -6.5f, 4.05f);
        CosmeticModels.box(m, out, 0, 0xFF1C1C20, -3, -4.6f, -4.1f, -1.2f, -3.6f, -4f);
        CosmeticModels.box(m, out, 0, 0xFF1C1C20, 1.2f, -4.6f, -4.1f, 3, -3.6f, -4f);
        CosmeticModels.box(m, out, 0, shirt, -4, 0, -2, 4, 12, 2);
        CosmeticModels.box(m, out, 0, arm, -8, 0, -2, -4, 12, 2);
        CosmeticModels.box(m, out, 0, arm, 4, 0, -2, 8, 12, 2);
        CosmeticModels.box(m, out, 0, legs, -4, 12, -2, 0, 24, 2);
        CosmeticModels.box(m, out, 0, legs, 0, 12, -2, 4, 24, 2);
    }

    /** Average colour of a skin area (x, y, w, h on the 64x64 skin), or the fallback. */
    private static int skinColour(BufferedImage skin, int x, int y, int w, int h, int fallback) {
        if (skin == null || skin.getWidth() < 64) return fallback;
        long r = 0, g = 0, b = 0, n = 0;
        for (int yy = y; yy < y + h && yy < skin.getHeight(); yy++) {
            for (int xx = x; xx < x + w; xx++) {
                int c = skin.getRGB(xx, yy);
                if ((c >>> 24) < 128) continue;
                r += c >> 16 & 255;
                g += c >> 8 & 255;
                b += c & 255;
                n++;
            }
        }
        return n == 0 ? fallback : 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
    }

    static Map<String, String> parse(String code) {
        Map<String, String> out = new HashMap<>();
        if (code == null) return out;
        for (String part : code.split(",")) {
            if (part.isEmpty()) continue;
            int i = part.indexOf(':');
            out.put(i < 0 ? part : part.substring(0, i), i < 0 ? "" : part.substring(i + 1));
        }
        return out;
    }
}

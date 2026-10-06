package dev.cobra.launcher.ui.pages;

import dev.cobra.client.core.cosmetics.CosmeticModels;

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

    private record Face(double[] xs, double[] ys, double depth, Color colour) {}

    /** Renders {@code code} (e.g. "wings:angel,halo:angel") on a player, turned {@code yawDeg} degrees. */
    static BufferedImage render(String code, int w, int h, double yawDeg, boolean withPlayer, BufferedImage skin) {
        Map<String, String> wear = parse(code);
        List<double[][]> quads = new ArrayList<>();
        List<Integer> colours = new ArrayList<>();
        List<double[]> normals = new ArrayList<>();
        CosmeticModels.Out out = (p, nx, ny, nz, argb, light) -> {
            double[][] q = new double[4][3];
            for (int i = 0; i < 4; i++) {
                q[i][0] = p[i * 3];
                q[i][1] = p[i * 3 + 1];
                q[i][2] = p[i * 3 + 2];
            }
            quads.add(q);
            colours.add(argb);
            normals.add(new double[]{nx, ny, nz});
        };
        if (withPlayer) player(out, skin);
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
                xs[j] = rx;
                ys[j] = -ry;
                depth += rz2;
                minX = Math.min(minX, rx);
                maxX = Math.max(maxX, rx);
                minY = Math.min(minY, -ry);
                maxY = Math.max(maxY, -ry);
            }
            double[] n = normals.get(i);
            double wx = -n[0], wy = -n[1], wz = n[2];
            double nz = -wx * Math.sin(yaw) + wz * Math.cos(yaw), nx = wx * Math.cos(yaw) + wz * Math.sin(yaw);
            double light = 0.62 + 0.38 * Math.max(0, -nz * 0.55 + wy * 0.6 + nx * 0.25);
            Color c = new Color(colours.get(i), true);
            Color lit = new Color(clamp(c.getRed() * light), clamp(c.getGreen() * light), clamp(c.getBlue() * light), c.getAlpha());
            faces.add(new Face(xs, ys, depth / 4, lit));
        }
        faces.sort((a, b) -> Double.compare(b.depth(), a.depth()));
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        if (faces.isEmpty()) return img;
        double scale = Math.min((w - 16) / (maxX - minX), (h - 16) / (maxY - minY));
        double ox = w / 2.0 - (minX + maxX) / 2 * scale, oy = h / 2.0 - (minY + maxY) / 2 * scale;
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (Face f : faces) {
            Path2D p = new Path2D.Double();
            p.moveTo(ox + f.xs()[0] * scale, oy + f.ys()[0] * scale);
            for (int j = 1; j < 4; j++) p.lineTo(ox + f.xs()[j] * scale, oy + f.ys()[j] * scale);
            p.closePath();
            g.setColor(f.colour());
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

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
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

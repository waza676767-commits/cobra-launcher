package dev.life.launcher.ui;

import java.awt.*;
import java.awt.geom.*;

/** Outline icons on a 24-unit grid. */
public final class Icons {
    private Icons() {}

    private static final java.util.Map<String, java.awt.image.BufferedImage> CACHE = new java.util.HashMap<>();

    /**
     * Draws an icon. Rasterised once per name/size/colour/screen scale and then blitted, because
     * stroking the vector paths every frame was a big part of hover/animation repaint cost.
     */
    public static void paint(Graphics2D g0, String name, double x, double y, double size, Color c) {
        double scale = Math.max(1, g0.getTransform().getScaleX());
        int px = (int) Math.ceil(size * scale) + 2;
        String key = name + "|" + Math.round(size * 4) + "|" + c.getRGB() + "|" + Math.round(scale * 100);
        java.awt.image.BufferedImage img = CACHE.get(key);
        if (img == null) {
            if (CACHE.size() > 600) CACHE.clear();
            img = new java.awt.image.BufferedImage(px, px, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D ig = img.createGraphics();
            ig.translate(1, 1);
            ig.scale(scale, scale);
            vector(ig, name, 0, 0, size, c);
            ig.dispose();
            CACHE.put(key, img);
        }
        Graphics2D g = (Graphics2D) g0.create();
        g.translate(x, y);
        g.scale(1 / scale, 1 / scale);
        g.drawImage(img, -1, -1, null);
        g.dispose();
    }

    private static void vector(Graphics2D g0, String name, double x, double y, double size, Color c) {
        Graphics2D g = (Graphics2D) g0.create();
        Theme.aa(g);
        g.translate(x, y);
        g.scale(size / 24.0, size / 24.0);
        g.setColor(c);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D p = new Path2D.Double();
        switch (name) {
            case "home" -> {
                p.moveTo(3.5, 10.5); p.lineTo(12, 3.8); p.lineTo(20.5, 10.5); p.lineTo(20.5, 19);
                p.quadTo(20.5, 20.5, 19, 20.5); p.lineTo(15, 20.5); p.lineTo(15, 14.5); p.lineTo(9, 14.5);
                p.lineTo(9, 20.5); p.lineTo(5, 20.5); p.quadTo(3.5, 20.5, 3.5, 19); p.closePath();
                g.draw(p);
            }
            case "mods" -> {
                p.moveTo(12, 2.8); p.lineTo(20.2, 7.4); p.lineTo(20.2, 16.6); p.lineTo(12, 21.2);
                p.lineTo(3.8, 16.6); p.lineTo(3.8, 7.4); p.closePath();
                p.moveTo(3.8, 7.4); p.lineTo(12, 12); p.lineTo(20.2, 7.4);
                p.moveTo(12, 12); p.lineTo(12, 21.2);
                g.draw(p);
            }
            case "packs" -> {
                p.moveTo(12, 3); p.lineTo(21, 7.8); p.lineTo(12, 12.6); p.lineTo(3, 7.8); p.closePath();
                p.moveTo(3, 12); p.lineTo(12, 16.8); p.lineTo(21, 12);
                p.moveTo(3, 16.2); p.lineTo(12, 21); p.lineTo(21, 16.2);
                g.draw(p);
            }
            case "analytics" -> {
                g.draw(new RoundRectangle2D.Double(3, 3, 18, 18, 10, 10));
                p.moveTo(7, 15); p.lineTo(10.5, 11); p.lineTo(13.5, 13.5); p.lineTo(17, 8.5);
                g.draw(p);
            }
            case "settings" -> {
                int teeth = 8;
                for (int i = 0; i < teeth; i++) {
                    double a = i * Math.PI * 2 / teeth;
                    double[][] pts = {{7.4, a - 0.30}, {9.6, a - 0.17}, {9.6, a + 0.17}, {7.4, a + 0.30}};
                    for (int k = 0; k < pts.length; k++) {
                        double px = 12 + Math.cos(pts[k][1]) * pts[k][0], py = 12 + Math.sin(pts[k][1]) * pts[k][0];
                        if (i == 0 && k == 0) p.moveTo(px, py); else p.lineTo(px, py);
                    }
                    double next = (i + 1) * Math.PI * 2 / teeth - 0.30;
                    p.append(new Arc2D.Double(12 - 7.4, 12 - 7.4, 14.8, 14.8, -Math.toDegrees(a + 0.30), -Math.toDegrees(next - (a + 0.30)), Arc2D.OPEN), true);
                }
                p.closePath();
                g.draw(p);
                g.draw(new Ellipse2D.Double(9, 9, 6, 6));
            }
            case "user" -> {
                g.draw(new Ellipse2D.Double(8.3, 3.8, 7.4, 7.4));
                p.moveTo(4.5, 20.5); p.curveTo(4.5, 15.5, 8, 13.8, 12, 13.8); p.curveTo(16, 13.8, 19.5, 15.5, 19.5, 20.5);
                g.draw(p);
            }
            case "chevron-right" -> { p.moveTo(9.5, 6); p.lineTo(15.5, 12); p.lineTo(9.5, 18); g.draw(p); }
            case "chevron-left" -> { p.moveTo(14.5, 6); p.lineTo(8.5, 12); p.lineTo(14.5, 18); g.draw(p); }
            case "chevron-down" -> { p.moveTo(6, 9.5); p.lineTo(12, 15.5); p.lineTo(18, 9.5); g.draw(p); }
            case "close" -> { p.moveTo(6.5, 6.5); p.lineTo(17.5, 17.5); p.moveTo(17.5, 6.5); p.lineTo(6.5, 17.5); g.draw(p); }
            case "minimize" -> { p.moveTo(6.5, 12); p.lineTo(17.5, 12); g.draw(p); }
            case "plus" -> { p.moveTo(12, 5.5); p.lineTo(12, 18.5); p.moveTo(5.5, 12); p.lineTo(18.5, 12); g.draw(p); }
            case "check" -> { p.moveTo(5, 12.5); p.lineTo(10, 17.2); p.lineTo(19, 7); g.draw(p); }
            case "trash" -> {
                p.moveTo(4.5, 7); p.lineTo(19.5, 7);
                p.moveTo(9.5, 7); p.lineTo(9.5, 4.8); p.lineTo(14.5, 4.8); p.lineTo(14.5, 7);
                p.moveTo(6.5, 7); p.lineTo(7.5, 19.2); p.quadTo(7.6, 20.5, 9, 20.5); p.lineTo(15, 20.5); p.quadTo(16.4, 20.5, 16.5, 19.2); p.lineTo(17.5, 7);
                g.draw(p);
            }
            case "folder" -> {
                p.moveTo(3.5, 7); p.quadTo(3.5, 5, 5.5, 5); p.lineTo(9.5, 5); p.lineTo(11.5, 7.2); p.lineTo(18.5, 7.2);
                p.quadTo(20.5, 7.2, 20.5, 9.2); p.lineTo(20.5, 17.5); p.quadTo(20.5, 19.5, 18.5, 19.5); p.lineTo(5.5, 19.5);
                p.quadTo(3.5, 19.5, 3.5, 17.5); p.closePath();
                g.draw(p);
            }
            case "search" -> { g.draw(new Ellipse2D.Double(4, 4, 12.5, 12.5)); p.moveTo(15.2, 15.2); p.lineTo(20, 20); g.draw(p); }
            case "download" -> {
                p.moveTo(12, 4); p.lineTo(12, 15); p.moveTo(7.5, 10.8); p.lineTo(12, 15.3); p.lineTo(16.5, 10.8);
                p.moveTo(4.5, 16.5); p.lineTo(4.5, 19.5); p.lineTo(19.5, 19.5); p.lineTo(19.5, 16.5);
                g.draw(p);
            }
            case "copy" -> {
                g.draw(new RoundRectangle2D.Double(8.5, 8.5, 12, 12, 5, 5));
                p.moveTo(15.5, 8.5); p.lineTo(15.5, 5.5); p.quadTo(15.5, 3.5, 13.5, 3.5); p.lineTo(5.5, 3.5);
                p.quadTo(3.5, 3.5, 3.5, 5.5); p.lineTo(3.5, 13.5); p.quadTo(3.5, 15.5, 5.5, 15.5); p.lineTo(8.5, 15.5);
                g.draw(p);
            }
            case "external" -> {
                p.moveTo(13.5, 4); p.lineTo(20, 4); p.lineTo(20, 10.5); p.moveTo(20, 4); p.lineTo(11, 13);
                p.moveTo(17.5, 14); p.lineTo(17.5, 18.5); p.quadTo(17.5, 20, 16, 20); p.lineTo(5.5, 20);
                p.quadTo(4, 20, 4, 18.5); p.lineTo(4, 8); p.quadTo(4, 6.5, 5.5, 6.5); p.lineTo(10, 6.5);
                g.draw(p);
            }
            case "play" -> {
                p.moveTo(8, 5.5); p.lineTo(18.5, 12); p.lineTo(8, 18.5); p.closePath();
                g.fill(p);
                g.draw(p);
            }
            case "logout" -> {
                p.moveTo(10, 4); p.lineTo(6, 4); p.quadTo(4, 4, 4, 6); p.lineTo(4, 18); p.quadTo(4, 20, 6, 20); p.lineTo(10, 20);
                p.moveTo(10, 12); p.lineTo(20, 12); p.moveTo(16, 8); p.lineTo(20, 12); p.lineTo(16, 16);
                g.draw(p);
            }
            case "sliders" -> {
                p.moveTo(4, 7); p.lineTo(20, 7); p.moveTo(4, 12); p.lineTo(20, 12); p.moveTo(4, 17); p.lineTo(20, 17);
                g.draw(p);
                g.setColor(c);
                fillDot(g, 9, 7); fillDot(g, 15, 12); fillDot(g, 8, 17);
            }
            case "clock" -> {
                g.draw(new Ellipse2D.Double(3.5, 3.5, 17, 17));
                p.moveTo(12, 7.5); p.lineTo(12, 12); p.lineTo(15, 14);
                g.draw(p);
            }

            case "keyboard" -> {
                g.draw(new RoundRectangle2D.Double(2.5, 6, 19, 12, 5, 5));
                for (int i = 0; i < 4; i++) { p.moveTo(6 + i * 4, 10); p.lineTo(6.2 + i * 4, 10); }
                for (int i = 0; i < 4; i++) { p.moveTo(6 + i * 4, 13); p.lineTo(6.2 + i * 4, 13); }
                p.moveTo(8, 15.5); p.lineTo(16, 15.5);
                g.draw(p);
            }
            case "mouse" -> {
                g.draw(new RoundRectangle2D.Double(6.5, 3, 11, 18, 11, 11));
                p.moveTo(12, 3); p.lineTo(12, 9.5); p.moveTo(6.5, 9.5); p.lineTo(17.5, 9.5);
                g.draw(p);
            }
            case "gauge" -> {
                g.draw(new Arc2D.Double(3, 5, 18, 18, 0, 180, Arc2D.OPEN));
                p.moveTo(12, 14); p.lineTo(16.5, 8.5); p.moveTo(3, 14); p.lineTo(21, 14);
                g.draw(p);
            }
            case "signal" -> {
                p.moveTo(5, 19); p.lineTo(5, 16); p.moveTo(9.5, 19); p.lineTo(9.5, 12.5);
                p.moveTo(14, 19); p.lineTo(14, 9); p.moveTo(18.5, 19); p.lineTo(18.5, 5);
                g.draw(p);
            }
            case "pin" -> {
                p.moveTo(12, 21); p.curveTo(12, 21, 5, 13.5, 5, 9.5); p.curveTo(5, 5.5, 8.2, 3, 12, 3);
                p.curveTo(15.8, 3, 19, 5.5, 19, 9.5); p.curveTo(19, 13.5, 12, 21, 12, 21);
                g.draw(p);
                g.draw(new Ellipse2D.Double(9.5, 7, 5, 5));
            }
            case "compass" -> {
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                p.moveTo(15.5, 8.5); p.lineTo(13.2, 13.2); p.lineTo(8.5, 15.5); p.lineTo(10.8, 10.8); p.closePath();
                g.fill(p);
            }
            case "shield" -> {
                p.moveTo(12, 3); p.lineTo(19.5, 6); p.lineTo(19.5, 11.5); p.curveTo(19.5, 16.5, 15.5, 19.5, 12, 21);
                p.curveTo(8.5, 19.5, 4.5, 16.5, 4.5, 11.5); p.lineTo(4.5, 6); p.closePath();
                g.draw(p);
            }
            case "ruler" -> {
                p.moveTo(3, 12); p.lineTo(21, 12); p.moveTo(6.5, 8.5); p.lineTo(3, 12); p.lineTo(6.5, 15.5);
                p.moveTo(17.5, 8.5); p.lineTo(21, 12); p.lineTo(17.5, 15.5);
                g.draw(p);
            }
            case "flame" -> {
                p.moveTo(12, 21); p.curveTo(7.5, 21, 5.5, 17.5, 5.5, 14.5); p.curveTo(5.5, 10, 10, 8, 10.5, 3);
                p.curveTo(14, 5.5, 18.5, 9.5, 18.5, 14.5); p.curveTo(18.5, 17.5, 16.5, 21, 12, 21); p.closePath();
                g.draw(p);
            }
            case "flask" -> {
                p.moveTo(9.5, 3); p.lineTo(14.5, 3); p.moveTo(10.5, 3); p.lineTo(10.5, 9); p.lineTo(5, 18.5);
                p.quadTo(4.5, 20.5, 6.5, 20.5); p.lineTo(17.5, 20.5); p.quadTo(19.5, 20.5, 19, 18.5);
                p.lineTo(13.5, 9); p.lineTo(13.5, 3); p.moveTo(7.2, 15); p.lineTo(16.8, 15);
                g.draw(p);
            }
            case "skull" -> {
                p.moveTo(6, 16); p.curveTo(3.5, 13, 4, 4, 12, 4); p.curveTo(20, 4, 20.5, 13, 18, 16);
                p.lineTo(18, 19.5); p.lineTo(6, 19.5); p.closePath();
                g.draw(p);
                g.fill(new Ellipse2D.Double(8, 10, 3, 3)); g.fill(new Ellipse2D.Double(13, 10, 3, 3));
            }
            case "list" -> {
                for (int i = 0; i < 4; i++) { p.moveTo(8, 6 + i * 4); p.lineTo(20, 6 + i * 4); }
                g.draw(p);
                for (int i = 0; i < 4; i++) g.fill(new Ellipse2D.Double(3.5, 5 + i * 4, 2, 2));
            }
            case "wind" -> {
                p.moveTo(3, 9); p.lineTo(15, 9); p.curveTo(18, 9, 18, 5, 15.5, 5);
                p.moveTo(3, 13); p.lineTo(19, 13); p.curveTo(22, 13, 22, 17.5, 19, 17.5);
                p.moveTo(3, 17); p.lineTo(11, 17);
                g.draw(p);
            }
            case "sword" -> {
                p.moveTo(20, 4); p.lineTo(20, 8); p.lineTo(9.5, 18.5); p.lineTo(5.5, 14.5); p.lineTo(16, 4); p.closePath();
                p.moveTo(5, 13); p.lineTo(11, 19); p.moveTo(7.5, 16.5); p.lineTo(4, 20);
                g.draw(p);
            }
            case "sun" -> {
                g.draw(new Ellipse2D.Double(8, 8, 8, 8));
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    p.moveTo(12 + Math.cos(a) * 6.5, 12 + Math.sin(a) * 6.5); p.lineTo(12 + Math.cos(a) * 9, 12 + Math.sin(a) * 9);
                }
                g.draw(p);
            }
            case "crosshair" -> {
                g.draw(new Ellipse2D.Double(5, 5, 14, 14));
                p.moveTo(12, 2.5); p.lineTo(12, 8); p.moveTo(12, 16); p.lineTo(12, 21.5);
                p.moveTo(2.5, 12); p.lineTo(8, 12); p.moveTo(16, 12); p.lineTo(21.5, 12);
                g.draw(p);
            }
            case "drop" -> {
                p.moveTo(12, 3); p.curveTo(12, 3, 5.5, 10.5, 5.5, 14.5); p.curveTo(5.5, 18.5, 8.5, 21, 12, 21);
                p.curveTo(15.5, 21, 18.5, 18.5, 18.5, 14.5); p.curveTo(18.5, 10.5, 12, 3, 12, 3); p.closePath();
                g.draw(p);
            }
            case "camera" -> {
                g.draw(new RoundRectangle2D.Double(3, 7, 18, 13, 5, 5));
                p.moveTo(8.5, 7); p.lineTo(10, 4.5); p.lineTo(14, 4.5); p.lineTo(15.5, 7);
                g.draw(p);
                g.draw(new Ellipse2D.Double(8.5, 10, 7, 7));
            }
            case "shake" -> {
                g.draw(new RoundRectangle2D.Double(6, 6, 12, 12, 4, 4));
                p.moveTo(3, 9); p.lineTo(3, 15); p.moveTo(21, 9); p.lineTo(21, 15);
                g.draw(p);
            }
            case "eye" -> {
                p.moveTo(2.5, 12); p.curveTo(5.5, 6.5, 18.5, 6.5, 21.5, 12); p.curveTo(18.5, 17.5, 5.5, 17.5, 2.5, 12); p.closePath();
                g.draw(p);
                g.draw(new Ellipse2D.Double(9, 9, 6, 6));
            }
            case "aperture" -> {
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                p.moveTo(12, 3); p.lineTo(16, 12); p.moveTo(20, 9); p.lineTo(10, 10);
                p.moveTo(17, 19); p.lineTo(10.5, 12.5); p.moveTo(6, 18.5); p.lineTo(9, 9.5);
                g.draw(p);
            }
            case "sparkle" -> {
                p.moveTo(12, 3); p.quadTo(12.8, 11.2, 21, 12); p.quadTo(12.8, 12.8, 12, 21); p.quadTo(11.2, 12.8, 3, 12);
                p.quadTo(11.2, 11.2, 12, 3); p.closePath();
                g.fill(p);
            }
            case "run" -> {
                p.moveTo(5, 6); p.lineTo(11, 12); p.lineTo(5, 18); p.moveTo(12, 6); p.lineTo(18, 12); p.lineTo(12, 18);
                g.draw(p);
            }
            case "flag" -> {
                p.moveTo(5.5, 21); p.lineTo(5.5, 3.5); p.lineTo(18.5, 3.5); p.lineTo(15.5, 8); p.lineTo(18.5, 12.5); p.lineTo(5.5, 12.5);
                g.draw(p);
            }
            case "chat" -> {
                p.moveTo(4, 5.5); p.quadTo(4, 4, 5.5, 4); p.lineTo(18.5, 4); p.quadTo(20, 4, 20, 5.5); p.lineTo(20, 14.5);
                p.quadTo(20, 16, 18.5, 16); p.lineTo(10, 16); p.lineTo(6, 20); p.lineTo(6, 16); p.lineTo(5.5, 16);
                p.quadTo(4, 16, 4, 14.5); p.closePath();
                g.draw(p);
            }
            case "mask" -> {
                p.moveTo(3, 8); p.curveTo(8, 5, 16, 5, 21, 8); p.curveTo(21, 15, 17, 17.5, 14, 15.5);
                p.curveTo(13, 14.8, 11, 14.8, 10, 15.5); p.curveTo(7, 17.5, 3, 15, 3, 8); p.closePath();
                g.draw(p);
            }
            case "bed" -> {
                p.moveTo(3, 19); p.lineTo(3, 6); p.moveTo(3, 15); p.lineTo(21, 15); p.lineTo(21, 19);
                p.moveTo(3, 11); p.lineTo(21, 11); p.lineTo(21, 15);
                g.draw(p);
                g.draw(new RoundRectangle2D.Double(5, 7.5, 5, 3.5, 2, 2));
            }
            case "star" -> {
                p.moveTo(12, 2.5); p.curveTo(12.6, 7.5, 13.6, 9.6, 14.3, 9.7); p.curveTo(15.5, 10.5, 17, 11.4, 21.5, 12);
                p.curveTo(17, 12.6, 15.5, 13.5, 14.3, 14.3); p.curveTo(13.6, 14.4, 12.6, 16.5, 12, 21.5);
                p.curveTo(11.4, 16.5, 10.4, 14.4, 9.7, 14.3); p.curveTo(8.5, 13.5, 7, 12.6, 2.5, 12);
                p.curveTo(7, 11.4, 8.5, 10.5, 9.7, 9.7); p.curveTo(10.4, 9.6, 11.4, 7.5, 12, 2.5); p.closePath();
                g.fill(p);
            }
            case "link" -> {
                g.draw(new RoundRectangle2D.Double(3, 8.5, 10, 7, 7, 7));
                g.draw(new RoundRectangle2D.Double(11, 8.5, 10, 7, 7, 7));
            }
            case "globe" -> {
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                g.draw(new Ellipse2D.Double(8, 3, 8, 18));
                p.moveTo(3, 12); p.lineTo(21, 12);
                g.draw(p);
            }
            default -> g.draw(new Ellipse2D.Double(5, 5, 14, 14));
        }
        g.dispose();
    }

    private static void fillDot(Graphics2D g, double cx, double cy) {
        Color c = g.getColor();
        g.setColor(Theme.BLACK);
        g.fill(new Ellipse2D.Double(cx - 2.4, cy - 2.4, 4.8, 4.8));
        g.setColor(c);
        g.draw(new Ellipse2D.Double(cx - 2.4, cy - 2.4, 4.8, 4.8));
    }
}

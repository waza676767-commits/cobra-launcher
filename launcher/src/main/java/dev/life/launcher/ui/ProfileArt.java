package dev.life.launcher.ui;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/** Profile picture tile: the chosen image, or initials on a monochrome gradient. */
public final class ProfileArt {
    private ProfileArt() {}

    public static void draw(Graphics2D g0, BufferedImage img, String name, double x, double y, double size, boolean large) {
        Graphics2D g = (Graphics2D) g0.create();
        Theme.aa(g);
        double r = size * 0.3;
        Shape shape = new RoundRectangle2D.Double(x, y, size, size, r * 2, r * 2);
        if (img != null) {
            Theme.roundedImage(g, img, x, y, size, size, r * 2, true);   // smooth corners
        } else {
            int seed = Math.abs(name.hashCode());
            Color a = Theme.mix(Theme.PANEL, Theme.TEXT, 0.18 + (seed % 5) * 0.05);
            Color b = Theme.mix(Theme.PANEL, Theme.BLACK, 0.2);
            g.setPaint(new GradientPaint((float) x, (float) y, a, (float) (x + size), (float) (y + size), b));
            g.fill(shape);
            String letters = initials(name);
            Theme.center(g, letters, Theme.font(Theme.BOLD, (float) (size * (letters.length() > 1 ? 0.34 : 0.42))), Theme.TEXT, x, y, size, size);
        }
        g.setColor(Theme.alpha(Theme.TEXT, large ? 0.18 : 0.12));
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, size - 1, size - 1, r * 2 - 1, r * 2 - 1));
        g.dispose();
    }

    public static String initials(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return "?";
        String[] parts = n.split("\\s+");
        if (parts.length >= 2) return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
        return n.substring(0, Math.min(2, n.length())).toUpperCase();
    }
}

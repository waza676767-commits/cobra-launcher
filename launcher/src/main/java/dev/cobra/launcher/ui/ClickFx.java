package dev.cobra.launcher.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Click effect: a small ring plus a burst of dots where you click, ivory in dark mode and
 * charcoal in light mode. Lives on top of everything but never takes mouse events.
 */
public final class ClickFx extends JComponent {
    private record Burst(double x, double y, long start, double[] angles, double[] speeds) {}

    private static final long LIFE = 520_000_000L;
    private final List<Burst> bursts = new ArrayList<>();
    private final Timer timer = new Timer(15, e -> tick());

    public ClickFx(JFrame frame) {
        setOpaque(false);
        Toolkit.getDefaultToolkit().addAWTEventListener((AWTEventListener) ev -> {
            if (!(ev instanceof MouseEvent me) || me.getID() != MouseEvent.MOUSE_PRESSED) return;
            if (!(me.getSource() instanceof Component src) || SwingUtilities.getWindowAncestor(src) != frame) return;
            if (!dev.cobra.launcher.core.Settings.get().animations) return;
            Point p = SwingUtilities.convertPoint(src, me.getPoint(), this);
            spawn(p.x, p.y);
        }, AWTEvent.MOUSE_EVENT_MASK);
    }

    /** Invisible to hit-testing, so clicks and cursors go straight through. */
    @Override
    public boolean contains(int x, int y) {
        return false;
    }

    private void spawn(double x, double y) {
        int n = 7;
        double[] a = new double[n], s = new double[n];
        double base = Math.random() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            a[i] = base + i * Math.PI * 2 / n + (Math.random() - 0.5) * 0.5;
            s[i] = 16 + Math.random() * 12;
        }
        bursts.add(new Burst(x, y, System.nanoTime(), a, s));
        if (!timer.isRunning()) timer.start();
        repaint((int) x - 40, (int) y - 40, 80, 80);
    }

    private void tick() {
        long now = System.nanoTime();
        for (Burst b : bursts) repaint((int) b.x - 40, (int) b.y - 40, 80, 80);
        bursts.removeIf(b -> now - b.start > LIFE);
        if (bursts.isEmpty()) timer.stop();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        if (bursts.isEmpty()) return;
        Graphics2D g = Theme.aa(g0.create());
        long now = System.nanoTime();
        Color ink = Theme.TEXT;
        for (Burst b : bursts) {
            double t = Math.min(1, (now - b.start) / (double) LIFE);
            double ease = 1 - Math.pow(1 - t, 3);
            // ring
            double rr = 4 + 14 * ease;
            g.setColor(Theme.alpha(ink, 0.45 * (1 - t)));
            g.setStroke(new BasicStroke(1.4f));
            g.draw(new Ellipse2D.Double(b.x - rr, b.y - rr, rr * 2, rr * 2));
            // dots
            for (int i = 0; i < b.angles.length; i++) {
                double d = b.speeds[i] * ease;
                double px = b.x + Math.cos(b.angles[i]) * d, py = b.y + Math.sin(b.angles[i]) * d;
                double size = 3.2 * (1 - t) + 0.6;
                g.setColor(Theme.alpha(ink, 0.9 * (1 - t)));
                g.fill(new Ellipse2D.Double(px - size / 2, py - size / 2, size, size));
            }
        }
        g.dispose();
    }
}

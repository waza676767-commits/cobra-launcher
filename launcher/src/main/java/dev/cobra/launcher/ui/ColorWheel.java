package dev.cobra.launcher.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.function.IntConsumer;

/**
 * Colour wheel popup: pick any colour (hue round the wheel, saturation towards the edge), a
 * brightness bar under it and the hex code. Changes apply live; click outside or Esc to close.
 */
public final class ColorWheel extends JComponent {
    private static final int W = 232, H = 292, R = 92;
    private static ColorWheel openNow;

    private final IntConsumer onChange;
    private float hue, sat, val;
    private int drag;           // 0 none, 1 wheel, 2 brightness
    private BufferedImage wheel;
    private float wheelVal = -1;
    private AWTEventListener outside;

    private ColorWheel(int rgb, IntConsumer onChange) {
        this.onChange = onChange;
        float[] hsb = Color.RGBtoHSB(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, null);
        hue = hsb[0];
        sat = hsb[1];
        val = hsb[2];
        setOpaque(false);
        MouseAdapter m = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                drag = inWheel(e.getX(), e.getY()) ? 1 : inBar(e.getX(), e.getY()) ? 2 : 0;
                update(e.getX(), e.getY());
            }

            @Override public void mouseDragged(MouseEvent e) { update(e.getX(), e.getY()); }

            @Override public void mouseReleased(MouseEvent e) { drag = 0; }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    /** Opens the wheel next to {@code anchor}; {@code onChange} gets every new RGB (live). */
    public static void open(Component anchor, int rgb, IntConsumer onChange) {
        close();
        JLayeredPane lp = SwingUtilities.getRootPane(anchor).getLayeredPane();
        ColorWheel w = new ColorWheel(rgb, onChange);
        Point p = SwingUtilities.convertPoint(anchor, 0, anchor.getHeight() + 6, lp);
        int x = Math.max(8, Math.min(lp.getWidth() - W - 8, p.x));
        int y = p.y + H > lp.getHeight() - 8 ? SwingUtilities.convertPoint(anchor, 0, -H - 6, lp).y : p.y;
        w.setBounds(x, Math.max(8, y), W, H);
        lp.add(w, JLayeredPane.POPUP_LAYER);
        lp.repaint();
        openNow = w;
        // click anywhere else (or Esc) closes it
        w.outside = ev -> {
            if (ev instanceof MouseEvent me && me.getID() == MouseEvent.MOUSE_PRESSED) {
                Component c = me.getComponent();
                if (c != null && !SwingUtilities.isDescendingFrom(c, w)) SwingUtilities.invokeLater(ColorWheel::close);
            } else if (ev instanceof KeyEvent ke && ke.getID() == KeyEvent.KEY_PRESSED && ke.getKeyCode() == KeyEvent.VK_ESCAPE) {
                SwingUtilities.invokeLater(ColorWheel::close);
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(w.outside, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
    }

    public static void close() {
        ColorWheel w = openNow;
        openNow = null;
        if (w == null) return;
        Toolkit.getDefaultToolkit().removeAWTEventListener(w.outside);
        Container parent = w.getParent();
        if (parent != null) {
            parent.remove(w);
            parent.repaint();
        }
    }

    private int cx() { return W / 2; }

    private int cy() { return 18 + R; }

    private boolean inWheel(int x, int y) {
        double dx = x - cx(), dy = y - cy();
        return dx * dx + dy * dy <= (R + 6) * (R + 6);
    }

    private Rectangle bar() { return new Rectangle(18, cy() + R + 18, W - 36, 14); }

    private boolean inBar(int x, int y) {
        Rectangle b = bar();
        return y >= b.y - 6 && y <= b.y + b.height + 6 && x >= b.x - 6 && x <= b.x + b.width + 6;
    }

    private void update(int x, int y) {
        if (drag == 1) {
            double dx = x - cx(), dy = y - cy();
            hue = (float) ((Math.atan2(dy, dx) / (2 * Math.PI) + 1) % 1);
            sat = (float) Math.min(1, Math.hypot(dx, dy) / R);
        } else if (drag == 2) {
            Rectangle b = bar();
            val = (float) Math.max(0, Math.min(1, (x - b.x) / (double) b.width));
        } else return;
        onChange.accept(rgb());
        repaint();
    }

    private int rgb() { return Color.HSBtoRGB(hue, sat, val) & 0xFFFFFF; }

    private BufferedImage wheel() {
        if (wheel != null && wheelVal == val) return wheel;
        int d = R * 2 + 1;
        BufferedImage img = new BufferedImage(d, d, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < d; y++) {
            for (int x = 0; x < d; x++) {
                double dx = x - R, dy = y - R, r = Math.hypot(dx, dy);
                if (r > R + 0.5) continue;
                float h = (float) ((Math.atan2(dy, dx) / (2 * Math.PI) + 1) % 1);
                int c = Color.HSBtoRGB(h, (float) Math.min(1, r / R), val);
                int a = r > R - 0.5 ? (int) (255 * (R + 0.5 - r)) : 255;   // soft edge
                img.setRGB(x, y, a << 24 | (c & 0xFFFFFF));
            }
        }
        wheel = img;
        wheelVal = val;
        return img;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.aa(g0.create());
        Theme.surface(g, this, 0, 0, W, H, 20, 0, 0.8);
        g.drawImage(wheel(), cx() - R, cy() - R, null);
        // marker
        double ang = hue * 2 * Math.PI, rr = sat * R;
        double mx = cx() + Math.cos(ang) * rr, my = cy() + Math.sin(ang) * rr;
        g.setColor(new Color(rgb()));
        g.fill(new Ellipse2D.Double(mx - 7, my - 7, 14, 14));
        g.setStroke(new BasicStroke(2.5f));
        g.setColor(Color.WHITE);
        g.draw(new Ellipse2D.Double(mx - 7, my - 7, 14, 14));
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(0, 0, 0, 90));
        g.draw(new Ellipse2D.Double(mx - 8.5, my - 8.5, 17, 17));
        // brightness bar
        Rectangle b = bar();
        int full = Color.HSBtoRGB(hue, sat, 1f);
        Theme.fill(g, b.x, b.y, b.width, b.height, b.height / 2.0,
                new GradientPaint(b.x, 0, Color.BLACK, b.x + b.width, 0, new Color(full)));
        double kx = b.x + val * b.width;
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(kx - 8, b.y + b.height / 2.0 - 8, 16, 16));
        g.setColor(new Color(rgb()));
        g.fill(new Ellipse2D.Double(kx - 5, b.y + b.height / 2.0 - 5, 10, 10));
        // preview + hex
        int py = b.y + b.height + 16;
        Theme.fill(g, 18, py, 36, 26, 8, new Color(rgb()));
        Theme.stroke(g, 18, py, 36, 26, 8, Theme.LINE_2, 1f);
        Theme.left(g, String.format("#%06X", rgb()), Theme.font(Theme.MEDIUM, 15f), Theme.TEXT, 64, py, 26);
        g.dispose();
    }
}

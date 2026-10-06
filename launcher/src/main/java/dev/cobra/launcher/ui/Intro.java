package dev.cobra.launcher.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Start-up intro: the star fades and grows in on the backdrop colour, holds for a beat, then the
 * whole cover fades away while the logo drifts larger, revealing the launcher underneath.
 */
public final class Intro extends JComponent {
    private static final long IN = 520, HOLD = 260, OUT = 520;
    private final long start = System.currentTimeMillis();
    private final Timer timer;

    public Intro() {
        setOpaque(false);
        timer = new Timer(1000 / 60, e -> {
            if (System.currentTimeMillis() - start > IN + HOLD + OUT) finish();
            else repaint();
        });
        timer.start();
    }

    /** Swallows clicks while covering; fully transparent to them once it's fading out. */
    @Override
    public boolean contains(int x, int y) {
        return System.currentTimeMillis() - start < IN + HOLD;
    }

    private void finish() {
        timer.stop();
        Container p = getParent();
        if (p != null) {
            p.remove(this);
            p.repaint();
        }
    }

    private static double ease(double t) {
        t = Math.max(0, Math.min(1, t));
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        long t = System.currentTimeMillis() - start;
        double logoIn = ease(t / (double) IN);
        double out = ease((t - IN - HOLD) / (double) OUT);
        Graphics2D g = Theme.aa(g0.create());
        g.setColor(Theme.alpha(Theme.BLACK, 1 - out));
        g.fillRect(0, 0, getWidth(), getHeight());
        double size = 120 * (0.82 + 0.18 * logoIn) * (1 + 0.12 * out);
        double alpha = logoIn * (1 - out);
        Theme.logo(g, getWidth() / 2.0, getHeight() / 2.0 - 16, size, alpha, Theme.TEXT);
        // name fades in a touch after the logo
        double name = ease((t - 180) / (double) IN) * (1 - out);
        if (name > 0.01) {
            Graphics2D gt = (Graphics2D) g.create();
            gt.setComposite(Theme.fade(name));
            Font f = Theme.font(Theme.BOLD, 26f);
            String s = "C O B R A";
            gt.setFont(f);
            int w = gt.getFontMetrics().stringWidth(s);
            gt.setColor(Theme.TEXT);
            gt.drawString(s, getWidth() / 2 - w / 2, (int) (getHeight() / 2.0 + size / 2 + 20 - 8 * (1 - name)));
            gt.dispose();
        }
        g.dispose();
    }
}

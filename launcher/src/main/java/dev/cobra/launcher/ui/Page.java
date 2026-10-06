package dev.cobra.launcher.ui;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A sidebar destination. Pages lay out their children manually in {@link #doLayout()}.
 * Opening a page fades it in and lifts it a few pixels.
 */
public abstract class Page extends JPanel {
    private static final long STAGGER = 45, EACH = 320, MAX = 900;
    private long revealAt = -1;
    private final Timer revealTimer = new Timer(1000 / 60, e -> {
        if (System.currentTimeMillis() - revealAt > MAX) ((Timer) e.getSource()).stop();
        repaint();
    });

    protected Page() {
        super(null);
        setOpaque(false);
    }

    /** Called every time the page becomes visible. */
    public void onShow() {}

    public abstract String title();

    public abstract String icon();

    /** Starts the staggered entrance (called by the window when the page is shown). */
    public void reveal() {
        if (!Anim.enabled) return;
        revealAt = System.currentTimeMillis();
        revealTimer.restart();
    }

    private static double ease(double t) {
        t = Math.max(0, Math.min(1, t));
        return 1 - Math.pow(1 - t, 3);
    }

    @Override
    protected void paintChildren(Graphics g) {
        long t = revealAt < 0 ? Long.MAX_VALUE : System.currentTimeMillis() - revealAt;
        if (t > MAX) {
            super.paintChildren(g);
            return;
        }
        // Whole page rises and fades in as one piece. (Painting children one by one ourselves
        // bypassed Swing's double buffer and left ghost copies of pages on screen.)
        double p = ease(t / (double) EACH);
        if (p <= 0.001) return;
        Graphics2D cg = (Graphics2D) g.create();
        cg.translate(0, (int) Math.round((1 - p) * 12));
        if (p < 0.999) cg.setComposite(Theme.fade(p));
        super.paintChildren(cg);
        cg.dispose();
    }
}

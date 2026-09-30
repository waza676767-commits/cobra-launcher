package dev.cobra.launcher.ui;

import dev.cobra.launcher.core.AppLock;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.Arrays;

/**
 * Password screen, in two halves: on the left the Cobra mark on black with thin guide lines, on
 * the right a card with "Login", an underlined password field, "Remember me" and a round SIGN IN
 * button. Also used once to set the password (twice, to be sure).
 */
public final class LockScreen extends JComponent {
    private final boolean setup;
    private final Runnable done;
    private final JPasswordField pass = field(), again = field();
    private final Components.Interactive signIn;
    private boolean remember;
    /** Breathing: the mark slowly swells and glows (about one breath every 4 s). */
    private final Timer breathe = new Timer(33, e -> repaint(0, 0, card().x, getHeight()));
    private final long born = System.currentTimeMillis();

    @Override
    public void addNotify() {
        super.addNotify();
        if (Anim.enabled) breathe.start();
    }

    @Override
    public void removeNotify() {
        breathe.stop();
        super.removeNotify();
    }

    /** 0..1..0 over one breath (eased in and out). */
    private double breath() {
        double t = ((System.currentTimeMillis() - born) % 4200) / 4200.0;
        return 0.5 - 0.5 * Math.cos(t * Math.PI * 2);
    }
    private String error = "";
    private long shakeAt;

    private LockScreen(boolean setup, Runnable done) {
        this.setup = setup;
        this.done = done;
        setLayout(null);
        setOpaque(true);
        add(pass);
        if (setup) add(again);
        signIn = new Components.Interactive() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = Theme.aa(g0.create());
                double s = 1 + 0.05 * hover.get() - (pressed ? 0.04 : 0);
                double d = Math.min(getWidth(), getHeight()) * s;
                g.setColor(Color.WHITE);
                g.fill(new Ellipse2D.Double((getWidth() - d) / 2, (getHeight() - d) / 2, d, d));
                Theme.center(g, setup ? "SAVE" : "SIGN IN", Theme.tracked(Theme.BOLD, 11f, 0.08f), Color.BLACK, 0, 0, getWidth(), getHeight());
                g.dispose();
            }
        };
        signIn.onClick(this::submit);
        add(signIn);
        pass.addActionListener(e -> {
            if (setup) again.requestFocusInWindow();
            else submit();
        });
        again.addActionListener(e -> submit());
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Rectangle c = card();
                if (!setup && new Rectangle(c.x + 34, c.y + 318, 200, 22).contains(e.getPoint())) {
                    remember = !remember;
                    repaint();
                }
                if (new Rectangle(c.x + c.width - 150, c.y + 20, 130, 24).contains(e.getPoint())) {
                    if (setup) done.run();   // "Skip"
                    else MainWindow.get().toast("Forgot it? Delete " + AppLock.FILE + " and restart the launcher.");
                }
            }
        });
    }

    /** Shows the lock (if a password is set and not remembered), then runs {@code after}. */
    public static void showIfLocked(JRootPane root, Runnable after) {
        if (!AppLock.locked()) {
            after.run();
            return;
        }
        show(root, false, after);
    }

    /** First start / Settings: choose a password. */
    public static void showSetup(JRootPane root, Runnable after) {
        show(root, true, after);
    }

    private static void show(JRootPane root, boolean setup, Runnable after) {
        JLayeredPane lp = root.getLayeredPane();
        LockScreen[] ref = new LockScreen[1];
        java.awt.event.ComponentAdapter resize = new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                ref[0].setBounds(0, 0, lp.getWidth(), lp.getHeight());
            }
        };
        ref[0] = new LockScreen(setup, () -> {
            lp.removeComponentListener(resize);           // no leftover listener once it's closed
            lp.remove(ref[0]);
            lp.repaint();
            if (after != null) after.run();
        });
        ref[0].setBounds(0, 0, lp.getWidth(), lp.getHeight());
        lp.add(ref[0], Integer.valueOf(JLayeredPane.DRAG_LAYER + 10));
        lp.addComponentListener(resize);
        lp.revalidate();
        lp.repaint();
        SwingUtilities.invokeLater(ref[0].pass::requestFocusInWindow);
    }

    private static JPasswordField field() {
        JPasswordField f = new JPasswordField() {
            @Override
            protected void paintComponent(Graphics g0) {
                super.paintComponent(g0);
                Graphics2D g = Theme.aa(g0.create());
                g.setColor(hasFocus() ? new Color(255, 255, 255, 220) : new Color(255, 255, 255, 90));
                g.fillRect(0, getHeight() - 1, getWidth(), 1);     // just an underline
                g.dispose();
            }
        };
        f.setOpaque(false);
        f.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        f.setForeground(Color.WHITE);
        f.setCaretColor(Color.WHITE);
        f.setEchoChar('\u2022');
        f.setFont(Theme.font(Theme.REGULAR, 16f));
        return f;
    }

    private void submit() {
        char[] p = pass.getPassword();
        try {
            if (setup) {
                char[] a = again.getPassword();
                if (p.length < 4) fail("Use at least 4 characters.");
                else if (!Arrays.equals(p, a)) fail("The two passwords don't match.");
                else {
                    AppLock.set(p);
                    MainWindow.get().toast("Password set. You'll be asked for it when the launcher opens.");
                    done.run();
                }
                Arrays.fill(a, '\0');
            } else if (AppLock.check(p, remember)) {
                done.run();
            } else {
                fail("Wrong password.");
            }
        } catch (Exception e) {
            fail("Couldn't save it: " + e.getMessage());
        } finally {
            Arrays.fill(p, '\0');
        }
    }

    private void fail(String msg) {
        error = msg;
        shakeAt = System.currentTimeMillis();
        pass.setText("");
        again.setText("");
        pass.requestFocusInWindow();
        Timer t = new Timer(16, null);
        t.addActionListener(e -> {
            doLayout();
            repaint();
            if (System.currentTimeMillis() - shakeAt > 420) t.stop();
        });
        t.start();
    }

    private Rectangle card() {
        int w = getWidth(), h = getHeight(), pad = 18;
        int cw = Math.max(360, (int) (w * 0.44));
        long t = System.currentTimeMillis() - shakeAt;
        int shake = t < 400 ? (int) Math.round(Math.sin(t / 30.0) * 8 * (1 - t / 400.0)) : 0;
        return new Rectangle(w - cw - pad + shake, pad, cw, h - 2 * pad);
    }

    @Override
    public void doLayout() {
        Rectangle c = card();
        int fx = c.x + 34, fw = c.width - 68;
        pass.setBounds(fx, c.y + 250, fw, 34);
        again.setBounds(fx, c.y + 318, fw, 34);
        signIn.setBounds(c.x + c.width - 34 - 92, c.y + c.height - 34 - 92, 92, 92);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.aa(g0.create());
        int w = getWidth(), h = getHeight();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);
        Rectangle c = card();
        int lw = c.x - 18;                                    // the left half
        // thin guide lines: a cross through the mark, one diagonal, the half's edges
        double br = Anim.enabled ? breath() : 0.5;
        g.setColor(new Color(255, 255, 255, (int) (20 + 16 * br)));
        g.setStroke(new BasicStroke(1f));
        double cx = lw / 2.0 + 9, cy = h / 2.0;
        g.draw(new Line2D.Double(28, cy, lw, cy));
        g.draw(new Line2D.Double(cx, 22, cx, h - 22));
        g.draw(new Line2D.Double(28, h - 60, lw, 60));
        g.draw(new Line2D.Double(lw, 22, lw, h - 22));
        double size = Math.min(150, lw * 0.34);
        // the breath: a soft light blooming behind the mark, and the mark swelling a little
        float glowR = (float) (size * (0.9 + 0.35 * br));
        g.setPaint(new RadialGradientPaint(new java.awt.geom.Point2D.Double(cx, cy), glowR, new float[]{0f, 1f},
                new Color[]{new Color(255, 255, 255, (int) (18 + 30 * br)), new Color(255, 255, 255, 0)}));
        g.fill(new Ellipse2D.Double(cx - glowR, cy - glowR, glowR * 2, glowR * 2));
        Theme.logo(g, cx, cy, size * (0.96 + 0.07 * br), 0.85 + 0.15 * br, Color.WHITE);
        Theme.left(g, "Cobra Launcher", Theme.font(Theme.BOLD, 15f), Color.WHITE, 28, 22, 24);
        Theme.left(g, "\u00a9 Cobra " + java.time.Year.now() + ". All rights reserved.", Theme.font(Theme.REGULAR, 10.5f),
                new Color(255, 255, 255, 110), 28, h - 40, 18);
        // the card
        g.setPaint(new RadialGradientPaint(new java.awt.geom.Point2D.Double(c.x + c.width * 0.3, c.y + c.height * 0.35), (float) (c.width * 0.9),
                new float[]{0f, 1f}, new Color[]{new Color(0x262626), new Color(0x171717)}));
        g.fill(new java.awt.geom.RoundRectangle2D.Double(c.x, c.y, c.width, c.height, 28, 28));
        Theme.left(g, setup ? "Skip" : "Forgot?", Theme.font(Theme.REGULAR, 11.5f), new Color(255, 255, 255, 150),
                c.x + c.width - 34 - Theme.width(g, setup ? "Skip" : "Forgot?", Theme.font(Theme.REGULAR, 11.5f)), c.y + 20, 24);
        Theme.left(g, setup ? "Set a password" : "Login", Theme.font(Theme.REGULAR, setup ? 38f : 46f), Color.WHITE, c.x + 34, c.y + 150, 60);
        Theme.left(g, "Password", Theme.font(Theme.MEDIUM, 11f), new Color(255, 255, 255, 170), c.x + 34, c.y + 226, 18);
        if (setup) {
            Theme.left(g, "Password again", Theme.font(Theme.MEDIUM, 11f), new Color(255, 255, 255, 170), c.x + 34, c.y + 294, 18);
            Theme.left(g, "Asked when the launcher opens. You can remove it in Settings.", Theme.font(Theme.REGULAR, 11.5f),
                    new Color(255, 255, 255, 120), c.x + 34, c.y + 364, 20);
        } else {
            g.setColor(new Color(255, 255, 255, 180));
            g.setStroke(new BasicStroke(1.2f));
            g.draw(new Ellipse2D.Double(c.x + 34, c.y + 321, 14, 14));
            if (remember) g.fill(new Ellipse2D.Double(c.x + 37.5, c.y + 324.5, 7, 7));
            Theme.left(g, "Remember me for 7 days", Theme.font(Theme.REGULAR, 11.5f), new Color(255, 255, 255, 200), c.x + 56, c.y + 318, 22);
        }
        if (!error.isEmpty()) Theme.left(g, error, Theme.font(Theme.MEDIUM, 12f), new Color(0xFF6B6B), c.x + 34, c.y + (setup ? 390 : 350), 22);
        g.dispose();
    }
}

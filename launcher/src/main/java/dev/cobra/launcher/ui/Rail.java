package dev.cobra.launcher.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * The left rail: the Cobra mark in a circle at the top, a tall pill with one icon per page (the
 * current one sits in a bright circle that glides between them), and your face in a circle at the
 * bottom. Page names show as tooltips.
 */
public final class Rail extends JComponent {
    public static final int WIDTH = 60;
    private static final int CIRCLE = 60, ICON_BOX = 42, GAP = 6;

    private final List<Page> pages;
    private final IntConsumer onSelect;
    private final Anim.Tween select = new Anim.Tween(this, 0).rate(12);
    private int hover = -1;          // -1 none, -2 logo, -3 avatar

    public Rail(List<Page> pages, IntConsumer onSelect) {
        this.pages = pages;
        this.onSelect = onSelect;
        setOpaque(false);
        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { setHover(hit(e.getPoint())); }
            @Override public void mouseExited(MouseEvent e) { setHover(-1); }

            @Override
            public void mouseClicked(MouseEvent e) {
                tip().hideTip();
                int h = hit(e.getPoint());
                if (h >= 0) onSelect.accept(h);
                else if (h == -2) onSelect.accept(0);
                else if (h == -3) {
                    MainWindow mw = MainWindow.get();
                    if (mw.account() == null) mw.openLogin(null);
                    else for (int i = 0; i < pages.size(); i++) if (pages.get(i).title().equals("Accessories")) onSelect.accept(i);
                }
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    public int currentWidth() { return WIDTH; }

    private Tip tipView;

    private Tip tip() {
        if (tipView == null) {
            tipView = new Tip();
            getRootPane().getLayeredPane().add(tipView, Integer.valueOf(JLayeredPane.POPUP_LAYER));
        }
        return tipView;
    }

    /** The hover label: a dark glassy pill with a little pointer, sliding in from the rail. */
    private static final class Tip extends JComponent {
        private final Anim.Tween shown = new Anim.Tween(this, 0).rate(18);
        private final Anim.Tween y = new Anim.Tween(this, 0).rate(20);
        private String text = "";
        private int anchorX;

        Tip() {
            setOpaque(false);
            setFocusable(false);
        }

        @Override
        public boolean contains(int x, int y) { return false; }   // never takes the mouse

        void showTip(String t, Rectangle anchor) {
            text = t;
            anchorX = anchor.x + 8;
            int h = 30;
            double ty = anchor.y + (anchor.height - h) / 2.0;
            if (shown.get() < 0.05) y.set(ty);                  // first appearance: no slide from far away
            else y.to(ty);
            FontMetrics fm = getFontMetrics(Theme.font(Theme.MEDIUM, 13f));
            int w = fm.stringWidth(t) + 30;
            setBounds(anchorX, 0, w + 12, getParent() == null ? 0 : getParent().getHeight());
            shown.to(1);
            repaint();
        }

        void hideTip() {
            shown.to(0);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            double a = shown.get();
            if (a < 0.01 || text.isEmpty()) return;
            Graphics2D g = Theme.aa(g0.create());
            int h = 30, w = getWidth() - 12;
            double yy = y.get();
            double slide = (1 - a) * -8;                         // slides out of the rail
            g.translate(6 + slide, yy);
            g.setComposite(AlphaComposite.SrcOver.derive((float) Math.min(1, a)));
            // soft shadow
            g.setColor(new Color(0, 0, 0, 60));
            g.fill(new java.awt.geom.RoundRectangle2D.Double(1, 3, w, h, h, h));
            // pill + pointer
            java.awt.geom.Path2D arrow = new java.awt.geom.Path2D.Double();
            arrow.moveTo(0.5, h / 2.0);
            arrow.lineTo(7, h / 2.0 - 5);
            arrow.lineTo(7, h / 2.0 + 5);
            arrow.closePath();
            Color fill = Theme.isLight() ? new Color(255, 255, 255, 245) : new Color(23, 23, 23, 240);
            g.setColor(fill);
            g.fill(arrow);
            g.fill(new java.awt.geom.RoundRectangle2D.Double(5, 0, w - 5, h, h, h));
            g.setColor(Theme.isLight() ? new Color(0, 0, 0, 26) : new Color(255, 255, 255, 40));
            g.setStroke(new BasicStroke(1f));
            g.draw(new java.awt.geom.RoundRectangle2D.Double(5.5, 0.5, w - 6, h - 1, h - 1, h - 1));
            Theme.left(g, text, Theme.font(Theme.MEDIUM, 13f), Theme.TEXT, 20, 0, h);
            g.dispose();
        }
    }

    public boolean expanded() { return false; }

    public void setSelected(int i) { select.to(i); }

    private void setHover(int h) {
        if (h == hover) return;
        hover = h;
        // a small label slides out next to the icon (instead of a plain system tooltip)
        if (h == -1) tip().hideTip();
        else {
            Rectangle r = h >= 0 ? item(h) : h == -2 ? new Rectangle(0, 0, CIRCLE, CIRCLE) : new Rectangle(0, getHeight() - CIRCLE, CIRCLE, CIRCLE);
            MainWindow mw = MainWindow.get();
            String text = h >= 0 ? pages.get(h).title() : h == -2 ? "Home" : mw != null && mw.account() == null ? "Sign in" : "Account";
            tip().showTip(text, SwingUtilities.convertRectangle(this, new Rectangle(WIDTH, r.y, 0, r.height), getRootPane().getLayeredPane()));
        }
        setCursor(Cursor.getPredefinedCursor(h == -1 ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
        repaint();
    }

    private Rectangle pill() {
        return new Rectangle(0, CIRCLE + 10, WIDTH, getHeight() - 2 * (CIRCLE + 10));
    }

    /** Icons are centred in the pill (spread out a little when there's room). */
    private Rectangle item(int i) {
        Rectangle p = pill();
        int n = pages.size();
        int step = Math.min(ICON_BOX + 16, Math.max(ICON_BOX + GAP, (p.height - 24) / Math.max(1, n)));
        int top = p.y + (p.height - step * n) / 2 + (step - ICON_BOX) / 2;
        return new Rectangle((WIDTH - ICON_BOX) / 2, top + i * step, ICON_BOX, ICON_BOX);
    }

    private int hit(Point p) {
        if (p.y < CIRCLE) return -2;
        if (p.y > getHeight() - CIRCLE) return -3;
        for (int i = 0; i < pages.size(); i++) if (item(i).contains(p)) return i;
        return -1;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.aa(g0.create());
        int h = getHeight();
        // logo circle
        Theme.surface(g, this, 0, 0, CIRCLE, CIRCLE, CIRCLE / 2.0, hover == -2 ? 1 : 0, 0.6);
        Theme.logo(g, CIRCLE / 2.0, CIRCLE / 2.0, 24, 1);
        // the pill with the page icons
        Rectangle p = pill();
        Theme.surface(g, this, p.x, p.y, p.width, p.height, WIDTH / 2.0, 0, 0.6);
        double sel = select.get();
        int i0 = (int) Math.floor(Math.max(0, Math.min(pages.size() - 1, sel)));
        int i1 = Math.min(pages.size() - 1, i0 + 1);
        Rectangle a = item(i0), b = item(i1);
        double f = sel - i0, sy = a.y + (b.y - a.y) * f;
        Glass.glow(g, a.x, sy, ICON_BOX, ICON_BOX, ICON_BOX / 2.0, Theme.ACCENT, 4);
        g.setColor(Theme.ACCENT);
        g.fill(new Ellipse2D.Double(a.x, sy, ICON_BOX, ICON_BOX));
        for (int i = 0; i < pages.size(); i++) {
            Rectangle r = item(i);
            double on = Math.max(0, 1 - Math.abs(sel - i));
            if (i == hover && on < 0.5) {
                g.setColor(Theme.alpha(Theme.TEXT, 0.08));
                g.fill(new Ellipse2D.Double(r.x, r.y, r.width, r.height));
            }
            Color c = Theme.mix(i == hover ? Theme.TEXT : Theme.MUTED, Theme.ON_ACCENT, on);
            Icons.paint(g, pages.get(i).icon(), r.x + (r.width - 19) / 2.0, r.y + (r.height - 19) / 2.0, 19, c);
        }
        // your face at the bottom
        int ay = h - CIRCLE;
        Theme.surface(g, this, 0, ay, CIRCLE, CIRCLE, CIRCLE / 2.0, hover == -3 ? 1 : 0, 0.6);
        MainWindow mw = MainWindow.get();
        java.awt.image.BufferedImage face = mw == null ? null : mw.face();
        if (face != null) Theme.roundedImage(g, face, 12, ay + 12, CIRCLE - 24, CIRCLE - 24, CIRCLE - 24, false);
        else Icons.paint(g, "user", (CIRCLE - 22) / 2.0, ay + (CIRCLE - 22) / 2.0, 22, Theme.SOFT);
        g.dispose();
    }
}

package dev.cobra.launcher.ui;

import dev.cobra.launcher.core.Profiles;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Floating icon rail: expand toggle, page icons, then your profiles (picture tiles) and a "+"
 * tile. Click a profile to switch to it, click the active one (or right-click any) to edit it.
 */
public final class Sidebar extends JComponent {
    public static final int COLLAPSED = 72, EXPANDED = 226;
    private static final int ITEM = 46, GAP = 6, TOP = 70;   // 7 pages + profiles fit a 700 px window

    private final List<Page> pages;
    private final IntConsumer onSelect;
    private final Runnable onResize;
    private final Anim.Tween width;
    private final Anim.Tween select;
    private final List<Anim.Tween> hovers = new ArrayList<>();
    private final Map<String, Anim.Tween> profileHover = new HashMap<>();
    private final Anim.Tween toggleHover, addHover;
    private int selected;
    private int profileScroll;
    /** The old profile list at the bottom of the sidebar (replaced by the Profiles page). */
    private static final boolean SHOW_PROFILES = false;
    private Timer resizeTimer;

    public Sidebar(List<Page> pages, boolean expanded, IntConsumer onSelect, Runnable onResize) {
        this.pages = pages;
        this.onSelect = onSelect;
        this.onResize = onResize;
        this.width = new Anim.Tween(this, expanded ? 1 : 0).rate(13);
        this.select = new Anim.Tween(this, 0).rate(11);
        this.toggleHover = new Anim.Tween(this, 0).rate(18);
        this.addHover = new Anim.Tween(this, 0).rate(18);
        for (int i = 0; i < pages.size(); i++) hovers.add(new Anim.Tween(this, 0).rate(18));
        setOpaque(false);

        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { updateHover(e.getPoint()); }
            @Override public void mouseExited(MouseEvent e) { updateHover(new Point(-1, -1)); }

            @Override
            public void mouseClicked(MouseEvent e) {
                Point p = e.getPoint();
                if (toggleRect().contains(p)) {
                    setExpanded(!expanded());
                    if (MainWindow.get() != null) MainWindow.get().saveSidebar();
                    return;
                }
                for (int i = 0; i < pages.size(); i++) {
                    if (itemRect(i).contains(p)) {
                        Sidebar.this.onSelect.accept(i);
                        return;
                    }
                }
                if (!SHOW_PROFILES) return;
                List<Profiles.Profile> list = Profiles.all();
                for (int i = 0; i < list.size(); i++) {
                    if (!profileRect(i).contains(p) || !inProfileArea(p)) continue;
                    Profiles.Profile pr = list.get(i);
                    boolean active = pr.id.equals(Profiles.current().id);
                    if (SwingUtilities.isRightMouseButton(e) || active) MainWindow.get().editProfile(pr);
                    else MainWindow.get().selectProfile(pr);
                    return;
                }
                if (addRect().contains(p) && inProfileArea(p)) MainWindow.get().editProfile(null);
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                int max = Math.max(0, contentHeight() - (getHeight() - profilesTop() - 14));
                profileScroll = Math.max(0, Math.min(max, profileScroll + e.getWheelRotation() * 28));
                repaint();
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        addMouseWheelListener(m);
    }

    public boolean expanded() { return width.target() > 0.5; }

    public void setExpanded(boolean e) {
        width.to(e ? 1 : 0);
        if (resizeTimer != null) resizeTimer.stop();
        resizeTimer = new Timer(15, null);
        resizeTimer.addActionListener(ev -> {
            onResize.run();
            if (Math.abs(width.get() - width.target()) < 0.001) resizeTimer.stop();
        });
        resizeTimer.start();
        onResize.run();
    }

    public int currentWidth() {
        return (int) Math.round(COLLAPSED + (EXPANDED - COLLAPSED) * width.get());
    }

    public void setSelected(int i) {
        selected = i;
        select.to(i);
    }

    private Rectangle toggleRect() {
        int w = currentWidth();
        return new Rectangle(w - (COLLAPSED + ITEM) / 2, 14, ITEM, ITEM);
    }

    private Rectangle itemRect(int i) {
        int w = currentWidth();
        int x = (COLLAPSED - ITEM) / 2;
        return new Rectangle(x, TOP + i * (ITEM + GAP), w - 2 * x, ITEM);
    }

    private int profilesTop() {
        return TOP + pages.size() * (ITEM + GAP) + 34;
    }

    private Rectangle profileRect(int i) {
        int w = currentWidth();
        int x = (COLLAPSED - ITEM) / 2;
        return new Rectangle(x, profilesTop() + i * (ITEM + GAP) - profileScroll, w - 2 * x, ITEM);
    }

    private Rectangle addRect() {
        return profileRect(Profiles.all().size());
    }

    private int contentHeight() {
        return (Profiles.all().size() + 1) * (ITEM + GAP);
    }

    private boolean inProfileArea(Point p) {
        return p.y >= profilesTop() - 4 && p.y <= getHeight() - 8;
    }

    private Anim.Tween hoverFor(String id) {
        return profileHover.computeIfAbsent(id, k -> new Anim.Tween(this, 0).rate(18));
    }

    private void updateHover(Point p) {
        for (int i = 0; i < pages.size(); i++) hovers.get(i).to(itemRect(i).contains(p) ? 1 : 0);
        toggleHover.to(toggleRect().contains(p) ? 1 : 0);
        boolean area = inProfileArea(p);
        List<Profiles.Profile> list = Profiles.all();
        String tip = null;
        boolean hand = toggleRect().contains(p);
        for (int i = 0; i < pages.size(); i++) hand |= itemRect(i).contains(p);
        for (int i = 0; i < list.size(); i++) {
            boolean in = area && profileRect(i).contains(p);
            hoverFor(list.get(i).id).to(in ? 1 : 0);
            if (in) {
                hand = true;
                boolean active = list.get(i).id.equals(Profiles.current().id);
                tip = list.get(i).name + (active ? "  (click to edit)" : "");
            }
        }
        boolean add = area && addRect().contains(p);
        addHover.to(add ? 1 : 0);
        if (add) {
            hand = true;
            tip = "New profile";
        }
        setToolTipText(expanded() ? null : tip);
        setCursor(Cursor.getPredefinedCursor(hand ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.aa(g0.create());
        int w = currentWidth(), h = getHeight();
        profileScroll = Math.max(0, Math.min(profileScroll, Math.max(0, contentHeight() - (h - profilesTop() - 14))));
        double t = width.get();

        if (Glass.on()) {
            Glass.surface(g, this, 0, 0, w, h, 26, 0);   // same blur as the page sheet
        } else {
            Theme.fill(g, 0, 0, w, h, 22, new GradientPaint(0, 0, Theme.alpha(Theme.PANEL, 0.78), 0, h, Theme.alpha(Theme.PANEL_2, 0.5)));
            Theme.stroke(g, 0, 0, w, h, 22, Theme.LINE, 1f);
        }

        // expand / collapse
        Rectangle tr = toggleRect();
        Theme.fill(g, tr.x, tr.y, tr.width, tr.height, 14, Theme.alpha(Theme.TEXT, 0.05 * toggleHover.get()));
        Icons.paint(g, t > 0.5 ? "chevron-left" : "chevron-right", tr.x + 13, tr.y + 13, 20, Theme.mix(Theme.SOFT, Theme.TEXT, toggleHover.get()));
        if (t > 0.05) {
            Graphics2D gl = (Graphics2D) g.create();
            gl.setComposite(AlphaComposite.SrcOver.derive((float) Math.min(1, t * 1.4)));
            Theme.logo(gl, 34, tr.y + tr.height / 2.0, 26, 1);
            Theme.left(gl, "Cobra", Theme.font(Theme.BOLD, 16f), Theme.TEXT, 54, tr.y, tr.height);
            gl.dispose();
        }
        g.setColor(Theme.alpha(Theme.TEXT, 0.08));
        g.fillRect(14, TOP - 12, w - 28, 1);

        // pages: hover fills first, then one selection pill that glides between items
        for (int i = 0; i < pages.size(); i++) {
            double active = Math.max(0, 1 - Math.abs(select.get() - i));
            tile(g, itemRect(i), active, hovers.get(i).get());
        }
        pill(g);
        for (int i = 0; i < pages.size(); i++) {
            Rectangle r = itemRect(i);
            double active = Math.max(0, 1 - Math.abs(select.get() - i));
            Color ic = Theme.mix(Theme.mix(Theme.SOFT, Theme.TEXT, hovers.get(i).get()), (Glass.on() || Theme.isLight()) && active > 0.5 ? Theme.ON_ACCENT : Theme.TEXT, active);
            Icons.paint(g, pages.get(i).icon(), r.x + (ITEM - 21) / 2.0, r.y + (ITEM - 21) / 2.0, 21, ic);
            label(g, r, pages.get(i).title(), ic, t);
        }

        // profiles now have their own page in the list above
        if (!SHOW_PROFILES) {
            g.dispose();
            return;
        }
        int pt = profilesTop();
        g.setColor(Theme.alpha(Theme.TEXT, 0.08));
        g.fillRect(14, pt - 30, w - 28, 1);
        if (t > 0.3) {
            Graphics2D gl = (Graphics2D) g.create();
            gl.setComposite(AlphaComposite.SrcOver.derive((float) Math.min(1, (t - 0.3) * 1.6)));
            Theme.left(gl, "PROFILES", Theme.tracked(Theme.MEDIUM, 10.5f, 0.12f), Theme.MUTED, 18, pt - 26, 16);
            gl.dispose();
        }
        Graphics2D gp = (Graphics2D) g.create();
        gp.clipRect(0, pt - 4, w, h - pt - 6 + 4);
        List<Profiles.Profile> list = Profiles.all();
        String current = Profiles.current().id;
        for (int i = 0; i < list.size(); i++) {
            Profiles.Profile p = list.get(i);
            Rectangle r = profileRect(i);
            boolean active = p.id.equals(current);
            double hv = hoverFor(p.id).get();
            if (t > 0.05) Theme.fill(gp, r.x, r.y, r.width, r.height, 15, Theme.alpha(Theme.TEXT, 0.04 * hv + (active ? 0.05 : 0)));
            int size = ITEM - 8;
            int ix = r.x + 4, iy = r.y + 4;
            if (active) {
                gp.setColor(Theme.TEXT);
                gp.setStroke(new BasicStroke(2f));
                gp.draw(new java.awt.geom.RoundRectangle2D.Double(ix - 3, iy - 3, size + 6, size + 6, (size * 0.3 + 3) * 2, (size * 0.3 + 3) * 2));
            }
            ProfileArt.draw(gp, Profiles.icon(p), p.name, ix, iy, size, false);
            if (t > 0.05) {
                Graphics2D gl = (Graphics2D) gp.create();
                gl.clipRect(r.x, r.y, r.width - 8, r.height);
                gl.setComposite(AlphaComposite.SrcOver.derive((float) Math.min(1, (t - 0.05) * 1.5)));
                g.setFont(Theme.font(Theme.MEDIUM, 14f));
                String nm = Theme.ellipsize(p.name, gl.getFontMetrics(Theme.font(Theme.MEDIUM, 14f)), r.width - ITEM - 20);
                Theme.left(gl, nm, Theme.font(Theme.MEDIUM, 14f), active ? Theme.TEXT : Theme.mix(Theme.SOFT, Theme.TEXT, hv), r.x + ITEM + 4, r.y + 3, 22);
                Theme.left(gl, p.version + (active ? "  ·  edit" : ""), Theme.font(Theme.REGULAR, 11.5f), Theme.MUTED, r.x + ITEM + 4, r.y + 22, 18);
                gl.dispose();
            }
        }
        Rectangle ar = addRect();
        double ah = addHover.get();
        Graphics2D ga = (Graphics2D) gp.create();
        ga.setColor(Theme.alpha(Theme.TEXT, 0.25 + 0.35 * ah));
        ga.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{4f, 4f}, 0f));
        int size = ITEM - 8;
        ga.draw(new java.awt.geom.RoundRectangle2D.Double(ar.x + 4.5, ar.y + 4.5, size - 1, size - 1, size * 0.6, size * 0.6));
        ga.dispose();
        Icons.paint(gp, "plus", ar.x + 4 + (size - 18) / 2.0, ar.y + 4 + (size - 18) / 2.0, 18, Theme.mix(Theme.SOFT, Theme.TEXT, ah));
        if (t > 0.05) label(gp, ar, "New profile", Theme.mix(Theme.SOFT, Theme.TEXT, ah), t);
        gp.dispose();
        g.dispose();
    }

    /** The selected item's pill, placed between items while the selection animates. */
    private void pill(Graphics2D g) {
        double sel = Math.max(0, Math.min(pages.size() - 1, select.get()));
        int i0 = (int) Math.floor(sel), i1 = Math.min(pages.size() - 1, i0 + 1);
        double f = sel - i0;
        Rectangle a = itemRect(i0), b = itemRect(i1);
        double y = a.y + (b.y - a.y) * f, x = a.x, w = a.width, h = a.height;
        // a little stretch while it travels, like a drop of liquid
        double stretch = Math.sin(Math.PI * f) * 6;
        y -= stretch / 2;
        h += stretch;
        Graphics2D gb = (Graphics2D) g.create();
        if (Glass.on() || Theme.isLight()) {
            Glass.glow(gb, x, y, w, h, 15, Theme.ACCENT, 6);
            Theme.fill(gb, x, y, w, h, 15, Theme.ACCENT);
            Theme.fill(gb, x + 1, y + 1, w - 2, h / 2.0, 14, new GradientPaint(0, (float) y, new Color(255, 255, 255, 60), 0, (float) (y + h / 2), new Color(255, 255, 255, 0)));
        } else {
            Theme.fill(gb, x, y, w, h, 15, new GradientPaint(0, (float) y, Theme.mix(Theme.PANEL, Theme.TEXT, 0.15), 0, (float) (y + h), Theme.BLACK));
            Theme.stroke(gb, x, y, w, h, 15, Theme.alpha(Theme.TEXT, 0.55), 1f);
            gb.setColor(Theme.alpha(Theme.TEXT, 0.10));
            gb.fillRoundRect((int) x + 10, (int) y + 1, (int) w - 20, 1, 1, 1);
        }
        gb.dispose();
    }

    /** Idle / hover fill of an item (the selection itself is {@link #pill}). */
    private void tile(Graphics2D g, Rectangle r, double active, double hov) {
        if (active >= 0.99) return;
        double a = Glass.on() ? 0.07 * hov : 0.015 + 0.05 * hov;   // glass: items float free, only hover shows
        if (a > 0.002) Theme.fill(g, r.x, r.y, r.width, r.height, 15, Theme.alpha(Theme.TEXT, a * (1 - active)));
        if (!Glass.on()) Theme.stroke(g, r.x, r.y, r.width, r.height, 15, Theme.alpha(Theme.mix(Theme.LINE, Theme.MID, hov), 1 - active), 1f);
    }

    private void label(Graphics2D g, Rectangle r, String text, Color c, double t) {
        if (t <= 0.05) return;
        Graphics2D gl = (Graphics2D) g.create();
        gl.clipRect(r.x, r.y, r.width - 8, r.height);
        gl.setComposite(AlphaComposite.SrcOver.derive((float) Math.min(1, (t - 0.05) * 1.5)));
        Theme.left(gl, text, Theme.font(Theme.MEDIUM, 14.5f), c, r.x + ITEM + 4, r.y, r.height);
        gl.dispose();
    }

    @SuppressWarnings("unused")
    private static void dot(Graphics2D g, double cx, double cy, double r) {
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
    }
}

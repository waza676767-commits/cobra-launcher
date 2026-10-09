package dev.life.launcher.ui;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.JComponent;

/**
 * The sidebar: the LIFE wordmark on top, then the pages in three groups (Play, Library, App)
 * with a hairline between them, each a row with an icon and its name. The current page sits in a
 * soft rounded highlight that glides between rows. The menu button folds it to icons only. Your
 * account at the bottom.
 */
public final class NavSidebar extends JComponent {
    public static final int EXPANDED = 224, COLLAPSED = 76;
    private static final int ROW = 42, TOP = 76;
    /** Which page titles go in which group (others go last). */
    private static final String[][] GROUPS = {{"Home", "Mods", "Texture packs", "Profiles"}, {"Accessories", "Recordings", "Analytics"}, {"Settings", "Logs"}};

    private final List<Page> pages;
    private final IntConsumer onSelect;
    private final Anim.Tween select = new Anim.Tween(this, 0).rate(18);
    private final Anim.Tween width;
    private int hover = -1, selected;
    private boolean expanded;
    private final Runnable relayout;

    public NavSidebar(List<Page> pages, IntConsumer onSelect, boolean expanded, Runnable relayout) {
        this.pages = pages;
        this.onSelect = onSelect;
        this.expanded = expanded;
        this.relayout = relayout;
        width = new Anim.Tween(this, expanded ? EXPANDED : COLLAPSED).rate(16);
        setOpaque(false);
        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int h = hit(e.getPoint());
                if (h != hover) {
                    hover = h;
                    setCursor(Cursor.getPredefinedCursor(h != -1 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                    setToolTipText(!NavSidebar.this.expanded && h >= 0 ? pages.get(h).title() : null);
                    repaint();
                }
            }

            @Override public void mouseExited(MouseEvent e) {
                hover = -1;
                repaint();
            }

            @Override public void mouseClicked(MouseEvent e) {
                int h = hit(e.getPoint());
                if (h == -2) toggle();
                else if (h == -3) {
                    MainWindow mw = MainWindow.get();
                    if (mw == null) return;
                    if (mw.account() == null) mw.openLogin(null);
                    else for (int i = 0; i < pages.size(); i++) if (pages.get(i).title().equals("Accessories")) onSelect.accept(i);
                } else if (h >= 0) onSelect.accept(h);
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    public boolean expanded() { return expanded; }

    public int currentWidth() { return (int) Math.round(width.get()); }

    public void setSelected(int i) {
        selected = i;
        select.to(rowIndex(i));
    }

    private void toggle() {
        expanded = !expanded;
        width.to(expanded ? EXPANDED : COLLAPSED);
        javax.swing.Timer t = new javax.swing.Timer(16, null);
        t.addActionListener(e -> {
            if (relayout != null) relayout.run();
            if (Math.abs(width.get() - (expanded ? EXPANDED : COLLAPSED)) < 0.5) t.stop();
        });
        t.start();
        dev.life.launcher.core.Settings s = dev.life.launcher.core.Settings.get();
        s.sidebarExpanded = expanded;
        s.save();
    }

    /** Pages in display order (grouped). */
    private int[] order() {
        int[] out = new int[pages.size()];
        boolean[] used = new boolean[pages.size()];
        int n = 0;
        for (String[] grp : GROUPS) for (String t : grp) for (int i = 0; i < pages.size(); i++) {
            if (!used[i] && pages.get(i).title().equals(t)) {
                out[n++] = i;
                used[i] = true;
            }
        }
        for (int i = 0; i < pages.size(); i++) if (!used[i]) out[n++] = i;
        return out;
    }

    /** The row position (0, 1, 2, …) of a page. */
    private int rowIndex(int page) {
        int[] o = order();
        for (int k = 0; k < o.length; k++) if (o[k] == page) return k;
        return 0;
    }

    /** y of row k, with a gap for the dividers between groups. */
    private int rowY(int k) {
        int y = TOP + k * ROW, seen = 0;
        for (int g = 0; g < GROUPS.length - 1; g++) {
            seen += GROUPS[g].length;
            if (k >= seen) y += 17;
        }
        if (k >= pages.size() - GROUPS[GROUPS.length - 1].length) {    // the last group sits at the bottom
            int fromEnd = pages.size() - k;
            y = getHeight() - 70 - fromEnd * ROW;
        }
        return y;
    }

    /** -2 = the menu button, -3 = your account, page index, or -1. */
    private int hit(Point p) {
        if (p.y >= 18 && p.y < 58 && p.x < 58) return -2;
        if (p.y >= getHeight() - 62) return -3;
        int[] o = order();
        for (int k = 0; k < o.length; k++) {
            int y = rowY(k);
            if (p.y >= y && p.y < y + ROW - 4 && p.x >= 10 && p.x < getWidth() - 10) return o[k];
        }
        return -1;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.aa(g0.create());
        int w = getWidth(), h = getHeight();
        double open = Math.max(0, Math.min(1, (width.get() - COLLAPSED) / (EXPANDED - COLLAPSED)));
        // the panel itself: a darker sheet of frosted glass
        Theme.fill(g, 0, 0, w, h, 22, Theme.alpha(Theme.PANEL_2, 0.80));
        Theme.stroke(g, 0, 0, w, h, 22, new Color(255, 255, 255, 14), 1f);
        // menu button + wordmark
        int mh = hover == -2 ? 1 : 0;
        if (mh == 1) Theme.fill(g, 14, 18, 40, 40, 12, new Color(255, 255, 255, 18));
        for (int i = 0; i < 3; i++) Theme.fill(g, 24, 29 + i * 7, 20, 2.2, 1.1, Theme.TEXT);
        if (open > 0.05) {
            Graphics2D wg = (Graphics2D) g.create();
            wg.setComposite(Theme.fade(open));
            String custom = dev.life.launcher.core.Settings.get().launcherName;
            if (custom == null || custom.isBlank()) Theme.wordmark(wg, 66, 22, 120);
            else {                                                   // your own name instead of the LIFE logo
                wg.setFont(Theme.font(Theme.BOLD, 19f));
                Theme.left(wg, Theme.ellipsize(custom.trim(), wg.getFontMetrics(), w - 80), Theme.font(Theme.BOLD, 19f), Theme.TEXT, 66, 18, 40);
            }
            wg.dispose();
        }
        int[] o = order();
        // dividers between groups
        g.setColor(new Color(255, 255, 255, 22));
        int seen = 0;
        for (int gi = 0; gi < GROUPS.length - 1; gi++) {
            seen += GROUPS[gi].length;
            if (seen < o.length) {
                int y = gi == GROUPS.length - 2 ? rowY(seen) - 10 : rowY(seen) - 10;
                g.fillRect(18, y, w - 36, 1);
            }
        }
        // the highlight glides to the current row
        double sk = select.get();
        int k0 = (int) Math.floor(sk), k1 = Math.min(o.length - 1, k0 + 1);
        double sy = rowY(k0) + (rowY(k1) - rowY(k0)) * (sk - k0);
        Theme.fill(g, 10, sy, w - 20, ROW - 4, 12, new Color(255, 255, 255, 26));
        for (int k = 0; k < o.length; k++) {
            int i = o[k], y = rowY(k);
            boolean sel = i == selected, hov = i == hover;
            if (hov && !sel) Theme.fill(g, 10, y, w - 20, ROW - 4, 12, new Color(255, 255, 255, 12));
            Color ink = sel ? Theme.TEXT : Theme.alpha(Theme.TEXT, hov ? 0.92 : 0.72);
            Icons.paint(g, pages.get(i).icon(), 28, y + (ROW - 4 - 19) / 2.0, 19, ink);
            if (open > 0.05) {
                Graphics2D tg = (Graphics2D) g.create();
                tg.setComposite(Theme.fade(open));
                tg.clipRect(0, 0, w - 12, h);
                Theme.left(tg, pages.get(i).title(), Theme.font(sel ? Theme.BOLD : Theme.MEDIUM, 14f), ink, 62, y, ROW - 4);
                tg.dispose();
            }
        }
        // your account
        MainWindow mw = MainWindow.get();
        int ay = h - 58;
        if (hover == -3) Theme.fill(g, 10, ay - 2, w - 20, 48, 14, new Color(255, 255, 255, 14));
        java.awt.image.BufferedImage face = mw == null ? null : mw.face();
        g.setColor(new Color(255, 255, 255, 30));
        g.fill(new Ellipse2D.Double(20, ay + 4, 36, 36));
        if (face != null) Theme.roundedImage(g, face, 24, ay + 8, 28, 28, 8, false);
        else Icons.paint(g, "user", 29, ay + 13, 18, Theme.TEXT);
        if (open > 0.05) {
            Graphics2D tg = (Graphics2D) g.create();
            tg.setComposite(Theme.fade(open));
            tg.clipRect(0, 0, w - 12, h);
            String name = mw != null && mw.account() != null ? mw.account().name : "Sign in";
            Theme.left(tg, name, Theme.font(Theme.BOLD, 13.5f), Theme.TEXT, 66, ay + 4, 20);
            Theme.left(tg, mw != null && mw.account() != null ? "Microsoft account" : "to play online", Theme.font(Theme.REGULAR, 11.5f), Theme.MUTED, 66, ay + 22, 18);
            tg.dispose();
        }
        g.dispose();
    }
}

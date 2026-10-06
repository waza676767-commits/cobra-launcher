package dev.cobra.launcher.ui;

import dev.cobra.launcher.game.GameVersion;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * The version picker: a search box, then two sections. "With Abyss Client" (the versions Cobra
 * Client is built for) and "Without Abyss Client" (every other Minecraft release, on Fabric with
 * your mods, or vanilla where Fabric doesn't exist). Scrolls; opens above the anchor when there's
 * no room below.
 */
public final class VersionMenu {
    private static final int ROW = 46, HEAD = 34, GAP = 2;

    private VersionMenu() {}

    /** Picks the version for the current profile (Home, Mods, Texture packs). */
    public static void open(JComponent anchor, int width, Runnable after) {
        MainWindow mw = MainWindow.get();
        open(anchor, width, mw.version(), v -> {
            mw.setVersion(v);
            if (after != null) after.run();
        });
    }

    /** Picks a version and hands it to {@code pick} (the profile editor uses this). */
    public static void open(JComponent anchor, int width, GameVersion current, Consumer<GameVersion> pick) {
        MainWindow mw = MainWindow.get();
        JComponent menu = new Components.Card(null, 18).solid();
        menu.setLayout(null);
        Components.Input search = new Components.Input("", "Search versions, e.g. 1.20", "search");
        JPanel list = new JPanel(null);
        list.setOpaque(false);
        JScrollPane scroll = Components.scroll(list);
        Runnable fill = () -> {
            list.removeAll();
            String q = search.getText().trim();
            int y = 4, w = width - 24;
            boolean[] header = {false, false};
            for (GameVersion v : GameVersion.values()) {
                if (!q.isEmpty() && !v.id.contains(q)) continue;
                int sec = v.hasCobra() ? 0 : 1;
                if (!header[sec]) {
                    header[sec] = true;
                    Header h = new Header(sec == 0 ? "With Abyss Client" : "Without Abyss Client",
                            sec == 0 ? "Built in, every module ready" : "Your mods (Fabric) or vanilla, no Abyss modules");
                    h.setBounds(0, y, w, HEAD);
                    list.add(h);
                    y += HEAD;
                }
                Row row = new Row(v, v == current, () -> {
                    mw.closePopup();
                    pick.accept(v);
                });
                row.setBounds(0, y, w, ROW);
                list.add(row);
                y += ROW + GAP;
            }
            if (y == 4) {
                Header h = new Header("No version matches", "Try a shorter search");
                h.setBounds(0, y, w, HEAD);
                list.add(h);
                y += HEAD;
            }
            list.setPreferredSize(new Dimension(w, y + 4));
            list.revalidate();
            list.repaint();
        };
        fill.run();
        search.onChange(fill);
        menu.add(search);
        menu.add(scroll);
        int h = Math.min(460, mw.frame.getLayeredPane().getHeight() - 60);
        search.setBounds(10, 10, width - 20, 40);
        scroll.setBounds(8, 58, width - 12, h - 66);
        // the full list arrives in the background the first time
        if (GameVersion.count() < 3) GameVersion.refresh(() -> SwingUtilities.invokeLater(fill));
        else GameVersion.refresh(null);

        JLayeredPane lp = mw.frame.getLayeredPane();
        Point below = SwingUtilities.convertPoint(anchor, 0, anchor.getHeight() + 8, lp);
        Point above = SwingUtilities.convertPoint(anchor, 0, -8 - h, lp);
        Point p = below.y + h <= lp.getHeight() - 12 ? below : above;
        int x = Math.max(12, Math.min(p.x, lp.getWidth() - width - 12));
        mw.showPopup(menu, new Rectangle(x, Math.max(12, Math.min(p.y, lp.getHeight() - h - 12)), width, h));
        SwingUtilities.invokeLater(search::requestFocusInWindow);
    }

    private static final class Header extends JComponent {
        private final String title, sub;

        Header(String title, String sub) {
            this.title = title;
            this.sub = sub;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            Theme.left(g, title.toUpperCase(), Theme.tracked(Theme.BOLD, 10.5f, 0.1f), Theme.MUTED, 10, 4, 18);
            Theme.left(g, sub, Theme.font(Theme.REGULAR, 11f), Theme.alpha(Theme.MUTED, 0.75), 10, 18, 14);
            g.dispose();
        }
    }

    private static final class Row extends Components.Interactive {
        private final GameVersion v;
        private final boolean selected;

        Row(GameVersion v, boolean selected, Runnable pick) {
            this.v = v;
            this.selected = selected;
            onClick(pick);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            int w = getWidth(), h = getHeight();
            Theme.fill(g, 0, 0, w, h, 12, Theme.alpha(Theme.TEXT, 0.06 * hover.get() + (selected ? 0.07 : 0)));
            Theme.left(g, v.id, Theme.font(Theme.BOLD, 14f), Theme.TEXT, 12, 4, 20);
            Theme.left(g, v.subtitle(), Theme.font(Theme.REGULAR, 11.5f), Theme.SOFT, 12, 23, 18);
            if (v.hasCobra()) Theme.logo(g, w - (selected ? 60 : 30), h / 2.0, 15, 0.9);
            if (selected) Icons.paint(g, "check", w - 34, (h - 16) / 2.0, 16, Theme.TEXT);
            g.dispose();
        }
    }
}

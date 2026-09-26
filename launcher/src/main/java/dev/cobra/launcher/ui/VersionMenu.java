package dev.cobra.launcher.ui;

import dev.cobra.launcher.game.GameVersion;

import javax.swing.*;
import java.awt.*;

/** Version picker popup, shared by Home, Mods and Texture packs. Opens above the anchor when there's no room below. */
public final class VersionMenu {
    private static final int ROW = 54, GAP = 4;

    private VersionMenu() {}

    public static void open(JComponent anchor, int width, Runnable after) {
        MainWindow mw = MainWindow.get();
        JComponent menu = new Components.Card(null, 18).solid();
        GameVersion[] all = GameVersion.values();
        int y = 8;
        for (GameVersion v : all) {
            Row row = new Row(v, v == mw.version(), after);
            row.setBounds(8, y, width - 16, ROW);
            menu.add(row);
            y += ROW + GAP;
        }
        int h = y + 4;
        JLayeredPane lp = mw.frame.getLayeredPane();
        Point below = SwingUtilities.convertPoint(anchor, 0, anchor.getHeight() + 8, lp);
        Point above = SwingUtilities.convertPoint(anchor, 0, -8 - h, lp);
        Point p = below.y + h <= lp.getHeight() - 12 ? below : above;
        int x = Math.max(12, Math.min(p.x, lp.getWidth() - width - 12));
        mw.showPopup(menu, new Rectangle(x, Math.max(12, p.y), width, h));
    }

    private static final class Row extends Components.Interactive {
        private final GameVersion v;
        private final boolean selected;

        Row(GameVersion v, boolean selected, Runnable after) {
            this.v = v;
            this.selected = selected;
            onClick(() -> {
                MainWindow.get().closePopup();
                MainWindow.get().setVersion(v);
                if (after != null) after.run();
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            int w = getWidth(), h = getHeight();
            Theme.fill(g, 0, 0, w, h, 12, Theme.alpha(Theme.TEXT, 0.06 * hover.get() + (selected ? 0.05 : 0)));
            Theme.left(g, v.id, Theme.font(Theme.BOLD, 15f), Theme.TEXT, 16, 6, 22);
            Theme.left(g, v.subtitle(), Theme.font(Theme.REGULAR, 12.5f), Theme.SOFT, 16, 27, 20);
            if (v.hasCobra()) {
                Theme.logo(g, w - (selected ? 64 : 34), h / 2.0, 16, 0.9);
            }
            if (selected) Icons.paint(g, "check", w - 38, (h - 18) / 2.0, 18, Theme.TEXT);
            g.dispose();
        }
    }
}

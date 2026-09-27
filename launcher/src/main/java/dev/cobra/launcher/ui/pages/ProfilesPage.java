package dev.cobra.launcher.ui.pages;

import dev.cobra.launcher.core.Profiles;
import dev.cobra.launcher.game.GameVersion;
import dev.cobra.launcher.ui.*;

import javax.swing.*;
import java.awt.*;

/** Your profiles: play, edit or delete one, make a new one, or import one from another launcher. */
public final class ProfilesPage extends Page {
    private final Components.Stack list = new Components.Stack(10);
    private final JScrollPane scroll = Components.scroll(list);
    private final Components.Button create, importBtn;

    public ProfilesPage() {
        create = new Components.Button("New profile", "plus", Components.Variant.PRIMARY, () -> MainWindow.get().editProfile(null));
        importBtn = new Components.Button("Import", "download", Components.Variant.GHOST, SettingsPage::importProfileNow);
        add(create);
        add(importBtn);
        add(scroll);
    }

    @Override public String title() { return "Profiles"; }
    @Override public String icon() { return "star"; }

    @Override
    public void onShow() {
        list.removeAll();
        for (Profiles.Profile p : Profiles.all()) list.add(new Row(p));
        list.animateIn();
        list.revalidate();
        list.repaint();
    }

    @Override
    public void doLayout() {
        int w = getWidth(), h = getHeight();
        Dimension cd = create.getPreferredSize(), id = importBtn.getPreferredSize();
        create.setBounds(w - cd.width, 6, cd.width, 40);
        importBtn.setBounds(w - cd.width - 8 - id.width, 6, id.width, 40);
        scroll.setBounds(0, 80, w + 10, h - 80);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.aa(g0.create());
        Theme.left(g, title(), Theme.font(Theme.BOLD, 30f), Theme.TEXT, 0, 0, 42);
        Theme.left(g, "Each profile has its own mods, packs, worlds and settings. Pick the one to play.",
                Theme.font(Theme.REGULAR, 13.5f), Theme.SOFT, 0, 44, 22);
        g.dispose();
    }

    private final class Row extends JPanel {
        private final Profiles.Profile p;

        Row(Profiles.Profile p) {
            super(null);
            this.p = p;
            setOpaque(false);
            boolean current = p.id.equals(Profiles.current().id);
            Components.Button use = new Components.Button(current ? "Selected" : "Select", current ? "check" : "play",
                    current ? Components.Variant.GHOST : Components.Variant.PRIMARY, () -> {
                MainWindow.get().selectProfile(p);
                onShow();
            });
            use.setEnabled(!current);
            Components.Button edit = new Components.Button("Edit", "settings", Components.Variant.GHOST, () -> MainWindow.get().editProfile(p));
            Components.Button folder = new Components.Button("", "folder", Components.Variant.GHOST,
                    () -> MainWindow.openPath(Profiles.gameDir(p, GameVersion.MODERN)));
            add(use);
            add(edit);
            add(folder);
        }

        @Override public Dimension getPreferredSize() { return new Dimension(100, 72); }

        @Override
        public void doLayout() {
            int w = getWidth();
            getComponent(2).setBounds(w - 12 - 44, 16, 44, 40);
            getComponent(1).setBounds(w - 12 - 44 - 8 - 96, 16, 96, 40);
            getComponent(0).setBounds(w - 12 - 44 - 8 - 96 - 8 - 116, 16, 116, 40);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            int w = getWidth(), h = getHeight();
            boolean current = p.id.equals(Profiles.current().id);
            Theme.surface(g, this, 0, 0, w, h, 18, 0, current ? 0.4 : 0.1);
            if (current) Theme.stroke(g, 1, 1, w - 2, h - 2, 17, Theme.alpha(Theme.ACCENT, 0.8), 1.5f);
            ProfileArt.draw(g, Profiles.icon(p), p.name, 16, 14, 44, false);
            String sub = p.gameVersion().id + " · " + p.gameVersion().loaderName + (p.locked() ? " · built in, mods fixed" : "");
            g.setFont(Theme.font(Theme.MEDIUM, 15f));
            Theme.left(g, Theme.ellipsize(p.name, g.getFontMetrics(), w - 380), Theme.font(Theme.MEDIUM, 15f), Theme.TEXT, 74, 14, 24);
            Theme.left(g, sub, Theme.font(Theme.REGULAR, 12.5f), Theme.MUTED, 74, 38, 20);
            g.dispose();
        }
    }
}

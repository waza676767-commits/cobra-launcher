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
    private final Components.Button create, importBtn, addCategory;
    private final Components.Input categoryName = new Components.Input("", "New category, e.g. PvP", "plus");

    public ProfilesPage() {
        addCategory = new Components.Button("Add category", "plus", Components.Variant.GHOST, this::addCategory);
        categoryName.addActionListener(e -> addCategory());
        add(categoryName);
        add(addCategory);
        create = new Components.Button("New profile", "plus", Components.Variant.PRIMARY, () -> MainWindow.get().editProfile(null));
        importBtn = new Components.Button("Import", "download", Components.Variant.GHOST, SettingsPage::importProfileNow);
        add(create);
        add(importBtn);
        add(scroll);
    }

    @Override public String title() { return "Profiles"; }
    @Override public String icon() { return "sliders"; }

    private static java.util.List<String> categories() {
        java.util.List<String> cats = new java.util.ArrayList<>(dev.cobra.launcher.core.Settings.get().profileCategories);
        for (Profiles.Profile p : Profiles.all()) {
            if (p.category != null && !p.category.isBlank() && !cats.contains(p.category)) cats.add(p.category);
        }
        return cats;
    }

    /** Profiles grouped by category (your categories first, then the ones without a category). */
    @Override
    public void onShow() {
        list.removeAll();
        java.util.List<Profiles.Profile> all = Profiles.all();
        for (String cat : categories()) {
            java.util.List<Profiles.Profile> in = all.stream().filter(p -> cat.equals(p.category)).toList();
            list.add(new Header(cat, in.size(), true));
            for (Profiles.Profile p : in) list.add(new Row(p));
        }
        java.util.List<Profiles.Profile> rest = all.stream().filter(p -> p.category == null || p.category.isBlank()).toList();
        if (!categories().isEmpty() && !rest.isEmpty()) list.add(new Header("No category", rest.size(), false));
        for (Profiles.Profile p : rest) list.add(new Row(p));
        list.animateIn();
        list.revalidate();
        list.repaint();
    }

    private void addCategory() {
        String name = categoryName.getText().trim();
        if (name.isEmpty()) {
            MainWindow.get().toast("Type a name for the category first (e.g. PvP).");
            return;
        }
        var s = dev.cobra.launcher.core.Settings.get();
        if (!s.profileCategories.contains(name)) s.profileCategories.add(name);
        s.save();
        categoryName.setText("");
        onShow();
        MainWindow.get().toast("Category \"" + name + "\" added. Use the tag button on a profile to put it there.");
    }

    /** A category title with how many profiles it has; the × removes the category (its profiles stay). */
    private final class Header extends JPanel {
        private final String name;
        private final int count;

        Header(String name, int count, boolean removable) {
            super(null);
            this.name = name;
            this.count = count;
            setOpaque(false);
            if (removable) {
                Components.Button remove = new Components.Button("", "close", Components.Variant.GHOST, () -> {
                    var s = dev.cobra.launcher.core.Settings.get();
                    s.profileCategories.remove(name);
                    s.save();
                    for (Profiles.Profile p : Profiles.all()) if (name.equals(p.category)) p.category = null;
                    Profiles.save();
                    onShow();
                });
                remove.setToolTipText("Remove this category (its profiles stay)");
                add(remove);
            }
        }

        @Override public Dimension getPreferredSize() { return new Dimension(100, 40); }

        @Override
        public void doLayout() {
            if (getComponentCount() > 0) getComponent(0).setBounds(getWidth() - 12 - 32, 6, 32, 30);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            Theme.left(g, name.toUpperCase(), Theme.tracked(Theme.BOLD, 12.5f, 0.12f), Theme.SOFT, 4, 10, 26);
            int tw = Theme.width(g, name.toUpperCase(), Theme.tracked(Theme.BOLD, 12.5f, 0.12f));
            Theme.left(g, String.valueOf(count), Theme.font(Theme.REGULAR, 12f), Theme.MUTED, 4 + tw + 10, 10, 26);
            g.setColor(Theme.alpha(Theme.TEXT, 0.08));
            g.fillRect(4 + tw + 32, 23, Math.max(0, getWidth() - tw - 90), 1);
            g.dispose();
        }
    }

    @Override
    public void doLayout() {
        int w = getWidth(), h = getHeight();
        Dimension cd = create.getPreferredSize(), id = importBtn.getPreferredSize(), ad = addCategory.getPreferredSize();
        create.setBounds(w - cd.width, 6, cd.width, 40);
        importBtn.setBounds(w - cd.width - 8 - id.width, 6, id.width, 40);
        categoryName.setBounds(0, 80, Math.min(320, w - ad.width - 8), 40);
        addCategory.setBounds(categoryName.getWidth() + 8, 80, ad.width, 40);
        scroll.setBounds(0, 132, w + 10, h - 132);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.aa(g0.create());
        Theme.left(g, title(), Theme.font(Theme.REGULAR, 34f), Theme.TEXT, 0, 0, 42);
        Theme.left(g, "Each profile has its own mods, packs, worlds and settings. Group them in categories.",
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
            Components.Button tag = new Components.Button("", "list", Components.Variant.GHOST, () -> {
                java.util.List<String> cats = categories();
                if (cats.isEmpty()) {
                    MainWindow.get().toast("Make a category first: type a name above and press Add category.");
                    return;
                }
                java.util.List<String> options = new java.util.ArrayList<>(cats);
                options.add("No category");
                MainWindow.get().ask("Category for " + p.name, "Pick where this profile goes on the Profiles page.", options, -1, i -> {
                    p.category = i < cats.size() ? cats.get(i) : null;
                    Profiles.save();
                    onShow();
                });
            });
            tag.setToolTipText("Put this profile in a category");
            add(use);
            add(edit);
            add(folder);
            add(tag);
        }

        @Override public Dimension getPreferredSize() { return new Dimension(100, 72); }

        @Override
        public void doLayout() {
            int w = getWidth();
            getComponent(2).setBounds(w - 12 - 44, 16, 44, 40);
            getComponent(3).setBounds(w - 12 - 44 - 8 - 44, 16, 44, 40);
            getComponent(1).setBounds(w - 12 - 44 - 8 - 44 - 8 - 96, 16, 96, 40);
            getComponent(0).setBounds(w - 12 - 44 - 8 - 44 - 8 - 96 - 8 - 116, 16, 116, 40);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            int w = getWidth(), h = getHeight();
            boolean current = p.id.equals(Profiles.current().id);
            Theme.surface(g, this, 0, 0, w, h, 18, 0, current ? 0.4 : 0.1);
            if (current) Theme.stroke(g, 1, 1, w - 2, h - 2, 17, Theme.alpha(Theme.ACCENT, 0.8), 1.5f);
            ProfileArt.draw(g, Profiles.icon(p), p.name, 16, 14, 44, false);
            String sub = p.gameVersion().id + " · " + p.gameVersion().loaderName + (p.locked() ? " · built in, mods fixed" : "")
                    + (p.category != null && !p.category.isBlank() ? " · " + p.category : "");
            g.setFont(Theme.font(Theme.MEDIUM, 15f));
            Theme.left(g, Theme.ellipsize(p.name, g.getFontMetrics(), w - 380), Theme.font(Theme.MEDIUM, 15f), Theme.TEXT, 74, 14, 24);
            Theme.left(g, sub, Theme.font(Theme.REGULAR, 12.5f), Theme.MUTED, 74, 38, 20);
            g.dispose();
        }
    }
}

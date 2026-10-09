package dev.life.launcher.ui.pages;

import dev.life.launcher.content.ContentManager;
import dev.life.launcher.content.Modrinth;
import dev.life.launcher.game.GameVersion;
import dev.life.launcher.ui.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ContentPage extends Page {
    private static final ExecutorService IO = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "life-content");
        t.setDaemon(true);
        return t;
    });
    private static final Map<String, BufferedImage> ICONS = new ConcurrentHashMap<>();

    private final Modrinth.Kind kind;
    private final Components.Button versions;
    private final Components.Segmented tabs;
    private final Components.Button addFiles, openFolder;
    private final Components.Input search, filter;
    private List<ContentManager.Entry> installed = List.of();
    private final Components.Stack list = new Components.Stack(10);
    private final JScrollPane scroll = Components.scroll(list);
    // Browse Modrinth: pages of 30, the next page loads as you scroll near the bottom
    private String browseQuery = "";
    private int browseOffset;
    private boolean browseMore, browseLoading;
    private final Timer debounce;
    private boolean browsing;
    private int searchToken;

    public ContentPage(Modrinth.Kind kind) {
        this.kind = kind;
        versions = new Components.Button("", "chevron-down", Components.Variant.GHOST, null);
        versions.radius(19);
        versions.onClick(() -> VersionMenu.open(versions, 280, this::refresh));
        tabs = new Components.Segmented(List.of("Installed", "Browse Modrinth"), 0, i -> {
            browsing = i == 1;
            refresh();
        });
        addFiles = new Components.Button("Add files", "plus", Components.Variant.GHOST, this::chooseFiles);
        openFolder = new Components.Button("", "folder", Components.Variant.GHOST,
                () -> MainWindow.openPath(ContentManager.dir(version(), kind)));
        search = new Components.Input("", kind == Modrinth.Kind.MODS ? "Search mods on Modrinth" : "Search resource packs on Modrinth", "search");
        debounce = new Timer(350, e -> runSearch());
        debounce.setRepeats(false);
        search.onChange(debounce::restart);
        filter = new Components.Input("", kind == Modrinth.Kind.MODS ? "Search installed mods" : "Search installed packs", "search");
        filter.onChange(() -> showInstalled(installed));
        add(versions);
        versions.setVisible(false);   // only 1.21.11 now; the subtitle already says it
        add(tabs);
        add(addFiles);
        updateAll = new Components.Button("", "download", Components.Variant.GHOST, this::updateAll);   // icon only: keeps the search roomy
        updateAll.setToolTipText("Update all mods to their newest version");
        add(updateAll);
        add(openFolder);
        add(search);
        add(filter);
        add(scroll);
        scroll.getVerticalScrollBar().addAdjustmentListener(e -> {
            JScrollBar b = scroll.getVerticalScrollBar();
            if (browseMore && !browseLoading && b.getValue() + b.getVisibleAmount() > b.getMaximum() - 400) loadMore();
        });

        setTransferHandler(new TransferHandler() {
            @Override public boolean canImport(TransferSupport s) { return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor); }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport s) {
                try {
                    List<File> files = (List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    importPaths(files.stream().map(File::toPath).toList());
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        });
    }

    private GameVersion version() {
        return MainWindow.get().version();
    }

    @Override public String title() { return kind == Modrinth.Kind.MODS ? "Mods" : "Texture packs"; }
    @Override public String icon() { return kind == Modrinth.Kind.MODS ? "mods" : "packs"; }

    @Override
    public void onShow() {
        refresh();
    }

    @Override
    public void doLayout() {
        int w = getWidth(), h = getHeight();
        versions.setText(version().id + "  " + version().loaderName);
        Dimension vd = versions.getPreferredSize();
        versions.setBounds(w - vd.width, 6, vd.width, 38);
        Dimension td = tabs.getPreferredSize();
        tabs.setBounds(0, 86, td.width, 40);
        int bx = w;
        for (Components.Button b : new Components.Button[]{openFolder, addFiles, updateAll}) {
            int bw = b.getPreferredSize().width;
            bx -= bw;
            b.setBounds(bx, 87, bw, 38);
            bx -= 10;
        }
        search.setBounds(td.width + 14, 86, w - td.width - 14, 40);
        filter.setBounds(td.width + 14, 86, Math.max(100, bx - td.width - 14 - 4), 40);
        boolean fps = fpsLocked();
        if (fps && browsing) browsing = false;
        tabs.setVisible(!fps);
        addFiles.setVisible(!browsing && !fps);
        updateAll.setVisible(!browsing && !fps && kind == Modrinth.Kind.MODS);   // packs don't need it
        openFolder.setVisible(!browsing);
        search.setVisible(browsing);
        filter.setVisible(!browsing);
        scroll.setBounds(0, 146, w + 10, h - 146);
        list.doLayout();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.aa(g0.create());
        Theme.left(g, title(), Theme.font(Theme.REGULAR, 34f), Theme.TEXT, 0, 0, 42);
        GameVersion v = version();
        String sub = (kind == Modrinth.Kind.MODS ? "Mods" : "Packs") + " in profile \"" + dev.life.launcher.core.Profiles.current().name
                + "\" for " + v.id + " (" + v.loaderName + "). Drop files anywhere here to add them.";
        if (fpsLocked()) sub = "FPS Boost: Sodium and optimisation mods, installed and kept up to date for you. Launch to set them up.";
        Theme.left(g, sub, Theme.font(Theme.REGULAR, 13.5f), Theme.SOFT, 0, 44, 22);
        g.dispose();
    }

    // -------------------------------------------------------------- data

    private void refresh() {
        browseMore = false;
        doLayout();
        if (browsing) {
            runSearch();
            return;
        }
        GameVersion v = version();
        if (kind == Modrinth.Kind.MODS && v.vanilla()) {
            list.removeAll();
            list.add(new Empty(v.id + " is vanilla", "Mods need Forge or Fabric. Texture packs still work for this version."));
            list.revalidate();
            list.repaint();
            return;
        }
        IO.submit(() -> {
            List<ContentManager.Entry> entries = ContentManager.list(v, kind);
            SwingUtilities.invokeLater(() -> showInstalled(entries));
        });
        repaint();
    }

    private void showInstalled(List<ContentManager.Entry> all) {
        installed = all;
        String q = filter.getText().trim().toLowerCase();
        List<ContentManager.Entry> entries = q.isEmpty() ? all
                : all.stream().filter(e -> e.name().toLowerCase().contains(q) || e.path().getFileName().toString().toLowerCase().contains(q)).toList();
        list.removeAll();
        if (entries.isEmpty() && !all.isEmpty()) {
            list.add(new Empty("Nothing matches \"" + filter.getText().trim() + "\"", "Try another name, or clear the search."));
        } else if (entries.isEmpty()) {
            list.add(new Empty(kind == Modrinth.Kind.MODS ? "No mods in this profile yet" : "No texture packs yet",
                    "Add .%s files, drop them here, or browse Modrinth.".formatted(kind == Modrinth.Kind.MODS ? "jar" : "zip")));
        }
        for (ContentManager.Entry e : entries) list.add(new InstalledRow(e));
        list.animateIn();
        list.revalidate();
        list.doLayout();
        list.repaint();
        scroll.getVerticalScrollBar().setValue(0);
    }

    private void runSearch() {
        browseMore = false;
        if (!browsing) return;
        int token = ++searchToken;
        GameVersion v = version();
        if (kind == Modrinth.Kind.MODS && v.vanilla()) {
            list.removeAll();
            list.add(new Empty(v.id + " is vanilla", "Mods need Forge or Fabric. Pick another version at the top right."));
            list.revalidate();
            list.repaint();
            return;
        }
        String q = search.getText();
        list.removeAll();
        list.add(new Empty("Searching Modrinth", q.isBlank() ? "Most downloaded for " + v.id : "\"" + q + "\" for " + v.id));
        list.revalidate();
        list.repaint();
        IO.submit(() -> {
            try {
                List<Modrinth.Project> res = Modrinth.search(q, kind, v);
                SwingUtilities.invokeLater(() -> {
                    if (token != searchToken) return;
                    list.removeAll();
                    if (res.isEmpty()) list.add(new Empty("Nothing found", "Try a different search."));
                    for (Modrinth.Project p : res) list.add(new BrowseRow(p));
                    browseQuery = q;
                    browseOffset = res.size();
                    browseMore = res.size() >= 30;
                    list.animateIn();
                    list.revalidate();
                    list.doLayout();
                    list.repaint();
                    scroll.getVerticalScrollBar().setValue(0);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    if (token != searchToken) return;
                    list.removeAll();
                    list.add(new Empty("Can't reach Modrinth", "Check your connection and try again."));
                    list.revalidate();
                    list.repaint();
                });
            }
        });
    }

    private void chooseFiles() {
        List<Path> paths = kind == Modrinth.Kind.MODS ? FilePicker.many("Add mods", "Mods", "jar")
                : FilePicker.many("Add texture packs", "Texture packs", "zip");
        if (!paths.isEmpty()) {
            importPaths(paths);
        }
    }

    /** Opens Browse Modrinth with this search (the iOS Home's search pill). */
    public void browseFor(String query) {
        tabs.select(1);
        browsing = true;
        search.setText(query);
        refresh();
        runSearch();
    }

    /** The built-in FPS Boost profile's mods are fixed: no adding, removing or browsing. */
    private boolean fpsLocked() {
        return kind == Modrinth.Kind.MODS && dev.life.launcher.core.Profiles.current().locked();
    }

    private Components.Button updateAll;

    /** Updates every outdated mod / pack to its newest version for this Minecraft version (Modrinth). */
    private void updateAll() {
        updateAll.setEnabled(false);
        MainWindow.get().toast("Checking your mods for updates…");
        GameVersion v = version();
        new Thread(() -> {
            String msg;
            try {
                int n = Modrinth.updateAll(ContentManager.dir(v, kind), kind, v);
                msg = n == 0 ? "Everything is up to date." : "Updated " + n + (n == 1 ? " file." : " files.");
            } catch (Exception e) {
                msg = "Update failed: " + e.getMessage();
            }
            String m = msg;
            SwingUtilities.invokeLater(() -> {
                updateAll.setEnabled(true);
                MainWindow.get().toast(m);
                refresh();
            });
        }, "life-update-all").start();
    }

    private void importPaths(List<Path> paths) {
        if (fpsLocked()) {
            MainWindow.get().toast("FPS Boost's mods are set up for you and can't be changed. Make another profile for your own mods.");
            return;
        }
        GameVersion v = version();
        IO.submit(() -> {
            try {
                int n = ContentManager.importFiles(v, kind, paths);
                SwingUtilities.invokeLater(() -> {
                    String what = kind == Modrinth.Kind.MODS ? (n == 1 ? "mod" : "mods") : (n == 1 ? "pack" : "packs");
                    MainWindow.get().toast(n == 0 ? "Only ." + (kind == Modrinth.Kind.MODS ? "jar" : "zip") + " files can be added here."
                            : "Added " + n + " " + what + " to " + v.id);
                    if (!browsing) refresh();
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> MainWindow.get().toast("Couldn't add files: " + e.getMessage()));
            }
        });
    }

    // -------------------------------------------------------------- rows

    private static void drawBadge(Graphics2D g, BufferedImage icon, String name, int x, int y, int s) {
        if (icon != null) {
            Theme.roundedImage(g, icon, x, y, s, s, 20, true);
            return;
        }
        Theme.fill(g, x, y, s, s, 10, new GradientPaint(x, y, Theme.MID, x, y + s, Theme.RAISED));
        String letter = name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        Theme.center(g, letter, Theme.font(Theme.BOLD, s * 0.42f), Theme.TEXT, x, y, s, s);
    }

    private final class InstalledRow extends JComponent {
        private final ContentManager.Entry e;
        private final Components.Toggle toggle;
        private final Components.IconButton trash;

        private BufferedImage icon;

        InstalledRow(ContentManager.Entry e) {
            this.e = e;
            setLayout(null);
            IO.submit(() -> {
                BufferedImage img = dev.life.launcher.content.Images.localIcon(e.path());
                if (img != null) SwingUtilities.invokeLater(() -> { icon = img; repaint(); });
            });
            toggle = new Components.Toggle(e.enabled(), on -> IO.submit(() -> {
                try {
                    ContentManager.setEnabled(version(), kind, e, on);
                    SwingUtilities.invokeLater(ContentPage.this::refresh);
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> MainWindow.get().toast("Couldn't change " + e.name() + ": " + ex.getMessage()));
                }
            }));
            trash = new Components.IconButton("trash", 18, () -> IO.submit(() -> {
                try {
                    ContentManager.delete(version(), kind, e);
                    SwingUtilities.invokeLater(() -> {
                        MainWindow.get().toast("Removed " + e.name());
                        refresh();
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> MainWindow.get().toast("Couldn't remove " + e.name()));
                }
            }));
            trash.hoverColor(Theme.DANGER);
            if (!e.locked()) {
                add(toggle);
                add(trash);
            }
        }

        @Override public Dimension getPreferredSize() { return new Dimension(100, 66); }

        @Override
        public void doLayout() {
            int w = getWidth(), h = getHeight();
            trash.setBounds(w - 48, (h - 34) / 2, 34, 34);
            toggle.setBounds(w - 108, (h - 28) / 2, 50, 28);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            int w = getWidth(), h = getHeight();
            Theme.surface(g, this, 0, 0, w, h, 16);
            if (e.locked() && e.name().equals("Life Client")) {
                Theme.fill(g, 14, 13, 40, 40, 10, Theme.BLACK);
                Theme.logo(g, 34, 33, 30, 1);
            } else drawBadge(g, icon, e.name(), 14, 13, 40);
            g.setFont(Theme.font(Theme.MEDIUM, 14.5f));
            String name = Theme.ellipsize(e.name(), g.getFontMetrics(), w - 260);
            Color nameColor = e.enabled() || e.locked() ? Theme.TEXT : Theme.MUTED;
            Theme.left(g, name, Theme.font(Theme.MEDIUM, 14.5f), nameColor, 68, 12, 22);
            Theme.left(g, ContentManager.human(e.size()), Theme.font(Theme.REGULAR, 12.5f), Theme.MUTED, 68, 34, 20);
            if (e.locked()) {
                Font f = Theme.font(Theme.MEDIUM, 12f);
                int tw = Theme.width(g, e.lockedReason(), f) + 22;
                Theme.stroke(g, w - tw - 18, (h - 26) / 2.0, tw, 26, 13, Theme.LINE_2, 1f);
                Theme.center(g, e.lockedReason(), f, Theme.SOFT, w - tw - 18, (h - 26) / 2.0, tw, 26);
            }
            g.dispose();
        }
    }

    private final class BrowseRow extends JComponent {
        private final Modrinth.Project p;
        private final Components.Button install;
        private BufferedImage icon;

        BrowseRow(Modrinth.Project p) {
            this.p = p;
            setLayout(null);
            install = new Components.Button("Install", "download", Components.Variant.GHOST, null);
            install.onClick(this::install);
            add(install);
            icon = ICONS.get(p.iconUrl());
            if (icon == null && !p.iconUrl().isEmpty()) {
                IO.submit(() -> {
                    try {
                        byte[] b = Modrinth.icon(p.iconUrl());
                        BufferedImage img = dev.life.launcher.content.Images.decode(b);
                        if (img != null) {
                            ICONS.put(p.iconUrl(), img);
                            SwingUtilities.invokeLater(() -> { icon = img; repaint(); });
                        }
                    } catch (Exception ignored) {}
                });
            }
        }

        private void install() {
            GameVersion v = version();
            install.setEnabled(false);
            install.setText("Installing");
            IO.submit(() -> {
                try {
                    String file = Modrinth.installLatest(p.id(), v, ContentManager.dir(v, kind), kind, kind == Modrinth.Kind.MODS);
                    SwingUtilities.invokeLater(() -> {
                        install.setText("Installed");
                        MainWindow.get().toast("Installed " + p.title() + " for " + v.id + (file == null ? "" : ""));
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        install.setEnabled(true);
                        install.setText("Install");
                        MainWindow.get().toast("Couldn't install " + p.title() + ": " + e.getMessage());
                    });
                }
            });
        }

        @Override public Dimension getPreferredSize() { return new Dimension(100, 78); }

        @Override
        public void doLayout() {
            int bw = 124;
            install.setBounds(getWidth() - bw - 16, (getHeight() - 38) / 2, bw, 38);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            int w = getWidth(), h = getHeight();
            Theme.surface(g, this, 0, 0, w, h, 16);
            drawBadge(g, icon, p.title(), 14, 14, 50);
            int tx = 80, max = w - tx - 170;
            g.setFont(Theme.font(Theme.MEDIUM, 15f));
            String title = Theme.ellipsize(p.title(), g.getFontMetrics(), max);
            Theme.left(g, title, Theme.font(Theme.MEDIUM, 15f), Theme.TEXT, tx, 12, 22);
            int titleW = Theme.width(g, title, Theme.font(Theme.MEDIUM, 15f));
            Theme.left(g, "by " + p.author() + "   " + downloads(p.downloads()) + " downloads", Theme.font(Theme.REGULAR, 12.5f), Theme.MUTED, tx + titleW + 10, 13, 22);
            g.setFont(Theme.font(Theme.REGULAR, 13f));
            Theme.left(g, Theme.ellipsize(p.description(), g.getFontMetrics(), max), Theme.font(Theme.REGULAR, 13f), Theme.SOFT, tx, 40, 22);
            g.dispose();
        }

        private String downloads(long n) {
            if (n >= 1_000_000) return String.format("%.1fM", n / 1e6);
            if (n >= 1_000) return String.format("%.0fk", n / 1e3);
            return String.valueOf(n);
        }
    }

    private static final class Empty extends JComponent {
        private final String title, hint;

        Empty(String title, String hint) {
            this.title = title;
            this.hint = hint;
        }

        @Override public Dimension getPreferredSize() { return new Dimension(100, 160); }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            int w = getWidth(), h = getHeight();
            Theme.surface(g, this, 0, 0, w, h, 24, 0, 0.1);
            Icons.paint(g, "packs", w / 2.0 - 12, h / 2.0 - 62, 24, Theme.MUTED);
            Theme.center(g, title, Theme.font(Theme.MEDIUM, 16f), Theme.TEXT, 0, h / 2.0 - 26, w, 26);
            Theme.center(g, hint, Theme.font(Theme.REGULAR, 13.5f), Theme.SOFT, 0, h / 2.0 + 2, w, 22);
            g.dispose();
        }
    }

    /** Appends the next page of Modrinth results (infinite scroll). */
    private void loadMore() {
        browseLoading = true;
        int token = searchToken, offset = browseOffset;
        String q = browseQuery;
        GameVersion v = version();
        IO.submit(() -> {
            try {
                List<Modrinth.Project> res = Modrinth.search(q, kind, v, offset);
                SwingUtilities.invokeLater(() -> {
                    browseLoading = false;
                    if (token != searchToken) return;
                    for (Modrinth.Project p : res) list.add(new BrowseRow(p));
                    browseOffset += res.size();
                    browseMore = res.size() >= 30;
                    list.revalidate();
                    list.doLayout();
                    list.repaint();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> browseLoading = false);
            }
        });
    }
}

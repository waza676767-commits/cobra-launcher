package dev.life.launcher.ui.pages;

import dev.life.launcher.core.Paths;
import dev.life.launcher.core.Profiles;
import dev.life.launcher.game.GameVersion;
import dev.life.launcher.ui.*;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

/**
 * Logs: the game's logs and crash reports of every profile plus the launcher's own, newest first.
 * Read one in a tidy viewer (errors red, warnings amber, times muted), copy it, or open the folder.
 */
public final class LogsPage extends Page {
    private final Components.Stack list = new Components.Stack(10);
    private final JScrollPane scroll = Components.scroll(list);
    private final Components.Button openFolder;
    private Viewer viewer;

    private record Entry(Path file, String kind) {}

    public LogsPage() {
        openFolder = new Components.Button("Open logs folder", "folder", Components.Variant.GHOST,
                () -> MainWindow.openPath(Profiles.gameDir(Profiles.current(), GameVersion.MODERN).resolve("logs")));
        add(openFolder);
        add(scroll);
    }

    @Override public String title() { return "Logs"; }
    @Override public String icon() { return "list"; }

    @Override
    public void onShow() {
        refresh();
    }

    private void refresh() {
        list.removeAll();
        List<Entry> all = new ArrayList<>();
        for (Profiles.Profile p : Profiles.all()) {
            Path game = Profiles.gameDir(p, GameVersion.MODERN);
            collect(all, game.resolve("logs"), "Game log · " + p.name, ".log");
            collect(all, game.resolve("crash-reports"), "Crash report · " + p.name, ".txt");
        }
        collect(all, Paths.LOGS, "Launcher", ".log");
        collect(all, Paths.LOGS.resolve("recorder"), "Screen Recorder", ".log");
        all.sort((a, b) -> Long.compare(b.file().toFile().lastModified(), a.file().toFile().lastModified()));
        if (all.isEmpty()) list.add(new Empty());
        int n = 0;
        for (Entry e : all) {
            if (n++ >= 60) break;
            list.add(new Row(e));
        }
        list.animateIn();
        list.revalidate();
        list.repaint();
    }

    private static void collect(List<Entry> out, Path dir, String kind, String ext) {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(f -> f.getFileName().toString().endsWith(ext) && Files.isRegularFile(f)).forEach(f -> out.add(new Entry(f, kind)));
        } catch (Exception ignored) {}
    }

    @Override
    public void doLayout() {
        int w = getWidth(), h = getHeight();
        Dimension od = openFolder.getPreferredSize();
        openFolder.setBounds(w - od.width, 6, od.width, 40);
        scroll.setBounds(0, 80, w + 10, h - 80);
        if (viewer != null) viewer.setBounds(0, 0, viewer.getParent().getWidth(), viewer.getParent().getHeight());
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.aa(g0.create());
        Theme.left(g, title(), Theme.font(Theme.REGULAR, 34f), Theme.TEXT, 0, 0, 42);
        Theme.left(g, "Game logs and crash reports of every profile. Read them here or open the folder.",
                Theme.font(Theme.REGULAR, 13.5f), Theme.SOFT, 0, 44, 22);
        g.dispose();
    }

    private void open(Entry e) {
        JLayeredPane lp = SwingUtilities.getRootPane(this).getLayeredPane();
        if (viewer != null) lp.remove(viewer);
        viewer = new Viewer(e, () -> {
            lp.remove(viewer);
            viewer = null;
            lp.repaint();
        });
        viewer.setBounds(0, 0, lp.getWidth(), lp.getHeight());
        lp.add(viewer, Integer.valueOf(JLayeredPane.MODAL_LAYER + 3));
        lp.revalidate();
        lp.repaint();
    }

    private static final class Empty extends JComponent {
        @Override public Dimension getPreferredSize() { return new Dimension(100, 150); }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            int w = getWidth(), h = getHeight();
            Theme.surface(g, this, 0, 0, w, h, 24, 0, 0.1);
            Icons.paint(g, "list", w / 2.0 - 12, h / 2.0 - 46, 24, Theme.MUTED);
            Theme.center(g, "No logs yet", Theme.font(Theme.MEDIUM, 15f), Theme.TEXT, 0, h / 2.0 - 12, w, 22);
            Theme.center(g, "They appear after you launch the game once.", Theme.font(Theme.REGULAR, 13f), Theme.MUTED, 0, h / 2.0 + 12, w, 20);
            g.dispose();
        }
    }

    private final class Row extends JPanel {
        private final Entry e;

        Row(Entry e) {
            super(null);
            this.e = e;
            setOpaque(false);
            Components.Button read = new Components.Button("Read", "eye", Components.Variant.PRIMARY, () -> open(e));
            Components.Button show = new Components.Button("Show", "folder", Components.Variant.GHOST, () -> MainWindow.openPath(e.file().getParent()));
            add(read);
            add(show);
        }

        @Override public Dimension getPreferredSize() { return new Dimension(100, 66); }

        @Override
        public void doLayout() {
            int w = getWidth();
            getComponent(1).setBounds(w - 12 - 96, 13, 96, 40);
            getComponent(0).setBounds(w - 12 - 96 - 8 - 96, 13, 96, 40);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            int w = getWidth(), h = getHeight();
            Theme.surface(g, this, 0, 0, w, h, 18, 0, 0.1);
            boolean crash = e.kind().startsWith("Crash");
            Color tint = crash ? new Color(0xFF5A5A) : Theme.ACCENT;
            Theme.fill(g, 14, 13, 40, 40, 10, Theme.alpha(tint, 0.18));
            Icons.paint(g, crash ? "close" : "list", 24, 23, 20, tint);
            long size = e.file().toFile().length();
            String meta = e.kind() + "  ·  " + new SimpleDateFormat("d MMM, HH:mm").format(new Date(e.file().toFile().lastModified()))
                    + "  ·  " + (size >= 1 << 20 ? String.format("%.1f MB", size / (double) (1 << 20)) : Math.max(1, size / 1024) + " KB");
            g.setFont(Theme.font(Theme.MEDIUM, 14.5f));
            Theme.left(g, Theme.ellipsize(e.file().getFileName().toString(), g.getFontMetrics(), w - 320), Theme.font(Theme.MEDIUM, 14.5f), Theme.TEXT, 68, 12, 22);
            g.setFont(Theme.font(Theme.REGULAR, 12.5f));
            Theme.left(g, Theme.ellipsize(meta, g.getFontMetrics(), w - 320), Theme.font(Theme.REGULAR, 12.5f), Theme.MUTED, 68, 34, 20);
            g.dispose();
        }
    }

    /** Full-window log reader: coloured lines, copy, open in your editor. */
    private static final class Viewer extends JComponent {
        private final Entry e;
        private final List<String> lines = new ArrayList<>();
        private final JScrollPane scroll;
        private final Components.Button copy, edit, close;

        Viewer(Entry e, Runnable onClose) {
            this.e = e;
            setLayout(null);
            addMouseListener(new java.awt.event.MouseAdapter() {});   // block clicks to the page behind
            try {
                List<String> all = Files.readAllLines(e.file(), StandardCharsets.UTF_8);
                lines.addAll(all.size() > 6000 ? all.subList(all.size() - 6000, all.size()) : all);
            } catch (Exception ex) {
                try {
                    String raw = new String(Files.readAllBytes(e.file()), StandardCharsets.ISO_8859_1);
                    for (String l : raw.split("\\r?\\n")) lines.add(l);
                } catch (Exception ignored) {
                    lines.add("Couldn't read this file.");
                }
            }
            JComponent text = new JComponent() {
                final Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 12);

                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(600, lines.size() * 17 + 16);
                }

                @Override
                protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g.setFont(mono);
                    Rectangle clip = g.getClipBounds();
                    int first = Math.max(0, (clip.y - 8) / 17), last = Math.min(lines.size(), (clip.y + clip.height) / 17 + 2);
                    for (int i = first; i < last; i++) {
                        String l = lines.get(i);
                        String u = l.toUpperCase();
                        Color c = u.contains("ERROR") || u.contains("FATAL") || u.contains("EXCEPTION") || l.startsWith("\tat ") || l.startsWith("Caused by")
                                ? new Color(0xFF6B6B) : u.contains("WARN") ? new Color(0xF5C542) : Theme.SOFT;
                        int y = 8 + i * 17 + 13;
                        int split = l.startsWith("[") ? l.indexOf(']') + 1 : 0;   // [12:34:56] in a quieter colour
                        if (split > 0 && split < 20) {
                            g.setColor(Theme.MUTED);
                            g.drawString(l.substring(0, split), 10, y);
                            g.setColor(c);
                            g.drawString(l.substring(split), 10 + g.getFontMetrics().stringWidth(l.substring(0, split)), y);
                        } else {
                            g.setColor(c);
                            g.drawString(l, 10, y);
                        }
                    }
                    g.dispose();
                }
            };
            scroll = Components.scroll(text);
            scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            add(scroll);
            copy = new Components.Button("Copy all", "copy", Components.Variant.GHOST, () -> {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(String.join("\n", lines)), null);
                MainWindow.get().toast("Log copied to your clipboard.");
            });
            edit = new Components.Button("Open", "external", Components.Variant.GHOST, () -> MainWindow.openPath(e.file()));
            close = new Components.Button("Close", "close", Components.Variant.PRIMARY, onClose);
            add(copy);
            add(edit);
            add(close);
            SwingUtilities.invokeLater(() -> {   // start at the end, where the interesting part usually is
                JScrollBar b = scroll.getVerticalScrollBar();
                b.setValue(b.getMaximum());
            });
        }

        private Rectangle card() {
            int w = getWidth(), h = getHeight();
            return new Rectangle(40, 30, Math.max(300, w - 80), Math.max(200, h - 60));
        }

        @Override
        public void doLayout() {
            Rectangle c = card();
            scroll.setBounds(c.x + 16, c.y + 60, c.width - 32, c.height - 76);
            close.setBounds(c.x + c.width - 16 - 110, c.y + 12, 110, 38);
            edit.setBounds(c.x + c.width - 16 - 110 - 8 - 100, c.y + 12, 100, 38);
            copy.setBounds(c.x + c.width - 16 - 110 - 8 - 100 - 8 - 120, c.y + 12, 120, 38);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            g.setColor(Theme.alpha(Theme.BLACK, 0.72));
            g.fillRect(0, 0, getWidth(), getHeight());
            Rectangle c = card();
            Theme.surface(g, this, c.x, c.y, c.width, c.height, 22, 0, 0.6);
            g.setFont(Theme.font(Theme.BOLD, 17f));
            Theme.left(g, Theme.ellipsize(e.file().getFileName().toString() + "  ·  " + e.kind(), g.getFontMetrics(), c.width - 420),
                    Theme.font(Theme.BOLD, 17f), Theme.TEXT, c.x + 22, c.y + 16, 30);
            g.dispose();
        }
    }
}

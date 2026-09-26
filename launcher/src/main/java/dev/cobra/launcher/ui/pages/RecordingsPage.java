package dev.cobra.launcher.ui.pages;

import dev.cobra.launcher.core.Paths;
import dev.cobra.launcher.ui.*;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

/**
 * Your Cobra Client screen recordings (Screen Recorder module in game: start / pause / stop keys,
 * 1080p at 30, 60 or 120 fps). Newest first; play, show in folder or delete.
 */
public final class RecordingsPage extends Page {
    public static final Path DIR = Paths.ROOT.resolve("recordings");

    private final Components.Stack list = new Components.Stack(10);
    private final JScrollPane scroll = Components.scroll(list);
    private final Components.Button openFolder;

    public RecordingsPage() {
        openFolder = new Components.Button("Open folder", "folder", Components.Variant.GHOST, () -> {
            try {
                Files.createDirectories(DIR);
            } catch (Exception ignored) {}
            MainWindow.openUri(DIR.toUri().toString());
        });
        add(openFolder);
        add(scroll);
    }

    @Override public String title() { return "Recordings"; }
    @Override public String icon() { return "camera"; }

    @Override
    public void onShow() {
        refresh();
    }

    private void refresh() {
        list.removeAll();
        List<Path> files = new ArrayList<>();
        try {
            if (Files.isDirectory(DIR)) {
                try (Stream<Path> s = Files.list(DIR)) {
                    s.filter(p -> p.getFileName().toString().endsWith(".mp4")).forEach(files::add);
                }
            }
        } catch (Exception ignored) {}
        files.sort((a, b) -> Long.compare(b.toFile().lastModified(), a.toFile().lastModified()));
        if (files.isEmpty()) list.add(new Empty());
        for (Path p : files) list.add(new Row(p));
        list.animateIn();
        list.revalidate();
        list.repaint();
        repaint();
    }

    @Override
    public void doLayout() {
        int w = getWidth(), h = getHeight();
        Dimension od = openFolder.getPreferredSize();
        openFolder.setBounds(w - od.width, 6, od.width, 40);
        scroll.setBounds(0, 80, w + 10, h - 80);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.aa(g0.create());
        Theme.left(g, title(), Theme.font(Theme.BOLD, 30f), Theme.TEXT, 0, 0, 42);
        Theme.left(g, "Screen Recorder in game: turn it on in Cobra's modules, then F9 start, F10 pause, F12 stop (changeable).",
                Theme.font(Theme.REGULAR, 13.5f), Theme.SOFT, 0, 44, 22);
        g.dispose();
    }

    private final class Empty extends JComponent {
        @Override public Dimension getPreferredSize() { return new Dimension(100, 150); }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            int w = getWidth(), h = getHeight();
            Theme.surface(g, this, 0, 0, w, h, 24, 0, 0.1);
            Icons.paint(g, "camera", w / 2.0 - 12, h / 2.0 - 46, 24, Theme.MUTED);
            Theme.center(g, "No recordings yet", Theme.font(Theme.MEDIUM, 15f), Theme.TEXT, 0, h / 2.0 - 12, w, 22);
            Theme.center(g, "In game: Screen Recorder module → press F9 to start.", Theme.font(Theme.REGULAR, 13f), Theme.MUTED, 0, h / 2.0 + 12, w, 20);
            g.dispose();
        }
    }

    private final class Row extends JPanel {
        private final Path file;

        Row(Path file) {
            super(null);
            this.file = file;
            setOpaque(false);
            Components.Button play = new Components.Button("Play", "play", Components.Variant.PRIMARY, () -> MainWindow.openUri(file.toUri().toString()));
            Components.Button folder = new Components.Button("Show", "folder", Components.Variant.GHOST, () -> MainWindow.openUri(DIR.toUri().toString()));
            Components.Button del = new Components.Button("", "trash", Components.Variant.GHOST, () -> {
                try {
                    Files.deleteIfExists(file);
                } catch (Exception ignored) {}
                refresh();
            });
            add(play);
            add(folder);
            add(del);
        }

        @Override public Dimension getPreferredSize() { return new Dimension(100, 66); }

        @Override
        public void doLayout() {
            int w = getWidth();
            Component[] c = getComponents();
            c[2].setBounds(w - 44 - 12, 13, 44, 40);
            c[1].setBounds(w - 44 - 12 - 8 - 96, 13, 96, 40);
            c[0].setBounds(w - 44 - 12 - 8 - 96 - 8 - 96, 13, 96, 40);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            int w = getWidth(), h = getHeight();
            Theme.surface(g, this, 0, 0, w, h, 18, 0, 0.1);
            Theme.fill(g, 14, 13, 40, 40, 10, Theme.alpha(Theme.ACCENT, 0.18));
            Icons.paint(g, "camera", 24, 23, 20, Theme.ACCENT);
            String name = file.getFileName().toString().replaceFirst("\\.mp4$", "");
            long size = file.toFile().length();
            String meta = new SimpleDateFormat("d MMM yyyy, HH:mm").format(new Date(file.toFile().lastModified()))
                    + "  ·  " + (size > 1 << 30 ? String.format("%.1f GB", size / (double) (1 << 30)) : String.format("%.0f MB", size / (double) (1 << 20)));
            Theme.left(g, name, Theme.font(Theme.MEDIUM, 14.5f), Theme.TEXT, 68, 12, 22);
            Theme.left(g, meta, Theme.font(Theme.REGULAR, 12.5f), Theme.MUTED, 68, 34, 20);
            g.dispose();
        }
    }
}

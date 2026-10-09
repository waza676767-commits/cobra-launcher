package dev.life.launcher.ui.pages;

import dev.life.launcher.game.GameVersion;
import dev.life.launcher.game.Playtime;
import dev.life.launcher.ui.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.Locale;

public final class AnalyticsPage extends Page {
    private static final long DAY = 86_400_000L;
    private final Chart chart = new Chart();
    private final Split split = new Split();
    private long week, today, longest;
    private int sessions;

    public AnalyticsPage() {
        add(chart);
        add(split);
    }

    @Override public String title() { return "Analytics"; }
    @Override public String icon() { return "analytics"; }

    @Override
    public void onShow() {
        long now = System.currentTimeMillis();
        long weekStart = LocalDate.now().minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        week = Playtime.totalSeconds(null, weekStart);
        today = Playtime.totalSeconds(null, todayStart);
        sessions = Playtime.sessionsSince(weekStart);
        longest = Playtime.longestSince(weekStart);
        chart.data = Playtime.lastDays(7);
        split.values.clear();
        for (GameVersion gv : GameVersion.values()) {
            long secs = Playtime.totalSeconds(gv.id, weekStart);
            if (secs > 0) split.values.put(gv.id, secs);
        }
        repaint();
    }

    @Override
    public void doLayout() {
        int w = getWidth(), h = getHeight();
        int top = 188;
        int splitH = 118;
        chart.setBounds(0, top, w, h - top - splitH - 16);
        split.setBounds(0, h - splitH, w, splitH);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.aa(g0.create());
        int w = getWidth();
        Theme.left(g, "Analytics", Theme.font(Theme.REGULAR, 34f), Theme.TEXT, 0, 0, 42);
        Theme.left(g, "Play time from this launcher over the last 7 days.", Theme.font(Theme.REGULAR, 13.5f), Theme.SOFT, 0, 44, 22);

        String[][] stats = {
                {"Last 7 days", Playtime.format(week)},
                {"Today", Playtime.format(today)},
                {"Sessions", String.valueOf(sessions)},
                {"Longest session", Playtime.format(longest)}};
        int gap = 14, cw = (w - gap * 3) / 4, y = 84, ch = 88;
        for (int i = 0; i < 4; i++) {
            int x = i * (cw + gap);
            if (i == 0) Theme.fill(g, x, y, cw, ch, 18, Theme.TEXT);
            else Theme.surface(g, this, x, y, cw, ch, 18);
            Color fg = i == 0 ? Theme.BLACK : Theme.TEXT;
            Theme.left(g, stats[i][0], Theme.font(Theme.MEDIUM, 12.5f), i == 0 ? Theme.mix(Theme.BLACK, Theme.TEXT, 0.45) : Theme.SOFT, x + 20, y + 14, 20);
            Theme.left(g, stats[i][1], Theme.font(Theme.BOLD, 28f), fg, x + 20, y + 38, 38);
        }
        g.dispose();
    }

    private static final class Chart extends JComponent {
        long[] data = new long[7];
        int hover = -1;

        Chart() {
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) { hover = index(e.getX()); repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        private int index(int x) {
            int pad = 60, cw = (getWidth() - pad - 24) / 7;
            int i = (x - pad) / Math.max(1, cw);
            return x >= pad && i >= 0 && i < 7 ? i : -1;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            int w = getWidth(), h = getHeight();
            Theme.surface(g, this, 0, 0, w, h, 20);
            Theme.left(g, "Daily play time", Theme.font(Theme.MEDIUM, 14f), Theme.TEXT, 22, 14, 24);

            long max = 0;
            for (long d : data) max = Math.max(max, d);
            long hours = Math.max(1, (long) Math.ceil(max / 3600.0));
            long scale = hours * 3600;

            int pad = 60, top = 56, bottom = h - 40, ch = bottom - top;
            for (int i = 0; i <= 2; i++) {
                int y = bottom - ch * i / 2;
                g.setColor(Theme.alpha(Theme.LINE_2, i == 0 ? 0.9 : 0.4));
                g.fillRect(pad, y, w - pad - 24, 1);
                String lbl = i == 0 ? "0" : (hours * i / 2.0 == Math.floor(hours * i / 2.0) ? (hours * i / 2) + "h" : String.format("%.1fh", hours * i / 2.0));
                Theme.left(g, lbl, Theme.font(Theme.REGULAR, 11.5f), Theme.MUTED, 22, y - 10, 20);
            }

            int cw = (w - pad - 24) / 7;
            LocalDate today = LocalDate.now();
            for (int i = 0; i < 7; i++) {
                int x = pad + i * cw;
                double v = data[i] / (double) scale;
                int bh = (int) Math.round(ch * v);
                int bw = Math.min(46, cw - 22);
                int bx = x + (cw - bw) / 2;
                boolean isToday = i == 6;
                Color c = isToday ? Theme.TEXT : (i == hover ? Theme.SOFT : Theme.MID);
                if (bh > 0) {
                    // rounded top, flat bottom: clip a taller rounded bar at the baseline
                    Graphics2D gc = (Graphics2D) g.create();
                    gc.clipRect(bx, bottom - bh, bw, bh);
                    Theme.fill(gc, bx, bottom - bh, bw, bh + 12, Math.min(10, bw / 2.0), c);
                    gc.dispose();
                }
                if (bh == 0) Theme.fill(g, bx, bottom - 3, bw, 3, 1.5, Theme.LINE_2);
                LocalDate d = today.minusDays(6 - i);
                String day = isToday ? "Today" : d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault());
                Theme.center(g, day, Theme.font(isToday ? Theme.MEDIUM : Theme.REGULAR, 12f), isToday ? Theme.TEXT : Theme.SOFT, x, bottom + 10, cw, 20);
                if (i == hover) {
                    String t = Playtime.format(data[i]);
                    Font f = Theme.font(Theme.MEDIUM, 12.5f);
                    int tw = Theme.width(g, t, f) + 20;
                    int ty = Math.max(top - 30, bottom - bh - 36);
                    Theme.fill(g, x + (cw - tw) / 2.0, ty, tw, 26, 9, Theme.TEXT);
                    Theme.center(g, t, f, Theme.BLACK, x + (cw - tw) / 2.0, ty, tw, 26);
                }
            }
            g.dispose();
        }
    }

    private static final class Split extends JComponent {
        final java.util.LinkedHashMap<String, Long> values = new java.util.LinkedHashMap<>();

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            int w = getWidth(), h = getHeight();
            Theme.surface(g, this, 0, 0, w, h, 20);
            Theme.left(g, "By version", Theme.font(Theme.MEDIUM, 14f), Theme.TEXT, 22, 14, 24);
            long total = 0;
            for (long v : values.values()) total += v;
            int bx = 22, by = 54, bw = w - 44;
            Theme.fill(g, bx, by, bw, 10, 5, Theme.LINE_2);
            if (total == 0) {
                Theme.left(g, "No play time in the last 7 days yet.", Theme.font(Theme.REGULAR, 13f), Theme.SOFT, bx, by + 22, 24);
                g.dispose();
                return;
            }
            double x = bx;
            int i = 0, n = values.size();
            java.util.List<Color> shades = new java.util.ArrayList<>();
            for (String id : values.keySet()) {
                Color c = Theme.mix(Theme.TEXT, Theme.MID, n <= 1 ? 0 : i / (double) (n - 1));
                shades.add(c);
                double seg = bw * values.get(id) / (double) total;
                Graphics2D gc = (Graphics2D) g.create();
                gc.clip(new java.awt.geom.RoundRectangle2D.Double(bx, by, bw, 10, 10, 10));
                gc.setColor(c);
                gc.fill(new java.awt.geom.Rectangle2D.Double(x, by, Math.max(2, seg - (i < n - 1 ? 2 : 0)), 10));
                gc.dispose();
                x += seg;
                i++;
            }
            int lx = bx;
            i = 0;
            for (String id : values.keySet()) {
                String label = id + "  " + Playtime.format(values.get(id));
                Theme.fill(g, lx, by + 29, 10, 10, 3, shades.get(i++));
                Theme.left(g, label, Theme.font(Theme.REGULAR, 13f), Theme.SOFT, lx + 18, by + 22, 24);
                lx += 18 + Theme.width(g, label, Theme.font(Theme.REGULAR, 13f)) + 26;
            }
            g.dispose();
        }
    }
}

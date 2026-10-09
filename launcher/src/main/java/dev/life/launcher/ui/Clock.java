package dev.life.launcher.ui;

import dev.life.launcher.core.Http;

import javax.swing.*;
import java.awt.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Title-bar clock: time with the date underneath. The time zone comes from your IP
 * (ipapi.co, once per start); if that fails it uses the system time zone.
 */
public final class Clock extends JComponent {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH);
    private volatile ZoneId zone = ZoneId.systemDefault();
    private volatile String place = "";

    public Clock() {
        setOpaque(false);
        Timer t = new Timer(1000, e -> repaint());
        t.start();
        Thread lookup = new Thread(() -> {
            try {
                Http.Response r = Http.get("https://ipapi.co/json/");
                if (r.ok()) {
                    var j = r.json();
                    if (j.has("timezone")) zone = ZoneId.of(j.get("timezone").getAsString());
                    if (j.has("city")) place = j.get("city").getAsString();
                    SwingUtilities.invokeLater(this::repaint);
                }
            } catch (Exception ignored) {
                // offline or rate-limited: keep the system zone
            }
        }, "life-clock");
        lookup.setDaemon(true);
        lookup.start();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(118, 40);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.aa(g0.create());
        ZonedDateTime now = ZonedDateTime.now(zone);
        int w = getWidth();
        Font tf = Theme.font(Theme.BOLD, 16f), df = Theme.font(Theme.REGULAR, 11.5f);
        var cs = dev.life.launcher.core.Settings.get();
        String pattern = (cs.clock24 ? "HH:mm" : "h:mm") + (cs.clockSeconds ? ":ss" : "") + (cs.clock24 ? "" : " a");
        String time = now.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
        String date = cs.clockDate ? now.format(DATE) + (place.isEmpty() ? "" : "  " + place) : "";
        if (date.isEmpty()) {                                  // time only: centre it vertically
            Theme.left(g, time, tf, Theme.TEXT, w - Theme.width(g, time, tf), 0, 40);
            g.dispose();
            return;
        }
        g.setFont(df);
        date = Theme.ellipsize(date, g.getFontMetrics(), w);
        Theme.left(g, time, tf, Theme.TEXT, w - Theme.width(g, time, tf), 0, 22);
        Theme.left(g, date, df, Theme.MUTED, w - Theme.width(g, date, df), 20, 18);
        g.dispose();
    }
}

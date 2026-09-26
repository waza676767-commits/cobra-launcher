package dev.cobra.launcher.ui.pages;

import dev.cobra.launcher.core.BuildInfo;
import dev.cobra.launcher.game.GameVersion;
import dev.cobra.launcher.game.Playtime;
import dev.cobra.launcher.ui.*;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;

public final class HomePage extends Page {
    private final LaunchButton launch = new LaunchButton();
    private final VersionChip chip = new VersionChip();

    public HomePage() {
        add(launch);
        add(chip);
        SwingUtilities.invokeLater(() -> MainWindow.get().onStateChange(this::repaint));
    }

    @Override public String title() { return "Home"; }
    @Override public String icon() { return "home"; }

    @Override
    public void doLayout() {
        int w = getWidth(), h = getHeight();
        int bw = 288;
        int ly = (int) (h * 0.585);
        launch.setBounds((w - bw) / 2, ly, bw, 60);
        chip.setBounds((w - bw) / 2, ly + 74, bw, 52);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.aa(g0.create());
        int w = getWidth(), h = getHeight();
        double cy = h * 0.23;
        // soft pool of shade behind the hero so it reads on any wallpaper (light: a glow of white)
        if (dev.cobra.launcher.ui.Wallpaper.active()) {
            Color pool = Theme.isLight() ? new Color(250, 248, 246, 150) : new Color(0, 0, 0, 125);
            Color none = Theme.isLight() ? new Color(250, 248, 246, 0) : new Color(0, 0, 0, 0);
            double ry = h * 0.46, rx = Math.min(w * 0.47, 420);   // fades out before the page edges
            Graphics2D gp = (Graphics2D) g.create();
            gp.translate(w / 2.0, h * 0.47);
            gp.scale(rx / ry, 1);
            gp.setPaint(new RadialGradientPaint(new Point.Double(0, 0), (float) ry, new float[]{0f, 1f}, new Color[]{pool, none}));
            gp.fill(new Ellipse2D.Double(-ry, -ry, ry * 2, ry * 2));
            gp.dispose();
        }
        float[] fr = {0f, 1f};
        Color[] c = {Theme.alpha(Theme.TEXT, 0.07), Theme.alpha(Theme.TEXT, 0)};
        g.setPaint(new RadialGradientPaint(new Point.Double(w / 2.0, cy), 170f, fr, c));
        g.fill(new Ellipse2D.Double(w / 2.0 - 170, cy - 170, 340, 340));
        Theme.logo(g, w / 2.0, cy, 136, 1);

        Color shade = Theme.isLight() ? new Color(255, 255, 255, 90) : new Color(0, 0, 0, 110);
        Theme.center(g, "COBRA", Theme.tracked(Theme.BOLD, 46f, 0.28f), shade, 0, cy + 78, w, 60);
        Theme.center(g, "COBRA", Theme.tracked(Theme.BOLD, 46f, 0.28f), Theme.TEXT, 0, cy + 76, w, 60);

        MainWindow mw = MainWindow.get();
        String sub;
        if (mw != null && dev.cobra.launcher.core.Settings.get().offline) sub = "No account mode: offline, singleplayer only";
        else if (mw == null || mw.account() == null) sub = "Sign in to play";
        else if (mw.running()) sub = "Minecraft " + mw.version().id + " is running";
        else {
            long week = Playtime.totalSeconds(null, System.currentTimeMillis() - 7L * 86_400_000L);
            sub = "Welcome back, " + mw.account().name + (week > 0 ? "  —  " + Playtime.format(week) + " played this week" : "");
        }
        Theme.center(g, sub, Theme.font(Theme.REGULAR, 14f), Theme.mix(Theme.SOFT, Theme.TEXT, 0.35), 0, cy + 132, w, 24);
        Theme.center(g, BuildInfo.NAME + " " + BuildInfo.VERSION, Theme.font(Theme.REGULAR, 12f), Theme.SOFT, 0, h - 28, w, 20);
        g.dispose();
    }

    /** The one loud element: a white pill with a slow ring on hover. */
    private final class LaunchButton extends Components.Interactive {
        private static final long SWEEP = 900, EVERY = 5200;
        private final long born = System.currentTimeMillis();
        /** A soft light sweeps across the button every few seconds; only runs while it's on screen. */
        private boolean sweeping;
        private final Timer sweep = new Timer(1000 / 50, e -> {
            MainWindow mw = MainWindow.get();
            boolean awake = mw != null && mw.frame.getState() != Frame.ICONIFIED && !mw.running();
            boolean now = awake && isShowing() && dev.cobra.launcher.ui.Anim.enabled && sweepPhase() >= 0;
            if (now || sweeping) repaint();      // idle between sweeps: no repaints at all
            sweeping = now;
        });

        LaunchButton() {
            onClick(() -> MainWindow.get().launch());
            sweep.start();
        }

        private double sweepPhase() {
            long t = (System.currentTimeMillis() - born) % EVERY;
            return t < SWEEP ? t / (double) SWEEP : -1;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            MainWindow mw = MainWindow.get();
            boolean busy = mw != null && mw.running();
            int w = getWidth(), h = getHeight();
            double hv = hover.get();
            double inset = 4 - 4 * hv;
            Theme.stroke(g, inset, inset, w - inset * 2, h - inset * 2, h / 2.0, Theme.alpha(Theme.ACCENT, 0.3 * hv), 1.5f);
            double p = pressed ? 1.5 : 0;
            Color fill = busy ? Theme.RAISED : Theme.mix(Theme.ACCENT, Theme.SOFT, pressed ? 0.25 : 0);
            Theme.fill(g, 6 + p, 6 + p, w - 12 - p * 2, h - 12 - p * 2, (h - 12) / 2.0, fill);
            double sp = busy || !dev.cobra.launcher.ui.Anim.enabled ? -1 : sweepPhase();
            if (sp >= 0) {
                double e = sp < 0.5 ? 4 * sp * sp * sp : 1 - Math.pow(-2 * sp + 2, 3) / 2;
                double cx = -w * 0.4 + e * w * 1.8, band = w * 0.28;
                Graphics2D gs = (Graphics2D) g.create();
                gs.clip(new java.awt.geom.RoundRectangle2D.Double(6 + p, 6 + p, w - 12 - p * 2, h - 12 - p * 2, h - 12, h - 12));
                Color edge = Theme.isLight() ? new Color(255, 255, 255, 0) : new Color(120, 120, 128, 0);
                Color mid = Theme.isLight() ? new Color(255, 255, 255, 70) : new Color(120, 120, 128, 60);
                gs.setPaint(new LinearGradientPaint((float) (cx - band), 0, (float) (cx + band), (float) (h * 0.35),
                        new float[]{0f, 0.5f, 1f}, new Color[]{edge, mid, edge}));
                gs.fillRect(0, 0, w, h);
                gs.dispose();
            }
            Color fg = busy ? Theme.SOFT : Theme.ON_ACCENT;
            String label = busy ? "Playing" : "Launch";
            Font f = Theme.tracked(Theme.BOLD, 18f, 0.04f);
            int tw = Theme.width(g, label, f);
            double x = (w - tw - 26) / 2.0;
            Icons.paint(g, busy ? "clock" : "play", x, (h - 18) / 2.0, 18, fg);
            Theme.left(g, label, f, fg, x + 26, 0, h);
            g.dispose();
        }
    }

    private final class VersionChip extends Components.Interactive {
        VersionChip() {
            onClick(this::openMenu);
        }

        private void openMenu() {   // one version now: the chip opens the profile (name, picture)
            MainWindow.get().editProfile(dev.cobra.launcher.core.Profiles.current());
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            MainWindow mw = MainWindow.get();
            GameVersion v = mw == null ? GameVersion.MODERN : mw.version();
            int w = getWidth(), h = getHeight();
            double hv = hover.get();
            Theme.surface(g, this, 6, 0, w - 12, h, 16, hv, 0.2);
            dev.cobra.launcher.core.Profiles.Profile prof = dev.cobra.launcher.core.Profiles.current();
            ProfileArt.draw(g, dev.cobra.launcher.core.Profiles.icon(prof), prof.name, 16, (h - 30) / 2.0, 30, false);
            Font nf = Theme.font(Theme.MEDIUM, 14f);
            g.setFont(nf);
            String pn = Theme.ellipsize(prof.name, g.getFontMetrics(), 110);
            Theme.left(g, pn, nf, Theme.TEXT, 56, 0, h);
            int nw = Theme.width(g, pn, nf);
            Theme.left(g, v.id, Theme.font(Theme.BOLD, 14f), Theme.SOFT, 56 + nw + 10, 0, h);
            Icons.paint(g, "settings", w - 44, (h - 18) / 2.0, 18, Theme.mix(Theme.SOFT, Theme.TEXT, hv));
            g.dispose();
        }
    }
}

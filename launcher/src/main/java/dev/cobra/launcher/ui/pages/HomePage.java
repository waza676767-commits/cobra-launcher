package dev.cobra.launcher.ui.pages;

import dev.cobra.launcher.core.Profiles;
import dev.cobra.launcher.core.Settings;
import dev.cobra.launcher.game.GameVersion;
import dev.cobra.launcher.game.Playtime;
import dev.cobra.launcher.ui.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Home. You (your real skin, turning slowly) in the middle with a soft light behind, a greeting
 * top-left, and underneath a player-style Launch widget: your profile like a song (art, name,
 * version), a progress bar, and the controls: previous profile, Play, next profile, version.
 * On the right, two quiet cards: quick switches and your profiles.
 */
public final class HomePage extends Page {
    private static final int SIDE = 250, GAP = 14;

    private final SkinView me = new SkinView();
    private final Player player = new Player();
    private final QuickCard quick = new QuickCard();
    private final ProfilesCard profiles = new ProfilesCard();

    public HomePage() {
        setLayout(null);
        add(me);
        add(player);
        add(quick);
        add(profiles);
    }

    @Override public String title() { return "Home"; }
    @Override public String icon() { return "home"; }

    @Override
    public void onShow() {
        me.reload();
        profiles.repaint();
        repaint();
    }

    @Override
    public void doLayout() {
        int w = getWidth(), h = getHeight();
        boolean side = w > 780;
        int mainW = side ? w - SIDE - GAP : w;
        int top = 60;
        // right column: quick switches and profiles
        if (side) {
            quick.setVisible(true);
            profiles.setVisible(true);
            int qh = 46 + 6 * 34 + 10;
            quick.setBounds(w - SIDE, top, SIDE, qh);
            profiles.setBounds(w - SIDE, top + qh + GAP, SIDE, h - top - qh - GAP);
        } else {
            quick.setVisible(false);
            profiles.setVisible(false);
        }
        // the player widget at the bottom middle, you above it
        int pw = Math.min(520, mainW - 40), ph = 164;
        player.setBounds((mainW - pw) / 2, h - ph - 6, pw, ph);
        int sh = Math.max(140, Math.min(360, h - ph - 6 - 120));
        int sw = (int) (sh * 0.62);
        me.setBounds((mainW - sw) / 2, h - ph - 6 - 16 - sh, sw, sh);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.aa(g0.create());
        int w = getWidth(), h = getHeight();
        int mainW = w > 780 ? w - SIDE - GAP : w;
        // a soft light behind you
        Rectangle m = me.getBounds();
        g.setPaint(new RadialGradientPaint(new Point2D.Double(m.getCenterX(), m.getCenterY() + m.height * 0.1), (float) (m.height * 0.75),
                new float[]{0f, 1f}, new Color[]{new Color(120, 170, 220, 46), new Color(120, 170, 220, 0)}));
        g.fill(new Rectangle(0, 0, mainW, h));
        // the greeting, top left
        MainWindow mw = MainWindow.get();
        int hour = LocalTime.now().getHour();
        String hello = hour < 5 ? "Good night" : hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
        String name = mw != null && mw.account() != null ? mw.account().name : "player";
        Settings cs = Settings.get();
        if (cs.homeGreeting != null && !cs.homeGreeting.isBlank()) {
            hello = cs.homeGreeting.trim();
            name = "";
        }
        Theme.left(g, hello + (name.isEmpty() ? "" : ","), Theme.font(Theme.MEDIUM, 15f), Theme.MUTED, 8, 4, 22);
        if (!name.isEmpty()) Theme.left(g, name, Theme.font(Theme.BOLD, 30f), Theme.TEXT, 6, 26, 40);
        long week = Playtime.totalSeconds(null, System.currentTimeMillis() - 7L * 86_400_000L);
        String sub = mw != null && mw.running() ? "Minecraft is running" : week > 0 ? Playtime.format(week) + " played this week" : "Ready when you are";
        Theme.left(g, sub, Theme.font(Theme.REGULAR, 13f), Theme.MUTED, 8, 66, 20);
        g.dispose();
    }

    // ------------------------------------------------------------------ the Launch widget

    /** Like a music player: the profile is the "song", Play launches it, ◀◀ / ▶▶ switch profile. */
    private final class Player extends JComponent {
        private int hover = -1;                  // 0 prev, 1 play, 2 next, 3 version, 4 art/name
        private final Anim.Tween press = new Anim.Tween(this, 0).rate(20);
        private final Timer bars = new Timer(80, e -> { if (isShowing()) repaint(); });

        Player() {
            setOpaque(false);
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int h = hit(e.getPoint());
                    if (h != hover) {
                        hover = h;
                        setCursor(Cursor.getPredefinedCursor(h >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                        repaint();
                    }
                }

                @Override public void mouseExited(MouseEvent e) {
                    hover = -1;
                    repaint();
                }

                @Override public void mousePressed(MouseEvent e) {
                    if (hit(e.getPoint()) == 1) press.to(1);
                }

                @Override public void mouseReleased(MouseEvent e) {
                    press.to(0);
                }

                @Override public void mouseClicked(MouseEvent e) {
                    int h = hit(e.getPoint());
                    MainWindow mw = MainWindow.get();
                    if (h == 1) mw.launch();
                    else if (h == 0 || h == 2) step(h == 0 ? -1 : 1);
                    else if (h == 3) VersionMenu.open(Player.this, 320, () -> repaint());
                    else if (h == 4) for (int i = 0; i < mw.pageCount(); i++) if (mw.pageTitle(i).equals("Profiles")) mw.showPage(i);
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        @Override public void addNotify() { super.addNotify(); bars.start(); }
        @Override public void removeNotify() { bars.stop(); super.removeNotify(); }

        private void step(int dir) {
            List<Profiles.Profile> all = Profiles.all();
            if (all.size() < 2) return;
            int i = 0;
            for (int k = 0; k < all.size(); k++) if (all.get(k).id.equals(Profiles.current().id)) i = k;
            MainWindow.get().selectProfile(all.get(Math.floorMod(i + dir, all.size())));
            HomePage.this.repaint();
        }

        private Rectangle btn(int i) {
            int w = getWidth(), cy = getHeight() - 42;
            return switch (i) {
                case 0 -> new Rectangle(w / 2 - 110, cy - 22, 52, 44);
                case 1 -> new Rectangle(w / 2 - 28, cy - 28, 56, 56);
                case 2 -> new Rectangle(w / 2 + 58, cy - 22, 52, 44);
                case 3 -> new Rectangle(w - 64, cy - 20, 44, 40);
                default -> new Rectangle(20, 18, w - 110, 64);
            };
        }

        private int hit(Point p) {
            for (int i = 0; i < 5; i++) if (btn(i).contains(p)) return i;
            return -1;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            int w = getWidth(), h = getHeight();
            MainWindow mw = MainWindow.get();
            boolean running = mw != null && mw.running();
            // the widget: deep black, very round, with a faint top light
            g.setColor(new Color(0, 0, 0, 110));
            g.fill(new java.awt.geom.RoundRectangle2D.Double(0, 6, w, h - 6, 64, 64));
            Theme.fill(g, 0, 0, w, h - 6, 32, new GradientPaint(0, 0, new Color(18, 18, 20, 245), 0, h, new Color(4, 4, 5, 245)));
            Theme.stroke(g, 0, 0, w, h - 6, 32, new Color(255, 255, 255, 16), 1f);
            // the "song": profile art, name, version
            Profiles.Profile p = Profiles.current();
            ProfileArt.draw(g, Profiles.icon(p), p.name, 22, 20, 60, true);
            g.setFont(Theme.font(Theme.BOLD, 17f));
            Theme.left(g, Theme.ellipsize(p.name, g.getFontMetrics(), w - 190), Theme.font(Theme.BOLD, 17f), Theme.TEXT, 98, 26, 24);
            GameVersion v = mw == null ? GameVersion.MODERN : mw.version();
            Theme.left(g, v.id + (v.hasCobra() ? "  ·  Abyss Client" : "  ·  " + v.loaderName), Theme.font(Theme.MEDIUM, 13.5f), Theme.MUTED, 98, 50, 20);
            // the little equaliser: moves while Minecraft runs
            double t = System.currentTimeMillis() / 140.0;
            for (int i = 0; i < 5; i++) {
                double amp = running ? 0.35 + 0.65 * Math.abs(Math.sin(t * (0.7 + i * 0.23) + i)) : 0.3 + 0.12 * i % 3 * 0.2;
                double bh = 6 + 16 * amp;
                Theme.fill(g, w - 64 + i * 6, 46 - bh / 2, 3, bh, 1.5, Theme.mix(Theme.ACCENT, new Color(255, 140, 120), 0.35 + i * 0.1));
            }
            // progress row: this week's play time
            long week = Playtime.totalSeconds(null, System.currentTimeMillis() - 7L * 86_400_000L);
            double frac = Math.min(1, week / (10 * 3600.0));
            Font small = Theme.font(Theme.MEDIUM, 11.5f);
            String left = Playtime.format(week), right = running ? "playing" : "this week";
            int ly = 90;
            Theme.left(g, left, small, Theme.MUTED, 22, ly - 8, 16);
            int rw = Theme.width(g, right, small);
            Theme.left(g, right, small, Theme.MUTED, w - 22 - rw, ly - 8, 16);
            int bx = 22 + Theme.width(g, left, small) + 12, bw = w - 22 - rw - 12 - bx;
            Theme.fill(g, bx, ly - 2, bw, 5, 2.5, new Color(255, 255, 255, 34));
            Theme.fill(g, bx, ly - 2, Math.max(5, bw * frac), 5, 2.5, new Color(255, 255, 255, 150));
            // controls
            paintSkip(g, btn(0), -1, hover == 0);
            Rectangle pr = btn(1);
            double s = 1 - 0.08 * press.get() + (hover == 1 ? 0.04 : 0);
            double d = pr.width * s, px = pr.getCenterX() - d / 2, py = pr.getCenterY() - d / 2;
            g.setColor(Color.WHITE);
            if (running) {                                               // two bars like "pause" while it runs
                Theme.fill(g, pr.getCenterX() - 12, pr.getCenterY() - 16, 9, 32, 3, Color.WHITE);
                Theme.fill(g, pr.getCenterX() + 3, pr.getCenterY() - 16, 9, 32, 3, Color.WHITE);
            } else {
                Path2D tri = new Path2D.Double();
                tri.moveTo(px + d * 0.30, py + d * 0.18);
                tri.lineTo(px + d * 0.84, py + d * 0.5);
                tri.lineTo(px + d * 0.30, py + d * 0.82);
                tri.closePath();
                g.fill(tri);
            }
            paintSkip(g, btn(2), 1, hover == 2);
            Rectangle vr = btn(3);
            if (hover == 3) Theme.fill(g, vr.x, vr.y, vr.width, vr.height, 12, new Color(255, 255, 255, 20));
            Icons.paint(g, "packs", vr.getCenterX() - 10, vr.getCenterY() - 10, 20, Theme.alpha(Theme.TEXT, hover == 3 ? 1 : 0.7));
            g.dispose();
        }

        /** ◀◀ or ▶▶ (two solid triangles). */
        private void paintSkip(Graphics2D g, Rectangle r, int dir, boolean hov) {
            g.setColor(Theme.alpha(Color.WHITE, hov ? 1 : 0.85));
            double cx = r.getCenterX(), cy = r.getCenterY(), s = 13;
            for (int k = 0; k < 2; k++) {
                double ox = cx + (k == 0 ? -s : 0) * dir * -1 - (dir < 0 ? s : 0);
                Path2D t = new Path2D.Double();
                if (dir > 0) {
                    t.moveTo(ox, cy - s * 0.8);
                    t.lineTo(ox + s, cy);
                    t.lineTo(ox, cy + s * 0.8);
                } else {
                    t.moveTo(ox + s, cy - s * 0.8);
                    t.lineTo(ox, cy);
                    t.lineTo(ox + s, cy + s * 0.8);
                }
                t.closePath();
                g.fill(t);
            }
        }
    }

    // ------------------------------------------------------------------ right column cards

    /** A quiet card (image-style): dark, rounded, a title. */
    private static void card(Graphics2D g, JComponent c, int w, int h, String title) {
        Theme.surface(g, c, 0, 0, w, h, 20, 0, 0.2);
        Theme.left(g, title, Theme.font(Theme.BOLD, 14f), Theme.TEXT, 18, 12, 24);
    }

    private record Chip(String name, String icon, BooleanSupplier on, Runnable flip) {}

    /** Quick switches as round-cornered chips; white = on. */
    private final class QuickCard extends JComponent {
        private int hover = -1;

        QuickCard() {
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int h = at(e.getPoint());
                    if (h != hover) {
                        hover = h;
                        setCursor(Cursor.getPredefinedCursor(h >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                        repaint();
                    }
                }

                @Override public void mouseExited(MouseEvent e) {
                    hover = -1;
                    repaint();
                }

                @Override public void mouseClicked(MouseEvent e) {
                    int h = at(e.getPoint());
                    if (h >= 0) {
                        chips().get(h).flip().run();
                        repaint();
                    }
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        private List<Chip> chips() {
            Settings s = Settings.get();
            MainWindow mw = MainWindow.get();
            List<Chip> c = new ArrayList<>();
            c.add(new Chip("Animations", "sparkle", () -> s.animations, () -> mw.setAnimations(!s.animations)));
            c.add(new Chip("Discord", "chat", () -> s.discordRpc, () -> {
                s.discordRpc = !s.discordRpc;
                s.save();
                dev.cobra.launcher.core.DiscordPresence.refresh();
            }));
            c.add(new Chip("Keep open", "pin", () -> s.keepOpen, () -> { s.keepOpen = !s.keepOpen; s.save(); }));
            c.add(new Chip("Fullscreen", "aperture", () -> s.fullscreen, () -> { s.fullscreen = !s.fullscreen; s.save(); }));
            c.add(new Chip("Vulkan", "flame", () -> s.superOptimization, () -> mw.setSuperOptimization(!s.superOptimization)));
            c.add(new Chip("Lite mode", "gauge", () -> s.moreOptimization, () -> mw.setMoreOptimization(!s.moreOptimization)));
            return c;
        }

        private Rectangle chip(int i) {
            return new Rectangle(10, 44 + i * 34, getWidth() - 20, 32);
        }

        private int at(Point p) {
            for (int i = 0; i < 6; i++) if (chip(i).contains(p)) return i;
            return -1;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            card(g, this, getWidth(), getHeight(), "Quick");
            List<Chip> cs = chips();
            for (int i = 0; i < cs.size(); i++) {
                Rectangle r = chip(i);
                boolean on = cs.get(i).on().getAsBoolean(), h = i == hover;
                if (h) Theme.fill(g, r.x, r.y, r.width, r.height, 10, new Color(255, 255, 255, 12));
                Icons.paint(g, cs.get(i).icon(), r.x + 10, r.y + 8, 16, Theme.alpha(Theme.TEXT, on ? 1 : 0.6));
                Theme.left(g, cs.get(i).name(), Theme.font(Theme.MEDIUM, 13f), Theme.alpha(Theme.TEXT, on ? 1 : 0.75), r.x + 36, r.y, r.height);
                // a small switch on the right
                int sx = r.x + r.width - 40, sy = r.y + 8;
                Theme.fill(g, sx, sy, 30, 16, 8, on ? Color.WHITE : new Color(255, 255, 255, 40));
                g.setColor(on ? new Color(0x111114) : Color.WHITE);
                g.fill(new Ellipse2D.Double(on ? sx + 16 : sx + 2, sy + 2, 12, 12));
            }
            g.dispose();
        }
    }

    /** Your profiles: click one to use it; the current one is marked. */
    private final class ProfilesCard extends JComponent {
        private int hover = -1;

        ProfilesCard() {
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int h = at(e.getPoint());
                    if (h != hover) {
                        hover = h;
                        setCursor(Cursor.getPredefinedCursor(h != -1 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                        repaint();
                    }
                }

                @Override public void mouseExited(MouseEvent e) {
                    hover = -1;
                    repaint();
                }

                @Override public void mouseClicked(MouseEvent e) {
                    int h = at(e.getPoint());
                    MainWindow mw = MainWindow.get();
                    if (h == -2) {
                        for (int i = 0; i < mw.pageCount(); i++) if (mw.pageTitle(i).equals("Profiles")) mw.showPage(i);
                    } else if (h >= 0) {
                        mw.selectProfile(Profiles.all().get(h));
                        HomePage.this.repaint();
                    }
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        private Rectangle row(int i) { return new Rectangle(10, 46 + i * 54, getWidth() - 20, 50); }

        private int at(Point p) {
            if (p.y < 40 && p.x > getWidth() - 70) return -2;
            List<Profiles.Profile> all = Profiles.all();
            for (int i = 0; i < all.size(); i++) {
                Rectangle r = row(i);
                if (r.y + r.height > getHeight() - 8) break;
                if (r.contains(p)) return i;
            }
            return -1;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            int w = getWidth(), h = getHeight();
            card(g, this, w, h, "Profiles");
            Font f = Theme.font(Theme.MEDIUM, 12f);
            Theme.left(g, "All", f, hover == -2 ? Theme.TEXT : Theme.MUTED, w - 18 - Theme.width(g, "All", f) - 14, 12, 24);
            Icons.paint(g, "chevron-right", w - 30, 18, 12, hover == -2 ? Theme.TEXT : Theme.MUTED);
            List<Profiles.Profile> all = Profiles.all();
            String cur = Profiles.current().id;
            for (int i = 0; i < all.size(); i++) {
                Rectangle r = row(i);
                if (r.y + r.height > h - 8) break;
                Profiles.Profile p = all.get(i);
                boolean sel = p.id.equals(cur), hov = i == hover;
                if (sel || hov) Theme.fill(g, r.x, r.y, r.width, r.height, 14, new Color(255, 255, 255, sel ? 22 : 12));
                ProfileArt.draw(g, Profiles.icon(p), p.name, r.x + 8, r.y + 7, 36, true);
                g.setFont(Theme.font(Theme.BOLD, 13f));
                Theme.left(g, Theme.ellipsize(p.name, g.getFontMetrics(), r.width - 80), Theme.font(Theme.BOLD, 13f), Theme.TEXT, r.x + 54, r.y + 6, 20);
                Theme.left(g, p.gameVersion().id, Theme.font(Theme.REGULAR, 11.5f), Theme.MUTED, r.x + 54, r.y + 25, 18);
                if (sel) Icons.paint(g, "check", r.x + r.width - 26, r.y + 17, 16, Theme.TEXT);
            }
            g.dispose();
        }
    }
}

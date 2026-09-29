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
    private final ProfileStack stack = new ProfileStack();
    private final SkinsCard skins = new SkinsCard();
    private final QuickCard quick = new QuickCard();
    private final Notch notch = new Notch();
    private final Components.Button restore = new Components.Button("Show cards", "plus", Components.Variant.SUBTLE, this::askRestore);
    /** Edit mode: every card shows its \u00d7 (like arranging widgets on a phone). */
    private boolean editing;
    private final Components.Button edit = new Components.Button("Edit", "settings", Components.Variant.SUBTLE, () -> {
        editing = !editing;
        HomePage.this.edit.setText(editing ? "Done" : "Edit");
        if (editing) HomePage.this.wiggle.start();
        else HomePage.this.wiggle.stop();
        repaint();
    });
    private final Timer relayout = new Timer(15, e -> {
        doLayout();
        repaint();
        if (!stack.moving() && !skins.moving() && !quick.moving()) ((Timer) e.getSource()).stop();
    });

    /** While editing, the cards wiggle gently (phone home-screen style). */
    private final Timer wiggle = new Timer(33, e -> {
        stack.repaint();
        skins.repaint();
        quick.repaint();
    });

    public HomePage() {
        add(restore);
        add(edit);
        add(launch);
        add(stack);
        add(skins);
        add(quick);
        add(notch);
        SwingUtilities.invokeLater(() -> MainWindow.get().onStateChange(() -> {
            stack.repaint();
            repaint();
        }));
    }

    java.util.List<String> hiddenCards() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (Card c : new Card[]{stack, skins, quick}) if (c.hidden()) out.add(c.name);
        return out;
    }

    /** "Show cards": bring back one hidden card, or all of them. */
    private void askRestore() {
        java.util.List<Card> hidden = new java.util.ArrayList<>();
        for (Card c : new Card[]{stack, skins, quick}) if (c.hidden()) hidden.add(c);
        if (hidden.isEmpty()) return;
        java.util.List<String> options = new java.util.ArrayList<>();
        for (Card c : hidden) options.add("Show " + c.name);
        if (hidden.size() > 1) options.add("Show all");
        MainWindow.get().ask("Home cards", "Pick a card to bring back. Hide one again with the \u00d7 on its corner.", options,
                options.size() - 1, i -> {
                    if (i < hidden.size()) hidden.get(i).setHidden(false);
                    else for (Card c : hidden) c.setHidden(false);
                });
    }

    /**
     * A Home card you can hide (the \u00d7 in its corner, shown on hover) and bring back ("Show cards").
     * Hiding shrinks and fades it away while the other cards grow into the space.
     */
    private abstract class Card extends JComponent {
        final String name, key;
        private final dev.cobra.launcher.ui.Anim.Tween visible;
        private boolean inside, onX;

        Card(String name, String key) {
            this.name = name;
            this.key = key;
            visible = new dev.cobra.launcher.ui.Anim.Tween(this, hidden() ? 0 : 1);
            enableEvents(java.awt.AWTEvent.MOUSE_EVENT_MASK | java.awt.AWTEvent.MOUSE_MOTION_EVENT_MASK);
        }

        boolean hidden() { return dev.cobra.launcher.core.Settings.get().homeHidden.contains(key); }

        double vis() { return visible.get(); }

        boolean moving() { return Math.abs(visible.get() - visible.target()) > 0.001; }

        void setHidden(boolean h) {
            var s = dev.cobra.launcher.core.Settings.get();
            if (h) s.homeHidden.add(key);
            else s.homeHidden.remove(key);
            s.save();
            visible.over(h ? 0 : 1, 260, null);
            relayout.start();
        }

        /** Where the \u00d7 goes (top right unless a card says otherwise). */
        Rectangle closeRect() { return new Rectangle(getWidth() - 36, 12, 24, 24); }

        @Override
        protected void processMouseEvent(java.awt.event.MouseEvent e) {
            if (e.getID() == java.awt.event.MouseEvent.MOUSE_ENTERED) inside = true;
            if (e.getID() == java.awt.event.MouseEvent.MOUSE_EXITED) inside = false;
            boolean x = closeRect().contains(e.getPoint());
            if (x && (e.getID() == java.awt.event.MouseEvent.MOUSE_CLICKED || e.getID() == java.awt.event.MouseEvent.MOUSE_PRESSED
                    || e.getID() == java.awt.event.MouseEvent.MOUSE_RELEASED)) {
                if (e.getID() == java.awt.event.MouseEvent.MOUSE_CLICKED) setHidden(true);
                return;                                          // the card itself doesn't get this click
            }
            super.processMouseEvent(e);
            repaint();
        }

        @Override
        protected void processMouseMotionEvent(java.awt.event.MouseEvent e) {
            boolean x = closeRect().contains(e.getPoint());
            if (x != onX) {
                onX = x;
                repaint();
            }
            super.processMouseMotionEvent(e);
        }

        @Override
        public void paint(Graphics g0) {
            double t = vis();
            if (t <= 0.01 || getWidth() < 4 || getHeight() < 4) return;
            Graphics2D g = (Graphics2D) g0.create();
            if (editing && t > 0.99 && Anim.enabled) {          // wiggle
                double a = Math.sin(System.currentTimeMillis() / 90.0 + key.hashCode()) * Math.toRadians(0.5);
                g.rotate(a, getWidth() / 2.0, getHeight() / 2.0);
            }
            if (t < 0.999) {                                   // closing / opening: fade and shrink to the middle
                double s = 0.9 + 0.1 * t;
                g.translate(getWidth() / 2.0, getHeight() / 2.0);
                g.scale(s, s);
                g.translate(-getWidth() / 2.0, -getHeight() / 2.0);
                g.setComposite(AlphaComposite.SrcOver.derive((float) t));
            }
            super.paint(g);
            if ((inside || editing) && t > 0.99) {             // the \u00d7 (always shown while editing)
                Rectangle r = closeRect();
                Graphics2D x = Theme.aa(g);
                x.setColor(onX ? new Color(0xE5484D) : Theme.alpha(Theme.BLACK, 0.55));
                x.fill(new Ellipse2D.Double(r.x, r.y, r.width, r.height));
                Icons.paint(x, "close", r.x + 6, r.y + 6, 12, Color.WHITE);
            }
            g.dispose();
        }
    }

    @Override public String title() { return "Home"; }
    @Override public String icon() { return "home"; }

    @Override
    public void onShow() {
        skins.reload();
        repaint();
    }

    // ----------------------------------------------------------------- layout (image: a dashboard)
    private static final int GAP = 12, RIGHT = 380, R = 30;
    /** The hero starts a little in, so the round notch on its left edge fits inside the page. */
    private static final int HX = 26;

    /** Right column width: shrinks away when both of its cards are hidden. */
    private int rightW() {
        return (int) Math.round((RIGHT + GAP) * Math.max(stack.vis(), skins.vis()));
    }

    private int leftW() { return getWidth() - rightW(); }

    /** The quick-settings card sits in the hero's bottom-right corner (shrinks into it when hidden). */
    private Rectangle quickRect() {
        int lw = leftW(), h = getHeight();
        double t = quick.vis();
        int qw = (int) (lw * 0.54 * t), qh = (int) (Math.max(170, h * 0.33) * t);
        return new Rectangle(lw - qw, h - qh, qw, qh);
    }

    /** Hero: the whole left column minus the quick card (an L shape), with a round bite for the notch. */
    private java.awt.geom.Area heroShape() {
        int lw = leftW(), h = getHeight();
        Rectangle q = quickRect();
        java.awt.geom.Area a = new java.awt.geom.Area(new java.awt.geom.RoundRectangle2D.Double(HX, 0, lw - HX, h, R * 2, R * 2));
        if (q.width > 2 && q.height > 2) {
            a.subtract(new java.awt.geom.Area(new java.awt.geom.RoundRectangle2D.Double(q.x - GAP, q.y - GAP, q.width + GAP + 40, q.height + GAP + 40, (R + GAP) * 2, (R + GAP) * 2)));
        }
        // the title pill (top right) keeps its own corner when the right column is gone
        int pill = 380;
        a.subtract(new java.awt.geom.Area(new java.awt.geom.RoundRectangle2D.Double(getWidth() - pill - GAP, -40, pill + GAP + 40, 48 + GAP + 40, (24 + GAP) * 2, (24 + GAP) * 2)));
        int ny = notchY();
        a.subtract(new java.awt.geom.Area(new Ellipse2D.Double(HX - 32, ny - 32, 64, 64)));
        return a;
    }

    private int notchY() { return (int) (getHeight() * 0.5); }

    @Override
    public void doLayout() {
        boolean old = MainWindow.classic();
        for (JComponent c : new JComponent[]{stack, skins, quick, notch, edit}) c.setVisible(!old);
        if (old) {                                            // the previous Home: logo, title and Launch in the middle
            restore.setVisible(false);
            int bw = 300;
            launch.setBounds((getWidth() - bw) / 2, (int) (getHeight() * 0.6), bw, 56);
            return;
        }
        int w = getWidth(), h = getHeight(), lw = leftW();
        Rectangle q = quickRect();
        quick.setBounds(q);
        // the Launch pill grows into the space when the quick card goes away (animated with it)
        double qt = quick.vis();
        int small = Math.max(200, Math.min(330, (q.width > 2 ? q.x - GAP : lw) - HX - 32));
        int big = Math.max(small, Math.min(560, lw - HX - 32));
        int lwid = (int) Math.round(small + (big - small) * (1 - qt));
        int lh = (int) Math.round(52 + 8 * (1 - qt));
        launch.setBounds(HX + 16, h - 16 - lh, lwid, lh);
        int rx = w - rightW() + GAP, top = 48 + GAP, avail = h - top;
        double wp = 0.56 * stack.vis(), ws = 0.44 * skins.vis();
        int stackH = wp + ws <= 0 ? 0 : (int) ((avail - GAP * Math.min(1, Math.min(stack.vis(), skins.vis()) * 2)) * wp / (wp + ws));
        stack.setBounds(rx, top, RIGHT, stackH);
        skins.setBounds(rx, top + stackH + (stackH > 0 ? GAP : 0), RIGHT, Math.max(0, h - top - stackH - (stackH > 0 ? GAP : 0)));
        notch.setBounds(HX - 24, notchY() - 24, 48, 48);
        edit.setBounds(HX + 18, 40, 92, 30);
        restore.setVisible(!hiddenCards().isEmpty());
        restore.setBounds(HX + 18 + 100, 40, 136, 30);
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.aa(g0.create());
        if (MainWindow.classic()) {
            paintClassic(g);
            g.dispose();
            return;
        }
        int lw = leftW(), h = getHeight();
        java.awt.geom.Area hero = heroShape();
        // the hero shows your wallpaper, cut to its shape with smooth edges
        paintHero(g, hero, lw, h);
        // title: logo, wordmark and a line about you
        double cy = h * 0.3;
        lw = lw + HX;                                        // centre the title on the hero itself
        Theme.logo(g, lw / 2.0, cy - 10, 96, 1);
        Color shade = new Color(0, 0, 0, 90);
        Theme.center(g, "COBRA", Theme.tracked(Theme.BOLD, 40f, 0.3f), shade, 0, cy + 50, lw, 52);
        Theme.center(g, "COBRA", Theme.tracked(Theme.BOLD, 40f, 0.3f), Color.WHITE, 0, cy + 48, lw, 52);
        MainWindow mw = MainWindow.get();
        String sub;
        if (mw != null && mw.running()) sub = "Minecraft is running";
        else if (mw != null && mw.account() != null) {
            long week = Playtime.totalSeconds(null, System.currentTimeMillis() - 7L * 86_400_000L);
            sub = "Welcome back, " + mw.account().name + (week > 0 ? "  —  " + Playtime.format(week) + " played this week" : "");
        } else sub = "Sign in to play";
        Theme.center(g, sub, Theme.font(Theme.REGULAR, 14f), new Color(255, 255, 255, 200), 0, cy + 100, lw, 22);
        Theme.left(g, BuildInfo.NAME + " " + BuildInfo.VERSION, Theme.font(Theme.REGULAR, 11.5f), new Color(255, 255, 255, 140), HX + 22, 16, 20);
        g.dispose();
    }

    /** The previous Home look: centred logo, wordmark and a line about you. */
    private void paintClassic(Graphics2D g) {
        int w = getWidth(), h = getHeight();
        double cy = h * 0.25;
        if (Wallpaper.active()) {
            Color pool = Theme.isLight() ? new Color(255, 255, 255, 150) : new Color(0, 0, 0, 125);
            Color none = Theme.isLight() ? new Color(255, 255, 255, 0) : new Color(0, 0, 0, 0);
            double ry = h * 0.46, rx = Math.min(w * 0.47, 420);
            Graphics2D gp = (Graphics2D) g.create();
            gp.translate(w / 2.0, h * 0.47);
            gp.scale(rx / ry, 1);
            gp.setPaint(new RadialGradientPaint(new java.awt.geom.Point2D.Double(0, 0), (float) ry, new float[]{0f, 1f}, new Color[]{pool, none}));
            gp.fill(new Ellipse2D.Double(-ry, -ry, ry * 2, ry * 2));
            gp.dispose();
        }
        Theme.logo(g, w / 2.0, cy, 128, 1);
        Theme.center(g, "COBRA", Theme.tracked(Theme.BOLD, 44f, 0.28f), Theme.TEXT, 0, cy + 72, w, 58);
        MainWindow mw = MainWindow.get();
        String sub = mw != null && mw.account() != null ? "Welcome back, " + mw.account().name : "Sign in to play";
        Theme.center(g, sub, Theme.font(Theme.REGULAR, 14f), Theme.SOFT, 0, cy + 128, w, 24);
        Theme.center(g, BuildInfo.NAME + " " + BuildInfo.VERSION, Theme.font(Theme.REGULAR, 12f), Theme.MUTED, 0, h - 28, w, 20);
    }

    private BufferedImageCache heroCache = new BufferedImageCache();

    private void paintHero(Graphics2D g, java.awt.geom.Area hero, int lw, int h) {
        java.awt.image.BufferedImage buf = heroCache.get(lw, h);
        Graphics2D b = buf.createGraphics();
        b.setComposite(AlphaComposite.Clear);
        b.fillRect(0, 0, lw, h);
        b.setComposite(AlphaComposite.SrcOver);
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        b.setColor(Color.WHITE);
        b.fill(hero);                                       // smooth mask
        b.setComposite(AlphaComposite.SrcIn);
        if (Wallpaper.active() && !Glass.lite()) {
            Wallpaper.paint(b, lw, h);
        } else {
            b.setPaint(new GradientPaint(0, 0, new Color(0x373737), lw, h, new Color(0x0A0A0A)));
            b.fillRect(0, 0, lw, h);
        }
        // darker towards the bottom so the Launch pill and the text always read
        b.setComposite(AlphaComposite.SrcAtop);
        b.setPaint(new GradientPaint(0, (float) (h * 0.45), new Color(0, 0, 0, 0), 0, h, new Color(0, 0, 0, 150)));
        b.fillRect(0, 0, lw, h);
        b.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 70), 0, (float) (h * 0.35), new Color(0, 0, 0, 0)));
        b.fillRect(0, 0, lw, h);
        b.dispose();
        g.drawImage(buf, 0, 0, null);
        // a hairline of light along the edge, like the frame
        Graphics2D e = (Graphics2D) g.create();
        e.setColor(new Color(255, 255, 255, 26));
        e.setStroke(new BasicStroke(1f));
        e.draw(hero);
        e.dispose();
    }

    /** Reused offscreen buffer for the hero (one per size). */
    private static final class BufferedImageCache {
        private java.awt.image.BufferedImage img;

        java.awt.image.BufferedImage get(int w, int h) {
            if (img == null || img.getWidth() != Math.max(1, w) || img.getHeight() != Math.max(1, h)) {
                img = new java.awt.image.BufferedImage(Math.max(1, w), Math.max(1, h), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            }
            return img;
        }
    }

    // ----------------------------------------------------------------- the notch button (your profile)

    private final class Notch extends Components.Interactive {
        Notch() {
            onClick(() -> MainWindow.get().editProfile(dev.cobra.launcher.core.Profiles.current()));
            setToolTipText("Edit this profile");
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            double hv = hover.get();
            g.setColor(Theme.mix(Theme.SOFT, Theme.TEXT, hv));
            g.fill(new Ellipse2D.Double(2, 2, 44, 44));
            var p = dev.cobra.launcher.core.Profiles.current();
            ProfileArt.draw(g, dev.cobra.launcher.core.Profiles.icon(p), p.name, 8, 8, 32, false);
            g.dispose();
        }
    }

    // ----------------------------------------------------------------- profiles: a stack of cards

    private final class ProfileStack extends Card {
        private int hover = -1;

        ProfileStack() {
            super("Profiles", "profiles");
            java.awt.event.MouseAdapter m = new java.awt.event.MouseAdapter() {
                @Override public void mouseMoved(java.awt.event.MouseEvent e) {
                    int h = at(e.getY());
                    if (h != hover) {
                        hover = h;
                        setCursor(Cursor.getPredefinedCursor(h >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                        repaint();
                    }
                }

                @Override public void mouseExited(java.awt.event.MouseEvent e) {
                    hover = -1;
                    repaint();
                }

                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    int i = at(e.getY());
                    java.util.List<dev.cobra.launcher.core.Profiles.Profile> list = shown();
                    if (i < 0 || i >= list.size()) return;
                    if (i == list.size() - 1) {           // the front card: the Profiles page
                        MainWindow mw = MainWindow.get();
                        for (int k = 0; k < mw.pageCount(); k++) if (mw.pageTitle(k).equals("Profiles")) mw.showPage(k);
                    } else {
                        MainWindow.get().selectProfile(list.get(i));
                    }
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        /** Up to three profiles, the current one last (in front). */
        java.util.List<dev.cobra.launcher.core.Profiles.Profile> shown() {
            var cur = dev.cobra.launcher.core.Profiles.current();
            java.util.List<dev.cobra.launcher.core.Profiles.Profile> others = new java.util.ArrayList<>();
            for (var p : dev.cobra.launcher.core.Profiles.all()) if (!p.id.equals(cur.id)) others.add(p);
            java.util.List<dev.cobra.launcher.core.Profiles.Profile> out = new java.util.ArrayList<>(others.subList(0, Math.min(2, others.size())));
            out.add(cur);
            return out;
        }

        private static final int STEP = 54;

        private int at(int y) {
            int n = shown().size();
            for (int i = n - 1; i >= 0; i--) if (y >= i * STEP) return i;
            return -1;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            int w = getWidth(), h = getHeight();
            var list = shown();
            int n = list.size();
            Color[] fills = {Theme.RAISED, Theme.LINE_2, Theme.SOFT};
            for (int i = 0; i < n; i++) {
                var p = list.get(i);
                boolean front = i == n - 1;
                int y = i * STEP;
                Color fill = fills[fills.length - n + i];
                if (i == hover && !front) fill = Theme.mix(fill, Theme.TEXT, 0.12);
                Theme.fill(g, 0, y, w, h - y, R, fill);
                Color ink = front ? Theme.BLACK : Theme.TEXT;
                // the little tab on the left (like a folder label)
                Theme.fill(g, 12, y + 14, 20, 26, 8, Theme.alpha(ink, front ? 0.18 : 0.14));
                g.setFont(Theme.font(Theme.MEDIUM, 14.5f));
                Theme.left(g, Theme.ellipsize(p.name, g.getFontMetrics(), w - 150), Theme.font(Theme.MEDIUM, 14.5f), ink, 44, y + 12, 30);
                Theme.left(g, p.gameVersion().id, Theme.font(Theme.REGULAR, 12f), Theme.alpha(ink, 0.6), w - 70, y + 12, 30);
                if (front) {
                    ProfileArt.draw(g, dev.cobra.launcher.core.Profiles.icon(p), p.name, 18, y + 58, 56, true);
                    Theme.left(g, "Playing this profile", Theme.font(Theme.REGULAR, 12.5f), Theme.alpha(ink, 0.6), 88, y + 60, 22);
                    Theme.left(g, p.locked() ? "FPS Boost · mods set up for you" : "Fabric · Cobra Client", Theme.font(Theme.MEDIUM, 13.5f), ink, 88, y + 82, 22);
                    Theme.left(g, hover == i ? "Open Profiles  →" : "Tap a card behind to switch", Theme.font(Theme.REGULAR, 12f),
                            Theme.alpha(ink, 0.55), 18, h - 34, 22);
                }
            }
            g.dispose();
        }
    }

    // ----------------------------------------------------------------- skins: a fan of cards

    private final class SkinsCard extends Card {
        private final java.util.List<java.awt.image.BufferedImage> skins = new java.util.ArrayList<>();
        private boolean hover;

        @Override
        Rectangle closeRect() { return new Rectangle(getWidth() - 80, 16, 24, 24); }   // left of the arrow

        SkinsCard() {
            super("Skins", "skins");
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseEntered(java.awt.event.MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(java.awt.event.MouseEvent e) { hover = false; repaint(); }

                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    MainWindow mw = MainWindow.get();
                    for (int k = 0; k < mw.pageCount(); k++) if (mw.pageTitle(k).equals("Accessories")) mw.showPage(k);
                }
            });
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        void reload() {
            skins.clear();
            for (var p : dev.cobra.launcher.core.Accessories.library(true)) {
                if (skins.size() >= 3) break;
                try {
                    var img = javax.imageio.ImageIO.read(p.toFile());
                    if (img != null) skins.add(img);
                } catch (Exception ignored) {}
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            int w = getWidth(), h = getHeight();
            Theme.fill(g, 0, 0, w, h, R, Theme.alpha(Theme.TEXT, hover ? 0.12 : 0.08));
            Theme.fill(g, 16, 16, 86, 26, 13, Theme.TEXT);
            Theme.center(g, "Skins", Theme.font(Theme.MEDIUM, 13f), Theme.BLACK, 16, 16, 86, 26);
            g.setColor(Theme.alpha(Theme.TEXT, 0.5));
            g.setStroke(new BasicStroke(1.5f));
            g.draw(new Ellipse2D.Double(w - 44, 14, 28, 28));
            Icons.paint(g, "chevron-right", w - 38, 20, 16, Theme.TEXT);
            // the fan
            int n = Math.max(3, skins.size());
            for (int i = 0; i < n; i++) {
                Graphics2D c = (Graphics2D) g.create();
                double cx = w * 0.62, cy = h + 40;
                c.rotate(Math.toRadians(-28 + i * 14), cx, cy);
                int cw = (int) (w * 0.5), ch = (int) (h * 0.95);
                int x = (int) (cx - cw / 2.0), y = (int) (cy - ch);
                Theme.fill(c, x, y, cw, ch, 24, Theme.mix(Theme.RAISED, Theme.SOFT, 0.18 + 0.2 * i));
                if (i < skins.size()) {
                    AccessoriesPage.drawSkin(c, skins.get(skins.size() - 1 - i), false, new Rectangle(x + 10, y + 12, cw - 20, (int) (ch * 0.62)));
                }
                c.dispose();
            }
            if (skins.isEmpty()) {
                Theme.left(g, "Add your skins in Accessories", Theme.font(Theme.REGULAR, 12.5f), Theme.MUTED, 16, 52, 20);
            }
            g.dispose();
        }
    }

    // ----------------------------------------------------------------- quick settings: chips

    private final class QuickCard extends Card {
        private record Chip(String label, java.util.function.BooleanSupplier on, Runnable toggle) {}

        private final java.util.List<Chip> chips = new java.util.ArrayList<>();
        private int hover = -1;

        QuickCard() {
            super("Quick settings", "quick");
            var s = dev.cobra.launcher.core.Settings.get();
            chips.add(new Chip("Animations", () -> s.animations, () -> MainWindow.get().setAnimations(!s.animations)));
            chips.add(new Chip("Discord", () -> s.discordRpc, () -> {
                s.discordRpc = !s.discordRpc;
                s.save();
                dev.cobra.launcher.core.DiscordPresence.refresh();
            }));
            chips.add(new Chip("Keep open", () -> s.keepOpen, () -> {
                s.keepOpen = !s.keepOpen;
                s.save();
            }));
            chips.add(new Chip("Clear glass", () -> "glass".equals(s.style), () -> MainWindow.get().setStyle("glass".equals(s.style) ? "solid" : "glass")));
            chips.add(new Chip("Light", () -> s.lightMode, () -> MainWindow.get().setLight(!s.lightMode)));
            chips.add(new Chip("Fullscreen", () -> s.fullscreen, () -> {
                s.fullscreen = !s.fullscreen;
                s.save();
            }));
            chips.add(new Chip("Vulkan", () -> s.superOptimization, () -> MainWindow.get().setSuperOptimization(!s.superOptimization)));
            chips.add(new Chip("Lite mode", () -> s.moreOptimization, () -> MainWindow.get().setMoreOptimization(!s.moreOptimization)));
            chips.add(new Chip("All settings", () -> false, () -> MainWindow.get().openSettings()));
            java.awt.event.MouseAdapter m = new java.awt.event.MouseAdapter() {
                @Override public void mouseMoved(java.awt.event.MouseEvent e) {
                    int h = at(e.getPoint());
                    if (h != hover) {
                        hover = h;
                        setCursor(Cursor.getPredefinedCursor(h >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                        repaint();
                    }
                }

                @Override public void mouseExited(java.awt.event.MouseEvent e) {
                    hover = -1;
                    repaint();
                }

                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    int i = at(e.getPoint());
                    if (i < 0) return;
                    chips.get(i).toggle().run();
                    repaint();
                    HomePage.this.repaint();
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        private Rectangle chip(int i) {
            int w = getWidth(), cols = 3, cw = (w - 32 - (cols - 1) * 8) / cols, ch = 30;
            int top = 54, rowGap = Math.max(8, (getHeight() - top - 20 - 3 * ch) / 3);
            return new Rectangle(16 + (i % cols) * (cw + 8), top + (i / cols) * (ch + rowGap), cw, ch);
        }

        private int at(Point p) {
            for (int i = 0; i < chips.size(); i++) if (chip(i).contains(p)) return i;
            return -1;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            int w = getWidth(), h = getHeight();
            Theme.fill(g, 0, 0, w, h, R, Theme.alpha(Theme.TEXT, 0.08));
            Theme.fill(g, 16, 16, 110, 24, 12, Theme.alpha(Theme.TEXT, 0.9));
            Theme.center(g, "Quick settings", Theme.font(Theme.MEDIUM, 12f), Theme.BLACK, 16, 16, 110, 24);
            for (int i = 0; i < chips.size(); i++) {
                Rectangle r = chip(i);
                boolean on = chips.get(i).on().getAsBoolean();
                if (on) Theme.fill(g, r.x, r.y, r.width, r.height, r.height / 2.0, Theme.TEXT);
                else {
                    Theme.fill(g, r.x, r.y, r.width, r.height, r.height / 2.0, Theme.alpha(Theme.TEXT, i == hover ? 0.12 : 0.05));
                    Theme.stroke(g, r.x + 0.5, r.y + 0.5, r.width - 1, r.height - 1, r.height / 2.0, Theme.alpha(Theme.TEXT, 0.22), 1f);
                }
                g.setFont(Theme.font(Theme.MEDIUM, 12f));
                Theme.center(g, Theme.ellipsize(chips.get(i).label(), g.getFontMetrics(), r.width - 12), Theme.font(Theme.MEDIUM, 12f),
                        on ? Theme.BLACK : Theme.TEXT, r.x, r.y, r.width, r.height);
            }
            g.dispose();
        }
    }

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
            double hv = hover.get(), p = pressed ? 1 : 0;
            // a bright pill with a dark round badge holding the Cobra mark (like the frame's search bar)
            Color pill = busy ? Theme.alpha(Theme.TEXT, 0.35) : Theme.mix(Theme.ACCENT, Color.WHITE, 0.08 * hv);
            Glass.glow(g, p, p, w - 2 * p, h - 2 * p, (h - 2 * p) / 2.0, Theme.alpha(Theme.ACCENT, 0.6), (int) Math.round(3 + 5 * hv));
            Theme.fill(g, p, p, w - 2 * p, h - 2 * p, (h - 2 * p) / 2.0, pill);
            double sp = busy || !dev.cobra.launcher.ui.Anim.enabled ? -1 : sweepPhase();
            if (sp >= 0) {                               // the occasional light sweep
                double e = sp < 0.5 ? 4 * sp * sp * sp : 1 - Math.pow(-2 * sp + 2, 3) / 2;
                double cx = -w * 0.4 + e * w * 1.8, band = w * 0.25;
                Graphics2D gs = (Graphics2D) g.create();
                gs.setPaint(new LinearGradientPaint((float) (cx - band), 0, (float) (cx + band), (float) (h * 0.4),
                        new float[]{0f, 0.5f, 1f}, new Color[]{new Color(255, 255, 255, 0), new Color(255, 255, 255, 90), new Color(255, 255, 255, 0)}));
                gs.fill(new java.awt.geom.RoundRectangle2D.Double(p, p, w - 2 * p, h - 2 * p, h - 2 * p, h - 2 * p));
                gs.dispose();
            }
            double d = h - 12 - 2 * p;
            g.setColor(Theme.ON_ACCENT);
            g.fill(new Ellipse2D.Double(6 + p, 6 + p, d, d));
            Theme.logo(g, 6 + p + d / 2, 6 + p + d / 2, d * 0.5, 1, Theme.ACCENT);
            String label = busy ? "Playing" : "Launch";
            Color ink = Theme.ON_ACCENT;
            Font f = Theme.font(Theme.BOLD, 16.5f);
            Theme.left(g, label, f, ink, 6 + d + 14, 0, h);
            GameVersion v = mw == null ? GameVersion.MODERN : mw.version();
            Font small = Theme.font(Theme.MEDIUM, 12.5f);
            int vw = Theme.width(g, v.id, small);
            Theme.left(g, v.id, small, Theme.alpha(ink, 0.55), w - vw - 22, 0, h);
            g.dispose();
        }
    }
}

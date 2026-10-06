package dev.cobra.launcher.ui.pages;

import dev.cobra.launcher.core.Accessories;
import dev.cobra.launcher.core.Settings;
import dev.cobra.launcher.ui.*;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cosmetics, right under your skins and capes: a turntable of your character wearing everything
 * you picked (drag to spin it), glow and size controls, and a card for every cosmetic with a real
 * 3D preview and its colours / styles. Only other Cobra players see them in game.
 */
final class CosmeticsGallery extends JPanel {
    private static final int HEADER = 330, CARD_H = 250, GAP = 14;

    /** One wearable: key in the code, card name, choices, which way the preview looks, and its group. */
    private record Item(String key, String name, List<String> options, double yaw, String group) {
        Item(String key, String name, List<String> options, double yaw) {
            this(key, name, options, yaw, switch (key) {
                case "wings", "wingstyle", "tail", "backpack", "katana", "scarf" -> "Back";
                case "gloves", "feet" -> "Hands & feet";
                case "particles", "trail" -> "Effects";
                default -> "Head";
            });
        }
    }

    private static final String[] GROUPS = {"All", "Back", "Head", "Hands & feet", "Effects"};
    private String group = "All";
    private final Components.Segmented groups;

    static final List<Item> ITEMS = List.of(
            new Item("wings", "Wings", List.of("Off", "Angel", "Red", "Black", "Gold", "Blue", "Purple", "Pink", "Green", "Cyan", "Custom"), 200),
            new Item("wingstyle", "Wing style", List.of("Feather", "Dragon", "Butterfly", "Demon", "Energy"), 200),
            new Item("halo", "Halo", List.of("Off", "Angel", "Red", "Custom"), 25),
            new Item("hat", "Hat", List.of("Off", "Crown", "Top hat", "Witch"), 25),
            new Item("ears", "Cat ears", List.of("Off", "Black", "White", "Ginger", "Pink", "Custom"), 25),
            new Item("bunny", "Bunny ears", List.of("Off", "White", "Pink", "Black", "Brown", "Custom"), 25),
            new Item("horns", "Horns", List.of("Off", "Red", "Black", "White", "Gold", "Custom"), 25),
            new Item("antlers", "Antlers", List.of("Off", "Brown", "White", "Gold", "Custom"), 25),
            new Item("orbit", "Orbiting gems", List.of("Off", "Purple", "Cyan", "Red", "Gold", "Green", "Custom"), 25),
            new Item("scarf", "Scarf", List.of("Off", "Red", "Blue", "Green", "White", "Black", "Custom"), 200),
            new Item("glasses", "Sunglasses", List.of("Off", "Black", "Gold", "Pink", "Custom"), 20),
            new Item("headphones", "Headphones", List.of("Off", "Black", "White", "Pink", "Blue", "Custom"), 40),
            new Item("tail", "Tail", List.of("Off", "Black", "White", "Ginger", "Pink", "Fox", "Custom"), 215),
            new Item("backpack", "Backpack", List.of("Off", "Brown", "Black", "Blue", "Red", "Custom"), 205),
            new Item("katana", "Katana", List.of("Off", "On"), 205),
            new Item("gloves", "Boxing gloves", List.of("Off", "Red", "Blue", "Black", "Custom"), 30),
            new Item("feet", "Big feet", List.of("Off", "On"), 30),
            new Item("particles", "Particles", List.of("Off", "Sparkles", "Hearts", "Flames", "Soul fire", "Snow", "Magic", "Petals", "Notes"), 200),
            new Item("trail", "Trail", List.of("Off", "On"), 120));

    private final Turntable turntable = new Turntable();
    private final Components.Toggle glow;
    private final Components.Slider size;
    private final Components.Button clear;
    private final JPanel cards = new JPanel(null);

    CosmeticsGallery() {
        super(null);
        setOpaque(false);
        Settings s = Settings.get();
        glow = new Components.Toggle(s.cosGlow, v -> {
            s.cosGlow = v;
            s.save();
            changed();
        });
        size = new Components.Slider(70, 150, 5, s.cosSize, v -> Math.round(v) + "%", v -> {
            s.cosSize = (int) Math.round(v);
            s.save();
            changed();
        });
        clear = new Components.Button("Take everything off", "close", Components.Variant.GHOST, () -> {
            for (Item it : ITEMS) if (!it.key().equals("wingstyle")) set(it.key(), "Off");
            changed();
        });
        add(turntable);
        add(glow);
        add(size);
        add(clear);
        cards.setOpaque(false);
        for (Item it : ITEMS) cards.add(new Card(it));
        add(cards);
        groups = new Components.Segmented(List.of(GROUPS), 0, i -> {
            group = GROUPS[i];
            revalidate();
            doLayout();
            repaint();
            Container p = getParent();
            while (p != null && !(p instanceof JScrollPane)) p = p.getParent();
            if (p != null) p.revalidate();
        });
        add(groups);
    }

    private List<Component> visibleCards() {
        List<Component> out = new java.util.ArrayList<>();
        for (Component c : cards.getComponents()) {
            Card k = (Card) c;
            boolean show = group.equals("All") || k.it.group().equals(group);
            k.setVisible(show);
            if (show) out.add(k);
        }
        return out;
    }

    void changed() {
        turntable.refresh();
        for (Component c : cards.getComponents()) c.repaint();
        repaint();
    }

    private int columns() { return Math.max(2, Math.min(5, (getWidth() - 40) / 200)); }

    @Override
    public Dimension getPreferredSize() {
        int w = getParent() == null ? 900 : getParent().getWidth();
        int cols = Math.max(2, Math.min(5, (w - 40) / 200));
        int n = (int) ITEMS.stream().filter(it -> group.equals("All") || it.group().equals(group)).count();
        int rows = Math.max(1, (n + cols - 1) / cols);
        return new Dimension(100, 56 + HEADER + 52 + rows * (CARD_H + GAP) + 10);
    }

    @Override
    public void doLayout() {
        int w = getWidth();
        turntable.setBounds(20, 56, 280, HEADER - 16);
        int rx = 320, rw = w - rx - 20;
        glow.setBounds(rx + rw - 50, 80, 50, 30);
        size.setBounds(rx, 150, Math.min(360, rw), 30);
        clear.setBounds(rx, 216, 200, 40);
        Dimension gd = groups.getPreferredSize();
        groups.setBounds(20, 56 + HEADER, gd.width, 38);
        int cols = columns(), cw = (w - 40 - GAP * (cols - 1)) / cols;
        List<Component> shown = visibleCards();
        int rows = Math.max(1, (shown.size() + cols - 1) / cols);
        cards.setBounds(20, 56 + HEADER + 52, w - 40, rows * (CARD_H + GAP));
        for (int i = 0; i < shown.size(); i++) shown.get(i).setBounds((i % cols) * (cw + GAP), (i / cols) * (CARD_H + GAP), cw, CARD_H);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.aa(g0.create());
        int w = getWidth(), h = getHeight();
        Theme.surface(g, this, 0, 0, w, h, 20, 0, 0.1);
        Theme.left(g, "Cosmetics", Theme.font(Theme.BOLD, 16f), Theme.TEXT, 20, 14, 26);
        Theme.left(g, "Only other Abyss players see them · applies when you launch", Theme.font(Theme.REGULAR, 12.5f), Theme.MUTED, 118, 16, 22);
        int rx = 320;
        Theme.left(g, "Glow effects", Theme.font(Theme.MEDIUM, 14f), Theme.TEXT, rx, 76, 22);
        Theme.left(g, "Wings, halo, horns and katana glow softly and shine in the dark.", Theme.font(Theme.REGULAR, 12f), Theme.MUTED, rx, 96, 18);
        Theme.left(g, "Size", Theme.font(Theme.MEDIUM, 14f), Theme.TEXT, rx, 126, 22);
        String worn = Settings.get().cosmeticsCode();
        int count = worn.isEmpty() ? 0 : (int) worn.chars().filter(ch -> ch == ',').count() + 1;
        Theme.left(g, count == 0 ? "You're not wearing anything yet: pick something below." : "Drag the character to turn it around.",
                Theme.font(Theme.REGULAR, 12.5f), Theme.MUTED, rx, 190, 20);
        g.dispose();
    }

    // ------------------------------------------------------------------ values

    static String value(String key) {
        Settings s = Settings.get();
        return switch (key) {
            case "wings" -> s.cosWings;
            case "wingstyle" -> s.cosWingStyle == null ? "Feather" : s.cosWingStyle;
            case "halo" -> s.cosHalo;
            case "hat" -> s.cosHat;
            case "ears" -> s.cosEars;
            case "bunny" -> s.cosBunny;
            case "horns" -> s.cosHorns;
            case "glasses" -> s.cosGlasses;
            case "headphones" -> s.cosHeadphones;
            case "tail" -> s.cosTailKind;
            case "backpack" -> s.cosBackpack;
            case "gloves" -> s.cosGloves;
            case "antlers" -> s.cosAntlers;
            case "orbit" -> s.cosOrbit;
            case "scarf" -> s.cosScarf;
            case "katana" -> s.cosKatana ? "On" : "Off";
            case "feet" -> s.cosFeet ? "On" : "Off";
            case "particles" -> s.cosParticles == null ? "Off" : s.cosParticles;
            case "trail" -> s.cosTrail ? "On" : "Off";
            default -> "Off";
        };
    }

    static void set(String key, String v) {
        Settings s = Settings.get();
        switch (key) {
            case "wings" -> s.cosWings = v;
            case "wingstyle" -> s.cosWingStyle = v;
            case "halo" -> s.cosHalo = v;
            case "hat" -> s.cosHat = v;
            case "ears" -> s.cosEars = v;
            case "bunny" -> s.cosBunny = v;
            case "horns" -> s.cosHorns = v;
            case "glasses" -> s.cosGlasses = v;
            case "headphones" -> s.cosHeadphones = v;
            case "tail" -> {
                s.cosTailKind = v;
                s.cosTail = false;
            }
            case "backpack" -> s.cosBackpack = v;
            case "gloves" -> s.cosGloves = v;
            case "antlers" -> s.cosAntlers = v;
            case "orbit" -> s.cosOrbit = v;
            case "scarf" -> s.cosScarf = v;
            case "katana" -> s.cosKatana = !"Off".equals(v);
            case "feet" -> s.cosFeet = !"Off".equals(v);
            case "particles" -> s.cosParticles = v;
            case "trail" -> s.cosTrail = !"Off".equals(v);
            default -> { }
        }
        s.save();
    }

    /** Short code to preview one item with value {@code v} (an example when it's Off). */
    private static String sample(Item it, String v) {
        String key = it.key(), code;
        if (key.equals("particles") || key.equals("trail")) return "wings:" + ("Off".equals(value("wings")) ? "angel" : value("wings").toLowerCase());
        if (key.equals("wingstyle")) return "wings:" + ("Off".equals(value("wings")) ? "angel" : value("wings").toLowerCase()) + ",wingstyle:" + v.toLowerCase();
        if ("Off".equals(v)) v = it.options().get(Math.min(1, it.options().size() - 1));
        if (v.equals("On")) code = key;
        else code = key + ":" + v.toLowerCase().replace(" ", "");
        if (key.equals("wings") && !"Feather".equals(value("wingstyle"))) code += ",wingstyle:" + value("wingstyle").toLowerCase();
        return code;
    }

    static Color colourOf(String v) {
        if (v != null && v.matches("x[0-9a-fA-F]{6}")) return new Color(Integer.parseInt(v.substring(1), 16));
        return switch (v) {
            case "Angel", "White" -> new Color(0xF4F4EF);
            case "Red" -> new Color(0xD62B3A);
            case "Black" -> new Color(0x2A2A2E);
            case "Gold" -> new Color(0xE8B63A);
            case "Blue" -> new Color(0x3F86E8);
            case "Purple" -> new Color(0x8B5CF6);
            case "Pink" -> new Color(0xF4A6C8);
            case "Green" -> new Color(0x34C77B);
            case "Cyan" -> new Color(0x3FD0E0);
            case "Ginger", "Fox" -> new Color(0xE08A3C);
            case "Brown" -> new Color(0x7A5234);
            default -> null;                                          // a word, not a colour
        };
    }

    /**
     * The little stage behind a preview: the theme's own colours (a soft glow up top fading into the
     * dark base), a spotlight, and a floor ellipse.
     */
    private static void stage(Graphics2D g, double x, double y, double w, double h, double r) {
        int[] p = Theme.palette();
        Color top = Theme.isLight() ? new Color(0xE9E9EE) : Theme.mix(new Color(p[11]), new Color(p[3]), 0.35);
        Color bottom = Theme.isLight() ? new Color(0xD7D7DE) : new Color(p[2]);
        Theme.fill(g, x, y, w, h, r, new GradientPaint(0, (float) y, top, 0, (float) (y + h), bottom));
        Graphics2D s = (Graphics2D) g.create();
        s.clip(new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2));
        s.setPaint(new RadialGradientPaint(new Point2D.Double(x + w / 2, y + h * 0.35), (float) (h * 0.7), new float[]{0f, 1f},
                new Color[]{new Color(255, 255, 255, Theme.isLight() ? 120 : 46), new Color(255, 255, 255, 0)}));
        s.fill(new Rectangle2D.Double(x, y, w, h));
        s.setColor(new Color(0, 0, 0, 55));
        s.fill(new Ellipse2D.Double(x + w / 2 - w * 0.22, y + h - 22, w * 0.44, 12));
        s.dispose();
    }

    /** A few particles drawn over a preview, in the effect's colours (fixed spots, so they don't jump). */
    private static void sparkle(Graphics2D g, double x, double y, double w, double h, String fx, boolean trail) {
        Color c = switch (fx == null ? "" : fx) {
            case "Hearts" -> new Color(0xFF5577);
            case "Flames" -> new Color(0xFFA53A);
            case "Soul fire" -> new Color(0x5FD8F0);
            case "Snow" -> new Color(0xF4F8FF);
            case "Magic" -> new Color(0xB565FF);
            case "Petals" -> new Color(0xFFB7D5);
            case "Notes" -> new Color(0x66E08A);
            default -> new Color(0xFFF6C9);
        };
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < 16; i++) {
            double px = x + w * (0.15 + r.nextDouble() * 0.7), py = y + h * (trail ? 0.75 + r.nextDouble() * 0.2 : 0.1 + r.nextDouble() * 0.6);
            double s = 2 + r.nextDouble() * 4;
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 60));
            g.fill(new Ellipse2D.Double(px - s, py - s, s * 2, s * 2));
            g.setColor(c);
            if ("Hearts".equals(fx)) {
                g.fill(new Ellipse2D.Double(px - s * 0.5, py - s * 0.4, s * 0.55, s * 0.55));
                g.fill(new Ellipse2D.Double(px - s * 0.05, py - s * 0.4, s * 0.55, s * 0.55));
                Path2D v = new Path2D.Double();
                v.moveTo(px - s * 0.5, py - s * 0.1);
                v.lineTo(px + s * 0.5, py - s * 0.1);
                v.lineTo(px, py + s * 0.5);
                v.closePath();
                g.fill(v);
            } else {
                Path2D st = new Path2D.Double();                       // a tiny four-point sparkle
                st.moveTo(px, py - s);
                st.lineTo(px + s * 0.25, py - s * 0.25);
                st.lineTo(px + s, py);
                st.lineTo(px + s * 0.25, py + s * 0.25);
                st.lineTo(px, py + s);
                st.lineTo(px - s * 0.25, py + s * 0.25);
                st.lineTo(px - s, py);
                st.lineTo(px - s * 0.25, py - s * 0.25);
                st.closePath();
                g.fill(st);
            }
        }
    }

    private static final Map<String, BufferedImage> CACHE = new HashMap<>();

    private static BufferedImage preview(String code, int w, int h, double yaw) {
        String key = code + "|" + w + "|" + h + "|" + (int) yaw;
        BufferedImage img = CACHE.get(key);
        if (img == null) {
            if (CACHE.size() > 120) CACHE.clear();
            img = CosmeticPreview.render(code, w, h, yaw, true, null);
            CACHE.put(key, img);
        }
        return img;
    }

    // ------------------------------------------------------------------ the turntable

    /** Your character wearing everything picked; turns slowly, drag to spin. */
    private final class Turntable extends JComponent {
        private double yaw = 200, velocity = 0.6;
        private int lastX = -1;
        private BufferedImage skin;
        private long skinAt;
        private final Timer spin = new Timer(50, e -> {          // 20 fps is plenty for a slow spin
            if (lastX < 0) {
                yaw += velocity;
                velocity += (0.6 - velocity) * 0.04;                   // settles back to a slow spin
            }
            repaint();
        });

        Turntable() {
            java.awt.event.MouseAdapter m = new java.awt.event.MouseAdapter() {
                @Override public void mousePressed(java.awt.event.MouseEvent e) { lastX = e.getX(); }

                @Override public void mouseDragged(java.awt.event.MouseEvent e) {
                    double d = e.getX() - lastX;
                    yaw -= d * 0.8;
                    velocity = -d * 0.8;
                    lastX = e.getX();
                    repaint();
                }

                @Override public void mouseReleased(java.awt.event.MouseEvent e) { lastX = -1; }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        void refresh() {
            repaint();
        }

        @Override
        public void addNotify() {
            super.addNotify();
            if (Anim.enabled && !Glass.lite()) spin.start();          // still on slow PCs (More optimization)
        }

        @Override
        public void removeNotify() {
            spin.stop();
            super.removeNotify();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            if (!isShowing()) return;
            Graphics2D g = Theme.aa(g0.create());
            int w = getWidth(), h = getHeight();
            stage(g, 0, 0, w, h, 18);
            g.setColor(new Color(0, 0, 0, 60));                           // floor shadow
            g.fill(new Ellipse2D.Double(w / 2.0 - 60, h - 44, 120, 18));
            long now = System.currentTimeMillis();
            if (now - skinAt > 5000) {
                skin = Accessories.skin();
                skinAt = now;
            }
            BufferedImage img = CosmeticPreview.render(Settings.get().cosmeticsCode(), w - 20, h - 30, yaw, true, skin);
            g.drawImage(img, 10, 10, null);
            String fx = Settings.get().cosParticles;
            if (fx != null && !"Off".equals(fx)) sparkle(g, 10, 10, w - 20, h - 30, fx, Settings.get().cosTrail);
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ a card

    private final class Card extends JComponent {
        final Item it;
        private int hoverChip = -1;
        private boolean hover;

        Card(Item it) {
            this.it = it;
            java.awt.event.MouseAdapter m = new java.awt.event.MouseAdapter() {
                @Override public void mouseMoved(java.awt.event.MouseEvent e) {
                    int h = chipAt(e.getPoint());
                    if (h != hoverChip || !hover) {
                        hoverChip = h;
                        hover = true;
                        setCursor(Cursor.getPredefinedCursor(h >= 0 || e.getY() < CARD_H - 70 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                        repaint();
                    }
                }

                @Override public void mouseExited(java.awt.event.MouseEvent e) {
                    hover = false;
                    hoverChip = -1;
                    repaint();
                }

                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    int c = chipAt(e.getPoint());
                    List<String> opts = it.options();
                    if (c >= 0 && opts.get(c).equals("Custom")) {          // your own colour from the wheel
                        String cur = value(it.key());
                        int start = cur.startsWith("x") ? Integer.parseInt(cur.substring(1), 16) : 0x8B5CF6;
                        ColorWheel.open(Card.this, start, rgb -> {
                            set(it.key(), "x" + String.format("%06x", rgb & 0xFFFFFF));
                            changed();
                        });
                        return;
                    }
                    if (c >= 0) set(it.key(), opts.get(c));
                    else if (e.getY() < CARD_H - 70) {
                        int i = opts.indexOf(value(it.key()));
                        set(it.key(), opts.get((i + 1) % opts.size()));
                    }
                    changed();
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        private boolean words() {
            for (String o : it.options()) if (!o.equals("Off") && !o.equals("Custom") && colourOf(o) == null) return true;
            return false;
        }

        private Rectangle chip(int i) {
            List<String> opts = it.options();
            int n = opts.size();
            if (words()) {                                             // pills with text
                int gap = 4, pw = (getWidth() - 20 - gap * (n - 1)) / n;
                return new Rectangle(10 + i * (pw + gap), CARD_H - 38, pw, 26);
            }
            int gap = 5, d = Math.max(14, Math.min(22, (getWidth() - 24 - gap * (n - 1)) / n));
            int total = n * d + (n - 1) * gap, x0 = (getWidth() - total) / 2;
            return new Rectangle(x0 + i * (d + gap), CARD_H - 25 - d / 2, d, d);
        }

        private int chipAt(Point p) {
            for (int i = 0; i < it.options().size(); i++) if (chip(i).contains(p)) return i;
            return -1;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            int w = getWidth(), h = CARD_H;
            String cur = value(it.key());
            boolean on = !"Off".equals(cur) && !(it.key().equals("wingstyle") && "Off".equals(value("wings")));
            Theme.surface(g, this, 0, 0, w, h, 16, hover ? 1 : 0, 0.2);
            if (on) Theme.stroke(g, 0.5, 0.5, w - 1, h - 1, 15.5, Theme.alpha(Theme.ACCENT, 0.7), 1.2f);
            int ph = h - 90;
            stage(g, 8, 8, w - 16, ph, 12);
            BufferedImage img = preview(sample(it, cur), w - 24, ph - 8, it.yaw());
            Composite old = g.getComposite();
            if (!on) g.setComposite(Theme.fade(0.45f));
            g.drawImage(img, 12, 12, null);
            if (it.group().equals("Effects")) sparkle(g, 12, 12, w - 24, ph - 8, on ? value("particles") : "Sparkles", it.key().equals("trail"));
            g.setComposite(old);
            if (on) {
                Theme.fill(g, w - 16 - 66, 14, 60, 20, 10, Theme.ACCENT);
                Theme.center(g, "Wearing", Theme.font(Theme.MEDIUM, 10.5f), Theme.ON_ACCENT, w - 16 - 66, 14, 60, 20);
            }
            Theme.left(g, it.name(), Theme.font(Theme.MEDIUM, 14f), Theme.TEXT, 12, h - 80, 22);
            Theme.left(g, on ? (cur.startsWith("x") ? "Custom #" + cur.substring(1).toUpperCase() : cur) : "Off", Theme.font(Theme.REGULAR, 11.5f), Theme.MUTED, 12, h - 60, 16);
            List<String> opts = it.options();
            boolean words = words();
            for (int i = 0; i < opts.size(); i++) {
                Rectangle r = chip(i);
                boolean custom = opts.get(i).equals("Custom");
                boolean sel = opts.get(i).equals(cur) || custom && cur.startsWith("x");
                if (custom) {
                    for (int k = 0; k < 12; k++) {
                        g.setColor(Color.getHSBColor(k / 12f, 0.75f, 1f));
                        g.fill(new Arc2D.Double(r.x + 1, r.y + 1, r.width - 2, r.height - 2, k * 30, 31, Arc2D.PIE));
                    }
                    Color inner = cur.startsWith("x") ? colourOf(cur) : Theme.PANEL;
                    g.setColor(inner);
                    g.fill(new Ellipse2D.Double(r.x + 4, r.y + 4, r.width - 8, r.height - 8));
                    if (sel || i == hoverChip) {
                        g.setColor(sel ? Theme.TEXT : Theme.alpha(Theme.TEXT, 0.4));
                        g.setStroke(new BasicStroke(sel ? 2f : 1f));
                        g.draw(new Ellipse2D.Double(r.x - 2, r.y - 2, r.width + 4, r.height + 4));
                    }
                    continue;
                }
                if (words) {
                    Theme.fill(g, r.x, r.y, r.width, r.height, r.height / 2.0, sel ? Theme.TEXT : Theme.alpha(Theme.TEXT, i == hoverChip ? 0.14 : 0.07));
                    g.setFont(Theme.font(Theme.MEDIUM, 11f));
                    Theme.center(g, Theme.ellipsize(opts.get(i), g.getFontMetrics(), r.width - 6), Theme.font(Theme.MEDIUM, 11f),
                            sel ? Theme.BLACK : Theme.TEXT, r.x, r.y, r.width, r.height);
                    continue;
                }
                Color c = colourOf(opts.get(i));
                if (c == null) {                                        // "Off"
                    g.setColor(Theme.alpha(Theme.TEXT, sel ? 0.9 : 0.4));
                    g.setStroke(new BasicStroke(1.5f));
                    g.draw(new Ellipse2D.Double(r.x + 1, r.y + 1, r.width - 2, r.height - 2));
                    g.draw(new Line2D.Double(r.x + 5, r.y + r.height - 5, r.x + r.width - 5, r.y + 5));
                } else {
                    g.setColor(c);
                    g.fill(new Ellipse2D.Double(r.x + 2, r.y + 2, r.width - 4, r.height - 4));
                }
                if (sel || i == hoverChip) {
                    g.setColor(sel ? Theme.TEXT : Theme.alpha(Theme.TEXT, 0.4));
                    g.setStroke(new BasicStroke(sel ? 2f : 1f));
                    g.draw(new Ellipse2D.Double(r.x - 2, r.y - 2, r.width + 4, r.height + 4));
                }
            }
            g.dispose();
        }
    }
}

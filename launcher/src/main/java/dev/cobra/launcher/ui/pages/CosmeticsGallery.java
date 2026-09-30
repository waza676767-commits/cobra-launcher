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

    /** One wearable: key in the code, card name, choices, and which way the preview looks at it. */
    private record Item(String key, String name, List<String> options, double yaw) {}

    static final List<Item> ITEMS = List.of(
            new Item("wings", "Wings", List.of("Off", "Angel", "Red", "Black", "Gold", "Blue", "Purple", "Pink", "Green", "Cyan"), 200),
            new Item("wingstyle", "Wing style", List.of("Feather", "Dragon", "Butterfly"), 200),
            new Item("halo", "Halo", List.of("Off", "Angel", "Red"), 25),
            new Item("hat", "Hat", List.of("Off", "Crown", "Top hat", "Witch"), 25),
            new Item("ears", "Cat ears", List.of("Off", "Black", "White", "Ginger", "Pink"), 25),
            new Item("bunny", "Bunny ears", List.of("Off", "White", "Pink", "Black", "Brown"), 25),
            new Item("horns", "Horns", List.of("Off", "Red", "Black", "White", "Gold"), 25),
            new Item("glasses", "Sunglasses", List.of("Off", "Black", "Gold", "Pink"), 20),
            new Item("headphones", "Headphones", List.of("Off", "Black", "White", "Pink", "Blue"), 40),
            new Item("tail", "Tail", List.of("Off", "Black", "White", "Ginger", "Pink", "Fox"), 215),
            new Item("backpack", "Backpack", List.of("Off", "Brown", "Black", "Blue", "Red"), 205),
            new Item("katana", "Katana", List.of("Off", "On"), 205),
            new Item("gloves", "Boxing gloves", List.of("Off", "Red", "Blue", "Black"), 30),
            new Item("feet", "Big feet", List.of("Off", "On"), 30));

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
        int rows = (ITEMS.size() + cols - 1) / cols;
        return new Dimension(100, 56 + HEADER + rows * (CARD_H + GAP) + 10);
    }

    @Override
    public void doLayout() {
        int w = getWidth();
        turntable.setBounds(20, 56, 280, HEADER - 16);
        int rx = 320, rw = w - rx - 20;
        glow.setBounds(rx + rw - 50, 80, 50, 30);
        size.setBounds(rx, 150, Math.min(360, rw), 30);
        clear.setBounds(rx, 216, 200, 40);
        int cols = columns(), cw = (w - 40 - GAP * (cols - 1)) / cols;
        int rows = (ITEMS.size() + cols - 1) / cols;
        cards.setBounds(20, 56 + HEADER, w - 40, rows * (CARD_H + GAP));
        for (int i = 0; i < cards.getComponentCount(); i++) cards.getComponent(i).setBounds((i % cols) * (cw + GAP), (i / cols) * (CARD_H + GAP), cw, CARD_H);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.aa(g0.create());
        int w = getWidth(), h = getHeight();
        Theme.surface(g, this, 0, 0, w, h, 20, 0, 0.1);
        Theme.left(g, "Cosmetics", Theme.font(Theme.BOLD, 16f), Theme.TEXT, 20, 14, 26);
        Theme.left(g, "Only other Cobra players see them · applies when you launch", Theme.font(Theme.REGULAR, 12.5f), Theme.MUTED, 118, 16, 22);
        int rx = 320;
        Theme.left(g, "Glow effects", Theme.font(Theme.MEDIUM, 14f), Theme.TEXT, rx, 76, 22);
        Theme.left(g, "Wings, halo and katana glow softly and shine in the dark.", Theme.font(Theme.REGULAR, 12f), Theme.MUTED, rx, 96, 18);
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
            case "katana" -> s.cosKatana ? "On" : "Off";
            case "feet" -> s.cosFeet ? "On" : "Off";
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
            case "katana" -> s.cosKatana = !"Off".equals(v);
            case "feet" -> s.cosFeet = !"Off".equals(v);
            default -> { }
        }
        s.save();
    }

    /** Short code to preview one item with value {@code v} (an example when it's Off). */
    private static String sample(Item it, String v) {
        String key = it.key(), code;
        if (key.equals("wingstyle")) return "wings:" + ("Off".equals(value("wings")) ? "angel" : value("wings").toLowerCase()) + ",wingstyle:" + v.toLowerCase();
        if ("Off".equals(v)) v = it.options().get(Math.min(1, it.options().size() - 1));
        if (v.equals("On")) code = key;
        else code = key + ":" + v.toLowerCase().replace(" ", "");
        if (key.equals("wings") && !"Feather".equals(value("wingstyle"))) code += ",wingstyle:" + value("wingstyle").toLowerCase();
        return code;
    }

    static Color colourOf(String v) {
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
        private final Timer spin = new Timer(33, e -> {
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
            if (Anim.enabled) spin.start();
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
            Color a = Theme.mix(Theme.ACCENT, Theme.BLACK, 0.55), b = Theme.mix(Theme.ACCENT, Theme.BLACK, 0.85);
            Theme.fill(g, 0, 0, w, h, 18, new GradientPaint(0, 0, a, 0, h, b));
            g.setPaint(new RadialGradientPaint(new Point2D.Double(w / 2.0, h * 0.42), (float) (h * 0.6), new float[]{0f, 1f},
                    new Color[]{new Color(255, 255, 255, 70), new Color(255, 255, 255, 0)}));
            g.fill(new RoundRectangle2D.Double(0, 0, w, h, 36, 36));
            g.setColor(new Color(0, 0, 0, 60));                           // floor shadow
            g.fill(new Ellipse2D.Double(w / 2.0 - 60, h - 44, 120, 18));
            long now = System.currentTimeMillis();
            if (now - skinAt > 5000) {
                skin = Accessories.skin();
                skinAt = now;
            }
            BufferedImage img = CosmeticPreview.render(Settings.get().cosmeticsCode(), w - 20, h - 30, yaw, true, skin);
            g.drawImage(img, 10, 10, null);
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ a card

    private final class Card extends JComponent {
        private final Item it;
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
            for (String o : it.options()) if (!o.equals("Off") && colourOf(o) == null) return true;
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
            Theme.fill(g, 0, 0, w, h, 16, Theme.alpha(Theme.TEXT, hover ? 0.09 : 0.05));
            if (on) Theme.stroke(g, 0.5, 0.5, w - 1, h - 1, 15.5, Theme.alpha(Theme.ACCENT, 0.7), 1.2f);
            int ph = h - 90;
            Color a = Theme.mix(Theme.ACCENT, Theme.BLACK, 0.6), b = Theme.mix(Theme.ACCENT, Theme.BLACK, 0.86);
            Theme.fill(g, 8, 8, w - 16, ph, 12, new GradientPaint(0, 8, a, 0, 8 + ph, b));
            BufferedImage img = preview(sample(it, cur), w - 24, ph - 8, it.yaw());
            Composite old = g.getComposite();
            if (!on) g.setComposite(AlphaComposite.SrcOver.derive(0.45f));
            g.drawImage(img, 12, 12, null);
            g.setComposite(old);
            if (on) {
                Theme.fill(g, w - 16 - 66, 14, 60, 20, 10, Theme.ACCENT);
                Theme.center(g, "Wearing", Theme.font(Theme.MEDIUM, 10.5f), Theme.ON_ACCENT, w - 16 - 66, 14, 60, 20);
            }
            Theme.left(g, it.name(), Theme.font(Theme.MEDIUM, 14f), Theme.TEXT, 12, h - 80, 22);
            Theme.left(g, on ? cur : "Off", Theme.font(Theme.REGULAR, 11.5f), Theme.MUTED, 12, h - 60, 16);
            List<String> opts = it.options();
            boolean words = words();
            for (int i = 0; i < opts.size(); i++) {
                Rectangle r = chip(i);
                boolean sel = opts.get(i).equals(cur);
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

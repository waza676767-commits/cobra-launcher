package dev.cobra.launcher.ui.pages;

import dev.cobra.launcher.core.Settings;
import dev.cobra.launcher.ui.*;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.List;

/**
 * Cosmetics: a gallery of what you can wear in game (only other Cobra players see them). Each
 * card has a drawn preview on a soft gradient, a name and its choices; picked ones are marked.
 * Applies the next time you launch (and can be changed in game too).
 */
public final class CosmeticsPage extends Page {
    private final JPanel grid = new JPanel(null) {
        @Override
        public Dimension getPreferredSize() {
            int cols = columns(getParent() == null ? 800 : getParent().getWidth());
            int rows = (ITEMS.size() + cols - 1) / cols;
            return new Dimension(100, rows * (CARD_H + GAP));
        }

        @Override
        public void doLayout() {
            int cols = columns(getWidth()), cw = (getWidth() - 10 - GAP * (cols - 1)) / cols;
            for (int i = 0; i < getComponentCount(); i++) getComponent(i).setBounds((i % cols) * (cw + GAP), (i / cols) * (CARD_H + GAP), cw, CARD_H);
        }
    };
    private final JScrollPane scroll = Components.scroll(grid);
    private final Components.Toggle glow;

    private static final int CARD_H = 250, GAP = 14;

    private static int columns(int w) { return Math.max(2, Math.min(4, w / 230)); }

    /** One wearable: its name, its choices ("On" for a simple on/off), and where it's saved. */
    private record Item(String key, String name, List<String> options) {}

    private static final List<Item> ITEMS = List.of(
            new Item("wings", "Wings", List.of("Off", "Angel", "Red", "Black", "Gold", "Blue", "Purple", "Pink", "Green")),
            new Item("halo", "Halo", List.of("Off", "Angel", "Red")),
            new Item("ears", "Cat Ears", List.of("Off", "Black", "White", "Ginger", "Pink")),
            new Item("tail", "Cat Tail", List.of("Off", "On")),
            new Item("katana", "Katana", List.of("Off", "On")),
            new Item("gloves", "Boxing Gloves", List.of("Off", "Red", "Blue", "Black")),
            new Item("feet", "Big Feet", List.of("Off", "On")));

    public CosmeticsPage() {
        grid.setOpaque(false);
        for (Item it : ITEMS) grid.add(new Card(it));
        add(scroll);
        Settings s = Settings.get();
        glow = new Components.Toggle(s.cosGlow, v -> {
            s.cosGlow = v;
            s.save();
            grid.repaint();
        });
        add(glow);
    }

    @Override public String title() { return "Cosmetics"; }
    @Override public String icon() { return "sparkle"; }

    @Override
    public void onShow() {
        grid.revalidate();
        grid.repaint();
    }

    @Override
    public void doLayout() {
        int w = getWidth(), h = getHeight();
        glow.setBounds(w - 50, 12, 50, 30);
        scroll.setBounds(0, 80, w + 10, h - 80);
        grid.doLayout();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.aa(g0.create());
        Theme.left(g, title(), Theme.font(Theme.REGULAR, 34f), Theme.TEXT, 0, 0, 42);
        Theme.left(g, "What you wear in game. Only other Cobra players see it. Applies when you launch.",
                Theme.font(Theme.REGULAR, 13.5f), Theme.SOFT, 0, 44, 22);
        Theme.left(g, "Glow effects", Theme.font(Theme.MEDIUM, 13f), Theme.SOFT, getWidth() - 150, 12, 30);
        g.dispose();
    }

    // ------------------------------------------------------------------ values

    static String value(String key) {
        Settings s = Settings.get();
        return switch (key) {
            case "wings" -> s.cosWings;
            case "halo" -> s.cosHalo;
            case "ears" -> s.cosEars;
            case "gloves" -> s.cosGloves;
            case "tail" -> s.cosTail ? "On" : "Off";
            case "katana" -> s.cosKatana ? "On" : "Off";
            case "feet" -> s.cosFeet ? "On" : "Off";
            default -> "Off";
        };
    }

    static void set(String key, String v) {
        Settings s = Settings.get();
        switch (key) {
            case "wings" -> s.cosWings = v;
            case "halo" -> s.cosHalo = v;
            case "ears" -> s.cosEars = v;
            case "gloves" -> s.cosGloves = v;
            case "tail" -> s.cosTail = !"Off".equals(v);
            case "katana" -> s.cosKatana = !"Off".equals(v);
            case "feet" -> s.cosFeet = !"Off".equals(v);
            default -> { }
        }
        s.save();
    }

    static Color colourOf(String key, String v) {
        return switch (v) {
            case "Angel" -> key.equals("halo") ? new Color(0xFFE08A) : new Color(0xF7F7F2);
            case "Red" -> new Color(0xD62B3A);
            case "Black" -> new Color(0x2A2A2E);
            case "Gold" -> new Color(0xE8B63A);
            case "Blue" -> new Color(0x3F86E8);
            case "Purple" -> new Color(0x8B5CF6);
            case "Pink" -> new Color(0xF472B6);
            case "Green" -> new Color(0x34C77B);
            case "White" -> new Color(0xF2F2F2);
            case "Ginger" -> new Color(0xE08A3C);
            case "On" -> Theme.ACCENT;
            default -> Theme.MUTED;
        };
    }

    // ------------------------------------------------------------------ a card

    private final class Card extends JComponent {
        private final Item it;
        private int hoverChip = -1;
        private boolean hover;

        Card(Item it) {
            this.it = it;
            setOpaque(false);
            java.awt.event.MouseAdapter m = new java.awt.event.MouseAdapter() {
                @Override public void mouseMoved(java.awt.event.MouseEvent e) {
                    int h = chipAt(e.getPoint());
                    if (h != hoverChip || !hover) {
                        hoverChip = h;
                        hover = true;
                        setCursor(Cursor.getPredefinedCursor(h >= 0 || e.getY() < CARD_H - 60 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
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
                    else if (e.getY() < CARD_H - 60) {                 // clicking the picture: next choice
                        int i = opts.indexOf(value(it.key()));
                        set(it.key(), opts.get((i + 1) % opts.size()));
                    }
                    repaint();
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        /** The small colour dots / On-Off pills along the bottom. */
        private Rectangle chip(int i) {
            List<String> opts = it.options();
            boolean onOff = opts.size() == 2;
            if (onOff) return new Rectangle(14 + i * 62, CARD_H - 44, 56, 28);
            int n = opts.size(), gap = 6;
            int d = Math.max(14, Math.min(22, (getWidth() - 28 - gap * (n - 1)) / n));   // always fits the card
            int total = n * d + (n - 1) * gap;
            int x0 = Math.max(14, (getWidth() - total) / 2);
            return new Rectangle(x0 + i * (d + gap), CARD_H - 29 - d / 2, d, d);
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
            boolean on = !"Off".equals(cur);
            // card body
            Theme.surface(g, this, 0, 0, w, h, 20, hover ? 1 : 0, on ? 0.5 : 0.2);
            // preview area: a soft gradient of the accent, like a showcase
            int ph = h - 92;
            Color a = Theme.mix(Theme.ACCENT, Theme.BLACK, 0.55), b = Theme.mix(Theme.ACCENT, Theme.BLACK, 0.82);
            Theme.fill(g, 10, 10, w - 20, ph, 14, new GradientPaint(0, 10, a, 0, 10 + ph, b));
            Graphics2D rays = (Graphics2D) g.create();
            rays.clip(new RoundRectangle2D.Double(10, 10, w - 20, ph, 28, 28));
            rays.setPaint(new RadialGradientPaint(new Point2D.Double(w / 2.0, 10 + ph * 0.45), (float) (ph * 0.7), new float[]{0f, 1f},
                    new Color[]{new Color(255, 255, 255, 60), new Color(255, 255, 255, 0)}));
            rays.fillRect(10, 10, w - 20, ph);
            rays.dispose();
            Color c = on ? colourOf(it.key(), cur) : new Color(255, 255, 255, 90);
            boolean glowOn = on && Settings.get().cosGlow;
            drawPreview(g, it.key(), w / 2.0, 10 + ph / 2.0, Math.min(w - 40, ph - 20), c, glowOn);
            if (on) {                                                    // "equipped" tag
                Theme.fill(g, w - 20 - 76, 18, 68, 22, 11, Theme.ACCENT);
                Theme.center(g, "Wearing", Theme.font(Theme.MEDIUM, 11f), Theme.ON_ACCENT, w - 20 - 76, 18, 68, 22);
            }
            // name + choices
            Theme.left(g, it.name(), Theme.font(Theme.MEDIUM, 15f), Theme.TEXT, 14, h - 80, 24);
            Theme.left(g, on ? cur : "Not wearing", Theme.font(Theme.REGULAR, 12f), Theme.MUTED, 14, h - 60, 18);
            List<String> opts = it.options();
            for (int i = 0; i < opts.size(); i++) {
                Rectangle r = chip(i);
                boolean sel = opts.get(i).equals(cur);
                if (opts.size() == 2) {
                    Theme.fill(g, r.x, r.y, r.width, r.height, r.height / 2.0, sel ? Theme.TEXT : Theme.alpha(Theme.TEXT, i == hoverChip ? 0.14 : 0.07));
                    Theme.center(g, opts.get(i), Theme.font(Theme.MEDIUM, 12f), sel ? Theme.BLACK : Theme.TEXT, r.x, r.y, r.width, r.height);
                    continue;
                }
                if (i == 0) {                                            // "Off": an empty ring with a slash
                    g.setColor(Theme.alpha(Theme.TEXT, sel ? 0.9 : 0.4));
                    g.setStroke(new BasicStroke(1.5f));
                    g.draw(new Ellipse2D.Double(r.x + 1, r.y + 1, r.width - 2, r.height - 2));
                    g.draw(new Line2D.Double(r.x + 5, r.y + r.height - 5, r.x + r.width - 5, r.y + 5));
                } else {
                    g.setColor(colourOf(it.key(), opts.get(i)));
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

    // ------------------------------------------------------------------ previews (simple vector art)

    private static void drawPreview(Graphics2D g0, String key, double cx, double cy, double size, Color c, boolean glow) {
        Graphics2D g = (Graphics2D) g0.create();
        g.translate(cx, cy);
        double k = size / 140.0;
        g.scale(k, k);
        if (glow) {
            g.setPaint(new RadialGradientPaint(new Point2D.Double(0, 0), 70f, new float[]{0f, 1f},
                    new Color[]{new Color(c.getRed(), c.getGreen(), c.getBlue(), 110), new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
            g.fill(new Ellipse2D.Double(-70, -70, 140, 140));
        }
        Color dark = Theme.mix(c, Color.BLACK, 0.3);
        switch (key) {
            case "wings" -> {
                for (int s = -1; s <= 1; s += 2) {
                    for (int row = 0; row < 3; row++) {
                        Path2D f = new Path2D.Double();
                        double y = -26 + row * 14, len = 60 - row * 12;
                        f.moveTo(s * 8, y);
                        f.curveTo(s * (8 + len * 0.5), y - 18 + row * 4, s * (8 + len), y - 6, s * (8 + len), y + 6);
                        f.curveTo(s * (8 + len * 0.6), y + 14, s * 20, y + 16, s * 8, y + 12);
                        f.closePath();
                        g.setColor(Theme.mix(c, Color.BLACK, row * 0.1));
                        g.fill(f);
                    }
                }
                g.setColor(new Color(255, 255, 255, 60));
                g.fill(new RoundRectangle2D.Double(-8, -34, 16, 60, 8, 8));   // the body between them
            }
            case "halo" -> {
                g.setStroke(new BasicStroke(9f));
                g.setColor(c);
                g.draw(new Ellipse2D.Double(-44, -16, 88, 30));
                g.setStroke(new BasicStroke(2f));
                g.setColor(new Color(255, 255, 255, 170));
                g.draw(new Ellipse2D.Double(-44, -17, 88, 30));
            }
            case "ears" -> {
                g.setColor(new Color(255, 255, 255, 50));
                g.fill(new RoundRectangle2D.Double(-40, -10, 80, 60, 10, 10));   // a head
                for (int s = -1; s <= 1; s += 2) {
                    Path2D e = new Path2D.Double();
                    e.moveTo(s * 10, -8);
                    e.lineTo(s * 26, -48);
                    e.lineTo(s * 40, -8);
                    e.closePath();
                    g.setColor(c);
                    g.fill(e);
                    Path2D in = new Path2D.Double();
                    in.moveTo(s * 17, -12);
                    in.lineTo(s * 26, -36);
                    in.lineTo(s * 33, -12);
                    in.closePath();
                    g.setColor(new Color(0xF3A6B8));
                    g.fill(in);
                }
            }
            case "tail" -> {
                Path2D t = new Path2D.Double();
                t.moveTo(-30, 50);
                t.curveTo(-40, 0, 40, 10, 20, -45);
                g.setStroke(new BasicStroke(14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(c.equals(Theme.ACCENT) ? new Color(0x2A2A2E) : c);
                if (c.getAlpha() < 255) g.setColor(c);
                g.draw(t);
            }
            case "katana" -> {
                g.rotate(Math.toRadians(-40));
                g.setColor(new Color(0xD8DDE4));
                g.fill(new RoundRectangle2D.Double(-4, -62, 8, 90, 6, 6));
                g.setColor(new Color(0xC9A227));
                g.fill(new RoundRectangle2D.Double(-12, 26, 24, 6, 3, 3));
                g.setColor(new Color(0x1A1A1E));
                g.fill(new RoundRectangle2D.Double(-5, 32, 10, 34, 4, 4));
                g.setColor(new Color(0x7A1F2B));
                for (int y = 36; y < 64; y += 8) g.fill(new Rectangle2D.Double(-5, y, 10, 3));
            }
            case "gloves" -> {
                for (int s = -1; s <= 1; s += 2) {
                    g.setColor(c);
                    g.fill(new RoundRectangle2D.Double(s * 30 - 22, -26, 44, 50, 22, 22));
                    g.setColor(dark);
                    g.fill(new RoundRectangle2D.Double(s * 30 - 22 + (s < 0 ? 30 : -4), -8, 14, 22, 10, 10));
                    g.setColor(new Color(0xF4F4F4));
                    g.fill(new RoundRectangle2D.Double(s * 30 - 18, 22, 36, 12, 5, 5));
                }
            }
            case "feet" -> {
                Color skin = new Color(0xF0C8A0);
                for (int s = -1; s <= 1; s += 2) {
                    g.setColor(skin);
                    g.fill(new RoundRectangle2D.Double(s * 28 - 20, -20, 40, 56, 14, 14));
                    for (int t = 0; t < 4; t++) {
                        g.setColor(Theme.mix(skin, Color.BLACK, 0.08));
                        g.fill(new Ellipse2D.Double(s * 28 - 18 + t * 10, -30, 9, 11));
                    }
                }
            }
            default -> { }
        }
        g.dispose();
    }
}

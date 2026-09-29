package dev.cobra.launcher.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;

public final class Components {
    private Components() {}

    /** Base for clickable painted components: hover/press tweens, hand cursor, click callback. */
    public abstract static class Interactive extends JComponent {
        protected final Anim.Tween hover = new Anim.Tween(this, 0).rate(18);
        /** 0 → 1 while held down; buttons use it for a small squish. */
        protected final Anim.Tween press = new Anim.Tween(this, 0).rate(26);
        protected boolean pressed;
        protected Runnable onClick;

        protected Interactive() {
            setOpaque(false);
            setFocusable(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { if (isEnabled()) hover.to(1); }
                @Override public void mouseExited(MouseEvent e) { hover.to(0); press.to(0); pressed = false; repaint(); }
                @Override public void mousePressed(MouseEvent e) { if (isEnabled() && SwingUtilities.isLeftMouseButton(e)) { pressed = true; press.to(1); repaint(); pressedAt(e); } }
                @Override public void mouseReleased(MouseEvent e) {
                    boolean was = pressed;
                    pressed = false;
                    press.to(0);
                    repaint();
                    if (was && isEnabled() && contains(e.getPoint())) clicked(e);
                }
                @Override public void mouseDragged(MouseEvent e) { if (pressed) dragged(e); }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        protected void clicked(MouseEvent e) { if (onClick != null) onClick.run(); }
        protected void pressedAt(MouseEvent e) {}
        protected void dragged(MouseEvent e) {}

        public Interactive onClick(Runnable r) {
            this.onClick = r;
            return this;
        }
    }

    // ------------------------------------------------------------------ Button

    public enum Variant { PRIMARY, GHOST, SUBTLE, DANGER }

    public static class Button extends Interactive {
        private String text;
        private final String icon;
        private final Variant variant;
        private Font font = Theme.font(Theme.MEDIUM, 13.5f);
        private double radius = 12;

        public Button(String text, String icon, Variant variant, Runnable onClick) {
            this.text = text;
            this.icon = icon;
            this.variant = variant;
            this.onClick = onClick;
        }

        public Button font(Font f) { this.font = f; return this; }
        public Button radius(double r) { this.radius = r; return this; }
        public void setText(String t) { this.text = t; repaint(); }
        public String getText() { return text; }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(font);
            int w = (text == null || text.isEmpty() ? 0 : fm.stringWidth(text)) + (icon != null ? 18 + (text == null || text.isEmpty() ? 0 : 8) : 0) + 32;
            return new Dimension(w, 38);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            double h = hover.get();
            int w = getWidth(), ht = getHeight();
            double sq = 1 - 0.04 * press.get();          // squish while pressed
            if (sq < 0.999) {
                g.translate(w / 2.0, ht / 2.0);
                g.scale(sq, sq);
                g.translate(-w / 2.0, -ht / 2.0);
            }
            double r = Math.min(radius, ht / 2.0);
            Color fg;
            float alpha = isEnabled() ? 1f : 0.45f;
            g.setComposite(AlphaComposite.SrcOver.derive(alpha));
            switch (variant) {
                case PRIMARY -> {
                    if (Glass.on()) Glass.glow(g, 0, 0, w, ht, r, Theme.ACCENT, 0);
                    Theme.fill(g, 0, 0, w, ht, r, Theme.mix(Theme.ACCENT, Theme.SOFT, pressed ? 0.35 : h * 0.18));
                    if (Glass.on()) Theme.fill(g, 1, 1, w - 2, ht / 2.0, r, new GradientPaint(0, 0, new Color(255, 255, 255, Theme.isLight() ? 40 : 0), 0, (float) (ht / 2.0), new Color(255, 255, 255, 0)));
                    fg = Theme.ON_ACCENT;
                }
                case DANGER -> {
                    Theme.fill(g, 0, 0, w, ht, r, Theme.alpha(Theme.DANGER, 0.14 + 0.12 * h));
                    fg = Theme.DANGER;
                }
                case SUBTLE -> {
                    Theme.chip(g, 0, 0, w, ht, r, 0.2 + h * 0.8 + (pressed ? 0.3 : 0));
                    fg = Theme.TEXT;
                }
                default -> {
                    Theme.chip(g, 0, 0, w, ht, r, h * 0.8 + (pressed ? 0.3 : 0));
                    fg = Theme.TEXT;
                }
            }
            FontMetrics fm = g.getFontMetrics(font);
            boolean hasText = text != null && !text.isEmpty();
            int tw = hasText ? fm.stringWidth(text) : 0;
            int iconSize = 17;
            int total = tw + (icon != null ? iconSize + (hasText ? 8 : 0) : 0);
            double x = (w - total) / 2.0;
            if (icon != null) {
                Icons.paint(g, icon, x, (ht - iconSize) / 2.0, iconSize, fg);
                x += iconSize + (hasText ? 8 : 0);
            }
            if (hasText) Theme.left(g, text, font, fg, x, 0, ht);
            g.dispose();
        }
    }

    /** Square icon-only button used for window controls and row actions. */
    public static class IconButton extends Interactive {
        private final String icon;
        private final int iconSize;
        private Color hoverColor = Theme.TEXT;

        public IconButton(String icon, int iconSize, Runnable onClick) {
            this.icon = icon;
            this.iconSize = iconSize;
            this.onClick = onClick;
        }

        public IconButton hoverColor(Color c) { this.hoverColor = c; return this; }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            double h = hover.get();
            Theme.fill(g, 0, 0, getWidth(), getHeight(), 10, Theme.alpha(Theme.TEXT, 0.07 * h));
            Icons.paint(g, icon, (getWidth() - iconSize) / 2.0, (getHeight() - iconSize) / 2.0, iconSize, Theme.mix(Theme.SOFT, hoverColor, h));
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ Toggle

    /** Pill switch from the reference: off = outlined pill + white knob left, on = white pill + black knob right. */
    public static class Toggle extends Interactive {
        private boolean on;
        private final Anim.Tween pos;
        private Consumer<Boolean> onChange;

        public Toggle(boolean on, Consumer<Boolean> onChange) {
            this.on = on;
            this.onChange = onChange;
            this.pos = new Anim.Tween(this, on ? 1 : 0).rate(16);
            setPreferredSize(new Dimension(46, 26));
        }

        public boolean isOn() { return on; }

        public void setOn(boolean v, boolean notify) {
            if (v == on) return;
            on = v;
            pos.to(on ? 1 : 0);
            if (notify && onChange != null) onChange.accept(on);
        }

        @Override
        protected void clicked(MouseEvent e) { setOn(!on, true); }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            double t = pos.get();
            double w = 46, h = 26, x = (getWidth() - w) / 2.0, y = (getHeight() - h) / 2.0;
            Theme.chip(g, x, y, w, h, h / 2, 0.35 + 0.4 * hover.get());          // off: a soft track, no ring
            Theme.fill(g, x, y, w, h, h / 2, Theme.alpha(Theme.ACCENT, t));
            double k = h - 8;
            double kx = x + 4 + (w - 8 - k) * t;
            g.setColor(Theme.mix(Theme.TEXT, Theme.ON_ACCENT, t));
            g.fill(new Ellipse2D.Double(kx, y + 4, k, k));
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ Slider

    public static class Slider extends Interactive {
        private final double min, max, step;
        private double value;
        private final DoubleFunction<String> format;
        private final Consumer<Double> onChange;

        public Slider(double min, double max, double step, double value, DoubleFunction<String> format, Consumer<Double> onChange) {
            this.min = min;
            this.max = max;
            this.step = step;
            this.value = Math.max(min, Math.min(max, value));
            this.format = format;
            this.onChange = onChange;
            setPreferredSize(new Dimension(320, 30));
        }

        private int trackW() { return getWidth() - 86; }

        private void setFromMouse(int mx) {
            double p = Theme.clamp((mx - 8) / (double) (trackW() - 16));
            double v = Math.round((min + p * (max - min)) / step) * step;
            v = Math.max(min, Math.min(max, v));
            if (v != value) {
                value = v;
                repaint();
                if (onChange != null) onChange.accept(v);
            }
        }

        @Override protected void pressedAt(MouseEvent e) { setFromMouse(e.getX()); }
        @Override protected void dragged(MouseEvent e) { setFromMouse(e.getX()); }
        @Override protected void clicked(MouseEvent e) {}

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            int tw = trackW();
            double cy = getHeight() / 2.0;
            double p = Theme.clamp((value - min) / Math.max(1e-9, max - min));
            Theme.fill(g, 8, cy - 2, tw - 16, 4, 2, Theme.LINE_2);
            Theme.fill(g, 8, cy - 2, (tw - 16) * p, 4, 2, Theme.ACCENT);
            double kx = 8 + (tw - 16) * p;
            double r = 8 + hover.get() * 1.5 + (pressed ? 1 : 0);
            g.setColor(Theme.alpha(Theme.TEXT, 0.12 * hover.get()));
            g.fill(new Ellipse2D.Double(kx - r - 5, cy - r - 5, (r + 5) * 2, (r + 5) * 2));
            g.setColor(Theme.TEXT);
            g.fill(new Ellipse2D.Double(kx - r, cy - r, r * 2, r * 2));
            g.setColor(Theme.BLACK);
            g.fill(new Ellipse2D.Double(kx - 2.5, cy - 2.5, 5, 5));
            String label = format.apply(value);
            Font f = Theme.font(Theme.MEDIUM, 13.5f);
            Theme.left(g, label, f, Theme.TEXT, getWidth() - Theme.width(g, label, f), 0, getHeight());
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ Input

    public static class Input extends JTextField {
        private final String placeholder;
        private final String icon;
        private final Anim.Tween focus = new Anim.Tween(this, 0).rate(16);

        public Input(String value, String placeholder, String icon) {
            super(value);
            this.placeholder = placeholder;
            this.icon = icon;
            setOpaque(false);
            setFont(Theme.font(Theme.REGULAR, 14f));
            setForeground(Theme.TEXT);
            setCaretColor(Theme.TEXT);
            setSelectionColor(Theme.STEEL);
            setSelectedTextColor(Theme.TEXT);
            setBorder(new EmptyBorder(0, icon != null ? 40 : 14, 0, 14));
            addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusGained(java.awt.event.FocusEvent e) { focus.to(1); }
                @Override public void focusLost(java.awt.event.FocusEvent e) { focus.to(0); }
            });
        }

        public Input onChange(Runnable r) {
            getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { r.run(); }
                @Override public void removeUpdate(DocumentEvent e) { r.run(); }
                @Override public void changedUpdate(DocumentEvent e) { r.run(); }
            });
            return this;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            // colours are re-applied here so switching light/dark takes effect without rebuilding the page
            setForeground(Theme.TEXT);
            setCaretColor(Theme.TEXT);
            setSelectionColor(Theme.STEEL);
            setSelectedTextColor(Theme.TEXT);
            Graphics2D g = Theme.aa(g0.create());
            int w = getWidth(), h = getHeight();
            Theme.chip(g, 0, 0, w, h, 12, 0.15 + 0.4 * focus.get());
            if (focus.get() > 0.01) Theme.stroke(g, 0.5, 0.5, w - 1, h - 1, 11.5, Theme.alpha(Theme.ACCENT, 0.55 * focus.get()), 1.2f);   // focus ring only
            if (icon != null) Icons.paint(g, icon, 14, (h - 17) / 2.0, 17, Theme.SOFT);
            if (getText().isEmpty() && placeholder != null) {
                Theme.left(g, placeholder, getFont(), Theme.MUTED, getInsets().left, 0, h);
            }
            g.dispose();
            super.paintComponent(g0);
        }
    }

    // --------------------------------------------------------------- Segmented

    public static class Segmented extends JComponent {
        private final List<String> options;
        private int selected;
        private final Anim.Tween slide;
        private final Consumer<Integer> onChange;
        private int hoverIdx = -1;

        public Segmented(List<String> options, int selected, Consumer<Integer> onChange) {
            this.options = options;
            this.selected = selected;
            this.onChange = onChange;
            this.slide = new Anim.Tween(this, selected).rate(16);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    int i = index(e.getX());
                    if (i >= 0 && i != Segmented.this.selected) {
                        select(i);
                        if (Segmented.this.onChange != null) Segmented.this.onChange.accept(i);
                    }
                }
                @Override public void mouseMoved(MouseEvent e) { hoverIdx = index(e.getX()); repaint(); }
                @Override public void mouseExited(MouseEvent e) { hoverIdx = -1; repaint(); }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        public void select(int i) {
            selected = i;
            slide.to(i);
        }

        public int selected() { return selected; }

        private int index(int x) {
            int seg = (getWidth() - 8) / options.size();
            int i = (x - 4) / Math.max(1, seg);
            return i >= 0 && i < options.size() ? i : -1;
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(Theme.font(Theme.MEDIUM, 13f));
            int max = 0;
            for (String o : options) max = Math.max(max, fm.stringWidth(o));
            return new Dimension((max + 36) * options.size() + 8, 38);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            int w = getWidth(), h = getHeight();
            Theme.chip(g, 0, 0, w, h, h / 2.0, 0.1);
            double seg = (w - 8) / (double) options.size();
            Theme.fill(g, 4 + seg * slide.get(), 4, seg, h - 8, (h - 8) / 2.0, Theme.ACCENT);
            for (int i = 0; i < options.size(); i++) {
                double near = 1 - Math.min(1, Math.abs(slide.get() - i));
                Color c = Theme.mix(i == hoverIdx ? Theme.TEXT : Theme.SOFT, Theme.ON_ACCENT, near);
                Theme.center(g, options.get(i), Theme.font(Theme.MEDIUM, 13f), c, 4 + seg * i, 0, seg, h);
            }
            g.dispose();
        }
    }

    // -------------------------------------------------------------------- Card

    public static class Card extends JPanel {
        private final double radius;
        private boolean solid;

        public Card solid() {
            solid = true;
            return this;
        }

        public Card(LayoutManager layout, double radius) {
            super(layout);
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            Theme.surface(g, this, 0, 0, getWidth(), getHeight(), radius, 0, solid ? 0.6 : 0.2);
            g.dispose();
        }
    }

    /** Single-line painted text. */
    public static class Text extends JComponent {
        private String text;
        private final Font font;
        private Color color;

        public Text(String text, Font font, Color color) {
            this.text = text;
            this.font = font;
            this.color = color;
        }

        public void set(String t) { text = t; revalidate(); repaint(); }
        public void color(Color c) { color = c; repaint(); }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(font);
            return new Dimension(fm.stringWidth(text == null ? "" : text) + 2, fm.getHeight() + 2);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            g.setFont(font);
            String s = Theme.ellipsize(text, g.getFontMetrics(), getWidth());
            Theme.left(g, s, font, color, 0, 0, getHeight());
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ Scroll

    public static JScrollPane scroll(JComponent view) {
        JScrollPane sp = new JScrollPane(view, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        sp.setBorder(null);
        sp.setViewportBorder(null);
        view.setOpaque(false);
        JScrollBar bar = sp.getVerticalScrollBar();
        bar.setUnitIncrement(18);
        bar.setOpaque(false);
        bar.setPreferredSize(new Dimension(8, 0));
        bar.setUI(new BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {}
            @Override protected JButton createDecreaseButton(int o) { return zero(); }
            @Override protected JButton createIncreaseButton(int o) { return zero(); }
            private JButton zero() { JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); return b; }
            @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {}
            @Override protected void paintThumb(Graphics g0, JComponent c, Rectangle r) {
                if (r.isEmpty()) return;
                Graphics2D g = Theme.aa(g0.create());
                Theme.fill(g, r.x + 2, r.y + 2, r.width - 4, r.height - 4, 2, isDragging ? Theme.MUTED : Theme.LINE_2);
                g.dispose();
            }
        });
        smoothWheel(sp);
        return sp;
    }

    /**
     * Smooth scrolling: the wheel moves a target, and the view glides there (ease-out, ~60 fps)
     * instead of jumping in steps. Fast wheel spins add up. Off with Animations off.
     */
    public static void smoothWheel(JScrollPane sp) {
        sp.setWheelScrollingEnabled(false);
        final double[] target = {-1};
        final Timer[] timer = new Timer[1];
        timer[0] = new Timer(15, e -> {
            JScrollBar b = sp.getVerticalScrollBar();
            int max = b.getMaximum() - b.getVisibleAmount();
            target[0] = Math.max(0, Math.min(max, target[0]));
            double cur = b.getValue();
            double next = cur + (target[0] - cur) * 0.22;
            if (Math.abs(target[0] - next) < 0.6) {
                b.setValue((int) Math.round(target[0]));
                timer[0].stop();
                return;
            }
            b.setValue((int) Math.round(next));
        });
        sp.addMouseWheelListener(e -> {
            JScrollBar b = sp.getVerticalScrollBar();
            if (!b.isVisible()) return;
            double delta = e.getPreciseWheelRotation() * 90;
            if (!Anim.enabled) {
                int max = b.getMaximum() - b.getVisibleAmount();
                b.setValue(Math.max(0, Math.min(max, b.getValue() + (int) Math.round(delta))));
                timer[0].stop();
                e.consume();
                return;
            }
            if (!timer[0].isRunning()) target[0] = b.getValue();
            target[0] += delta;
            timer[0].start();
            e.consume();
        });
    }

    /** Vertical stack that sizes to its children's preferred heights (for scrolling lists). */
    public static class Stack extends JPanel implements Scrollable {
        private final int gap;
        private long appearAt;
        private final Timer appearTimer = new Timer(15, e -> {
            repaint();
            if (System.nanoTime() - appearAt > 900_000_000L) ((Timer) e.getSource()).stop();
        });

        /** Rows slide in one after another. */
        public void animateIn() {
            if (!Anim.enabled) return;
            appearAt = System.nanoTime();
            appearTimer.restart();
        }

        @Override
        protected void paintChildren(Graphics g0) {
            long age = System.nanoTime() - appearAt;
            if (appearAt == 0 || age > 900_000_000L) {
                super.paintChildren(g0);
                return;
            }
            // the list rises and fades in as one piece (through Swing's own child painting)
            double t = Theme.clamp(age / 1e6 / 300.0);
            double e = 1 - Math.pow(1 - t, 3);
            if (e <= 0.001) return;
            Graphics2D g = (Graphics2D) g0.create();
            g.translate(0, (int) Math.round(12 * (1 - e)));
            g.setComposite(AlphaComposite.SrcOver.derive((float) e));
            super.paintChildren(g);
            g.dispose();
        }

        public Stack(int gap) {
            this.gap = gap;
            setOpaque(false);
            setLayout(null);
        }

        @Override
        public void doLayout() {
            int y = 0;
            for (Component c : getComponents()) {
                if (!c.isVisible()) continue;          // hidden (e.g. filtered by search): no gap left behind
                int h = c.getPreferredSize().height;
                c.setBounds(0, y, getWidth() - 10, h);
                y += h + gap;
            }
        }

        @Override
        public Dimension getPreferredSize() {
            int h = 0;
            for (Component c : getComponents()) if (c.isVisible()) h += c.getPreferredSize().height + gap;
            return new Dimension(100, Math.max(0, h - gap));
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 18; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height - 40; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}

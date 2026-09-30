package dev.cobra.launcher.ui.pages;

import dev.cobra.launcher.core.Accessories;
import dev.cobra.launcher.core.Settings;
import dev.cobra.launcher.ui.*;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Skins and capes: a library of saved ones (click to wear, × to forget), added from a file, a
 * player's name (their skin and official cape, from Mojang) or a link (NameMC skin/cape pages,
 * textures.minecraft.net or any .png). Cobra Client shows what you wear; "Apply to my Minecraft
 * account" makes the skin visible to everyone.
 */
public final class AccessoriesPage extends Page {
    private final Components.Stack stack = new Components.Stack(18);
    private final JScrollPane scroll = Components.scroll(stack);
    private final Card skinCard = new Card(true), capeCard = new Card(false);
    private final CosmeticsGallery gallery = new CosmeticsGallery();
    private final Components.Button upload;

    public AccessoriesPage() {
        upload = new Components.Button("Apply to my Minecraft account", "external", Components.Variant.GHOST, this::upload);
        skinCard.extra(upload);
        stack.add(skinCard);
        stack.add(capeCard);
        stack.add(gallery);                       // cosmetics, right below: just scroll down
        add(scroll);
    }

    @Override public String title() { return "Accessories"; }
    @Override public String icon() { return "user"; }

    @Override
    public void onShow() {
        skinCard.reload();
        capeCard.reload();
        upload.setEnabled(Accessories.hasSkin());
        repaint();
    }

    @Override
    public void doLayout() {
        scroll.setBounds(0, 76, getWidth() + 10, getHeight() - 76);
        stack.doLayout();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.aa(g0.create());
        Theme.left(g, "Accessories", Theme.font(Theme.REGULAR, 34f), Theme.TEXT, 0, 0, 42);
        Theme.left(g, "Your skins, capes and cosmetics. Scroll down for wings, hats, halos and more.",
                Theme.font(Theme.REGULAR, 13.5f), Theme.SOFT, 0, 44, 22);
        g.dispose();
    }

    /** One card: preview of what you wear, the library, and ways to add. */
    private final class Card extends JPanel {
        final boolean skin;
        private final Components.Input input;
        private final Components.Button add, file, browse, none;
        private final Components.Segmented arms;
        private final List<java.nio.file.Path> items = new java.util.ArrayList<>();
        private final java.util.Map<java.nio.file.Path, BufferedImage> thumbs = new java.util.HashMap<>();
        private BufferedImage worn;
        private int hover = -1;
        private boolean hoverX;
        private JComponent extra;

        Card(boolean skin) {
            super(null);
            this.skin = skin;
            setOpaque(false);
            input = new Components.Input("", "Player name, NameMC link or .png link", skin ? "user" : "link");
            add = new Components.Button("Add", "plus", Components.Variant.PRIMARY, this::addFromInput);
            input.addActionListener(e -> addFromInput());
            file = new Components.Button("File", "folder", Components.Variant.GHOST, this::addFile);
            browse = new Components.Button("NameMC", "globe", Components.Variant.GHOST,
                    () -> MainWindow.openUri(skin ? "https://namemc.com/minecraft-skins" : "https://namemc.com/capes"));
            none = new Components.Button(skin ? "Default skin" : "No cape", "close", Components.Variant.GHOST, () -> {
                try {
                    if (skin) Accessories.removeSkin();
                    else Accessories.removeCape();
                } catch (Exception ignored) {}
                AccessoriesPage.this.onShow();
            });
            arms = skin ? new Components.Segmented(List.of("Classic arms", "Slim arms"), Settings.get().skinSlim ? 1 : 0, i -> {
                Settings.get().skinSlim = i == 1;
                Settings.get().save();
                repaint();
            }) : null;
            for (JComponent c : new JComponent[]{input, add, file, browse, none}) add(c);
            if (arms != null) add(arms);
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int h = thumbAt(e.getX(), e.getY());
                    boolean x = h >= 0 && onX(h, e.getX(), e.getY());
                    if (h != hover || x != hoverX) {
                        hover = h;
                        hoverX = x;
                        setCursor(Cursor.getPredefinedCursor(h >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                        repaint();
                    }
                }

                @Override public void mouseExited(MouseEvent e) {
                    hover = -1;
                    repaint();
                }

                @Override public void mouseClicked(MouseEvent e) {
                    int i = thumbAt(e.getX(), e.getY());
                    if (i < 0) return;
                    java.nio.file.Path p = items.get(i);
                    try {
                        if (onX(i, e.getX(), e.getY())) Accessories.forget(p);
                        else Accessories.wear(p, skin);
                    } catch (Exception ex) {
                        MainWindow.get().toast(ex.getMessage());
                    }
                    AccessoriesPage.this.onShow();
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        void extra(JComponent c) {
            extra = c;
            add(c);
        }

        void reload() {
            items.clear();
            items.addAll(Accessories.library(skin));
            thumbs.keySet().retainAll(items);
            worn = skin ? Accessories.skin() : Accessories.cape();
            revalidate();
            repaint();
        }

        private BufferedImage thumb(java.nio.file.Path p) {
            return thumbs.computeIfAbsent(p, k -> {
                try {
                    return javax.imageio.ImageIO.read(k.toFile());
                } catch (Exception e) {
                    return null;
                }
            });
        }

        // layout: preview 170 wide on the left, library on the right, add controls at the bottom
        private static final int PREVIEW = 170, TW = 76, TH = 104, GAP = 10, CONTROLS = 104;

        private Rectangle grid() {
            return new Rectangle(PREVIEW + 40, 56, getWidth() - PREVIEW - 60, getHeight() - 56 - CONTROLS);
        }

        private int perRow() {
            return Math.max(1, (grid().width + GAP) / (TW + GAP));
        }

        private Rectangle thumbRect(int i) {
            Rectangle g = grid();
            return new Rectangle(g.x + (i % perRow()) * (TW + GAP), g.y + (i / perRow()) * (TH + GAP), TW, TH);
        }

        private int thumbAt(int x, int y) {
            for (int i = 0; i < items.size(); i++) if (thumbRect(i).contains(x, y)) return i;
            return -1;
        }

        private boolean onX(int i, int x, int y) {
            Rectangle r = thumbRect(i);
            return x >= r.x + r.width - 22 && y <= r.y + 22;
        }

        @Override
        public Dimension getPreferredSize() {
            int w = Math.max(400, getParent() == null ? 800 : getParent().getWidth());
            int per = Math.max(1, (w - PREVIEW - 60 + GAP) / (TW + GAP));
            int rows = Math.max(1, (items.size() + per - 1) / per);
            return new Dimension(100, Math.max(56 + 220 + CONTROLS, 56 + rows * (TH + GAP) + CONTROLS));
        }

        @Override
        public void doLayout() {
            int w = getWidth(), y = getHeight() - CONTROLS + 12;
            int x = 20;
            none.setBounds(w - 20 - 140, y, 140, 40);
            browse.setBounds(none.getX() - 8 - 110, y, 110, 40);
            file.setBounds(browse.getX() - 8 - 86, y, 86, 40);
            add.setBounds(file.getX() - 8 - 86, y, 86, 40);
            input.setBounds(x, y, Math.max(160, add.getX() - 8 - x), 40);
            int y2 = y + 48;
            if (arms != null) {
                Dimension d = arms.getPreferredSize();
                arms.setBounds(x, y2, d.width, 36);
            }
            if (extra != null) extra.setBounds(w - 20 - 250, y2, 250, 36);
        }

        private void addFromInput() {
            String text = input.getText().trim();
            if (text.isEmpty()) {
                MainWindow.get().toast(skin ? "Type a player name or paste a NameMC skin link." : "Type a player name or paste a NameMC cape link.");
                return;
            }
            add.setEnabled(false);
            new Thread(() -> {
                String msg;
                try {
                    if (text.contains("/") || text.startsWith("http")) {
                        BufferedImage img = Accessories.fromLink(text);
                        Accessories.addToLibrary(img, skin, skin ? "NameMC skin" : "NameMC cape");
                        msg = (skin ? "Skin" : "Cape") + " added and worn.";
                    } else {
                        Object[] got = Accessories.fromPlayer(text);
                        if (skin) {
                            Accessories.addToLibrary((BufferedImage) got[0], true, text);
                            Settings.get().skinSlim = (Boolean) got[2];
                            Settings.get().save();
                            msg = text + "'s skin added and worn.";
                        } else if (got[1] != null) {
                            Accessories.addToLibrary((BufferedImage) got[1], false, text);
                            msg = text + "'s cape added and worn.";
                        } else {
                            msg = text + " doesn't have an official cape.";
                        }
                    }
                } catch (Exception ex) {
                    msg = ex.getMessage();
                }
                String m = msg;
                SwingUtilities.invokeLater(() -> {
                    add.setEnabled(true);
                    input.setText("");
                    MainWindow.get().toast(m);
                    AccessoriesPage.this.onShow();
                });
            }, "cobra-accessory").start();
        }

        private void addFile() {
            java.nio.file.Path picked = FilePicker.one(skin ? "Choose a skin" : "Choose a cape", "PNG pictures", "png");
            if (picked == null) return;
            try {
                Accessories.addToLibrary(picked, skin, picked.getFileName().toString().replaceFirst("(?i)\\.png$", ""));
                MainWindow.get().toast((skin ? "Skin" : "Cape") + " added and worn. It shows in game next launch.");
            } catch (Exception e) {
                MainWindow.get().toast(e.getMessage());
            }
            AccessoriesPage.this.onShow();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.aa(g0.create());
            int w = getWidth(), h = getHeight();
            Theme.surface(g, this, 0, 0, w, h, 20);
            Theme.left(g, skin ? "Skins" : "Capes", Theme.font(Theme.BOLD, 16f), Theme.TEXT, 20, 14, 26);
            Theme.left(g, skin ? "PNG 64×64 (old 64×32 is converted) · click one to wear it" : "PNG 64×32, 64×64, 32×32 or HD · click one to wear it",
                    Theme.font(Theme.REGULAR, 12.5f), Theme.MUTED, 110, 16, 22);
            // what you wear
            Rectangle full = new Rectangle(20, 56, PREVIEW, h - 56 - CONTROLS - 8);
            Rectangle area = new Rectangle(full.x, full.y, full.width, full.height - 22);   // room for "Wearing" under it
            Theme.fill(g, full.x, full.y, full.width, full.height, 14, Theme.alpha(Theme.BLACK, Theme.isLight() ? 0.05 : 0.3));
            if (worn == null) {
                Theme.center(g, skin ? "Default skin" : "No cape", Theme.font(Theme.MEDIUM, 13.5f), Theme.SOFT, area.x, area.y, area.width, area.height);
            } else if (skin) {
                drawSkin(g, worn, Settings.get().skinSlim, area);
            } else {
                drawCape(g, worn, area);
            }
            Theme.center(g, "Wearing", Theme.font(Theme.REGULAR, 11.5f), Theme.MUTED, full.x, full.y + full.height - 22, full.width, 18);
            // library
            if (items.isEmpty()) {
                Rectangle gr = grid();
                Theme.center(g, "Nothing saved yet: add one below.", Theme.font(Theme.REGULAR, 13f), Theme.MUTED, gr.x, gr.y, gr.width, 120);
            }
            for (int i = 0; i < items.size(); i++) {
                Rectangle r = thumbRect(i);
                java.nio.file.Path p = items.get(i);
                boolean wearing = worn != null && Accessories.wearing(p, skin);
                boolean hv = i == hover;
                Theme.fill(g, r.x, r.y, r.width, r.height, 12, Theme.alpha(Theme.TEXT, hv ? 0.10 : 0.05));
                if (wearing) Theme.stroke(g, r.x + 1, r.y + 1, r.width - 2, r.height - 2, 11, Theme.ACCENT, 2f);
                BufferedImage img = thumb(p);
                Rectangle pic = new Rectangle(r.x + 6, r.y + 6, r.width - 12, r.height - 30);
                if (img != null) {
                    if (skin) drawSkin(g, img, false, pic);
                    else drawCape(g, img, pic);
                }
                String name = p.getFileName().toString().replaceFirst("(?i)\\.png$", "");
                g.setFont(Theme.font(Theme.REGULAR, 11f));
                Theme.center(g, Theme.ellipsize(name, g.getFontMetrics(), r.width - 8), Theme.font(Theme.REGULAR, 11f),
                        wearing ? Theme.TEXT : Theme.SOFT, r.x, r.y + r.height - 22, r.width, 18);
                if (hv) {   // forget
                    Theme.fill(g, r.x + r.width - 22, r.y + 4, 18, 18, 9, hoverX ? new Color(0xE5484D) : Theme.alpha(Theme.BLACK, 0.6));
                    Icons.paint(g, "close", r.x + r.width - 19, r.y + 7, 12, Color.WHITE);
                }
            }
            g.dispose();
        }
    }

    /** Front view of the skin: head, body, arms, legs, with the outer layer on top. */
    static void drawSkin(Graphics2D g0, BufferedImage s, boolean slim, Rectangle area) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int u = Math.max(2, Math.min(area.width / 18, area.height / 34));
        int ox = area.x + (area.width - 16 * u) / 2, oy = area.y + (area.height - 32 * u) / 2;
        int aw = slim ? 3 : 4;
        // {srcX, srcY, w, h, dstX, dstY} in skin pixels; base layer then overlay
        int[][] base = {{8, 8, 8, 8, 4, 0}, {20, 20, 8, 12, 4, 8}, {44, 20, aw, 12, 4 - aw, 8}, {36, 52, aw, 12, 12, 8}, {4, 20, 4, 12, 4, 20}, {20, 52, 4, 12, 8, 20}};
        int[][] over = {{40, 8, 8, 8, 4, 0}, {20, 36, 8, 12, 4, 8}, {44, 36, aw, 12, 4 - aw, 8}, {52, 52, aw, 12, 12, 8}, {4, 36, 4, 12, 4, 20}, {4, 52, 4, 12, 8, 20}};
        for (int[][] layer : new int[][][]{base, over}) {
            for (int[] p : layer) {
                g.drawImage(s, ox + p[4] * u, oy + p[5] * u, ox + (p[4] + p[2]) * u, oy + (p[5] + p[3]) * u,
                        p[0], p[1], p[0] + p[2], p[1] + p[3], null);
            }
        }
        g.dispose();
    }

    private static void drawCape(Graphics2D g0, BufferedImage c, Rectangle area) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int u = Math.max(2, Math.min(area.width / 12, area.height / 18));
        int ox = area.x + (area.width - 10 * u) / 2, oy = area.y + (area.height - 16 * u) / 2;
        int k = Math.max(1, c.getWidth() / 64);   // HD capes: same layout, k times the pixels
        g.drawImage(c, ox, oy, ox + 10 * u, oy + 16 * u, k, k, 11 * k, 17 * k, null);   // the outside face of the cape
        g.dispose();
    }

    private void upload() {
        upload.setEnabled(false);
        upload.setText("Uploading");
        Thread t = new Thread(() -> {
            String msg;
            try {
                Accessories.uploadSkin(MainWindow.get().account());
                msg = "Skin applied to your Minecraft account.";
            } catch (Exception e) {
                msg = e.getMessage();
            }
            String m = msg;
            SwingUtilities.invokeLater(() -> {
                upload.setText("Apply to my Minecraft account");
                upload.setEnabled(true);
                MainWindow.get().toast(m);
            });
        }, "cobra-skin-upload");
        t.setDaemon(true);
        t.start();
    }
}

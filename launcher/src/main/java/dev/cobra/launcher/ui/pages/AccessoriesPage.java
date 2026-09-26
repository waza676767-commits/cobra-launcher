package dev.cobra.launcher.ui.pages;

import dev.cobra.launcher.core.Accessories;
import dev.cobra.launcher.core.Settings;
import dev.cobra.launcher.ui.*;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/** Import your own skin and cape; Cobra Client shows them on your player. */
public final class AccessoriesPage extends Page {
    private final Components.Button importSkin, removeSkin, upload, importCape, removeCape;
    private final Components.Segmented model;
    private BufferedImage skin, cape;

    public AccessoriesPage() {
        importSkin = new Components.Button("Import skin", "folder", Components.Variant.PRIMARY, () -> pick(true));
        removeSkin = new Components.Button("Remove", "trash", Components.Variant.GHOST, () -> remove(true));
        upload = new Components.Button("Apply to my Minecraft account", "external", Components.Variant.GHOST, this::upload);
        importCape = new Components.Button("Import cape", "folder", Components.Variant.PRIMARY, () -> pick(false));
        removeCape = new Components.Button("Remove", "trash", Components.Variant.GHOST, () -> remove(false));
        model = new Components.Segmented(List.of("Classic arms", "Slim arms"), Settings.get().skinSlim ? 1 : 0, i -> {
            Settings.get().skinSlim = i == 1;
            Settings.get().save();
            repaint();
        });
        for (JComponent c : new JComponent[]{importSkin, removeSkin, upload, importCape, removeCape, model}) add(c);
    }

    @Override public String title() { return "Accessories"; }
    @Override public String icon() { return "user"; }

    @Override
    public void onShow() {
        skin = Accessories.skin();
        cape = Accessories.cape();
        removeSkin.setEnabled(skin != null);
        upload.setEnabled(skin != null);
        removeCape.setEnabled(cape != null);
        repaint();
    }

    private Rectangle card(int i) {
        int w = getWidth(), gap = 18, cw = (w - gap) / 2, top = 84;
        return new Rectangle(i * (cw + gap), top, cw, getHeight() - top);
    }

    @Override
    public void doLayout() {
        Rectangle a = card(0), b = card(1);
        int by = a.y + a.height - 150;
        Dimension md = model.getPreferredSize();
        model.setBounds(a.x + 20, by, Math.min(a.width - 40, md.width), 38);
        importSkin.setBounds(a.x + 20, by + 50, 150, 40);
        removeSkin.setBounds(a.x + 180, by + 50, 110, 40);
        upload.setBounds(a.x + 20, by + 100, a.width - 40, 36);
        importCape.setBounds(b.x + 20, by + 50, 150, 40);
        removeCape.setBounds(b.x + 180, by + 50, 110, 40);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.aa(g0.create());
        Theme.left(g, "Accessories", Theme.font(Theme.BOLD, 30f), Theme.TEXT, 0, 0, 42);
        Theme.left(g, "Your own skin and cape in Cobra Client. Other players only see the skin if you apply it to your account.",
                Theme.font(Theme.REGULAR, 13.5f), Theme.SOFT, 0, 44, 22);
        for (int i = 0; i < 2; i++) {
            Rectangle c = card(i);
            Theme.surface(g, this, c.x, c.y, c.width, c.height, 20);
            Theme.left(g, i == 0 ? "Skin" : "Cape", Theme.font(Theme.BOLD, 16f), Theme.TEXT, c.x + 20, c.y + 14, 26);
            Theme.left(g, i == 0 ? "PNG, 64×64 or old 64×32" : "PNG, 64×32, 64×64 or 32×32 (HD works too)",
                    Theme.font(Theme.REGULAR, 12.5f), Theme.MUTED, c.x + 20, c.y + 38, 20);
            int areaH = c.height - 150 - 80;
            Rectangle area = new Rectangle(c.x + 20, c.y + 66, c.width - 40, Math.max(60, areaH));
            Theme.fill(g, area.x, area.y, area.width, area.height, 14, Theme.alpha(Theme.BLACK, Theme.isLight() ? 0.05 : 0.35));
            BufferedImage img = i == 0 ? skin : cape;
            if (img == null) {
                Theme.center(g, i == 0 ? "No skin imported" : "No cape imported", Theme.font(Theme.MEDIUM, 14f), Theme.SOFT, area.x, area.y, area.width, area.height);
            } else if (i == 0) {
                drawSkin(g, img, Settings.get().skinSlim, area);
            } else {
                drawCape(g, img, area);
            }
        }
        g.dispose();
    }

    /** Front view of the skin: head, body, arms, legs, with the outer layer on top. */
    private static void drawSkin(Graphics2D g0, BufferedImage s, boolean slim, Rectangle area) {
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

    private void pick(boolean isSkin) {
        java.nio.file.Path picked = FilePicker.one(isSkin ? "Choose a skin" : "Choose a cape", "PNG pictures", "png");
        if (picked == null) return;
        try {
            if (isSkin) Accessories.importSkin(picked);
            else Accessories.importCape(picked);
            MainWindow.get().toast((isSkin ? "Skin" : "Cape") + " imported. It shows in game next launch.");
        } catch (Exception e) {
            MainWindow.get().toast(e.getMessage());
        }
        onShow();
    }

    private void remove(boolean isSkin) {
        try {
            if (isSkin) Accessories.removeSkin();
            else Accessories.removeCape();
        } catch (Exception ignored) {}
        onShow();
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

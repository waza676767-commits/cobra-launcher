package dev.cobra.client.core.ui;

import dev.cobra.client.core.Cobra;
import dev.cobra.client.core.Platform;
import dev.cobra.client.core.Render;

/**
 * Cobra main menu (the original layout): player top-left, icon bar top-centre, close top-right,
 * logo + COBRA, Singleplayer / Multiplayer. Follows light/dark; offline mode hides Multiplayer.
 */
public final class TitleMenu {
    private static final String[] ICONS = {"settings", "packs", "sliders"};
    private static final Platform.TitleAction[] ICON_ACTIONS = {Platform.TitleAction.OPTIONS, Platform.TitleAction.RESOURCE_PACKS, Platform.TitleAction.COBRA_MENU};
    private static final String[] ICON_TIPS = {"Options", "Resource Packs", "Cobra Settings"};
    private static boolean introPlayed;

    private final float[] hover = new float[12];   // one per button (Quit Game is #6)
    private final long openedAt = System.currentTimeMillis();
    private final boolean intro;
    private final Particles particles = new Particles();
    private float hoverVanilla;
    private int w, h, renderedButtonY;

    /**
     * Launcher wallpaper (animated too) with the menu's dimming, used behind Singleplayer,
     * Multiplayer and the other menus outside a world. False when there's no wallpaper.
     */
    public static boolean backdrop(Render r, int w, int h) {
        if (!r.wallpaper(w, h)) return false;
        r.rect(0, 0, w, h, Draw.light ? 0x88F4F4F6 : 0x70000000);
        return true;
    }

    public TitleMenu() {
        intro = !introPlayed && Cobra.animations;
        introPlayed = true;
    }

    private Render lastRender;

    public void render(Render r, int mx, int my) {
        lastRender = r;
        w = r.width();
        h = r.height();
        boolean light = Draw.light;
        int fg = Draw.FG;
        boolean shadow = !light;

        boolean wallpaper = r.wallpaper(w, h);
        if (wallpaper) r.rect(0, 0, w, h, light ? 0x88F4F4F6 : 0x70000000);
        else r.rect(0, 0, w, h, light ? 0xB8F4F4F6 : 0x8C000000);
        r.rect(0, 0, w, 28, light ? 0x70FFFFFF : 0x55000000);
        r.rect(0, 28, w, 1, light ? 0x22000000 : 0x22FFFFFF);

        // top-left: player
        r.head(8, 6, 16);
        r.text(Cobra.platform.playerName(), 30, 10, fg, shadow);

        // top-centre icons
        int start = w / 2 - (ICONS.length * 24 - 4) / 2;
        for (int i = 0; i < ICONS.length; i++) {
            int x = start + i * 24;
            iconButton(r, i, x, 4, ICONS[i], mx, my);
            if (hover[i] > 0.5f) {
                int tw = r.textWidth(ICON_TIPS[i]);
                Draw.round(r, x + 10 - tw / 2 - 4, 28, tw + 8, 13, 3, light ? 0xF0121316 : 0xE0000000);
                r.text(ICON_TIPS[i], x + 10 - tw / 2f, 31, 0xFFFFFFFF, false);
            }
        }
        iconButton(r, 3, w - 26, 4, "close", mx, my);
        // one click: the normal Minecraft main menu (Cobra's comes back after you play)
        int vw = Draw.spacedWidth(r, "VANILLA MENU", 0.6f) + 14, vx = w - 32 - vw;
        boolean vh = in(mx, my, vx, 4, vw, 20);
        hoverVanilla = Draw.approach(hoverVanilla, vh ? 1 : 0, 0.3f);
        if (Draw.light) Draw.box(r, vx, 4, vw, 20, 5, Draw.mix(0xE6FFFFFF, 0xFFFFFFFF, hoverVanilla), Draw.mix(0x55000000, 0xFF121316, hoverVanilla));
        else Draw.box(r, vx, 4, vw, 20, 5, Draw.mix(0xC0101012, 0xC02A2B2E, hoverVanilla), Draw.mix(0x60FFFFFF, 0xFFFFFFFF, hoverVanilla));
        Draw.label(r, "VANILLA MENU", vx + vw / 2f, 14, 0.75f, 0.6f, Draw.FG);

        // logo + name
        int logo = Math.min(56, h / 5);
        int ly = (int) (h * 0.24) - logo / 2 + Math.round(slide(0));
        r.texture("logo", w / 2f - logo / 2f, ly, logo, logo, fg);
        r.push();
        String name = "COBRA";
        float s = 2.6f;
        r.translate(w / 2f - r.textWidth(name) * s / 2f, ly + logo + 8);
        r.scale(s);
        r.text(name, 0, 0, fg, shadow);
        r.pop();
        String sub = "CLIENT  " + Cobra.platform.version();
        Draw.scaledText(r, sub, w / 2f - r.textWidth(sub) * 0.4f, ly + logo + 34, 0.8f, light ? 0xFF55565C : 0xFFB5B5B5, false);

        // main buttons
        int bw = Math.min(200, w - 40), bh = 20, bx = (w - bw) / 2;
        int by = (int) (h * 0.56) + Math.round(slide(250));   // buttons glide up after the logo
        renderedButtonY = by;
        mainButton(r, 4, bx, by, bw, bh, "Singleplayer", mx, my);
        if (Cobra.offline) {
            String note = "Offline mode: singleplayer only";
            Draw.scaledText(r, note, w / 2f - r.textWidth(note) * 0.4f, by + bh + 9, 0.8f, Draw.MUTED, false);
        } else {
            mainButton(r, 5, bx, by + bh + 6, bw, bh, "Multiplayer", mx, my);
        }
        mainButton(r, 6, bx, by + 2 * (bh + 6), bw, bh, "Quit Game", mx, my);

        // footer
        r.text(Cobra.NAME + " " + Cobra.VERSION + " (" + Cobra.platform.version() + ")", 4, h - 10, light ? 0xFF55565C : 0xFF9A9B9F, shadow);
        String copy = "Copyright Mojang AB. Do not distribute!";
        r.text(copy, w - r.textWidth(copy) - 3, h - 10, fg, shadow);

        particles.render(r);
        if (intro) {
            float t = (System.currentTimeMillis() - openedAt) / 700f;
            if (t < 1) r.rect(0, 0, w, h, Draw.alpha(light ? 0xFFFFFFFF : 0xFF000000, 1 - t * t));
        }
    }

    /** Entrance offset: starts 14 px low and settles, delayed by delayMs (first opening only). */
    private float slide(long delayMs) {
        if (!intro) return 0;
        float t = (System.currentTimeMillis() - openedAt - delayMs) / 600f;
        t = Math.max(0, Math.min(1, t));
        return (1 - (1 - (1 - t) * (1 - t) * (1 - t))) * 14;
    }

    private void iconButton(Render r, int i, int x, int y, String icon, int mx, int my) {
        boolean hov = in(mx, my, x, y, 20, 20);
        if (i < 0 || i >= hover.length) i = 0;
        hover[i] = Draw.approach(hover[i], hov ? 1 : 0, 0.3f);
        float t = hover[i];
        if (Draw.light) Draw.box(r, x, y, 20, 20, 5, Draw.mix(0xE6FFFFFF, 0xFFFFFFFF, t), Draw.mix(0x55000000, 0xFF121316, t));
        else Draw.box(r, x, y, 20, 20, 5, Draw.mix(0xC0101012, 0xC02A2B2E, t), Draw.mix(0x60FFFFFF, 0xFFFFFFFF, t));
        r.texture("icon/" + icon, x + 4, y + 4, 12, 12, Draw.FG);
    }

    /** Thin outlined button: white outline on dark, black outline on light. */
    private void mainButton(Render r, int i, int x, int y, int bw, int bh, String label, int mx, int my) {
        boolean hov = in(mx, my, x, y, bw, bh);
        hover[i] = Draw.approach(hover[i], hov ? 1 : 0, 0.3f);
        float t = hover[i];
        if (Draw.light) {
            r.rect(x, y, bw, bh, Draw.mix(0x88FFFFFF, 0xFF121316, t));
            Draw.outline(r, x, y, bw, bh, Draw.mix(0x88000000, 0xFF121316, t));
            Draw.centered(r, label, x + bw / 2f, y + (bh - 8) / 2f, Draw.mix(0xFF121316, 0xFFFFFFFF, t), false);
        } else {
            r.rect(x, y, bw, bh, Draw.mix(0x50000000, 0xFFFFFFFF, t));
            Draw.outline(r, x, y, bw, bh, Draw.mix(0x90FFFFFF, 0xFFFFFFFF, t));
            Draw.centered(r, label, x + bw / 2f, y + (bh - 8) / 2f, Draw.mix(0xFFFFFFFF, 0xFF000000, t), t < 0.5f);
        }
    }

    public void mouseClicked(double dmx, double dmy, int button) {
        int mx = (int) dmx, my = (int) dmy;
        particles.burst(mx, my);
        if (button != 0) return;
        int start = w / 2 - (ICONS.length * 24 - 4) / 2;
        for (int i = 0; i < ICONS.length; i++) {
            if (in(mx, my, start + i * 24, 4, 20, 20)) {
                Cobra.platform.titleAction(ICON_ACTIONS[i]);
                return;
            }
        }
        if (in(mx, my, w - 26, 4, 20, 20)) {
            Cobra.platform.titleAction(Platform.TitleAction.QUIT);
            return;
        }
        if (lastRender != null) {
            int vw = Draw.spacedWidth(lastRender, "VANILLA MENU", 0.6f) + 14;
            if (in(mx, my, w - 32 - vw, 4, vw, 20)) {
                Cobra.platform.titleAction(Platform.TitleAction.VANILLA_MENU);
                return;
            }
        }
        int bw = Math.min(200, w - 40), bh = 20, bx = (w - bw) / 2, by = renderedButtonY;
        if (in(mx, my, bx, by, bw, bh)) Cobra.platform.titleAction(Platform.TitleAction.SINGLEPLAYER);
        else if (!Cobra.offline && in(mx, my, bx, by + bh + 6, bw, bh)) Cobra.platform.titleAction(Platform.TitleAction.MULTIPLAYER);
        else if (in(mx, my, bx, by + 2 * (bh + 6), bw, bh)) Cobra.platform.titleAction(Platform.TitleAction.QUIT);
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    /**
     * Joining a server / loading a world: the Cobra mark breathing in the middle, with a ring of
     * dots running round it. Drawn over Minecraft's own connecting screens.
     */
    public static void loading(Render r, int w, int h) {
        long t = System.currentTimeMillis();
        float cx = w / 2f, cy = h / 2f - 46;
        float pulse = 0.5f + 0.5f * (float) Math.sin(t / 380.0);
        int size = Math.round(26 + 4 * pulse);
        r.texture("logo", cx - size / 2f, cy - size / 2f, size, size, Draw.alpha(Draw.FG, 0.75f + 0.25f * pulse));
        int dots = 12;
        float head = (t % 1200) / 1200f * dots;
        for (int i = 0; i < dots; i++) {
            double a = Math.PI * 2 * i / dots - Math.PI / 2;
            float dist = (head - i + dots) % dots;              // 0 = the leading dot
            float fade = Math.max(0.12f, 1 - dist / 6f);
            int d = dist < 1 ? 3 : 2;
            float x = cx + (float) Math.cos(a) * 24, y = cy + (float) Math.sin(a) * 24;
            r.rect(Math.round(x - d / 2f), Math.round(y - d / 2f), d, d, Draw.alpha(Draw.FG, fade));
        }
    }
}

package dev.cobra.client.core.ui;

import dev.cobra.client.core.Cobra;
import dev.cobra.client.core.Render;
import dev.cobra.client.core.module.Category;
import dev.cobra.client.core.module.Features;
import dev.cobra.client.core.module.HudModule;
import dev.cobra.client.core.module.Module;
import dev.cobra.client.core.module.Setting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Right Shift menu: Mods / Settings / Waypoints tabs with a sliding underline, a side panel
 * (player, categories with counts, modules-on bar, HUD buttons), a search bar, and module tiles:
 * icon, name and description on the left, a switch on the right, a gear when it has options.
 * Black see-through glass over the blurred game (light mode: white glass).
 */
public final class CobraMenu {
    public static final int KEY_OTHER = 0, KEY_ESCAPE = 1, KEY_BACKSPACE = 2, KEY_ENTER = 3;

    private static final String[] TABS = {"MODS", "SETTINGS", "WAYPOINTS"};
    private static final String[] CHIPS = {"All", "HUD", "Visual", "Utility", "Hypixel"};
    private static final String[] CHIP_ICONS = {"list", "gauge", "eye", "compass", "star"};
    private static final int CARD_H = 34, GAP = 5, ROW = 20, CHIP_H = 15;
    private static final int ON = 0xFF2E9E5B, ON_HOVER = 0xFF37B46A, OFF = 0xFFC7404F, OFF_HOVER = 0xFFDA4B5B;

    /** Set when the close button is clicked; the platform screen closes itself. */
    public boolean closeRequested;

    private int tab, chip;
    private boolean editing;
    private String search = "";
    private boolean searchFocus;
    private Module open;
    private Setting.Text textFocus;
    private Setting.Bind listening;
    private Setting.Number sliding;
    private int slideX, slideW;
    private float scroll, scrollTarget, appear;
    private float tabAnim, contentFade = 1;
    private final Map<Module, Float> cardHover = new HashMap<Module, Float>();
    private final Map<Module, Float> onAnim = new HashMap<Module, Float>();
    private float tabLine = -1, tabLineW;
    private long revealAt = System.currentTimeMillis();
    private final ColorPicker picker = new ColorPicker();
    private final Particles particles = new Particles();
    private HudModule drag;
    private float dragDx, dragDy;
    private int lastW, lastH;
    private Render lastRender;

    // layout (every frame)
    private int px, py, pw, ph, lx, lw, cx0, cy0, cw0, ch0, cols, cardW;

    public boolean editingHud() { return editing; }

    public CobraMenu() {
        this(false);
    }

    /** @param startInEditor Right Shift in game: open on the HUD editor, with a button to the modules */
    public CobraMenu(boolean startInEditor) {
        editing = startInEditor;
        startedInEditor = startInEditor;
    }

    private final boolean startedInEditor;

    // HUD editor: resizing, smart snapping, the button to the modules
    private HudModule resizing;
    private float resizeStartScale, resizeStartW;
    private int guideX = -1, guideY = -1;
    private float modsBtnHover;

    private int modsBtnW() { return 22; }

    private int modsBtnX() { return lastW / 2 - modsBtnW() / 2; }

    private int modsBtnY() { return lastH / 2 - modsBtnW() / 2; }

    public boolean typing() { return searchFocus || textFocus != null || listening != null || nameEdit != null; }

    public void setRender(Render r) { lastRender = r; }

    // ------------------------------------------------------------------ palette

    // Black glass over the blurred game (light mode: white glass). Only ENABLED/DISABLED keep colour.
    // colours follow the theme (the launcher's, when "Use the launcher's colours" is on)
    private static int tintOf(int a, int rgb) { return a << 24 | (rgb & 0xFFFFFF); }
    private static int backdrop() { return Draw.light ? 0x38FFFFFF : tintOf(0x48, Draw.BG == 0 ? 0 : Draw.mix(Draw.BG, 0xFF000000, 0.5f)); }
    private static int panel() { return Draw.light ? 0x9EF7F7F8 : tintOf(0x78, Draw.BG); }
    private static int panelLine() { return tintOf(0x40, Draw.light ? 0xFFFFFF : Draw.FG); }
    private static int side() { return Draw.light ? 0x66FFFFFF : 0x55000000; }
    private static int card() { return Draw.light ? 0x94FFFFFF : tintOf(0x50, Draw.mix(Draw.BG, 0xFF000000, 0.35f)); }
    private static int cardHov() { return Draw.light ? 0xD9FFFFFF : tintOf(0x1C, Draw.FG); }
    private static int line() { return Draw.light ? 0x22000000 : tintOf(0x24, Draw.FG); }
    private static int soft() { return Draw.light ? 0xFF505257 : Draw.SOFT; }
    private static int muted() { return Draw.light ? 0xFF8C8E94 : Draw.MUTED; }
    /** Selection colour: white on dark, black on light. */
    private static int accent() {
        int a = Cobra.platform == null ? 0 : Cobra.get(dev.cobra.client.core.module.Features.Client.class).accentArgb();
        if (a != 0) return a;
        return Draw.light ? 0xFF111113 : 0xFFF2F2F3;
    }

    /** Text drawn on top of the accent: dark on bright accents, white on dark ones. */
    private static int onAccent() {
        int a = accent();
        int lum = ((a >> 16 & 255) * 299 + (a >> 8 & 255) * 587 + (a & 255) * 114) / 1000;
        return lum > 150 ? 0xFF0A0A0B : 0xFFFFFFFF;
    }

    // ------------------------------------------------------------------ motion

    /** Restart the staggered entrance (menu open, tab / category / search change). */
    private void restartReveal() { revealAt = System.currentTimeMillis(); }

    /** How far item i still sits below its place: cards rise in one after another. */
    private float rise(int i, int stagger) {
        if (!Cobra.animations) return 0;
        float t = (System.currentTimeMillis() - revealAt - i * stagger) / 280f;
        t = Math.max(0, Math.min(1, t));
        float e = 1 - (1 - t) * (1 - t) * (1 - t);
        return (1 - e) * 12;
    }

    /** ENABLED/DISABLED colour glides between red and green. */
    private int onColor(Module m, boolean hov) {
        Float prev = onAnim.get(m);
        float v = Draw.approach(prev == null ? (m.isEnabled() ? 1 : 0) : prev, m.isEnabled() ? 1 : 0, 0.28f);
        onAnim.put(m, v);
        return Draw.mix(hov ? OFF_HOVER : OFF, hov ? ON_HOVER : ON, v);
    }

    // ================================================================= render

    /** Scale of the modules panel: 1 when it fits, smaller in small windows (never squeezed). */
    private float fit = 1;

    public void render(Render r, int mx, int my) {
        float user = Cobra.platform == null ? 1f : Cobra.get(dev.cobra.client.core.module.Features.Client.class).menuScale.f() / 100f;
        float want = editing ? 1 : Math.max(0.45f, Math.min(user, Math.min((r.width() - 16) / 520f, (r.height() - 16) / 300f)));
        // while you drag a slider (e.g. Menu size itself) the menu keeps its size, so the slider
        // doesn't run away from the mouse; the new size applies when you let go
        if (sliding == null && drag == null && resizing == null) fit = want;
        if (Math.abs(fit - 1f) < 0.001f) {
            fit = 1;
            renderScaled(r, mx, my);
            return;
        }
        r.push();
        r.scale(fit);
        renderScaled(r, Math.round(mx / fit), Math.round(my / fit));
        r.pop();
    }

    private void renderScaled(Render r, int mx, int my) {
        lastW = Math.round(r.width() / fit);
        lastH = Math.round(r.height() / fit);
        appear = Draw.approach(appear, 1, 0.22f);
        scroll = Draw.approach(scroll, scrollTarget, 0.35f);
        tabAnim = Draw.approach(tabAnim, tab, 0.3f);
        contentFade = Draw.approach(contentFade, 1, 0.2f);

        if (editing) {
            renderEditor(r, mx, my);
            particles.render(r);
            return;
        }
        r.rect(0, 0, lastW, lastH, Draw.alpha(backdrop(), appear));

        pw = Math.min(520, lastW - 16);          // with the scaling above this is 520 x 300 unless the window is tiny
        ph = Math.min(300, lastH - 16);
        px = (lastW - pw) / 2;
        // opening animation: rises in and pops to size with a small overshoot
        float back = appear >= 1 ? 1 : 1 + 2.2f * (float) Math.pow(appear - 1, 3) + 1.2f * (float) Math.pow(appear - 1, 2);
        py = (lastH - ph) / 2 + Math.round((1 - appear) * 40);
        float s = 0.86f + 0.14f * back;
        r.push();
        r.translate(lastW / 2f, lastH / 2f);
        r.scale(s);
        r.translate(-lastW / 2f, -lastH / 2f);

        Draw.box(r, px, py, pw, ph, 6, panel(), panelLine());

        // ----- header: logo, name, tabs, close
        r.texture("logo", px + 10, py + 8, 13, 13, Draw.FG);
        int nx = px + 28;
        nx += Draw.spaced(r, "\u00a7lCOBRA", nx, py + 11, 0.8f, Draw.FG, false) + 5;
        Draw.spaced(r, "CLIENT", nx, py + 11, 0.8f, soft(), false);
        int tx = px + 150;
        int trackW = 0;
        for (int i = 0; i < TABS.length; i++) trackW += Draw.spacedWidth(r, TABS[i], 0.8f) + 16 + (i > 0 ? 2 : 0);
        Draw.round(r, tx - 3, py + 5, trackW + 6, 19, 9, Draw.light ? 0x14000000 : 0x14FFFFFF);   // the track
        for (int i = 0; i < TABS.length; i++) {
            int w = Draw.spacedWidth(r, TABS[i], 0.8f) + 16;
            if (tab == i) {  // the white thumb glides to the selected tab
                if (tabLine < 0) {
                    tabLine = tx;
                    tabLineW = w;
                }
                tabLine = Draw.approach(tabLine, tx, 0.3f);
                tabLineW = Draw.approach(tabLineW, w, 0.3f);
            }
            tx += w + 2;
        }
        Draw.round(r, Math.round(tabLine), py + 7, Math.round(tabLineW), 15, 7, accent());
        tx = px + 150;
        for (int i = 0; i < TABS.length; i++) {
            int w = Draw.spacedWidth(r, TABS[i], 0.8f) + 16;
            boolean hov = in(mx, my, tx, py + 6, w, 17);
            boolean sel = tab == i;
            Draw.spaced(r, TABS[i], tx + 8, py + 11, 0.8f, sel ? onAccent() : hov ? Draw.FG : muted(), false);
            tx += w + 2;
        }
        boolean hc = in(mx, my, px + pw - 23, py + 6, 17, 17);
        Draw.box(r, px + pw - 23, py + 6, 17, 17, 3, hc ? 0xFFC7404F : card(), hc ? 0xFFC7404F : line());
        r.texture("icon/close", px + pw - 19, py + 10, 9, 9, hc ? 0xFFFFFFFF : Draw.FG);
        r.rect(px + 1, py + 29, pw - 2, 1, line());

        // ----- left: player + HUD buttons
        lx = px + 8;
        lw = 104;
        int ly = py + 36;
        Draw.box(r, lx, ly, lw, ph - 44, 5, side(), line());
        r.head(lx + 8, ly + 8, 22);
        String name = Cobra.platform.playerName();
        r.text(clip(r, name, lw - 40), lx + 35, ly + 9, Draw.FG, false);
        Draw.scaledText(r, Cobra.platform.version() + (Cobra.offline ? "  offline" : ""), lx + 35, ly + 20, 0.7f, muted(), false);
        int onCount = 0, total = 0;
        for (Module m : Cobra.MODULES) {
            if (m.hidden) continue;
            total++;
            if (m.isEnabled()) onCount++;
        }
        // categories (Mods tab), with how many modules each has
        if (tab == 0) {
            Draw.spaced(r, "CATEGORIES", lx + 8, ly + 36, 0.6f, muted(), false);
            for (int i = 0; i < CHIPS.length; i++) {
                int cy = ly + 46 + i * (CHIP_H + 2);
                boolean sel = chip == i && open == null, hov = in(mx, my, lx + 4, cy, lw - 8, CHIP_H);
                if (sel) Draw.round(r, lx + 4, cy, lw - 8, CHIP_H, 4, accent());
                else if (hov) Draw.round(r, lx + 4, cy, lw - 8, CHIP_H, 4, cardHov());
                int col = sel ? onAccent() : hov ? Draw.FG : soft();
                r.texture("icon/" + CHIP_ICONS[i], lx + 9, cy + 3, 9, 9, col);
                r.text(CHIPS[i], lx + 22, cy + 4, col, false);
                String n = String.valueOf(countIn(i));
                Draw.scaledText(r, n, lx + lw - 10 - r.textWidth(n) * 0.7f, cy + 5, 0.7f, sel ? Draw.alpha(onAccent(), 0.7f) : muted(), false);
            }
        }
        int barY = py + ph - 8 - 18 - 21 - 16;
        Draw.scaledText(r, onCount + " of " + total + " modules on", lx + 8, barY, 0.7f, soft(), false);
        r.rect(lx + 8, barY + 9, lw - 16, 2, line());
        r.rect(lx + 8, barY + 9, Math.round((lw - 16) * onCount / (float) Math.max(1, total)), 2, accent());
        int by = py + ph - 8 - 18;
        wideButton(r, lx + 6, by, lw - 12, 16, "EDIT HUD LAYOUT", mx, my, true);
        wideButton(r, lx + 6, by - 21, lw - 12, 16, "RESET HUD", mx, my, false);

        // ----- content
        cx0 = lx + lw + 8;
        cy0 = py + 36;
        cw0 = px + pw - 8 - cx0;
        ch0 = ph - 44;
        if (tab == 0 && open == null) renderMods(r, mx, my);
        else if (tab == 0 || tab == 1) renderSettings(r, mx, my, tab == 1 ? Cobra.get(Features.Client.class) : open);
        else renderWaypoints(r, mx, my);

        r.pop();
        if (picker.isOpen()) picker.render(r, mx, my);
        particles.render(r);
    }

    private int countIn(int chipIndex) {
        int n = 0;
        for (Module m : Cobra.MODULES) {
            if (m.hidden) continue;
            if (chipIndex > 0 && m.category != Category.values()[chipIndex - 1]) continue;
            n++;
        }
        return n;
    }

    private List<Module> visibleModules() {
        List<Module> out = new ArrayList<Module>();
        String q = search.toLowerCase(Locale.ROOT);
        Setting.Group.search = q;                           // fold-out groups open when the search hits them
        for (Module m : Cobra.MODULES) {
            if (m.hidden) continue;
            if (chip > 0 && m.category != Category.values()[chip - 1]) continue;
            if (!q.isEmpty() && !m.matches(q)) continue;
            out.add(m);
        }
        return out;
    }

    private void renderMods(Render r, int mx, int my) {
        // search across the top, count on the right
        List<Module> mods = visibleModules();
        String count = mods.size() + (mods.size() == 1 ? " module" : " modules");
        int cw = Math.round(r.textWidth(count) * 0.7f);
        int sw = cw0 - cw - 10;
        Draw.box(r, cx0, cy0, sw, 15, 7, card(), searchFocus ? accent() : line());
        r.texture("icon/search", cx0 + 6, cy0 + 4, 7, 7, searchFocus ? Draw.FG : muted());
        String shown = search.isEmpty() && !searchFocus ? "Search modules" : search + (searchFocus && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        r.text(clip(r, shown, sw - 22), cx0 + 17, cy0 + 4, search.isEmpty() && !searchFocus ? muted() : Draw.FG, false);
        Draw.scaledText(r, count, cx0 + cw0 - cw, cy0 + 5, 0.7f, muted(), false);

        int gy = cy0 + 21, gh = ch0 - 21;
        cols = cw0 >= 420 ? 3 : 2;
        cardW = (cw0 - (cols - 1) * GAP) / cols;
        int rows = (mods.size() + cols - 1) / cols;
        scrollTarget = Math.max(0, Math.min(Math.max(0, rows * (CARD_H + GAP) - GAP - gh), scrollTarget));
        r.scissor(cx0 - 2, gy - 1, cw0 + 4, gh + 2);
        boolean mouseIn = in(mx, my, cx0, gy, cw0, gh) && !picker.isOpen();
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            int x0 = cx0 + (i % cols) * (cardW + GAP);
            int yBase = gy + (i / cols) * (CARD_H + GAP) - Math.round(scroll);
            if (yBase + CARD_H < gy - 2 || yBase > gy + gh + 2) continue;
            boolean hov = mouseIn && in(mx, my, x0, yBase, cardW, CARD_H);
            int y0 = yBase + Math.round(rise(i, 22));
            Float hv = cardHover.get(m);
            float h = Draw.approach(hv == null ? 0 : hv, hov ? 1 : 0, 0.3f);
            cardHover.put(m, h);
            Float pv = onAnim.get(m);
            float on = Draw.approach(pv == null ? (m.isEnabled() ? 1 : 0) : pv, m.isEnabled() ? 1 : 0, 0.28f);
            onAnim.put(m, on);

            // tile: brighter when on, accent stripe on the left
            Draw.box(r, x0, y0, cardW, CARD_H, 5, Draw.mix(card(), cardHov(), Math.max(h, on * 0.55f)), hov ? Draw.alpha(accent(), 0.6f) : line());
            if (on > 0.01f) Draw.round(r, x0 + 2, y0 + 7, 2, CARD_H - 14, 1, Draw.alpha(accent(), on));
            // icon in a small rounded square (filled when on)
            int ix = x0 + 8, iy = y0 + (CARD_H - 20) / 2;
            Draw.round(r, ix, iy, 20, 20, 5, Draw.mix(Draw.alpha(line(), 1f), accent(), on));
            r.texture("icon/" + m.icon, ix + 4, iy + 4, 12, 12, Draw.mix(soft(), onAccent(), on));
            // name + description
            int tx = ix + 26, textW = cardW - (tx - x0) - 34;
            r.text(clip(r, m.name, textW), tx, y0 + 8, m.problem != null ? 0xFFE0676F : Draw.mix(soft(), Draw.FG, Math.max(on, h)), false);
            String sub = m.problem != null ? m.problem : m.description;
            Draw.scaledText(r, clipScaled(r, sub, textW, 0.65f), tx, y0 + 20, 0.65f, muted(), false);
            // switch (right) and gear (above it) when the module has options
            int swx = x0 + cardW - 26, swy = y0 + CARD_H - 15;
            Draw.toggle(r, swx, swy, on, panel() | 0xFF000000);
            if (!m.settings.isEmpty()) {
                boolean hg = mouseIn && in(mx, my, x0 + cardW - 20, y0 + 3, 14, 12);
                r.texture("icon/settings", x0 + cardW - 17, y0 + 5, 8, 8, hg ? Draw.FG : muted());
            }
        }
        if (mods.isEmpty()) Draw.centered(r, "No modules match \"" + search + "\"", cx0 + cw0 / 2f, gy + 30, soft(), false);
        r.noScissor();
    }

    private static String clipScaled(Render r, String s, int max, float scale) {
        if (r.textWidth(s) * scale <= max) return s;
        while (s.length() > 1 && r.textWidth(s + "...") * scale > max) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    private void renderSettings(Render r, int mx, int my, Module m) {
        boolean client = m instanceof Features.Client;
        if (!client) wideButton(r, cx0, cy0, 44, 15, "< BACK", mx, my, false);
        int tx = client ? cx0 : cx0 + 52;
        if (!client) r.texture("icon/" + m.icon, tx, cy0 + 2, 11, 11, Draw.FG);
        r.text(client ? "Client settings" : m.name, tx + (client ? 0 : 15), cy0 + 4, Draw.FG, false);
        if (!client) {
            Float pv = onAnim.get(m);
            float on = Draw.approach(pv == null ? (m.isEnabled() ? 1 : 0) : pv, m.isEnabled() ? 1 : 0, 0.28f);
            onAnim.put(m, on);
            String st = m.isEnabled() ? "On" : "Off";
            Draw.scaledText(r, st, cx0 + cw0 - 24 - r.textWidth(st) * 0.75f, cy0 + 5, 0.75f, m.isEnabled() ? Draw.FG : muted(), false);
            Draw.toggle(r, cx0 + cw0 - 20, cy0 + 3, on, panel() | 0xFF000000);
        }
        // Reset: every setting of this module back to normal (confirm with a second click)
        boolean confirm = resetArmed == m && System.currentTimeMillis() - resetAt < 3000;
        String rl = confirm ? "SURE?" : "RESET";
        int rw = Draw.spacedWidth(r, rl, 0.6f) + 14;
        wideButton(r, resetX(m, rw), cy0, rw, 15, rl, mx, my, confirm);
        String sub = m.problem != null ? m.problem : (client ? "Theme, animations, blur and the menu key" : m.description);
        Draw.scaledText(r, sub, cx0, cy0 + 20, 0.72f, m.problem != null ? 0xFFE0676F : soft(), false);

        int top = cy0 + 32, areaH = ch0 - 32;
        List<Setting<?>> list = m.shownSettings();
        scrollTarget = Math.max(0, Math.min(Math.max(0, list.size() * ROW - areaH), scrollTarget));
        r.scissor(cx0 - 2, top - 1, cw0 + 4, areaH + 2);
        for (int i = 0; i < list.size(); i++) {
            Setting<?> st = list.get(i);
            int yBase = top + i * ROW - Math.round(scroll);
            if (yBase + ROW < top - 2 || yBase > top + areaH) continue;
            int y = yBase + Math.round(rise(i, 18));
            int right = cx0 + cw0 - 2;
            if (in(mx, my, cx0, y, cw0, ROW) && !picker.isOpen()) r.rect(cx0, y, cw0, ROW - 1, Draw.alpha(accent(), 0.07f));
            r.rect(cx0, y + ROW - 1, cw0, 1, line());
            if (st instanceof Setting.Group) {                     // a fold-out heading
                Setting.Group gr = (Setting.Group) st;
                boolean open = gr.open();
                Draw.spaced(r, st.name.toUpperCase(Locale.ROOT), cx0 + 4, y + 7, 0.62f, Draw.FG, false);
                String arrow = open ? "-" : "+";
                r.text(arrow, right - r.textWidth(arrow) - 2, y + 6, soft(), false);
                continue;
            }
            if (st instanceof Setting.Action) {
                wideButton(r, cx0 + 2, y + 3, Math.min(cw0 - 4, Draw.spacedWidth(r, st.name.toUpperCase(Locale.ROOT), 0.6f) + 16), 14,
                        st.name.toUpperCase(Locale.ROOT), mx, my, false);
                continue;
            }
            r.text(st.name, cx0 + 4, y + 6, Draw.FG, false);
            if (st instanceof Setting.Bool) {
                Draw.toggle(r, right - 20, y + 5, ((Setting.Bool) st).on() ? 1 : 0, panel() | 0xFF000000);
            } else if (st instanceof Setting.Number) {
                Setting.Number n = (Setting.Number) st;
                String v = n.display();
                int vw = r.textWidth(v);
                int bw = 90, bx = right - vw - 8 - bw;
                float frac = (float) ((n.get() - n.min) / (n.max - n.min));
                r.rect(bx, y + 9, bw, 2, line());
                r.rect(bx, y + 9, Math.round(bw * frac), 2, accent());
                Draw.round(r, bx + Math.round(bw * frac) - 3, y + 7, 6, 6, 3, Draw.FG);
                r.text(v, right - vw, y + 6, soft(), false);
            } else if (st instanceof Setting.Mode) {
                String v = "< " + ((Setting.Mode) st).get() + " >";
                boolean hv = in(mx, my, right - r.textWidth(v), y, r.textWidth(v), ROW);
                r.text(v, right - r.textWidth(v), y + 6, hv ? Draw.FG : soft(), false);
            } else if (st instanceof Setting.Color) {
                int[] pr = Setting.Color.PRESETS;
                int argb = ((Setting.Color) st).argb();
                int cur = right - 16;
                Draw.box(r, cur, y + 3, 16, 14, 3, 0xFF000000 | argb, line());
                r.rect(cur + 10, y + 11, 5, 5, 0xFF000000);
                r.rect(cur + 11, y + 12, 3, 3, 0xFFFFFFFF);
                for (int k = 0; k < pr.length; k++) {
                    int sx = cur - 6 - (pr.length - k) * 10;
                    if ((argb | 0xFF000000) == pr[k]) r.rect(sx - 1, y + 4, 10, 10, Draw.FG);
                    r.rect(sx, y + 5, 8, 8, pr[k]);
                }
            } else if (st instanceof Setting.Text) {
                Setting.Text t = (Setting.Text) st;
                boolean f = textFocus == t;
                Draw.box(r, right - 90, y + 3, 90, 14, 3, card(), f ? accent() : line());
                String v = t.get() + (f && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
                r.text(clip(r, v, 82), right - 86, y + 6, Draw.FG, false);
            } else if (st instanceof Setting.Bind) {
                Setting.Bind b = (Setting.Bind) st;
                String label = listening == b ? "Press a key..." : Cobra.platform.keyName(b.code());
                int bw = Math.max(50, r.textWidth(label) + 14);
                boolean hv = in(mx, my, right - bw, y + 3, bw, 14);
                Draw.box(r, right - bw, y + 3, bw, 14, 3, listening == b ? accent() : hv ? cardHov() : card(), line());
                Draw.centered(r, label, right - bw / 2f, y + 6, listening == b ? onAccent() : Draw.FG, false);
            }
        }
        r.noScissor();
    }

    // ------------------------------------------------------------------ waypoints tab

    private static final int WP_ROW = 24;
    /** The waypoint whose name is being typed, and what's typed so far. */
    private Features.Waypoints.Point nameEdit;
    private String nameBuf = "";
    /** Set when the menu was opened just to name a new waypoint: Enter / Esc closes it again. */
    private boolean closeAfterName;
    /** The colour wheel edits this stand-in, which is copied onto the waypoint being recoloured. */
    private final Setting.Color wpColor = new Setting.Color("wpcolor", "Colour", 0xFFFFFFFF);
    private Features.Waypoints.Point colorFor;

    /** Opens straight on the Waypoints tab with this waypoint's name ready to type (the B key). */
    public void nameWaypoint(Features.Waypoints.Point pt) {
        editing = false;
        tab = 2;
        appear = Math.max(appear, 0.6f);
        startName(pt);
        closeAfterName = true;
    }

    private void startName(Features.Waypoints.Point pt) {
        nameEdit = pt;
        nameBuf = pt.name;
    }

    private void commitName() {
        if (nameEdit == null) return;
        Cobra.get(Features.Waypoints.class).rename(nameEdit, nameBuf);
        nameEdit = null;
    }

    private void renderWaypoints(Render r, int mx, int my) {
        Features.Waypoints wp = Cobra.get(Features.Waypoints.class);
        if (colorFor != null) {                                    // live preview while the wheel is open
            if (picker.isOpen()) colorFor.color = 0xFF000000 | (wpColor.argb() & 0xFFFFFF);
            else {
                wp.recolor(colorFor, wpColor.argb());
                colorFor = null;
            }
        }
        r.text("Waypoints", cx0, cy0 + 4, Draw.FG, false);
        wideButton(r, cx0 + cw0 - 104, cy0, 104, 15, "ADD HERE", mx, my, true);
        List<Features.Waypoints.Point> list = wp.here();
        Draw.scaledText(r, Cobra.platform.inWorld() ? list.size() + " here. Click a name to rename it, the colour to change it. Key: "
                + Cobra.platform.keyName(Cobra.platform.bindCode("waypoint")) : "Join a world to see its waypoints.",
                cx0, cy0 + 20, 0.72f, soft(), false);
        int top = cy0 + 32, areaH = ch0 - 32;
        scrollTarget = Math.max(0, Math.min(Math.max(0, list.size() * WP_ROW - areaH), scrollTarget));
        r.scissor(cx0 - 2, top - 1, cw0 + 4, areaH + 2);
        boolean mouseIn = in(mx, my, cx0, top, cw0, areaH) && !picker.isOpen();
        double[] cam = Cobra.platform.camera();
        for (int i = 0; i < list.size(); i++) {
            Features.Waypoints.Point pt = list.get(i);
            int yBase = top + i * WP_ROW - Math.round(scroll);
            if (yBase + WP_ROW < top - 2 || yBase > top + areaH) continue;
            int y = yBase + Math.round(rise(i, 18));
            boolean hovRow = mouseIn && in(mx, my, cx0, y, cw0, WP_ROW - 3);
            int c = 0xFF000000 | (pt.color & 0xFFFFFF);
            Draw.box(r, cx0, y, cw0, WP_ROW - 3, 4, hovRow ? cardHov() : card(), hovRow ? Draw.alpha(c, 0.7f) : line());
            Draw.round(r, cx0 + 1, y + 4, 2, WP_ROW - 11, 1, c);                    // colour stripe
            // colour swatch (opens the colour wheel)
            boolean hs = mouseIn && in(mx, my, cx0 + 7, y + 3, 15, 15);
            Draw.round(r, cx0 + 7, y + 3, 15, 15, 4, hs ? Draw.FG : line());
            Draw.round(r, cx0 + 8, y + 4, 13, 13, 3, c);
            Draw.round(r, cx0 + 10, y + 6, 4, 3, 1, 0x50FFFFFF);                     // a little shine
            // the name: click to type a new one
            String coords = pt.x + ", " + pt.y + ", " + pt.z;
            if (cam != null) {
                double dx = pt.x + 0.5 - cam[0], dy = pt.y - cam[1], dz = pt.z + 0.5 - cam[2];
                coords += "   " + Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)) + "m";
            }
            int coordsW = Math.round(r.textWidth(coords) * 0.72f);
            int nx = cx0 + 28, nw = cw0 - 28 - coordsW - 34;
            boolean editingThis = nameEdit == pt;
            boolean hn = mouseIn && in(mx, my, nx - 3, y + 3, nw + 6, 15);
            if (editingThis) Draw.box(r, nx - 3, y + 3, nw + 6, 15, 3, panel() | 0xFF000000, accent());
            else if (hn) Draw.round(r, nx - 3, y + 3, nw + 6, 15, 3, Draw.alpha(Draw.FG, 0.06f));
            String shown = editingThis ? nameBuf + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "") : pt.name;
            if (editingThis && nameBuf.isEmpty()) r.text("Type a name...", nx, y + 7, muted(), false);
            if (!editingThis || !nameBuf.isEmpty()) r.text(clip(r, shown, nw), nx, y + 7, Draw.FG, false);
            if (hn && !editingThis) {
                String hint = "rename";
                Draw.scaledText(r, hint, nx + nw - r.textWidth(hint) * 0.65f, y + 8, 0.65f, muted(), false);
            }
            Draw.scaledText(r, coords, cx0 + cw0 - 26 - coordsW, y + 8, 0.72f, soft(), false);
            boolean hd = mouseIn && in(mx, my, cx0 + cw0 - 20, y + 3, 15, 15);
            if (hd) Draw.round(r, cx0 + cw0 - 20, y + 3, 15, 15, 3, 0xFFC7404F);
            r.texture("icon/trash", cx0 + cw0 - 16, y + 7, 8, 8, hd ? 0xFFFFFFFF : soft());
        }
        r.noScissor();
        if (list.isEmpty() && Cobra.platform.inWorld()) Draw.centered(r, "No waypoints here yet", cx0 + cw0 / 2f, top + 20, muted(), false);
    }

    private void renderEditor(Render r, int mx, int my) {
        boolean world = Cobra.platform.inWorld();
        float in = appear;                                   // opening animation (Right Shift)
        float pop = in >= 1 ? 1 : 1 + 2.2f * (float) Math.pow(in - 1, 3) + 1.2f * (float) Math.pow(in - 1, 2);   // ease-out-back
        r.rect(0, 0, lastW, lastH, Draw.alpha(backdrop(), (world ? 0.35f : 0.8f) * in));
        boolean busy = drag != null || resizing != null;
        HudModule hover = drag != null ? drag : resizing != null ? resizing : hudAt(r, mx, my);
        for (Module m : Cobra.MODULES) {
            if (!(m instanceof HudModule) || !m.isEnabled()) continue;
            HudModule h = (HudModule) m;
            if (!world || !h.visible()) Cobra.drawHud(r, h, true);
            int x = h.screenX(r), y = h.screenY(r);
            int w = Math.round(h.w * h.scale.f()), hh = Math.round(h.h * h.scale.f());
            Draw.outline(r, x - 1, y - 1, w + 2, hh + 2, h == hover ? accent() : Draw.alpha(Draw.FG, 0.4f * in));
            if (h == hover) {
                r.rect(x - 1, y - 1, w + 2, hh + 2, Draw.alpha(accent(), 0.15f));
                // resize handle, bottom-right corner
                r.rect(x + w - 3, y + hh - 3, 6, 6, accent());
                r.rect(x + w - 2, y + hh - 2, 4, 4, onAccent());
            }
        }
        // snapping guides
        if (busy) {
            if (guideX >= 0) r.rect(guideX, 0, 1, lastH, Draw.alpha(accent(), 0.85f));
            if (guideY >= 0) r.rect(0, guideY, lastW, 1, Draw.alpha(accent(), 0.85f));
        }
        // the way to the modules: a small square with the Cobra mark, in the middle of the screen
        if (!busy) {
            int full = modsBtnW(), bs = Math.max(4, Math.round(full * pop));
            int bx = lastW / 2 - bs / 2, by = lastH / 2 - bs / 2;
            boolean hov = in(mx, my, bx, by, bs, bs) && hover == null;
            modsBtnHover = Draw.approach(modsBtnHover, hov ? 1 : 0, 0.3f);
            int grow = Math.round(modsBtnHover * 2);
            Draw.round(r, bx - 1 - grow, by - 1 - grow, bs + 2 + grow * 2, bs + 2 + grow * 2, 6 + grow, Draw.alpha(accent(), 0.35f + 0.3f * modsBtnHover));
            Draw.round(r, bx, by, bs, bs, 5, accent());
            r.texture("logo", bx + 4, by + 4, bs - 8, bs - 8, onAccent());
            if (modsBtnHover > 0.05f) {
                String tip = "Mods";
                int tw = r.textWidth(tip);
                Draw.box(r, bx + bs / 2 - tw / 2 - 5, by + bs + 5, tw + 10, 13, 3, panel(), panelLine());
                r.text(tip, bx + bs / 2f - tw / 2f, by + bs + 8, Draw.alpha(Draw.FG, modsBtnHover), false);
            }
        }
        String hint = "Drag: move \u00b7 Corner: resize \u00b7 Right-click: options \u00b7 Esc: close";
        float hs = Math.min(1f, (lastW - 24) / (float) (r.textWidth(hint) + 16));    // shrink on small windows
        int tw = Math.round(r.textWidth(hint) * hs);
        int hy = lastH - 30 + Math.round((1 - in) * 24);
        Draw.box(r, (lastW - tw) / 2 - 8, hy, tw + 16, 16, 4, panel(), panelLine());
        Draw.scaledText(r, hint, (lastW - tw) / 2f, hy + 4 + (1 - hs) * 4, hs, Draw.alpha(Draw.FG, in), false);
    }

    /** Is the pointer on the resize corner of h? */
    private boolean onHandle(Render r, HudModule h, int mx, int my) {
        int x = h.screenX(r), y = h.screenY(r);
        int w = Math.round(h.w * h.scale.f()), hh = Math.round(h.h * h.scale.f());
        return mx >= x + w - 5 && mx <= x + w + 4 && my >= y + hh - 5 && my <= y + hh + 4;
    }

    /**
     * Smart snapping: the dragged box's edges and centre line up with the screen edges and centre
     * and with every other HUD element's edges and centres (within 5 px). Sets the guide lines.
     */
    private float[] snap(float x, float y, float w, float h) {
        java.util.List<Float> vx = new java.util.ArrayList<Float>(), vy = new java.util.ArrayList<Float>();
        vx.add(0f);
        vx.add(lastW / 2f);
        vx.add((float) lastW);
        vy.add(0f);
        vy.add(lastH / 2f);
        vy.add((float) lastH);
        for (Module m : Cobra.MODULES) {
            if (!(m instanceof HudModule) || !m.isEnabled() || m == drag) continue;
            HudModule o = (HudModule) m;
            float ox = o.screenX(lastRender), oy = o.screenY(lastRender);
            float ow = o.w * o.scale.f(), oh = o.h * o.scale.f();
            vx.add(ox);
            vx.add(ox + ow / 2);
            vx.add(ox + ow);
            vy.add(oy);
            vy.add(oy + oh / 2);
            vy.add(oy + oh);
        }
        float bestX = 6, sx = x;
        guideX = -1;
        float[] mineX = {x, x + w / 2, x + w};
        for (int k = 0; k < 3; k++) {
            for (float c : vx) {
                float d = Math.abs(mineX[k] - c);
                if (d < bestX) {
                    bestX = d;
                    sx = x + (c - mineX[k]);
                    guideX = Math.round(c);
                }
            }
        }
        float bestY = 6, sy = y;
        guideY = -1;
        float[] mineY = {y, y + h / 2, y + h};
        for (int k = 0; k < 3; k++) {
            for (float c : vy) {
                float d = Math.abs(mineY[k] - c);
                if (d < bestY) {
                    bestY = d;
                    sy = y + (c - mineY[k]);
                    guideY = Math.round(c);
                }
            }
        }
        if (guideX >= lastW) guideX = lastW - 1;
        if (guideY >= lastH) guideY = lastH - 1;
        sx = Math.max(0, Math.min(lastW - w, sx));
        sy = Math.max(0, Math.min(lastH - h, sy));
        return new float[]{sx, sy};
    }

    private HudModule hudAt(Render r, int mx, int my) {
        HudModule found = null;
        for (Module m : Cobra.MODULES) {
            if (!(m instanceof HudModule) || !m.isEnabled()) continue;
            HudModule h = (HudModule) m;
            if (in(mx, my, h.screenX(r), h.screenY(r), Math.round(h.w * h.scale.f()), Math.round(h.h * h.scale.f()))) found = h;
        }
        return found;
    }

    private void wideButton(Render r, int x, int y, int w, int h, String label, int mx, int my, boolean primary) {
        boolean hov = in(mx, my, x, y, w, h);
        if (primary) Draw.round(r, x, y, w, h, 3, hov ? Draw.mix(accent(), 0xFF808080, 0.18f) : accent());
        else Draw.box(r, x, y, w, h, 3, hov ? cardHov() : card(), hov ? accent() : line());
        Draw.label(r, label, x + w / 2f, y + h / 2f, 0.72f, 0.9f, primary ? onAccent() : Draw.FG);
    }

    // ================================================================= input

    public boolean mouseClicked(double dmx, double dmy, int button) {
        int mx = (int) (dmx / fit), my = (int) (dmy / fit);
        particles.burst(mx, my);
        Render r = lastRender;
        if (picker.isOpen()) {
            if (!picker.click(mx, my)) picker.close();
            Cobra.save();
            return true;
        }
        if (listening != null) {
            listening = null;
            return true;
        }
        if (editing) {
            HudModule h = r == null ? null : hudAt(r, mx, my);
            if (h != null && button == 0 && onHandle(r, h, mx, my)) {       // resize from the corner
                resizing = h;
                resizeStartScale = h.scale.f();
                resizeStartW = Math.max(1, h.w * h.scale.f());
                return true;
            }
            if (h == null) {
                if (button == 0 && in(mx, my, modsBtnX(), modsBtnY(), modsBtnW(), modsBtnW())) {   // → the modules
                    editing = false;
                    contentFade = 0;
                    appear = 0;
                    restartReveal();
                    return true;
                }
                // a resize handle sticks out of the box a little
                for (Module m : Cobra.MODULES) {
                    if (m instanceof HudModule && m.isEnabled() && r != null && onHandle(r, (HudModule) m, mx, my)) {
                        resizing = (HudModule) m;
                        resizeStartScale = resizing.scale.f();
                        resizeStartW = Math.max(1, resizing.w * resizing.scale.f());
                        return true;
                    }
                }
                return false;
            }
            if (button == 1) {
                editing = false;
                tab = 0;
                open = h;
                scroll = scrollTarget = 0;
                return true;
            }
            drag = h;
            dragDx = mx - h.screenX(r);
            dragDy = my - h.screenY(r);
            return true;
        }
        searchFocus = false;
        textFocus = null;
        if (nameEdit != null) {                                   // clicking anywhere finishes the name
            commitName();
            closeAfterName = false;
        }
        if (!in(mx, my, px, py, pw, ph)) return false;
        if (in(mx, my, px + pw - 23, py + 6, 17, 17)) {
            Cobra.save();
            closeRequested = true;
            return true;
        }
        int tx = px + 150;
        for (int i = 0; i < TABS.length; i++) {
            int w = (r == null ? 50 : Draw.spacedWidth(r, TABS[i], 0.8f)) + 16;
            if (in(mx, my, tx, py + 6, w, 17)) {
                if (tab != i) {
                    contentFade = 0;
                    restartReveal();
                }
                tab = i;
                open = null;
                scroll = scrollTarget = 0;
                return true;
            }
            tx += w + 2;
        }
        if (tab == 0) {
            for (int i = 0; i < CHIPS.length; i++) {
                int cy = py + 36 + 46 + i * (CHIP_H + 2);
                if (in(mx, my, lx + 4, cy, lw - 8, CHIP_H)) {
                    if (chip != i || open != null) restartReveal();
                    chip = i;
                    open = null;
                    scroll = scrollTarget = 0;
                    return true;
                }
            }
        }
        int by = py + ph - 8 - 18;
        if (in(mx, my, lx + 6, by, lw - 12, 16)) {
            editing = true;
            return true;
        }
        if (in(mx, my, lx + 6, by - 21, lw - 12, 16)) {
            resetHud();
            return true;
        }
        if (!in(mx, my, cx0, cy0, cw0, ch0)) return true;
        if (tab == 2) return clickWaypoints(mx, my);
        if (tab == 0 && open == null) return clickMods(r, mx, my, button);
        return clickSettings(r, mx, my, tab == 1 ? Cobra.get(Features.Client.class) : open);
    }

    private boolean clickMods(Render r, int mx, int my, int button) {
        if (my < cy0 + 18) {
            searchFocus = true;
            return true;
        }
        int gy = cy0 + 21;
        List<Module> mods = visibleModules();
        for (int i = 0; i < mods.size(); i++) {
            int x0 = cx0 + (i % cols) * (cardW + GAP);
            int y0 = gy + (i / cols) * (CARD_H + GAP) - Math.round(scroll);
            if (!in(mx, my, x0, y0, cardW, CARD_H)) continue;
            Module m = mods.get(i);
            boolean onSwitch = mx >= x0 + cardW - 30 && my >= y0 + CARD_H - 18;
            boolean onGear = mx >= x0 + cardW - 20 && my < y0 + 16;
            if (button == 0 && (onSwitch || m.settings.isEmpty())) m.toggle();
            else if (!m.settings.isEmpty() && (onGear || button == 1 || !onSwitch)) {
                open = m;
                scroll = scrollTarget = 0;
                contentFade = 0;
                restartReveal();
            }
            Cobra.save();
            return true;
        }
        return true;
    }

    private Module resetArmed;
    private long resetAt;

    /** Where the Reset button sits: left of the On/Off switch (right edge for Client settings). */
    private int resetX(Module m, int rw) {
        return m instanceof Features.Client ? cx0 + cw0 - rw : cx0 + cw0 - 72 - rw;
    }

    private boolean clickSettings(Render r, int mx, int my, Module m) {
        boolean client = m instanceof Features.Client;
        int rw = (r == null ? 40 : Draw.spacedWidth(r, resetArmed == m ? "SURE?" : "RESET", 0.6f) + 14);
        if (in(mx, my, resetX(m, rw), cy0, rw, 15)) {
            if (resetArmed == m && System.currentTimeMillis() - resetAt < 3000) {
                m.resetSettings();
                Cobra.save();
                resetArmed = null;
                if (Cobra.platform != null) Cobra.platform.chat(m.name + ": settings reset to default");
            } else {
                resetArmed = m;
                resetAt = System.currentTimeMillis();
            }
            return true;
        }
        if (!client && in(mx, my, cx0, cy0, 44, 15)) {
            restartReveal();
            open = null;
            scroll = scrollTarget = 0;
            return true;
        }
        if (!client && in(mx, my, cx0 + cw0 - 64, cy0, 64, 15)) {
            m.toggle();
            Cobra.save();
            return true;
        }
        int top = cy0 + 32;
        List<Setting<?>> list = m.shownSettings();
        for (int i = 0; i < list.size(); i++) {
            Setting<?> st = list.get(i);
            int y = top + i * ROW - Math.round(scroll);
            if (my < top || !in(mx, my, cx0, y, cw0, ROW)) continue;
            int right = cx0 + cw0 - 2;
            if (st instanceof Setting.Group) ((Setting.Group) st).toggle();
            else if (st instanceof Setting.Action) ((Setting.Action) st).run();
            else if (st instanceof Setting.Bool) ((Setting.Bool) st).toggle();
            else if (st instanceof Setting.Number) {
                Setting.Number n = (Setting.Number) st;
                int vw = r == null ? 20 : r.textWidth(n.display());
                slideW = 90;
                slideX = right - vw - 8 - slideW;
                if (mx >= slideX - 4) {
                    sliding = n;
                    slide(mx);
                }
            } else if (st instanceof Setting.Mode) ((Setting.Mode) st).cycle(mx < right - 30 ? -1 : 1);
            else if (st instanceof Setting.Color) {
                Setting.Color c = (Setting.Color) st;
                int cur = right - 16;
                if (mx >= cur) picker.open(c, cur - ColorPicker.W + 16, y + ROW, lastW, lastH);
                else {
                    int[] pr = Setting.Color.PRESETS;
                    for (int k = 0; k < pr.length; k++) {
                        int sx = cur - 6 - (pr.length - k) * 10;
                        if (mx >= sx - 1 && mx < sx + 9) c.set((c.argb() & 0xFF000000) == 0 ? pr[k] : (c.argb() & 0xFF000000) | (pr[k] & 0xFFFFFF));
                    }
                }
            } else if (st instanceof Setting.Text) textFocus = (Setting.Text) st;
            else if (st instanceof Setting.Bind) listening = (Setting.Bind) st;
            Cobra.save();
            return true;
        }
        return true;
    }

    private boolean clickWaypoints(int mx, int my) {
        Features.Waypoints wp = Cobra.get(Features.Waypoints.class);
        if (in(mx, my, cx0 + cw0 - 104, cy0, 104, 15)) {
            Features.Waypoints.Point pt = wp.addHere();
            if (pt != null) startName(pt);                       // straight into naming it
            return true;
        }
        List<Features.Waypoints.Point> list = wp.here();
        int top = cy0 + 32;
        if (my < top) return true;
        for (int i = 0; i < list.size(); i++) {
            Features.Waypoints.Point pt = list.get(i);
            int y = top + i * WP_ROW - Math.round(scroll);
            if (!in(mx, my, cx0, y, cw0, WP_ROW - 3)) continue;
            if (in(mx, my, cx0 + cw0 - 20, y + 3, 15, 15)) {
                wp.remove(pt);
                return true;
            }
            if (in(mx, my, cx0 + 5, y + 1, 19, 19)) {
                colorFor = pt;
                wpColor.set(0xFF000000 | (pt.color & 0xFFFFFF));
                picker.open(wpColor, cx0 + 6, y + WP_ROW, lastW, lastH);
                return true;
            }
            startName(pt);                                        // anywhere else on the row: rename
            return true;
        }
        return true;
    }

    private void slide(int mx) {
        double f = Math.max(0, Math.min(1, (mx - slideX) / (double) slideW));
        sliding.set(sliding.min + f * (sliding.max - sliding.min));
    }

    public void mouseDragged(double mx0, double my0) {
        double mx = mx0 / fit, my = my0 / fit;
        if (picker.isOpen()) {
            picker.drag((int) mx, (int) my);
            return;
        }
        if (sliding != null) {
            slide((int) mx);
            return;
        }
        if (resizing != null && lastRender != null) {
            int x = resizing.screenX(lastRender);
            float newW = Math.max(8, (float) mx - x);
            double scale = Math.max(resizing.scale.min, Math.min(resizing.scale.max, resizeStartScale * newW / resizeStartW));
            int y = resizing.screenY(lastRender);
            resizing.scale.set(Math.round(scale * 20) / 20.0);        // steps of 0.05
            resizing.moveTo(lastRender, x, y);                          // keep the top-left corner in place
            guideX = guideY = -1;
            return;
        }
        if (drag != null && lastRender != null) {
            float x = (float) mx - dragDx, y = (float) my - dragDy;
            float w = drag.w * drag.scale.f(), h = drag.h * drag.scale.f();
            float[] p = snap(x, y, w, h);
            drag.moveTo(lastRender, p[0], p[1]);
        }
    }

    public void mouseReleased() {
        if (picker.isOpen()) picker.release();
        if (sliding != null || drag != null || resizing != null) Cobra.save();
        sliding = null;
        drag = null;
        resizing = null;
        guideX = guideY = -1;
    }

    public void mouseScrolled(double mx0, double my0, double amount) {
        double mx = mx0 / fit, my = my0 / fit;
        if (editing) {
            HudModule h = lastRender == null ? null : hudAt(lastRender, (int) mx, (int) my);
            if (h != null) {
                h.scale.set(h.scale.get() + (amount > 0 ? 0.05 : -0.05));
                Cobra.save();
            }
            return;
        }
        scrollTarget -= (float) amount * 26;
    }

    /** @return true when the screen should close */
    public boolean keyPressed(int key, int raw) {
        if (listening != null) {
            listening.set(key == KEY_ESCAPE || key == KEY_BACKSPACE ? -1 : raw);
            listening = null;
            Cobra.save();
            return false;
        }
        if (nameEdit != null) {
            if (key == KEY_BACKSPACE && !nameBuf.isEmpty()) nameBuf = nameBuf.substring(0, nameBuf.length() - 1);
            if (key == KEY_ENTER || key == KEY_ESCAPE) {
                if (key == KEY_ENTER || !nameBuf.trim().isEmpty()) commitName();
                else nameEdit = null;                             // Esc with nothing typed: keep the old name
                if (closeAfterName) {
                    closeAfterName = false;
                    Cobra.save();
                    return true;
                }
            }
            return false;
        }
        if (textFocus != null) {
            if (key == KEY_BACKSPACE && !textFocus.get().isEmpty()) textFocus.set(textFocus.get().substring(0, textFocus.get().length() - 1));
            if (key == KEY_ENTER || key == KEY_ESCAPE) textFocus = null;
            Cobra.save();
            return false;
        }
        if (searchFocus) {
            if (key == KEY_BACKSPACE && !search.isEmpty()) search = search.substring(0, search.length() - 1);
            if (key == KEY_ESCAPE || key == KEY_ENTER) searchFocus = false;
            scrollTarget = 0;
            return false;
        }
        if (key == KEY_ESCAPE) {
            if (picker.isOpen()) {
                picker.close();
                return false;
            }
            if (editing) {
                if (startedInEditor) {   // Right Shift's editor: Esc just closes (the middle button goes to the modules)
                    Cobra.save();
                    return true;
                }
                editing = false;         // opened from the modules ("Edit HUD layout"): back there
                return false;
            }
            if (open != null) {
                open = null;
                scroll = scrollTarget = 0;
                return false;
            }
            if (startedInEditor) {   // modules → back to the HUD editor, Esc there closes
                editing = true;
                return false;
            }
            Cobra.save();
            return true;
        }
        return false;
    }

    public void charTyped(char c) {
        if (c < 32 || c == 127 || listening != null) return;
        if (nameEdit != null) {
            if (nameBuf.length() < Features.Waypoints.NAME_MAX && c != '|') nameBuf += c;
            return;
        }
        if (textFocus != null) {
            if (textFocus.get().length() < textFocus.maxLength && c != '|') textFocus.set(textFocus.get() + c);
            return;
        }
        if (searchFocus && search.length() < 24) {
            search += c;
            scrollTarget = 0;
        }
    }

    private void resetHud() {
        for (Module m : Cobra.MODULES) {
            if (!(m instanceof HudModule)) continue;
            try {
                HudModule fresh = (HudModule) m.getClass().getDeclaredConstructor().newInstance();
                ((HudModule) m).px = fresh.px;
                ((HudModule) m).py = fresh.py;
                ((HudModule) m).scale.set(1.0);
            } catch (Exception ignored) {}
        }
        Cobra.save();
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private static String clip(Render r, String s, int max) {
        if (r.textWidth(s) <= max) return s;
        while (s.length() > 1 && r.textWidth(s + "...") > max) s = s.substring(0, s.length() - 1);
        return s + "...";
    }
}

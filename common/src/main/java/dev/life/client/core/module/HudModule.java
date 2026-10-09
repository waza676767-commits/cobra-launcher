package dev.life.client.core.module;

import dev.life.client.core.Render;

/** A module that draws a movable, scalable element on the HUD. */
public abstract class HudModule extends Module {
    /** Position as a fraction of the free space (screen minus element size), so it survives resizes. */
    public float px, py;
    public final Setting.Number scale;
    public final Setting.Bool background;
    public final Setting.Color color;
    public final Setting.Color bgColor;
    public final Setting.Bool rounded;
    public final Setting.Bool shadow;
    /** Unscaled size from the last draw. */
    public int w = 40, h = 16;

    protected HudModule(String id, String name, String description, boolean on, float px, float py) {
        this(id, name, description, Category.HUD, on, px, py);
    }

    protected HudModule(String id, String name, String description, Category category, boolean on, float px, float py) {
        super(id, name, description, category, on);
        this.px = px;
        this.py = py;
        scale = add(new Setting.Number("scale", "Scale", 1.0, 0.5, 3.0, 0.05, "x"));
        background = add(new Setting.Bool("background", "Background", true));
        color = add(new Setting.Color("color", "Text color", 0xFFFFFFFF));
        bgColor = add(new Setting.Color("bg", "Background color", 0x6A000000));
        rounded = add(new Setting.Bool("rounded", "Rounded corners", false));
        shadow = add(new Setting.Bool("shadow", "Text shadow", false));
    }

    /** Background box using this element's background colour / rounding. */
    protected void bg(Render r, int x, int y, int w, int h) {
        if (!background.on()) return;
        if (dev.life.client.core.Life.get(Features.Client.class).hudStyle.is("Liquid Glass")) {
            dev.life.client.core.ui.Draw.glassPanel(r, x, y, w, h, rounded.on() ? 4 : 2, bgColor.argb());
            return;
        }
        // HUD glass strength also sets how see-through the classic background is
        int c = bgColor.argb();
        int a = Math.max(0, Math.min(255, Math.round((c >>> 24) * dev.life.client.core.ui.Draw.glassK)));
        if (a == 0) return;
        c = a << 24 | (c & 0xFFFFFF);
        if (rounded.on()) dev.life.client.core.ui.Draw.round(r, x, y, w, h, 3, c);
        else r.rect(x, y, w, h, c);
    }

    protected boolean textShadow() {
        return shadow.on() || !background.on();
    }

    /** Draws at (0,0) and updates {@link #w}/{@link #h}. With {@code editing} true, draws sample data if there's nothing to show. */
    public abstract void draw(Render r, boolean editing);

    /** Whether there's anything to show outside the HUD editor. */
    public boolean visible() { return true; }

    public int screenX(Render r) {
        return Math.round(px * Math.max(0, r.width() - w * scale.f()));
    }

    public int screenY(Render r) {
        return Math.round(py * Math.max(0, r.height() - h * scale.f()));
    }

    public void moveTo(Render r, float x, float y) {
        float fw = Math.max(1, r.width() - w * scale.f()), fh = Math.max(1, r.height() - h * scale.f());
        px = Math.max(0, Math.min(1, x / fw));
        py = Math.max(0, Math.min(1, y / fh));
    }

    /** Standard single-line element: text on a translucent box. */
    protected void textBox(Render r, String text) {
        w = r.textWidth(text) + 10;
        h = 16;
        bg(r, 0, 0, w, h);
        r.text(text, 5, 4, color.argb(), textShadow());
    }
}

package dev.cobra.client.core.module;

import dev.cobra.client.core.ClickTracker;
import dev.cobra.client.core.Cobra;
import dev.cobra.client.core.Platform;
import dev.cobra.client.core.Render;
import dev.cobra.client.core.ui.Draw;

import java.util.List;
import java.util.Locale;

public final class HudModules {
    private HudModules() {}

    private static Platform p() { return Cobra.platform; }

    // ------------------------------------------------------------ Keystrokes

    public static final class Keystrokes extends HudModule {
        private final Setting.Bool mouse = add(new Setting.Bool("mouse", "Mouse buttons", true));
        private final Setting.Bool space = add(new Setting.Bool("space", "Space bar", true));
        private final Setting.Bool cps = add(new Setting.Bool("cps", "Show CPS", true));
        private final float[] anim = new float[7];

        public Keystrokes() {
            super("keystrokes", "Keystrokes", "Shows WASD, mouse and jump presses", true, 0.01f, 0.03f);
        }

        @Override
        public void draw(Render r, boolean editing) {
            int k = 22, g = 2;
            w = k * 3 + g * 2;
            int y = 0;
            key(r, 0, "W", k + g, y, k, k, Platform.Key.FORWARD);
            y += k + g;
            key(r, 1, "A", 0, y, k, k, Platform.Key.LEFT);
            key(r, 2, "S", k + g, y, k, k, Platform.Key.BACK);
            key(r, 3, "D", (k + g) * 2, y, k, k, Platform.Key.RIGHT);
            y += k + g;
            if (mouse.on()) {
                int mw = (w - g) / 2;
                key(r, 4, "LMB", 0, y, mw, k, Platform.Key.ATTACK);
                key(r, 5, "RMB", mw + g, y, w - mw - g, k, Platform.Key.USE);
                y += k + g;
            }
            if (space.on()) {
                key(r, 6, null, 0, y, w, 12, Platform.Key.JUMP);
                y += 12 + g;
            }
            h = y - g;
        }

        private void key(Render r, int i, String label, int x, int y, int kw, int kh, Platform.Key key) {
            boolean down = p().inWorld() && p().key(key);
            anim[i] = Draw.approach(anim[i], down ? 1 : 0, 0.35f);
            float t = anim[i];
            int base = background.on() ? bgColor.argb() : 0x00000000;
            int fill = Draw.mix(base, 0xD0FFFFFF, t);
            if (rounded.on()) Draw.round(r, x, y, kw, kh, 3, fill);
            else r.rect(x, y, kw, kh, fill);
            int fg = Draw.mix(color.argb(), 0xFF000000, t);
            if (label == null) {
                r.rect(x + kw / 2 - 12, y + kh / 2, 24, 1, fg);
                return;
            }
            boolean mouseKey = i == 4 || i == 5;
            if (mouseKey && cps.on()) {
                Draw.centered(r, label, x + kw / 2f, y + 3, fg, false);
                String c = ClickTracker.cps(i == 4 ? 0 : 1) + " CPS";
                r.push();
                r.translate(x + kw / 2f - r.textWidth(c) * 0.25f, y + 13);
                r.scale(0.5f);
                r.text(c, 0, 0, fg, false);
                r.pop();
            } else {
                Draw.centered(r, label, x + kw / 2f, y + (kh - 8) / 2f, fg, false);
            }
        }
    }

    // ----------------------------------------------------------------- small

    public static final class Cps extends HudModule {
        private final Setting.Bool right = add(new Setting.Bool("right", "Show right click", true));

        public Cps() { super("cps", "CPS Counter", "Clicks per second", false, 0.01f, 0.36f); }

        @Override
        public void draw(Render r, boolean editing) {
            textBox(r, right.on() ? ClickTracker.cps(0) + " | " + ClickTracker.cps(1) + " CPS" : ClickTracker.cps(0) + " CPS");
        }
    }

    public static final class Fps extends HudModule {
        public Fps() { super("fps", "FPS Display", "Frames per second", true, 0.01f, 0.42f); }

        @Override public void draw(Render r, boolean editing) { textBox(r, p().fps() + " FPS"); }
    }

    /** Hunger and saturation (the hidden food bar) as numbers. */
    public static final class Saturation extends HudModule {
        private final Setting.Bool hunger = add(new Setting.Bool("hunger", "Show hunger too", true));

        public Saturation() { super("saturation", "Saturation", "Your hidden saturation (and hunger) as numbers", false, 0.72f, 0.86f); }

        @Override
        public void draw(Render r, boolean editing) {
            float sat = p().saturation();
            int food = p().food();
            if (sat < 0 && !editing) {
                w = h = 0;
                return;
            }
            if (sat < 0) {
                sat = 5.2f;
                food = 18;
            }
            String t = (hunger.on() ? "Food " + food + "  " : "") + "Saturation " + String.format(java.util.Locale.ROOT, "%.1f", sat);
            textBox(r, t);
        }
    }

    /** The resource packs you have on, top to bottom. */
    public static final class PackInfo extends HudModule {
        private final Setting.Number max = add(new Setting.Number("max", "Packs shown", 5, 1, 12, 1, ""));

        public PackInfo() { super("packinfo", "Pack Info", "Which resource packs you're using", false, 0.72f, 0.2f); }

        @Override
        public void draw(Render r, boolean editing) {
            java.util.List<String> packs = p().activePacks();
            if (packs.isEmpty() && editing) packs = java.util.Arrays.asList("My PvP Pack", "Clear Glass");
            if (packs.isEmpty()) {
                w = h = 0;
                return;
            }
            int n = Math.min(packs.size(), max.i());
            int wMax = r.textWidth("Resource packs");
            for (int i = 0; i < n; i++) wMax = Math.max(wMax, r.textWidth(packs.get(i)));
            w = wMax + 10;
            h = (n + 1) * 10 + 6;
            bg(r, 0, 0, w, h);
            r.text("Resource packs", 5, 4, 0xFF9A9B9F, textShadow());
            for (int i = 0; i < n; i++) r.text(packs.get(i), 5, 14 + i * 10, color.argb(), textShadow());
        }
    }

    /** A little badge with the Cobra mark and your text (your name by default). */
    public static final class Watermark extends HudModule {
        private final Setting.Text text = add(new Setting.Text("text", "Text (empty = your name)", "", 20));
        private final Setting.Bool mark = add(new Setting.Bool("mark", "Cobra mark", true));

        public Watermark() { super("watermark", "Watermark", "The Cobra mark and your name in a small badge", false, 0.01f, 0.01f); }

        @Override
        public void draw(Render r, boolean editing) {
            String t = text.get() == null || text.get().trim().isEmpty() ? p().playerName() : text.get().trim();
            if (t == null) t = "Cobra";
            t = t.toUpperCase(java.util.Locale.ROOT);
            int tw = dev.cobra.client.core.ui.Draw.spacedWidth(r, t, 1f);
            int markW = mark.on() ? 14 : 0;
            w = tw + markW + 14;
            h = 18;
            bg(r, 0, 0, w, h);
            if (mark.on()) r.texture("logo", 6, 4, 10, 10, color.argb());
            dev.cobra.client.core.ui.Draw.spaced(r, t, 7 + markW, 5, 1f, color.argb(), textShadow());
        }
    }

    public static final class Ping extends HudModule {
        public Ping() { super("ping", "Ping Display", "Your latency to the server", false, 0.01f, 0.48f); }

        @Override public void draw(Render r, boolean editing) { textBox(r, p().ping() + " ms"); }
    }

    public static final class Coordinates extends HudModule {
        private final Setting.Mode layout = add(new Setting.Mode("layout", "Layout", "Vertical", "Vertical", "Horizontal"));
        private final Setting.Bool facing = add(new Setting.Bool("facing", "Show direction", true));

        public Coordinates() { super("coords", "Coordinates", "Your XYZ position and facing", false, 0.01f, 0.56f); }

        @Override
        public void draw(Render r, boolean editing) {
            String x = String.valueOf((long) Math.floor(p().x())), y = String.valueOf((long) Math.floor(p().y())), z = String.valueOf((long) Math.floor(p().z()));
            String dir = facingName(p().yaw());
            if (layout.is("Horizontal")) {
                textBox(r, "XYZ " + x + ", " + y + ", " + z + (facing.on() ? "  " + dir.charAt(0) : ""));
                return;
            }
            String[] lines = facing.on() ? new String[]{"X  " + x, "Y  " + y, "Z  " + z, dir} : new String[]{"X  " + x, "Y  " + y, "Z  " + z};
            int max = 0;
            for (String l : lines) max = Math.max(max, r.textWidth(l));
            w = max + 10;
            h = lines.length * 10 + 6;
            bg(r, 0, 0, w, h);
            for (int i = 0; i < lines.length; i++) r.text(lines[i], 5, 4 + i * 10, i == 3 ? 0xFF9A9B9F : color.argb(), textShadow());
        }
    }

    public static String facingName(float yaw) {
        float y = ((yaw % 360) + 360) % 360;
        String[] names = {"South", "South West", "West", "North West", "North", "North East", "East", "South East"};
        return names[Math.round(y / 45f) % 8];
    }

    // ---------------------------------------------------------------- Compass

    public static final class Compass extends HudModule {
        private final Setting.Number width = add(new Setting.Number("width", "Width", 180, 100, 300, 10, "px"));

        public Compass() { super("compass", "Direction HUD", "Compass strip with waypoints", false, 0.5f, 0.0f); }

        @Override
        public void draw(Render r, boolean editing) {
            w = width.i();
            h = 20;
            bg(r, 0, 0, w, h);
            float yaw = ((p().yaw() % 360) + 360) % 360;
            float ppd = 1.4f;
            for (int deg = 0; deg < 360; deg += 15) {
                float diff = wrap(deg - yaw);
                float x = w / 2f + diff * ppd;
                if (x < 4 || x > w - 4) continue;
                if (deg % 90 == 0) {
                    String s = deg == 0 ? "S" : deg == 90 ? "W" : deg == 180 ? "N" : "E";
                    r.text(s, x - r.textWidth(s) / 2f, 6, deg == 180 ? 0xFFFF6B6B : color.argb(), true);
                } else if (deg % 45 == 0) {
                    String s = deg == 45 ? "SW" : deg == 135 ? "NW" : deg == 225 ? "NE" : "SE";
                    r.push();
                    r.translate(x - r.textWidth(s) * 0.35f, 7);
                    r.scale(0.7f);
                    r.text(s, 0, 0, Draw.SOFT, false);
                    r.pop();
                } else {
                    r.rect(Math.round(x), 8, 1, 4, 0x90FFFFFF);
                }
            }
            Features.Waypoints wp = Cobra.get(Features.Waypoints.class);
            if (wp.isEnabled()) {
                for (Features.Waypoints.Point pt : wp.here()) {
                    double dx = pt.x + 0.5 - p().x(), dz = pt.z + 0.5 - p().z();
                    float target = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    float diff = wrap(target - yaw);
                    float x = w / 2f + diff * ppd;
                    if (x < 3 || x > w - 3) continue;
                    r.rect(Math.round(x) - 1, 1, 3, 3, pt.color);
                }
            }
            r.rect(w / 2, 15, 1, 4, 0xFFFFFFFF);
        }

        private static float wrap(float d) {
            d = ((d % 360) + 540) % 360 - 180;
            return d;
        }
    }

    // ----------------------------------------------------------------- Armor

    public static final class ArmorStatus extends HudModule {
        private final Setting.Bool hand = add(new Setting.Bool("hand", "Held item", true));
        private final Setting.Bool durability = add(new Setting.Bool("durability", "Durability numbers", true));

        public ArmorStatus() { super("armor", "Armor Status", "Armor and held item durability", false, 0.99f, 0.7f); }

        @Override
        public void draw(Render r, boolean editing) {
            List<Object> eq = p().inWorld() ? p().equipment() : java.util.Collections.<Object>emptyList();
            int y = 0, maxW = 18;
            int count = 0;
            for (int i = 0; i < eq.size(); i++) {
                if (i == 4 && !hand.on()) continue;
                Object s = eq.get(i);
                if (s == null) continue;
                r.item(s, 0, y);
                if (durability.on()) {
                    String d = p().durability(s);
                    r.text(d, 19, y + 4, color.argb(), true);
                    maxW = Math.max(maxW, 19 + r.textWidth(d));
                }
                y += 17;
                count++;
            }
            if (count == 0 && editing) {
                for (int i = 0; i < 4; i++) {
                    r.rect(1, i * 17 + 1, 14, 14, 0x40FFFFFF);
                    r.text("100", 19, i * 17 + 4, color.argb(), true);
                }
                y = 68;
                maxW = 19 + r.textWidth("100");
            }
            w = maxW;
            h = Math.max(16, y);
        }

        @Override
        public boolean visible() { return p().inWorld(); }
    }

    // --------------------------------------------------------- Reach / Combo

    public static final class Reach extends HudModule {
        private double last = -1;
        private long at;

        public Reach() { super("reach", "Reach Display", "Distance of your last hit", false, 0.01f, 0.62f); }

        public void hit(double reach) {
            last = reach;
            at = System.currentTimeMillis();
        }

        private boolean fresh() {
            return last >= 0 && System.currentTimeMillis() - at < 3000;
        }

        @Override
        public void draw(Render r, boolean editing) {
            textBox(r, String.format(Locale.ROOT, "%.2f blocks", fresh() ? last : 3.0));
        }

        /** Only on screen for a few seconds after a hit. */
        @Override
        public boolean visible() {
            return p().inWorld() && fresh();
        }
    }

    public static final class Combo extends HudModule {
        private Object target;
        private long attackTick = -100, lastHitTick = -100;
        private int combo, lastHurt;
        private boolean counted;

        public Combo() { super("combo", "Combo Counter", "Consecutive hits without getting hit", false, 0.01f, 0.68f); }

        public void attacked(Object t) {
            target = t;
            attackTick = Cobra.ticks();
            counted = false;
        }

        @Override
        public void onTick() {
            if (!p().inWorld()) return;
            long now = Cobra.ticks();
            if (target != null && !counted && now - attackTick <= 4 && p().hurtTime(target) >= 8) {
                combo++;
                counted = true;
                lastHitTick = now;
            }
            int hurt = p().playerHurtTime();
            if (hurt > lastHurt && hurt >= 8) combo = 0;
            lastHurt = hurt;
            if (now - lastHitTick > 60) combo = 0;
        }

        @Override
        public void draw(Render r, boolean editing) {
            textBox(r, combo == 0 ? "No Combo" : combo + " Combo");
        }
    }

    // ---------------------------------------------------------------- Potions

    public static final class Potions extends HudModule {
        public Potions() { super("potions", "Potion Effects", "Active effects with time left", false, 0.01f, 0.2f); }

        @Override
        public void draw(Render r, boolean editing) {
            List<Platform.Effect> list = p().inWorld() ? p().effects() : java.util.Collections.<Platform.Effect>emptyList();
            if (list.isEmpty() && editing) list = java.util.Collections.singletonList(new Platform.Effect(null, "Speed", 1, "1:30"));
            int y = 0, maxW = 60;
            for (Platform.Effect e : list) {
                bg(r, 0, y, w, 22);
                if (e.handle != null) r.effectIcon(e.handle, 2, y + 2);
                else r.rect(3, y + 3, 16, 16, 0x40FFFFFF);
                String name = e.name + (e.amplifier > 0 ? " " + roman(e.amplifier + 1) : "");
                r.text(name, 23, y + 3, color.argb(), true);
                r.text(e.duration, 23, y + 12, Draw.SOFT, true);
                maxW = Math.max(maxW, 27 + Math.max(r.textWidth(name), r.textWidth(e.duration)));
                y += 23;
            }
            w = maxW;
            h = Math.max(22, y - 1);
        }

        @Override public boolean visible() { return p().inWorld() && !p().effects().isEmpty(); }
    }

    public static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    // ---------------------------------------------------------------- Boss bar

    public static final class BossBar extends HudModule {
        public BossBar() {
            super("bossbar", "Boss Bar", "Movable, scalable boss health bar", true, 0.5f, 0.02f);
            background.set(false);
        }

        @Override
        public void draw(Render r, boolean editing) {
            List<Platform.Boss> bosses = p().inWorld() ? p().bosses() : java.util.Collections.<Platform.Boss>emptyList();
            if (bosses.isEmpty() && editing) bosses = java.util.Collections.singletonList(new Platform.Boss("Ender Dragon", 0.7f, 0xFFB86BFF));
            w = 182;
            int y = 0;
            for (Platform.Boss b : bosses) {
                int tw = r.widthObj(b.name);
                r.textObj(b.name, (w - tw) / 2f, y, color.argb(), true);
                if (background.on()) r.rect(0, y + 11, w, 5, bgColor.argb());
                r.rect(0, y + 11, w, 5, 0x60000000);
                r.rect(0, y + 11, Math.round(w * Math.max(0, Math.min(1, b.percent))), 5, b.color | 0xFF000000);
                y += 20;
            }
            h = Math.max(16, y - 4);
        }

        @Override public boolean visible() { return p().inWorld() && !p().bosses().isEmpty(); }
    }

    // -------------------------------------------------------------- Scoreboard

    public static final class Scoreboard extends HudModule {
        private final Setting.Bool numbers = add(new Setting.Bool("numbers", "Red numbers", false));
        private final Setting.Number opacity = add(new Setting.Number("opacity", "Background opacity", 30, 0, 100, 5, "%"));

        public Scoreboard() {
            super("scoreboard", "Scoreboard", "Movable sidebar with optional numbers", true, 1.0f, 0.45f);
        }

        @Override
        public void draw(Render r, boolean editing) {
            Platform.Sidebar sb = p().inWorld() ? p().sidebar() : null;
            if (sb == null && editing) {
                java.util.List<Object> names = new java.util.ArrayList<Object>(), scores = new java.util.ArrayList<Object>();
                String[] sample = {"", "Kills: 0", "Coins: 1,024", "", "cobra.gg"};
                for (int i = 0; i < sample.length; i++) {
                    names.add(sample[i]);
                    scores.add("\u00a7c" + (sample.length - i));
                }
                sb = new Platform.Sidebar("\u00a7e\u00a7lCOBRA", names, scores);
            }
            if (sb == null) {
                w = 60;
                h = 20;
                return;
            }
            int maxW = r.widthObj(sb.title);
            for (int i = 0; i < sb.names.size(); i++) {
                int lw = r.widthObj(sb.names.get(i)) + (numbers.on() ? r.widthObj(sb.scores.get(i)) + 6 : 0);
                maxW = Math.max(maxW, lw);
            }
            w = maxW + 6;
            int lines = sb.names.size();
            h = (lines + 1) * 9 + 3;
            int bg = ((int) (opacity.get() / 100 * 255) & 0xFF) << 24;
            int bgTitle = ((int) (Math.min(100, opacity.get() + 10) / 100 * 255) & 0xFF) << 24;
            r.rect(0, 0, w, 10, bgTitle);
            r.rect(0, 10, w, h - 10, bg);
            r.textObj(sb.title, (w - r.widthObj(sb.title)) / 2f, 1, 0xFFFFFFFF, false);
            for (int i = 0; i < lines; i++) {
                float y = 11 + i * 9;
                r.textObj(sb.names.get(i), 3, y, 0xFFFFFFFF, false);
                if (numbers.on()) {
                    Object s = sb.scores.get(i);
                    r.textObj(s, w - 3 - r.widthObj(s), y, 0xFFFFFFFF, false);
                }
            }
        }

        @Override public boolean visible() { return p().inWorld() && p().sidebar() != null; }
    }

    // ------------------------------------------------------------ Toggle sprint

    public static final class ToggleSprint extends HudModule {
        public final Setting.Bool sprint = add(new Setting.Bool("sprint", "Toggle sprint", true));
        public final Setting.Bool sneak = add(new Setting.Bool("sneak", "Toggle sneak", false));
        public final Setting.Bool showHud = add(new Setting.Bool("hud", "Show status", true));
        private boolean sprinting, sneaking;

        public ToggleSprint() {
            super("togglesprint", "Toggle Sprint", "Press sprint/sneak once instead of holding", Category.UTILITY, true, 0.01f, 0.97f);
            background.set(false);
        }

        @Override
        public void onTick() {
            if (!p().inWorld() || p().screenOpen()) return;
            if (sprint.on() && p().wasPressed(Platform.Key.SPRINT)) sprinting = !sprinting;
            if (sneak.on() && p().wasPressed(Platform.Key.SNEAK)) sneaking = !sneaking;
            if (!sprint.on()) sprinting = false;
            if (!sneak.on()) sneaking = false;
            if (sprinting) p().setKey(Platform.Key.SPRINT, true);
            if (sneaking) p().setKey(Platform.Key.SNEAK, true);
        }

        @Override
        public void onDisable() {
            if (sprinting) p().setKey(Platform.Key.SPRINT, false);
            if (sneaking) p().setKey(Platform.Key.SNEAK, false);
            sprinting = sneaking = false;
        }

        public String status() {
            if (sneaking) return "[Sneaking (Toggled)]";
            if (sprinting) return "[Sprinting (Toggled)]";
            if (p().inWorld() && p().key(Platform.Key.SPRINT)) return "[Sprinting (Key Held)]";
            return null;
        }

        @Override
        public void draw(Render r, boolean editing) {
            String s = status();
            textBox(r, s == null ? "[Sprinting (Toggled)]" : s);
        }

        @Override public boolean visible() { return showHud.on() && status() != null; }
    }

    // ---------------------------------------------------------------- BedWars

    /** Tracks kills, final kills and beds from Hypixel BedWars chat, and warns when your bed breaks. */
    public static final class BedWars extends HudModule {
        private final Setting.Bool alert = add(new Setting.Bool("alert", "Bed destroyed alert", true));
        private int kills, finals, beds;

        public BedWars() {
            super("bedwars", "BedWars Addons", "Game stats and bed alerts on Hypixel", Category.HYPIXEL, false, 0.99f, 0.3f);
        }

        public void onChat(String raw) {
            if (!isEnabled() || !p().server().toLowerCase(Locale.ROOT).contains("hypixel")) return;
            String line = raw.replaceAll("\u00a7.", "").trim();
            String me = p().playerName();
            if (line.contains("Protect your bed and destroy the enemy beds")) {
                kills = finals = beds = 0;
                return;
            }
            if (line.startsWith("BED DESTRUCTION >")) {
                if (line.contains("Your Bed")) {
                    if (alert.on()) p().title("\u00a7c\u00a7lBED DESTROYED", "\u00a77You can't respawn anymore");
                } else if (line.contains(me)) beds++;
                return;
            }
            // "<victim> was <flavour> by <you>." optionally followed by "FINAL KILL!"
            if (!line.startsWith(me) && containsWord(line, me) && (line.endsWith(".") || line.endsWith("FINAL KILL!"))) {
                if (line.endsWith("FINAL KILL!")) finals++;
                else kills++;
            }
        }

        private static boolean containsWord(String line, String name) {
            int i = line.indexOf(" " + name);
            if (i < 0) return false;
            int end = i + 1 + name.length();
            return end >= line.length() || !Character.isLetterOrDigit(line.charAt(end)) && line.charAt(end) != '_';
        }

        @Override
        public void draw(Render r, boolean editing) {
            String[] lines = {"Kills  " + kills, "Finals  " + finals, "Beds  " + beds};
            int max = 0;
            for (String l : lines) max = Math.max(max, r.textWidth(l));
            w = max + 10;
            h = 36;
            bg(r, 0, 0, w, h);
            for (int i = 0; i < lines.length; i++) r.text(lines[i], 5, 4 + i * 10, color.argb(), textShadow());
        }

        @Override
        public boolean visible() {
            return p().inWorld() && p().server().toLowerCase(Locale.ROOT).contains("hypixel");
        }
    }
}

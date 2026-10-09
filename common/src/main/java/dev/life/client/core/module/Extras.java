package dev.life.client.core.module;

import dev.life.client.core.Life;
import dev.life.client.core.Platform;
import dev.life.client.core.Render;
import dev.life.client.core.ui.Draw;

import java.util.List;
import java.util.Locale;

/** Newer modules: memory, stopwatch, server address, item counter, team view, chunk borders, hitboxes, glint, Hypixel tools. */
public final class Extras {
    private Extras() {}

    private static Platform p() { return Life.platform; }

    // ----------------------------------------------------------------- HUD

    public static final class Memory extends HudModule {
        private final Setting.Mode style = add(new Setting.Mode("style", "Show", "Percent + GB", "Percent + GB", "Percent", "GB"));

        public Memory() { super("memory", "Memory Usage", "How much RAM the game is using", false, 0.01f, 0.74f); }

        @Override
        public void draw(Render r, boolean editing) {
            Runtime rt = Runtime.getRuntime();
            long used = rt.totalMemory() - rt.freeMemory(), max = rt.maxMemory();
            int pct = (int) Math.round(used * 100.0 / Math.max(1, max));
            String gb = String.format(Locale.ROOT, "%.1f/%.1f GB", used / 1073741824.0, max / 1073741824.0);
            String s = style.is("Percent") ? "Mem " + pct + "%" : style.is("GB") ? "Mem " + gb : "Mem " + pct + "%  " + gb;
            textBox(r, s);
        }
    }

    public static final class Stopwatch extends HudModule {
        public final Setting.Bind startKey = add(new Setting.Bind("startkey", "Start / stop key", -1, null));
        public final Setting.Bind resetKey = add(new Setting.Bind("resetkey", "Reset key", -1, null));
        public final Setting.Mode mode = add(new Setting.Mode("mode", "Mode", "Stopwatch", "Stopwatch", "Timer"));
        public final Setting.Number minutes = add(new Setting.Number("minutes", "Timer minutes", 5, 1, 60, 1, " min"));
        private long startedAt, elapsed;
        private boolean running, startDown, resetDown, rang;

        public Stopwatch() {
            super("stopwatch", "Stopwatch / Timer", "Count up, or count down from a set time", false, 0.5f, 0.12f);
            add(new Setting.Action("toggle", "Start / stop", new Runnable() { @Override public void run() { toggleRun(); } }));
            add(new Setting.Action("reset", "Reset", new Runnable() { @Override public void run() { reset(); } }));
        }

        private void toggleRun() {
            if (running) {
                elapsed += System.currentTimeMillis() - startedAt;
                running = false;
            } else {
                startedAt = System.currentTimeMillis();
                running = true;
            }
        }

        private void reset() {
            running = false;
            elapsed = 0;
            rang = false;
        }

        private long ms() { return elapsed + (running ? System.currentTimeMillis() - startedAt : 0); }

        @Override
        public void onTick() {
            boolean menus = p().screenOpen();
            boolean s = !menus && p().rawKeyDown(startKey.code()), rs = !menus && p().rawKeyDown(resetKey.code());
            if (s && !startDown) toggleRun();
            if (rs && !resetDown) reset();
            startDown = s;
            resetDown = rs;
            if (mode.is("Timer") && running && !rang && ms() >= minutes.i() * 60000L) {
                rang = true;
                p().title("\u00a7f\u00a7lTIME'S UP", "\u00a77" + minutes.i() + " minute timer finished");
            }
        }

        @Override
        public void draw(Render r, boolean editing) {
            long t = mode.is("Timer") ? Math.max(0, minutes.i() * 60000L - ms()) : ms();
            long m = t / 60000, s = t / 1000 % 60, d = t / 100 % 10;
            String text = String.format(Locale.ROOT, "%02d:%02d.%d", m, s, d) + (running ? "" : "  \u00a77paused");
            textBox(r, text);
        }
    }

    public static final class ServerAddress extends HudModule {
        public ServerAddress() { super("serveraddress", "Server Address", "Shows which server you're on", false, 0.01f, 0.8f); }

        @Override
        public void draw(Render r, boolean editing) {
            String s = p().server();
            if (s.isEmpty() || s.equals("singleplayer")) s = "Singleplayer";
            textBox(r, s);
        }

        @Override public boolean visible() { return p().inWorld(); }
    }

    public static final class ItemCounter extends HudModule {
        private final Setting.Bool arrows = add(new Setting.Bool("arrows", "Arrows", true));
        private final Setting.Bool gapples = add(new Setting.Bool("gapples", "Golden apples", true));
        private final Setting.Bool pearls = add(new Setting.Bool("pearls", "Ender pearls", true));
        private final Setting.Bool potions = add(new Setting.Bool("potions", "Splash potions", false));
        private final Setting.Bool held = add(new Setting.Bool("held", "Held item", true));
        private final Setting.Bool hideZero = add(new Setting.Bool("hidezero", "Hide empty", true));

        public ItemCounter() { super("itemcounter", "Item Counter", "Counts arrows, gapples, pearls and more", false, 0.99f, 0.5f); }

        @Override
        public void draw(Render r, boolean editing) {
            String[][] rows = {{"minecraft:arrow", arrows.on() ? "1" : ""}, {"minecraft:golden_apple", gapples.on() ? "1" : ""},
                    {"minecraft:ender_pearl", pearls.on() ? "1" : ""}, {"minecraft:splash_potion", potions.on() ? "1" : ""},
                    {"held", held.on() ? "1" : ""}};
            int y = 0, maxW = 18;
            for (String[] row : rows) {
                if (row[1].isEmpty()) continue;
                int count = p().inWorld() ? p().countItem(row[0].equals("held") ? null : row[0]) : 0;
                Object stack = p().inWorld() ? p().itemStack(row[0]) : null;
                if (editing && stack == null) count = 16;
                if (!editing && (stack == null || count <= 0 && hideZero.on())) continue;
                if (background.on()) r.rect(0, y, w, 17, bgColor.argb());
                if (stack != null) r.item(stack, 1, y);
                else r.rect(3, y + 3, 11, 11, 0x40FFFFFF);
                String c = String.valueOf(count);
                r.text(c, 20, y + 5, color.argb(), shadow.on());
                maxW = Math.max(maxW, 24 + r.textWidth(c));
                y += 17;
            }
            w = maxW;
            h = Math.max(16, y);
        }

        @Override public boolean visible() { return p().inWorld(); }
    }

    public static final class TeamView extends HudModule {
        private final Setting.Number max = add(new Setting.Number("max", "Max players", 6, 1, 12, 1, ""));

        public TeamView() { super("teamview", "Team View", "Your teammates with health and distance", false, 0.99f, 0.15f); }

        @Override
        public void draw(Render r, boolean editing) {
            List<Platform.Teammate> team = p().inWorld() ? p().teammates() : java.util.Collections.<Platform.Teammate>emptyList();
            if (team.isEmpty() && editing) {
                team = new java.util.ArrayList<Platform.Teammate>();
                team.add(new Platform.Teammate("Teammate", 12, 18));
                team.add(new Platform.Teammate("Friend", 40, 9));
            }
            int y = 0, maxW = 60;
            int n = 0;
            for (Platform.Teammate t : team) {
                if (n++ >= max.i()) break;
                String hp = String.format(Locale.ROOT, "%.0f\u2764", t.health);
                String line = t.name + "  " + Math.round(t.distance) + "m";
                int lw = r.textWidth(line) + 8 + r.textWidth(hp);
                maxW = Math.max(maxW, lw + 10);
                if (background.on()) r.rect(0, y, w, 12, bgColor.argb());
                r.text(line, 4, y + 2, color.argb(), shadow.on());
                int hc = t.health > 12 ? 0xFF55FF55 : t.health > 6 ? 0xFFFFD93D : 0xFFFF5555;
                r.text(hp, w - 4 - r.textWidth(hp), y + 2, hc, shadow.on());
                y += 12;
            }
            w = maxW;
            h = Math.max(12, y);
        }

        @Override public boolean visible() { return p().inWorld() && !p().teammates().isEmpty(); }
    }

    // -------------------------------------------------------------- visual

    public static final class ChunkBorders extends Module {
        public ChunkBorders() { super("chunkborders", "Chunk Borders", "Shows chunk edges around you", Category.VISUAL, false); }

        @Override public void onEnable() { if (p() != null) p().setChunkBorders(true); }
        @Override public void onDisable() { if (p() != null) p().setChunkBorders(false); }
    }

    public static final class Hitboxes extends Module {
        public Hitboxes() { super("hitboxes", "Hitboxes", "Draws entity hitboxes (like F3+B)", Category.VISUAL, false); }

        private boolean applied;

        @Override
        public void onTick() {
            if (!applied && p().inWorld()) {
                p().setHitboxes(true);
                applied = true;
            }
        }

        @Override
        public void onDisable() {
            if (p() != null) p().setHitboxes(false);
            applied = false;
        }
    }

    public static final class GlintColorizer extends Module {
        public final Setting.Color color = add(new Setting.Color("color", "Glint color", 0xFFFF6BCB));

        public GlintColorizer() { super("glint", "Glint Colorizer", "Recolours the enchantment glint", Category.VISUAL, false); }
    }

    // ------------------------------------------------------------- Hypixel

    public static final class HypixelTools extends Module {
        public final Setting.Bool autoTip = add(new Setting.Bool("autotip", "AutoTip every 15 min", true));
        private long lastTip;

        public HypixelTools() {
            super("hypixel", "Hypixel Quickplay", "One-click game queues and AutoTip", Category.HYPIXEL, false);
            String[][] games = {{"Bed Wars Solo", "play bedwars_eight_one"}, {"Bed Wars Doubles", "play bedwars_eight_two"},
                    {"Bed Wars 3v3v3v3", "play bedwars_four_three"}, {"Bed Wars 4v4v4v4", "play bedwars_four_four"},
                    {"SkyWars Solo", "play solo_normal"}, {"SkyWars Doubles", "play teams_normal"},
                    {"Duels Classic", "play duels_classic_duel"}, {"Duels Sumo", "play duels_sumo_duel"},
                    {"Duels Bridge", "play duels_bridge_duel"}, {"Duels UHC", "play duels_uhc_duel"},
                    {"Back to lobby", "lobby"}};
            for (final String[] g : games) {
                add(new Setting.Action(g[1].replace(' ', '_'), g[0], new Runnable() {
                    @Override public void run() { if (onHypixel()) { p().command(g[1]); p().closeScreen(); } }
                }));
            }
        }

        private static boolean onHypixel() {
            return p() != null && p().server().toLowerCase(Locale.ROOT).contains("hypixel");
        }

        @Override
        public void onTick() {
            if (!autoTip.on() || !onHypixel()) return;
            long now = System.currentTimeMillis();
            if (lastTip == 0) lastTip = now - 14 * 60000L;   // first tip ~1 min after joining
            if (now - lastTip >= 15 * 60000L) {
                lastTip = now;
                p().command("tip all");
            }
        }
    }

    static int unused() { return Draw.FG; }

    /** Item display: the block you're looking at, with its icon (like WAILA, just the name). */
    public static final class BlockInfo extends HudModule {
        public final Setting.Bool icon = add(new Setting.Bool("icon", "Show icon", true));
        public final Setting.Bool id = add(new Setting.Bool("id", "Show block id", false));

        public BlockInfo() {
            super("blockinfo", "Block Info", "Shows the name of the block you're looking at", Category.HUD, false, 0.5f, 0.02f);
        }

        @Override
        public boolean visible() {
            return Life.platform.inWorld() && Life.platform.targetBlockName() != null;
        }

        @Override
        public void draw(Render r, boolean editing) {
            String name = Life.platform.targetBlockName();
            Object stack = Life.platform.targetBlockStack();
            String bid = id.on() ? Life.platform.targetBlockId() : null;
            if (name == null) {
                if (!editing) return;
                name = "Grass Block";
                bid = id.on() ? "minecraft:grass_block" : null;
                stack = null;
            }
            boolean showIcon = icon.on() && stack != null;
            int textX = showIcon ? 24 : 6;
            int tw = r.textWidth(name);
            if (bid != null) tw = Math.max(tw, Math.round(r.textWidth(bid) * 0.75f));
            w = textX + tw + 6;
            h = bid != null ? 26 : 20;
            bg(r, 0, 0, w, h);
            if (showIcon) r.item(stack, 4, (h - 16) / 2);
            r.text(name, textX, bid != null ? 4 : 6, color.argb(), textShadow());
            if (bid != null) dev.life.client.core.ui.Draw.scaledText(r, bid, textX, 15, 0.75f, 0xFFA0A0A0, textShadow());
        }
    }
}

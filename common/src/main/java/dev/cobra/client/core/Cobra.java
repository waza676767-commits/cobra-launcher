package dev.cobra.client.core;

import dev.cobra.client.core.module.Extras;
import dev.cobra.client.core.module.Features;
import dev.cobra.client.core.module.HudModule;
import dev.cobra.client.core.module.HudModules;
import dev.cobra.client.core.module.Module;
import dev.cobra.client.core.module.Setting;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Shared client core: module registry, config, events. */
public final class Cobra {
    public static final String NAME = "Cobra Client";
    public static final String VERSION = "2.1.0";
    public static final List<Module> MODULES = new ArrayList<Module>();
    public static Platform platform;
    public static boolean animations = true;
    /** Launched in the launcher's No account mode: singleplayer only. */
    public static boolean offline;
    private static File configFile;
    private static long ticks;

    private Cobra() {}

    public static void init(Platform p) {
        platform = p;
        MODULES.add(new Features.Client());
        // HUD
        MODULES.add(new HudModules.Keystrokes());
        MODULES.add(new HudModules.Cps());
        MODULES.add(new HudModules.Fps());
        MODULES.add(new HudModules.Ping());
        MODULES.add(new HudModules.Coordinates());
        MODULES.add(new HudModules.Compass());
        MODULES.add(new HudModules.ArmorStatus());
        MODULES.add(new HudModules.Reach());
        MODULES.add(new HudModules.Combo());
        MODULES.add(new HudModules.Potions());
        MODULES.add(new HudModules.BossBar());
        MODULES.add(new HudModules.Scoreboard());
        // Visual
        MODULES.add(new Features.MotionBlur());
        MODULES.add(new Features.ViewModel());
        MODULES.add(new Features.Fullbright());
        MODULES.add(new Features.Crosshair());
        MODULES.add(new Features.HitColor());
        MODULES.add(new Features.HurtCam());
        MODULES.add(new Features.ItemPhysics());
        MODULES.add(new Features.Zoom());
        MODULES.add(new Features.Freelook());
        MODULES.add(new Features.TimeChanger());
        MODULES.add(new Features.FovModifier());
        MODULES.add(new Features.Particles());
        MODULES.add(new Features.Tweaks());
        MODULES.add(new Features.MouseTrail());
        MODULES.add(new Features.Sky());
        MODULES.add(new Features.Cosmetics());
        MODULES.add(new Features.OldVisuals());
        MODULES.add(new Features.OldSounds());
        MODULES.add(new Features.MenuBlur());
        MODULES.add(new Features.PackOrganizer());
        MODULES.add(new Features.WavyCapes());
        MODULES.add(new HudModules.Saturation());
        MODULES.add(new HudModules.PackInfo());
        MODULES.add(new HudModules.Watermark());
        MODULES.add(new Features.Recorder());
        MODULES.add(new Features.BlockOverlay());
        // Utility
        MODULES.add(new HudModules.ToggleSprint());
        MODULES.add(new Features.Waypoints());
        MODULES.add(new Features.ChatMod());
        MODULES.add(new Features.NickHider());
        MODULES.add(new Features.ScreenshotUploader());
        MODULES.add(new Extras.Memory());
        MODULES.add(new Extras.Stopwatch());
        MODULES.add(new Extras.ServerAddress());
        MODULES.add(new Extras.ItemCounter());
        MODULES.add(new Extras.BlockInfo());
        MODULES.add(new Extras.TeamView());
        MODULES.add(new Extras.ChunkBorders());
        MODULES.add(new Extras.Hitboxes());
        MODULES.add(new Extras.GlintColorizer());
        // Hypixel
        MODULES.add(new HudModules.BedWars());
        MODULES.add(new Extras.HypixelTools());

        // card icons + a toggle key for every module
        String[][] icons = {{"keystrokes", "keyboard"}, {"cps", "mouse"}, {"fps", "gauge"}, {"ping", "signal"}, {"coords", "pin"},
                {"compass", "compass"}, {"armor", "shield"}, {"reach", "ruler"}, {"combo", "flame"}, {"potions", "flask"},
                {"bossbar", "skull"}, {"scoreboard", "list"}, {"motionblur", "wind"}, {"viewmodel", "sword"}, {"fullbright", "sun"},
                {"crosshair", "crosshair"}, {"hitcolor", "drop"}, {"hurtcam", "shake"}, {"itemphysics", "mods"}, {"zoom", "search"},
                {"freelook", "eye"}, {"timechanger", "clock"}, {"fov", "aperture"}, {"particles", "sparkle"}, {"togglesprint", "run"},
                {"waypoints", "flag"}, {"chat", "chat"}, {"nickhider", "mask"}, {"screenshots", "camera"}, {"bedwars", "bed"}, {"client", "settings"},
                {"memory", "gauge"}, {"stopwatch", "clock"}, {"serveraddress", "globe"}, {"itemcounter", "list"}, {"teamview", "user"},
                {"chunkborders", "mods"}, {"hitboxes", "crosshair"}, {"glint", "sparkle"}, {"hypixel", "link"},
                {"tweaks", "flame"}, {"mousetrail", "sparkle"}, {"sky", "sun"}, {"cosmetics", "star"}, {"oldvisuals", "sword"}, {"oldsounds", "sliders"}, 
                {"menublur", "eye"}, {"packorganizer", "packs"}, {"wavycapes", "flag"}, {"saturation", "drop"},
                {"packinfo", "packs"}, {"watermark", "star"}, {"recorder", "camera"}, {"blockoverlay", "crosshair"}, {"blockinfo", "search"}};
        for (Module m : MODULES) {
            for (String[] ic : icons) if (ic[0].equals(m.id)) m.icon = ic[1];
            if (!m.hidden) {
                m.toggleKey = new Setting.Bind("togglekey", "Toggle key", -1, null);
                m.settings.add(m.toggleKey);
            }
        }

        configFile = new File(new File(p.gameDir(), "config"), "cobra.properties");
        load();
        get(Features.Cosmetics.class).applyFromLauncher();   // cosmetics picked in the launcher
        // the launcher passes its Light mode / No account setting; it wins over the saved in-game choice
        String theme = System.getProperty("cobra.theme");
        Features.Client client = get(Features.Client.class);
        if (theme != null) client.theme.set("light".equalsIgnoreCase(theme) ? "Light" : "Dark");
        offline = Boolean.getBoolean("cobra.offline");
        syncClient();
        Features.ScreenshotUploader.startWatcher(new File(p.gameDir(), "screenshots"));
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override public void run() { save(); }
        }, "cobra-save"));
    }

    @SuppressWarnings("unchecked")
    private static final java.util.Map<Class<?>, Module> BY_TYPE = new java.util.HashMap<Class<?>, Module>();

    /** Module by class. Hooks call this many times per frame, so lookups are cached (was a list scan). */
    public static <T extends Module> T get(Class<T> type) {
        Module m = BY_TYPE.get(type);
        if (m == null) {
            for (Module x : MODULES) {
                if (type.isInstance(x)) {
                    m = x;
                    break;
                }
            }
            if (m == null) throw new IllegalArgumentException(type.getName());
            BY_TYPE.put(type, m);
        }
        return (T) m;
    }

    public static boolean on(Class<? extends Module> type) {
        return platform != null && get(type).isEnabled();
    }

    // ------------------------------------------------------------------ events

    /** Applies the Client settings (theme, animations). Cheap; runs every tick. */
    private static int customBg, customFg;
    private static String capeHash;
    private static long capeChecked;
    private static String capeUploadedFor;

    /**
     * What you wear for everyone else: your cosmetics plus "cape:HASH" when you imported a cape in
     * Cobra Launcher (others download it from the online list). The cape is uploaded once per
     * change.
     */
    public static String ownCode() {
        String cos = get(Features.Cosmetics.class).serialize();
        long now = System.currentTimeMillis();
        if (now - capeChecked > 10_000) {                      // re-check the file every 10 s
            capeChecked = now;
            try {
                java.io.File f = new java.io.File(new java.io.File(new java.io.File(platform.gameDir(), "config"), "cobra"), "cape.png");
                if (f.isFile() && f.length() > 0 && f.length() <= 256 * 1024) {
                    byte[] png = java.nio.file.Files.readAllBytes(f.toPath());
                    java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
                    byte[] d = md.digest(png);
                    StringBuilder h = new StringBuilder();
                    for (int i = 0; i < 4; i++) h.append(String.format("%02x", d[i]));
                    capeHash = h.toString();
                    String uuid = platform.playerUuid();
                    if (uuid != null && !(capeHash + uuid).equals(capeUploadedFor)) {
                        CobraOnline.uploadCape(uuid, png);
                        capeUploadedFor = capeHash + uuid;
                    }
                } else {
                    capeHash = null;
                }
            } catch (Exception ignored) {}
        }
        if (capeHash == null) return cos;
        return cos.isEmpty() ? "cape:" + capeHash : cos + ",cape:" + capeHash;
    }

    public static void syncClient() {
        Features.Client cc = get(Features.Client.class);
        dev.cobra.client.core.ui.Draw.speedK = cc.menuSpeed.f() / 100f;
        dev.cobra.client.core.ui.Draw.glassK = cc.hudGlass.f() / 100f;
        Features.Client c = get(Features.Client.class);
        int lbg = c.launcherColour("bg"), lfg = c.launcherColour("fg");
        boolean fromLauncher = lbg != -1 && lfg != -1 && !c.theme.is("Custom");
        if (c.theme.is("Custom") || fromLauncher) {
            int bg = fromLauncher ? lbg : c.themeBg.argb(), fg = fromLauncher ? lfg : c.themeText.argb();
            if (bg != customBg || fg != customFg) {
                dev.cobra.client.core.ui.Draw.setCustom(bg, fg);
                customBg = bg;
                customFg = fg;
            }
        } else {
            boolean light = c.theme.is("Light");
            if (customBg != 0 || light != dev.cobra.client.core.ui.Draw.light) dev.cobra.client.core.ui.Draw.setLight(light);
            customBg = customFg = 0;
        }
        animations = c.animations.on();
    }


    public static void tick() {
        ticks++;
        syncClient();
        if (ticks % 40 == 1) writePresence();
        if (ticks % 20 == 0) CobraOnline.ownCosmetics = ownCode();
        if (ticks % 20 == 0 && platform.inWorld()) {
            boolean tab = get(Features.Client.class).tabIcon.on();
            boolean wear = get(Features.Cosmetics.class).isEnabled();
            // share yourself for the Tab icon / your cosmetics; always fetch so you see other Cobra players' cosmetics
            CobraOnline.tick(platform.playerUuid(), tab || wear, true);
        }
        // per-module toggle keys (only while playing, not in menus)
        boolean menus = platform.screenOpen();
        for (Module m : MODULES) {
            if (m.toggleKey == null) continue;
            int code = m.toggleKey.code();
            boolean down = code >= 0 && !menus && platform.rawKeyDown(code);
            if (down && !m.keyWasDown) {
                m.toggle();
                save();
            }
            m.keyWasDown = down;
        }
        for (Module m : MODULES) {
            if (!m.isEnabled()) continue;
            try {
                m.onTick();
            } catch (Throwable t) {
                fail(m, t);
            }
        }
        if (ticks % 1200 == 0) save();
    }

    public static void renderHud(Render r) {
        Features.Recorder recorder = get(Features.Recorder.class);
        try {
            if (recorder.isEnabled()) recorder.renderBadge(r);
        } catch (Throwable t) {
            fail(recorder, t);
        }
        if (platform.hudHidden()) return;
        Features.Waypoints wp = get(Features.Waypoints.class);
        try {
            if (wp.isEnabled() && platform.inWorld()) wp.render(r);
        } catch (Throwable t) {
            fail(wp, t);
        }
        for (Module m : MODULES) {
            if (!(m instanceof HudModule) || !m.isEnabled()) continue;
            HudModule h = (HudModule) m;
            try {
                if (!h.visible()) continue;
                drawHud(r, h, false);
            } catch (Throwable t) {
                fail(m, t);
            }
        }
    }

    /**
     * Safety net: a module that throws is switched off (with the reason shown in the menu) instead
     * of crashing the game. Other mods and the rest of Cobra keep working.
     */
    public static void fail(Module m, Throwable t) {
        try {
            if (m.isEnabled()) m.toggle();
        } catch (Throwable ignored) {}
        m.problem = "Turned off after an error: " + t.getClass().getSimpleName();
        System.err.println("[Cobra] " + m.name + " failed and was turned off");
        t.printStackTrace();
    }

    public static void drawHud(Render r, HudModule h, boolean editing) {
        r.push();
        r.translate(h.screenX(r), h.screenY(r));
        r.scale(h.scale.f());
        h.draw(r, editing);
        r.pop();
    }

    /** Called by the platform when the local player attacks an entity; reach is eye→hit distance. */
    public static void onAttack(Object target, double reach) {
        get(HudModules.Reach.class).hit(reach);
        get(HudModules.Combo.class).attacked(target);
        Features.Particles particles = get(Features.Particles.class);
        if (particles.isEnabled()) particles.onHit(target);
    }

    public static void onMouseButton(int button) {
        if (!platform.screenOpen()) ClickTracker.click(button);
    }

    /** Plain text of every incoming chat/system line. */
    public static void onChat(String plain) {
        get(HudModules.BedWars.class).onChat(plain);
    }

    // ------------------------------------------------------------------ config

    public static void load() {
        Properties p = new Properties();
        if (configFile.exists()) {
            InputStream in = null;
            try {
                in = new FileInputStream(configFile);
                p.load(in);
            } catch (Exception ignored) {
            } finally {
                close(in);
            }
        }
        for (Module m : MODULES) {
            String en = p.getProperty(m.id + ".enabled");
            for (Setting<?> s : m.settings) {
                String v = p.getProperty(m.id + "." + s.id);
                if (v != null) s.load(v);
            }
            if (m instanceof HudModule) {
                HudModule h = (HudModule) m;
                h.px = parse(p.getProperty(m.id + ".x"), h.px);
                h.py = parse(p.getProperty(m.id + ".y"), h.py);
            }
            if (en != null) {
                boolean on = Boolean.parseBoolean(en);
                if (on != m.isEnabled()) m.loadEnabled(on);
                else if (on) m.loadEnabled(true);
            } else if (m.isEnabled()) m.loadEnabled(true);
        }
        // settings from older versions of Cobra
        if (p.getProperty("client.theme") == null && "true".equals(p.getProperty("cobra.light"))) get(Features.Client.class).theme.set("Light");
        if (p.getProperty("client.animations") == null && "false".equals(p.getProperty("cobra.animations"))) get(Features.Client.class).animations.set(false);
        get(Features.Waypoints.class).load(p);
    }

    public static synchronized void save() {
        if (configFile == null) return;
        Properties p = new Properties();
        for (Module m : MODULES) {
            p.setProperty(m.id + ".enabled", String.valueOf(m.isEnabled()));
            for (Setting<?> s : m.settings) {
                String v = s.save();
                if (v != null) p.setProperty(m.id + "." + s.id, v);
            }
            if (m instanceof HudModule) {
                p.setProperty(m.id + ".x", String.valueOf(((HudModule) m).px));
                p.setProperty(m.id + ".y", String.valueOf(((HudModule) m).py));
            }
        }

        get(Features.Waypoints.class).save(p);
        OutputStream out = null;
        try {
            configFile.getParentFile().mkdirs();
            out = new FileOutputStream(configFile);
            p.store(out, "Cobra Client");
        } catch (Exception ignored) {
        } finally {
            close(out);
        }
    }

    private static float parse(String s, float def) {
        try {
            return s == null ? def : Float.parseFloat(s);
        } catch (Exception e) {
            return def;
        }
    }

    private static void close(java.io.Closeable c) {
        try {
            if (c != null) c.close();
        } catch (Exception ignored) {}
    }

    public static long ticks() { return ticks; }

    private static String presenceLast = "";

    /**
     * Tells the launcher what you're doing, for Discord Rich Presence: one line in
     * config/cobra/presence.txt ("menu", "singleplayer" or "server:address"). Written only
     * when it changes, checked every 2 seconds.
     */
    private static void writePresence() {
        try {
            String server = platform.server();
            String line = !platform.inWorld() ? "menu"
                    : "singleplayer".equals(server) ? "singleplayer"
                    : server == null || server.isEmpty() ? "menu" : "server:" + server;
            if (line.equals(presenceLast)) return;
            presenceLast = line;
            java.io.File dir = new java.io.File(new java.io.File(platform.gameDir(), "config"), "cobra");
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            java.io.OutputStream out = new java.io.FileOutputStream(new java.io.File(dir, "presence.txt"));
            try {
                out.write(line.getBytes("UTF-8"));
            } finally {
                out.close();
            }
        } catch (Exception ignored) {
            // presence is best-effort
        }
    }
}

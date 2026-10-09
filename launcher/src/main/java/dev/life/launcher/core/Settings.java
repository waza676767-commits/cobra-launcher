package dev.life.launcher.core;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Settings {
    private static final Path FILE = Paths.ROOT.resolve("settings.json");
    private static Settings instance;

    public int ramMb = 4096;
    public int width = 1280;
    public int height = 720;
    public boolean fullscreen = false;
    public String jvmArgs = "";
    public boolean animations = true;
    public boolean keepOpen = false;
    public boolean sidebarExpanded = false;
    public String lastVersion = "1.21.11";
    public boolean lightMode = false;
    /** "solid" or "glass" (liquid glass surfaces over a blurred colour backdrop). */
    public String style = "solid";
    /** Glass blur strength, 0-100. */
    public int frost = 45;
    /** No account: skip Microsoft sign-in, play offline in singleplayer only. */
    public boolean offline = false;
    public String offlineName = "";
    public String selectedProfile = "default";
    /** "" (none), "image" or "video" (GIFs are imported as video frames). */
    public String wallpaperType = "";
    public String wallpaperName = "";
    /** How much the wallpaper is darkened (lightened in light mode), 0-90. */
    public int wallpaperDim = 45;
    /** Slim (Alex) arms for the imported skin. */
    public boolean skinSlim = false;
    /** Microsoft OAuth tenant: "consumers" (personal accounts) or "common" (personal + work/school). */
    public String msTenant = "consumers";
    /** Launcher accent colour: a name from Theme.ACCENTS; "Custom" uses accentCustom. */
    public String accent = "Life";
    public int accentCustom = 0x7C5CFF;
    /** Own background gradient (used when there's no wallpaper): off, or two colours + angle. */
    public boolean gradient = false;
    public boolean wallpaperTint = false;
    public int gradientA = 0x1B1B3A, gradientB = 0x0B0B0D;
    public int gradientAngle = 135;
    /** How strongly the gradient tints a wallpaper (0-100 %). Without a wallpaper it's the whole background. */
    public int gradientStrength = 45;
    /** Super optimization (Vulkan): VulkanMod + companions added to your profiles; off removes them. */
    public boolean superOptimization = false;
    /** The beta warning was shown (first start). */
    public boolean betaNoticeSeen = false;
    /** The first-start "set a password?" question was asked. */
    public boolean passwordAsked = false;
    /** The first-start speed check ran (it may have turned on More optimization). */
    public boolean speedChecked = false;

    /** Colour theme for the whole launcher (see Theme.THEMES); with themeInGame the game uses it too. */
    public String theme = "Life";
    public boolean themeInGame = true;
    // ---- cosmetics (sent to Life Client at launch; only Life players see them)
    public String cosEars = "Off", cosWings = "Off", cosHalo = "Off", cosGloves = "Off";
    public String cosWingStyle = "Feather", cosHat = "Off", cosBunny = "Off", cosHorns = "Off", cosGlasses = "Off",
            cosHeadphones = "Off", cosTailKind = "Off", cosBackpack = "Off", cosAntlers = "Off", cosOrbit = "Off", cosScarf = "Off",
            cosFlowers = "Off", cosBowtie = "Off", cosSpikes = "Off";
    public boolean cosTail = false, cosKatana = false, cosFeet = false, cosGlow = true;
    public int cosSize = 100;
    /** Built-in capes added to the library (1 = the first set). */
    public int presetCapes = 0;
    /** Simple Voice Chat installed in every Fabric profile. */
    public boolean voiceChat = true;
    /** Your own name for the launcher (empty = Life). */
    public String launcherName = "";
    /** The previous look (round rail, big Home card). */
    public boolean legacyGui = false;
    public String cosParticles = "Off";
    public boolean cosTrail = false;

    private static String c(String v) {
        return v.toLowerCase().replace(" ", "");
    }

    /** When cosmetics.txt from the game was last taken over (so it's only applied once per change). */
    public long cosSyncedAt = 0;

    private static String pretty(String v, String... options) {
        if (v == null || v.isEmpty()) return "Off";
        if (v.matches("x[0-9a-f]{6}")) return v;                         // a custom colour
        for (String o : options) if (c(o).equals(v)) return o;
        return "Off";
    }

    /** Takes over a cosmetics code written by the game (changes made in game with Right Shift). */
    public void applyCosmeticsCode(String code) {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        if (code != null && !code.equals("off")) {
            for (String part : code.split(",")) {
                if (part.isEmpty()) continue;
                int i = part.indexOf(':');
                m.put(i < 0 ? part : part.substring(0, i), i < 0 ? "" : part.substring(i + 1));
            }
        }
        cosWings = pretty(m.get("wings"), "Angel", "Red", "Black", "Gold", "Blue", "Purple", "Pink", "Green", "Cyan");
        cosWingStyle = m.containsKey("wingstyle") ? pretty(m.get("wingstyle"), "Feather", "Dragon", "Butterfly", "Demon", "Energy", "Fairy") : "Feather";
        if ("Off".equals(cosWingStyle)) cosWingStyle = "Feather";
        cosHalo = pretty(m.get("halo"), "Angel", "Red");
        cosHat = pretty(m.get("hat"), "Crown", "Top hat", "Witch", "Santa", "Viking");
        cosEars = pretty(m.get("ears"), "Black", "White", "Ginger", "Pink");
        cosBunny = pretty(m.get("bunny"), "White", "Pink", "Black", "Brown");
        cosHorns = pretty(m.get("horns"), "Red", "Black", "White", "Gold");
        cosGlasses = pretty(m.get("glasses"), "Black", "Gold", "Pink");
        cosHeadphones = pretty(m.get("headphones"), "Black", "White", "Pink", "Blue");
        cosTailKind = pretty(m.get("tail"), "Black", "White", "Ginger", "Pink", "Fox");
        cosTail = false;
        cosBackpack = pretty(m.get("backpack"), "Brown", "Black", "Blue", "Red");
        cosGloves = pretty(m.get("gloves"), "Red", "Blue", "Black");
        cosAntlers = pretty(m.get("antlers"), "Brown", "White", "Gold");
        cosOrbit = pretty(m.get("orbit"), "Purple", "Cyan", "Red", "Gold", "Green");
        cosScarf = pretty(m.get("scarf"), "Red", "Blue", "Green", "White", "Black");
        cosFlowers = pretty(m.get("flowers"), "Pink", "White", "Red", "Purple", "Gold");
        cosBowtie = pretty(m.get("bowtie"), "Red", "Black", "Blue", "Pink", "Gold");
        cosSpikes = pretty(m.get("spikes"), "Purple", "Red", "Black", "Green", "Gold");
        cosKatana = m.containsKey("katana");
        cosFeet = m.containsKey("feet");
        cosParticles = pretty(m.get("fx"), "Sparkles", "Hearts", "Flames", "Soul fire", "Snow", "Magic", "Petals", "Notes");
        cosTrail = m.containsKey("trail");
        if (!m.isEmpty()) cosGlow = m.containsKey("glow");
        try {
            cosSize = m.containsKey("size") ? Integer.parseInt(m.get("size")) : 100;
        } catch (NumberFormatException ignored) {}
    }

    /** The cosmetics as the short code the game and the online list use. */
    public String cosmeticsCode() {
        if (cosTail && "Off".equals(cosTailKind)) cosTailKind = "Black";      // the old on/off tail
        String[][] modes = {{"wings", cosWings}, {"halo", cosHalo}, {"hat", cosHat}, {"ears", cosEars}, {"bunny", cosBunny},
                {"horns", cosHorns}, {"glasses", cosGlasses}, {"headphones", cosHeadphones}, {"tail", cosTailKind},
                {"backpack", cosBackpack}, {"gloves", cosGloves}, {"antlers", cosAntlers}, {"orbit", cosOrbit}, {"scarf", cosScarf},
                {"flowers", cosFlowers}, {"bowtie", cosBowtie}, {"spikes", cosSpikes}};
        StringBuilder b = new StringBuilder();
        for (String[] m : modes) if (m[1] != null && !"Off".equals(m[1])) b.append(m[0]).append(':').append(c(m[1])).append(',');
        if (!"Off".equals(cosWings) && cosWingStyle != null && !"Feather".equals(cosWingStyle)) b.append("wingstyle:").append(c(cosWingStyle)).append(',');
        if (cosKatana) b.append("katana,");
        if (cosFeet) b.append("feet,");
        if (cosParticles != null && !"Off".equals(cosParticles)) {
            b.append("fx:").append(c(cosParticles)).append(',');
            if (cosTrail) b.append("trail,");
        }
        if (b.length() == 0) return "";
        if (cosSize != 100) b.append("size:").append(cosSize).append(',');
        if (cosGlow) b.append("glow,");
        return b.substring(0, b.length() - 1);
    }

    /** Join this server straight away when the game starts (empty = the main menu). */
    public String quickJoin = "";

    // ---- customization
    /** Corner roundness, 50–150 % (100 = normal). */
    public int roundness = 100;
    /** How dark/light the glass tint is and how visible the glass borders are, 0–200 % (100 = normal). */
    public int glassTint = 100, glassBorder = 30;
    /** One-time: the old bright outlines (100%) were turned down to 30%. */
    public boolean outlinesSoftened = false;
    /** Animation speed, 50–200 % (100 = normal). */
    public int animSpeed = 100;
    /** Clock: 24-hour or 12-hour, with seconds, and whether the date shows. */
    public boolean clock24 = true, clockSeconds = false, clockDate = true;
    /** Home: the big title (default LIFE) and your own line under it (empty = automatic). */
    public String homeTitle = "LIFE", homeGreeting = "";
    /** Home: show your wallpaper in the big card (off = a calm dark gradient). */
    public boolean heroWallpaper = true;
    /** Home cards you hid ("profiles", "skins", "quick"). */
    public java.util.Set<String> homeHidden = new java.util.HashSet<>();
    /** Profile categories (e.g. "PvP"), in the order they're shown. */
    public java.util.List<String> profileCategories = new java.util.ArrayList<>();
    /** More optimization: plain panels, single-colour background, no blur, no animations. */
    public boolean moreOptimization = false;
    /** Set once the Life look has been applied (the rebrand from Life). */
    public boolean lifeLook = false;
    /** Set once wallpapers turned off by the previous version have been switched back on. */
    public boolean wallpaperBack = false;
    public Boolean savedAnimationsLite;
    /** What Super optimization changed, to put back when it's turned off. */
    public String savedStyle, savedGlassLook;
    public Boolean savedAnimations;
    /** Glass look: "frosted" (soft blur, calm, no bending; default) or "liquid" (clear, bends at the rim). */
    public String glassLook = "frosted";   // "frosted" (default, like the reference widgets) or "liquid" (Clear)
    /** Discord Rich Presence: show "Playing Life Client" on your Discord profile. */
    public boolean discordRpc = true;
    /** Show the server address (e.g. "On mc.eclypse.net") in the Discord status. */
    public boolean discordShowServer = true;
    /** Your own Discord lines (empty = automatic), elapsed time, profile name, a "Get Life" button. */
    public String discordDetails = "", discordState = "";
    public boolean discordShowTime = true, discordShowProfile = false, discordButton = true;
    /** Second line (what you're doing) and the small round status icon. */
    public boolean discordShowActivity = true, discordSmallIcon = true;
    /** Discord application ID for the presence (its name is what Discord shows). See README. */
    public String discordAppId = "";
    /** Azure app (client) id used for Microsoft sign-in. See README. */
    public String clientId = "";

    public static Settings get() {
        if (instance == null) {
            try {
                instance = Files.exists(FILE) ? Http.GSON.fromJson(Files.readString(FILE), Settings.class) : new Settings();
            } catch (Exception e) {
                instance = new Settings();
            }
            if (instance == null) instance = new Settings();
            instance.ramMb = Math.max(1024, Math.min(instance.ramMb, maxRamMb()));
            // Life: dark only, see-through frosted glass over the liquid, the Life palette
            instance.lightMode = false;
            instance.glassLook = "frosted";
            instance.style = "glass";
            instance.theme = "Life";
            Legacy.settings(instance);
            if (!instance.lifeLook) {                               // once: the Life accent
                instance.lifeLook = true;
                instance.accent = "Life";
                instance.gradient = false;
            }
            if (!instance.wallpaperBack) {                          // the last version turned wallpapers off: bring yours back
                instance.wallpaperBack = true;
                if (instance.wallpaperType.isEmpty() && !instance.wallpaperName.isEmpty()) {
                    java.nio.file.Path d = Paths.ROOT.resolve("wallpaper");
                    boolean frames = false;
                    try (var st = java.nio.file.Files.list(d.resolve("frames"))) {
                        frames = st.findAny().isPresent();
                    } catch (Exception ignored) {}
                    if (frames) instance.wallpaperType = "video";
                    else if (java.nio.file.Files.exists(d.resolve("still.png"))) instance.wallpaperType = "image";
                }
            }
            if (!instance.outlinesSoftened) {                     // the bright outlines people didn't like
                if (instance.glassBorder == 100) instance.glassBorder = 30;
                instance.outlinesSoftened = true;
            }
        }
        return instance;
    }

    private static final java.util.concurrent.ScheduledExecutorService SAVER =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "life-settings-save");
                t.setDaemon(true);
                return t;
            });
    private static java.util.concurrent.ScheduledFuture<?> pending;

    static {
        // whatever is still waiting to be written is written when the launcher closes
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (instance != null) instance.saveNow();
        }, "life-settings-flush"));
    }

    /**
     * Saves soon (a quarter of a second later, once, however often it's called): dragging a slider
     * used to write the file dozens of times a second on the UI thread.
     */
    public void save() {
        synchronized (Settings.class) {
            if (pending != null) pending.cancel(false);
            pending = SAVER.schedule(this::saveNow, 250, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    /** Writes now, safely: to a temporary file first, then swapped in (never a half-written file). */
    public synchronized void saveNow() {
        try {
            Files.createDirectories(FILE.getParent());
            String json = Http.GSON.toJson(this);
            java.nio.file.Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(tmp, json);
            try {
                Files.move(tmp, FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String effectiveClientId() {
        if (clientId != null && !clientId.isBlank()) return clientId.trim();
        String prop = System.getProperty("life.clientId");
        if (prop != null && !prop.isBlank()) return prop.trim();
        return BuildInfo.CLIENT_ID;
    }

    public static int maxRamMb() {
        try {
            var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            long total = os.getTotalMemorySize() / (1024 * 1024);
            return (int) Math.max(2048, total - 1024);
        } catch (Throwable t) {
            return 16384;
        }
    }
}

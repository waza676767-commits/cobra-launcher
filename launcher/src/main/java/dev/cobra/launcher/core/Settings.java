package dev.cobra.launcher.core;

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
    public String accent = "Classic";
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
    /** "new" (dashboard with the rail) or "classic" (the previous look with the wide side panel). */
    public String launcherGui = "new";
    /** Home cards you hid ("profiles", "skins", "quick"). */
    public java.util.Set<String> homeHidden = new java.util.HashSet<>();
    /** Profile categories (e.g. "PvP"), in the order they're shown. */
    public java.util.List<String> profileCategories = new java.util.ArrayList<>();
    /** More optimization: plain panels, single-colour background, no blur, no animations. */
    public boolean moreOptimization = false;
    public Boolean savedAnimationsLite;
    /** What Super optimization changed, to put back when it's turned off. */
    public String savedStyle, savedGlassLook;
    public Boolean savedAnimations;
    /** Glass look: "frosted" (soft blur, calm, no bending; default) or "liquid" (clear, bends at the rim). */
    public String glassLook = "frosted";   // "frosted" (default, like the reference widgets) or "liquid" (Clear)
    /** Discord Rich Presence: show "Playing Cobra Client" on your Discord profile. */
    public boolean discordRpc = true;
    /** Show the server address (e.g. "On mc.eclypse.net") in the Discord status. */
    public boolean discordShowServer = true;
    /** Your own Discord lines (empty = automatic), elapsed time, profile name, a "Get Cobra" button. */
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
        }
        return instance;
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, Http.GSON.toJson(this));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String effectiveClientId() {
        if (clientId != null && !clientId.isBlank()) return clientId.trim();
        String prop = System.getProperty("cobra.clientId");
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

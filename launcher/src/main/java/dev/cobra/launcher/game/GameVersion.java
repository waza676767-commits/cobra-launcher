package dev.cobra.launcher.game;

/**
 * The version the launcher installs and runs: Minecraft 1.21.11 on Fabric, with Fabric API,
 * your Modrinth mods and the Cobra Client mod. (Older launcher builds had more versions; their
 * profiles are moved over to 1.21.11 by {@link dev.cobra.launcher.core.Profiles}.)
 */
public enum GameVersion {
    MODERN("1.21.11", "1.21.11", "fabric", "Fabric");

    /** Instance folder id and label shown in the UI. */
    public final String id;
    public final String mc;
    /** Modrinth loader key: fabric, forge or vanilla. */
    public final String loader;
    public final String loaderName;

    GameVersion(String id, String mc, String loader, String loaderName) {
        this.id = id;
        this.mc = mc;
        this.loader = loader;
        this.loaderName = loaderName;
    }

    public String bundledJar() {
        return "cobra-client-" + id + ".jar";
    }

    /** True when this build of the launcher carries the Cobra in-game client for this version. */
    public boolean hasCobra() {
        return GameVersion.class.getResource("/bundled/" + bundledJar()) != null;
    }

    public boolean vanilla() {
        return loader.equals("vanilla");
    }

    /** 1.13+ options.txt pack format ("file/" prefix, last entry = highest priority). */
    public boolean modernPacks() {
        return !mc.startsWith("1.8") && !mc.startsWith("1.7");
    }

    /** Short description shown under the version in pickers. */
    public String subtitle() {
        if (hasCobra()) return loaderName + " with Cobra Client";
        return vanilla() ? "Vanilla, texture packs only" : loaderName + " with your mods";
    }

    public static GameVersion byId(String id) {
        for (GameVersion v : values()) if (v.id.equals(id)) return v;
        return MODERN;
    }
}

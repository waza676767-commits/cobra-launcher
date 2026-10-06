package dev.cobra.launcher.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.cobra.launcher.core.Http;
import dev.cobra.launcher.core.Paths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A Minecraft version the launcher can install and run. 1.21.11 comes with Cobra Client built in;
 * every other release from Mojang's list is available too: on Fabric (with Fabric API and your
 * mods) when Fabric supports it, otherwise plain vanilla. The list is cached on disk and refreshed
 * in the background, so it works offline after the first time.
 */
public final class GameVersion {
    /** The version Cobra Client is built for (always first, and the default). */
    public static final GameVersion MODERN = new GameVersion("1.21.11", "fabric");

    private static final Map<String, GameVersion> ALL = new LinkedHashMap<>();
    private static final AtomicBoolean REFRESHING = new AtomicBoolean();
    private static final Path MOJANG_CACHE = Paths.CACHE.resolve("version_manifest_v2.json");
    private static final Path FABRIC_CACHE = Paths.CACHE.resolve("fabric_game_versions.json");

    static {
        ALL.put(MODERN.id, MODERN);
        try {
            load();
        } catch (Exception ignored) {}
    }

    /** Instance folder id and label shown in the UI (the Minecraft version, e.g. 1.20.1). */
    public final String id;
    public final String mc;
    /** Modrinth loader key: fabric or vanilla. */
    public final String loader;
    public final String loaderName;

    private GameVersion(String mc, String loader) {
        this.id = mc;
        this.mc = mc;
        this.loader = loader;
        this.loaderName = loader.equals("fabric") ? "Fabric" : "Vanilla";
    }

    public String bundledJar() {
        return "cobra-client-" + id + ".jar";
    }

    /** True when this build of the launcher carries the Cobra in-game client for this version. */
    private Boolean cobra;

    public boolean hasCobra() {
        if (cobra == null) cobra = GameVersion.class.getResource("/bundled/" + bundledJar()) != null;
        return cobra;
    }

    public boolean vanilla() {
        return loader.equals("vanilla");
    }

    /** 1.13+ options.txt pack format ("file/" prefix, last entry = highest priority). */
    public boolean modernPacks() {
        return !(mc.startsWith("1.8") || mc.startsWith("1.7") || mc.matches("1\\.(9|10|11|12)(\\..*)?"));
    }

    /** Short description shown under the version in pickers. */
    public String subtitle() {
        if (hasCobra()) return loaderName + " with Abyss Client";
        return vanilla() ? "Vanilla (Fabric isn't available for it)" : loaderName + " with your mods";
    }

    /** Position in {@link #values()} (what an enum's ordinal() used to be). */
    public int ordinal() {
        GameVersion[] v = values();
        for (int i = 0; i < v.length; i++) if (v[i] == this) return i;
        return 0;
    }

    @Override
    public String toString() { return id; }

    // ------------------------------------------------------------------ the list

    /** Every version: the Cobra ones first, then all other releases, newest first. */
    public static synchronized GameVersion[] values() {
        List<GameVersion> out = new ArrayList<>();
        for (GameVersion v : ALL.values()) if (v.hasCobra()) out.add(v);
        for (GameVersion v : ALL.values()) if (!v.hasCobra()) out.add(v);
        return out.toArray(new GameVersion[0]);
    }

    /** The version with this id; an unknown id (an old profile, an offline first start) still works. */
    public static synchronized GameVersion byId(String id) {
        if (id == null || id.isBlank()) return MODERN;
        GameVersion v = ALL.get(id);
        if (v != null) return v;
        if (!id.matches("[0-9][0-9A-Za-z._ -]{0,40}")) return MODERN;
        v = new GameVersion(id, guessFabric(id) ? "fabric" : "vanilla");
        ALL.put(id, v);
        return v;
    }

    /** Fabric runs on 1.14 and newer. */
    private static boolean guessFabric(String mc) {
        try {
            String[] p = mc.split("\\.");
            int minor = Integer.parseInt(p[1].replaceAll("[^0-9].*", ""));
            return Integer.parseInt(p[0]) > 1 || minor >= 14;
        } catch (Exception e) {
            return false;
        }
    }

    /** Builds the list from the cached Mojang and Fabric lists (called at start and after a refresh). */
    private static synchronized void load() throws Exception {
        if (!Files.isRegularFile(MOJANG_CACHE)) return;
        JsonObject manifest = JsonParser.parseString(Files.readString(MOJANG_CACHE)).getAsJsonObject();
        java.util.Set<String> fabric = new java.util.HashSet<>();
        if (Files.isRegularFile(FABRIC_CACHE)) {
            for (JsonElement e : JsonParser.parseString(Files.readString(FABRIC_CACHE)).getAsJsonArray()) {
                fabric.add(e.getAsJsonObject().get("version").getAsString());
            }
        }
        for (JsonElement e : manifest.getAsJsonArray("versions")) {
            JsonObject v = e.getAsJsonObject();
            if (!"release".equals(v.get("type").getAsString())) continue;
            String id = v.get("id").getAsString();
            if (ALL.containsKey(id)) continue;
            boolean fab = fabric.isEmpty() ? guessFabric(id) : fabric.contains(id);
            ALL.put(id, new GameVersion(id, fab ? "fabric" : "vanilla"));
        }
    }

    /** Fetches Mojang's and Fabric's version lists in the background (then calls {@code done} on success). */
    public static void refresh(Runnable done) {
        if (!REFRESHING.compareAndSet(false, true)) return;
        Thread t = new Thread(() -> {
            try {
                Http.Response m = Http.get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
                if (!m.ok()) return;
                JsonElement check = JsonParser.parseString(m.body());
                if (!check.isJsonObject() || !check.getAsJsonObject().has("versions")) return;
                Files.createDirectories(Paths.CACHE);
                Files.writeString(MOJANG_CACHE, m.body());
                try {
                    Http.Response f = Http.get("https://meta.fabricmc.net/v2/versions/game");
                    if (f.ok() && f.body().trim().startsWith("[")) Files.writeString(FABRIC_CACHE, f.body());
                } catch (Exception ignored) {}
                load();
                if (done != null) done.run();
            } catch (Exception ignored) {
                // offline: the cached (or built-in) list stays
            } finally {
                REFRESHING.set(false);
            }
        }, "cobra-versions");
        t.setDaemon(true);
        t.start();
    }

    /** How many versions are known (for "Loading versions…" hints). */
    public static synchronized int count() { return ALL.size(); }
}

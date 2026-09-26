package dev.cobra.launcher.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class Paths {
    public static final String OS_NAME = detectOs();
    public static final boolean ARM = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT).contains("aarch64");

    public static final Path ROOT = detectRoot();
    public static final Path LIBRARIES = ROOT.resolve("libraries");
    public static final Path ASSETS = ROOT.resolve("assets");
    public static final Path VERSIONS = ROOT.resolve("versions");
    public static final Path RUNTIMES = ROOT.resolve("runtimes");
    public static final Path INSTANCES = ROOT.resolve("instances");
    public static final Path CACHE = ROOT.resolve("cache");
    public static final Path LOGS = ROOT.resolve("logs");

    private Paths() {}

    public static void init() throws IOException {
        for (Path p : new Path[]{ROOT, LIBRARIES, ASSETS, VERSIONS, RUNTIMES, INSTANCES, CACHE, LOGS}) Files.createDirectories(p);
    }

    public static Path instance(String id) {
        Path p = INSTANCES.resolve(id);
        try {
            Files.createDirectories(p.resolve("mods"));
            Files.createDirectories(p.resolve("resourcepacks"));
        } catch (IOException ignored) {}
        return p;
    }

    /** Mojang's rule/os name: linux, windows, osx */
    private static String detectOs() {
        String n = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (n.contains("win")) return "windows";
        if (n.contains("mac")) return "osx";
        return "linux";
    }

    private static Path detectRoot() {
        String home = System.getProperty("user.home");
        switch (OS_NAME) {
            case "windows": {
                String appData = System.getenv("APPDATA");
                return Path.of(appData != null ? appData : home, ".cobralauncher");
            }
            case "osx":
                return Path.of(home, "Library", "Application Support", "CobraLauncher");
            default: {
                String xdg = System.getenv("XDG_DATA_HOME");
                Path base = xdg != null && !xdg.isBlank() ? Path.of(xdg) : Path.of(home, ".local", "share");
                return base.resolve("CobraLauncher");
            }
        }
    }
}

package dev.life.launcher.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Moving over from the launcher's old names. This is the only place they still appear: they're
 * needed to find the old data folder, saved logins, game settings and files and give them the Life
 * names, so nobody loses their accounts, worlds, profiles or keybinds. Everything runs once.
 */
public final class Legacy {
    private Legacy() {}

    /** Old names, lower case (data folders, files, keys). */
    private static final String[] OLD = {"cobra", "abyss"};
    /** The old secret-tool service saved logins were stored under (Linux keyring). */
    public static final String OLD_KEYRING = "cobra-launcher";

    /**
     * The data folder: if only the old one exists, it's renamed to the new one (instant, same
     * disk). If that's impossible, the old one keeps being used.
     */
    static Path root(Path fresh, String os, String home) {
        if (Files.exists(fresh)) return fresh;
        Path old;
        switch (os) {
            case "windows": {
                String appData = System.getenv("APPDATA");
                old = Path.of(appData != null ? appData : home, ".cobralauncher");
                break;
            }
            case "osx":
                old = Path.of(home, "Library", "Application Support", "CobraLauncher");
                break;
            default:
                old = fresh.resolveSibling("CobraLauncher");
        }
        if (!Files.isDirectory(old)) return fresh;
        try {
            Files.move(old, fresh, StandardCopyOption.ATOMIC_MOVE);
            return fresh;
        } catch (Exception e) {
            try {
                Files.move(old, fresh);
                return fresh;
            } catch (Exception e2) {
                return old;                                   // can't move it: keep using it as it is
            }
        }
    }

    /**
     * Renames the launcher's own old-named files inside the data folder (once):
     * <ul>
     *   <li>old client / launcher jars: deleted (the Life ones replace them; two clients can't run)</li>
     *   <li>markers (Java runtime ready, managed mods, FPS mods, voice chat), disabled-mod suffixes and
     *   sky packs: renamed</li>
     *   <li>each game folder's client settings (config) and keybinds / resource packs (options.txt)</li>
     *   <li>the cape library and the old Linux app-menu entry</li>
     * </ul>
     */
    public static void migrate() {
        Path done = Paths.ROOT.resolve(".life-names");
        if (Files.exists(done)) return;
        try {
            List<Path> all = new ArrayList<>();
            Files.walkFileTree(Paths.ROOT, java.util.EnumSet.noneOf(FileVisitOption.class), 6, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
                    String n = d.getFileName() == null ? "" : d.getFileName().toString();
                    // the game's own content is never touched
                    if (d != Paths.ROOT && (n.equals("saves") || n.equals("assets") || n.equals("libraries") || n.equals("logs")
                            || n.equals("screenshots") || n.equals("crash-reports") || n.equals("schematics") || n.equals("shaderpacks")))
                        return FileVisitResult.SKIP_SUBTREE;
                    all.add(d);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                    all.add(f);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path f, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
            // deepest first, so renaming a folder never breaks a path still to visit
            all.sort((a, b) -> Integer.compare(b.getNameCount(), a.getNameCount()));
            for (Path p : all) {
                try {
                    file(p);
                } catch (Exception ignored) {}
            }
            desktop();
            Files.writeString(done, "1");
        } catch (Exception ignored) {}
    }

    private static void file(Path p) throws IOException {
        if (p.equals(Paths.ROOT) || p.getFileName() == null) return;
        String n = p.getFileName().toString(), low = n.toLowerCase(Locale.ROOT);
        Path parent = p.getParent();
        String in = parent == null || parent.getFileName() == null ? "" : parent.getFileName().toString();
        if (n.equals("options.txt") && Files.isRegularFile(p)) {
            String s = Files.readString(p, StandardCharsets.UTF_8);
            String t = s.replace("key_key.cobra.", "key_key.life.").replace("cobra-sky-", "life-sky-");
            if (!t.equals(s)) Files.writeString(p, t, StandardCharsets.UTF_8);
            return;
        }
        if (!has(low)) return;
        if (low.startsWith("cobra-client") || low.startsWith("cobra-launcher.jar")) {        // replaced by the Life ones
            Files.deleteIfExists(p);
            return;
        }
        String target = null;
        if (low.equals(".cobra-ok")) target = ".life-ok";
        else if (low.startsWith(".cobra-") || low.startsWith(".abyss-")) target = ".life-" + n.substring(7);
        else if (low.endsWith(".cobra-off")) target = n.substring(0, n.length() - 10) + ".life-off";
        else if (low.startsWith("cobra-sky-")) target = "life-sky-" + n.substring(10);
        else if (in.equals("config") && n.equals("cobra.properties")) target = "life.properties";
        else if (in.equals("config") && n.equals("cobra") && Files.isDirectory(p)) target = "life";
        else if (in.equals("capes") && (n.equals("Abyss.png") || n.equals("Cobra.png"))) target = "Life.png";
        if (target == null) return;
        Path to = p.resolveSibling(target);
        if (Files.exists(to)) {
            if (Files.isRegularFile(p) && !in.equals("config")) Files.deleteIfExists(p);     // the new one is already there
            return;
        }
        Files.move(p, to);
    }

    private static boolean has(String low) {
        for (String o : OLD) if (low.contains(o)) return true;
        return false;
    }

    /** Linux: the old app-menu entry and icons (the new ones are written by DesktopIntegration). */
    private static void desktop() {
        if (!"linux".equals(Paths.OS_NAME)) return;
        try {
            String xdg = System.getenv("XDG_DATA_HOME");
            Path data = xdg != null && !xdg.isBlank() ? Path.of(xdg) : Path.of(System.getProperty("user.home"), ".local", "share");
            Files.deleteIfExists(data.resolve("applications").resolve("cobra-launcher.desktop"));
            for (int s : new int[]{16, 32, 48, 64, 128, 256, 512}) {
                Files.deleteIfExists(data.resolve("icons").resolve("hicolor").resolve(s + "x" + s).resolve("apps").resolve("cobra-launcher.png"));
            }
        } catch (Exception ignored) {}
    }

    /** Saved settings that still use an old name (accent colour, home title). */
    static void settings(Settings s) {
        if ("Cobra green".equals(s.accent) || "Abyss green".equals(s.accent) || "Kit green".equals(s.accent)) s.accent = "Green";
        if ("ABYSS".equals(s.homeTitle) || "COBRA".equals(s.homeTitle)) s.homeTitle = "LIFE";
        if (s.launcherName != null && (s.launcherName.trim().equalsIgnoreCase("Abyss") || s.launcherName.trim().equalsIgnoreCase("Cobra")))
            s.launcherName = "";
    }
}

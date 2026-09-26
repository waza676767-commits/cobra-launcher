package dev.cobra.launcher.core;

import dev.cobra.launcher.game.GameVersion;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Import a profile from another launcher: its options.txt (keybinds, video settings…), mods,
 * resource packs, shader packs and mod configs become a new Cobra profile. Prism Launcher,
 * MultiMC, Modrinth App, CurseForge, ATLauncher and the official Minecraft launcher, on Linux
 * (incl. Flatpak) and Windows.
 */
public final class ProfileImport {
    /** A launcher found on this PC. */
    public record Source(String name, List<Found> profiles) {}

    /** One of its profiles / instances. */
    public record Found(String name, Path gameDir) {}

    private ProfileImport() {}

    public static List<Source> detect() {
        String home = System.getProperty("user.home");
        String appdata = System.getenv("APPDATA");
        String xdg = System.getenv("XDG_DATA_HOME");
        Path data = xdg != null && !xdg.isBlank() ? Path.of(xdg) : Path.of(home, ".local", "share");
        List<Source> out = new ArrayList<>();

        List<Path> prism = new ArrayList<>(List.of(data.resolve("PrismLauncher/instances"),
                Path.of(home, ".var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances")));
        if (appdata != null) prism.add(Path.of(appdata, "PrismLauncher", "instances"));
        add(out, "Prism Launcher", instances(prism));

        List<Path> multimc = new ArrayList<>(List.of(data.resolve("multimc/instances"), Path.of(home, "MultiMC/instances")));
        if (appdata != null) multimc.add(Path.of(appdata, "MultiMC", "instances"));
        add(out, "MultiMC", instances(multimc));

        List<Path> modrinth = new ArrayList<>(List.of(data.resolve("ModrinthApp/profiles"), data.resolve("com.modrinth.theseus/profiles")));
        if (appdata != null) {
            modrinth.add(Path.of(appdata, "ModrinthApp", "profiles"));
            modrinth.add(Path.of(appdata, "com.modrinth.theseus", "profiles"));
        }
        add(out, "Modrinth App", plainDirs(modrinth));

        add(out, "CurseForge", plainDirs(List.of(Path.of(home, "curseforge/minecraft/Instances"),
                Path.of(home, "Documents/curseforge/minecraft/Instances"))));

        add(out, "ATLauncher", plainDirs(List.of(data.resolve("ATLauncher/instances"), Path.of(home, "ATLauncher/instances"),
                appdata != null ? Path.of(appdata, "ATLauncher", "instances") : Path.of(home, ".atlauncher/instances"))));

        List<Found> vanilla = new ArrayList<>();
        Path mc = appdata != null ? Path.of(appdata, ".minecraft") : Path.of(home, ".minecraft");
        if (Files.isDirectory(mc)) vanilla.add(new Found("Minecraft (.minecraft)", mc));
        add(out, "Minecraft Launcher", vanilla);
        return out;
    }

    private static void add(List<Source> out, String name, List<Found> found) {
        if (!found.isEmpty()) out.add(new Source(name, found));
    }

    /** Prism / MultiMC: instance.cfg gives the name, the game is in .minecraft or minecraft. */
    private static List<Found> instances(List<Path> roots) {
        List<Found> out = new ArrayList<>();
        for (Path root : roots) {
            for (Path inst : children(root)) {
                Path game = Files.isDirectory(inst.resolve(".minecraft")) ? inst.resolve(".minecraft") : inst.resolve("minecraft");
                if (!Files.isDirectory(game)) continue;
                String name = inst.getFileName().toString();
                Path cfg = inst.resolve("instance.cfg");
                if (Files.exists(cfg)) {
                    try (var r = Files.newBufferedReader(cfg)) {
                        Properties p = new Properties();
                        p.load(r);
                        name = p.getProperty("name", name);
                    } catch (Exception ignored) {}
                }
                out.add(new Found(name, game));
            }
        }
        return out;
    }

    /** Modrinth / CurseForge / ATLauncher: each folder is the game folder. */
    private static List<Found> plainDirs(List<Path> roots) {
        List<Found> out = new ArrayList<>();
        for (Path root : roots) {
            for (Path d : children(root)) {
                if (Files.isDirectory(d.resolve("mods")) || Files.exists(d.resolve("options.txt"))) {
                    out.add(new Found(d.getFileName().toString(), d));
                }
            }
        }
        return out;
    }

    private static List<Path> children(Path root) {
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> s = Files.list(root)) {
            return s.filter(Files::isDirectory).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Makes a new Cobra profile from {@code f}. Copies options.txt, mods, resource packs, shader
     * packs, config and the server list. Returns the new profile.
     */
    public static Profiles.Profile importInto(Found f) throws IOException {
        Profiles.Profile p = Profiles.create(f.name(), GameVersion.MODERN);
        Path to = Profiles.gameDir(p, GameVersion.MODERN);
        Files.createDirectories(to);
        for (String file : new String[]{"options.txt", "servers.dat", "optionsshaders.txt"}) {
            Path a = f.gameDir().resolve(file);
            if (Files.isRegularFile(a)) Files.copy(a, to.resolve(file), StandardCopyOption.REPLACE_EXISTING);
        }
        for (String dir : new String[]{"mods", "resourcepacks", "shaderpacks", "config"}) {
            copyTree(f.gameDir().resolve(dir), to.resolve(dir), dir.equals("mods"));
        }
        return p;
    }

    private static void copyTree(Path from, Path to, boolean modsOnly) throws IOException {
        if (!Files.isDirectory(from)) return;
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes a) throws IOException {
                if (modsOnly && !dir.equals(from)) return FileVisitResult.SKIP_SUBTREE;   // skip mods/.index etc.
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes a) throws IOException {
                String n = file.getFileName().toString();
                if (modsOnly && !(n.endsWith(".jar"))) return FileVisitResult.CONTINUE;
                if (modsOnly && (n.startsWith("fabric-api") || n.startsWith("cobra-client"))) return FileVisitResult.CONTINUE;
                Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}

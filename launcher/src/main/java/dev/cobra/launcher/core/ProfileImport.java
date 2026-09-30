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
    public record Found(String name, Path gameDir, Path modsDir) {
        public Found(String name, Path gameDir) { this(name, gameDir, gameDir.resolve("mods")); }
    }

    private ProfileImport() {}

    public static List<Source> detect() {
        String home = System.getProperty("user.home");
        String appdata = System.getenv("APPDATA");   // Windows
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

        // Lunar Client: its own mods per profile/version in ~/.lunarclient/profiles, options in .minecraft
        Path mcDir = appdata != null ? Path.of(appdata, ".minecraft") : Path.of(home, ".minecraft");
        List<Found> lunar = new ArrayList<>();
        for (Path prof : children(Path.of(home, ".lunarclient", "profiles"))) {
            for (Path ver : children(prof)) {
                if (Files.isDirectory(ver.resolve("mods"))) {
                    Path game = Files.exists(ver.resolve("options.txt")) ? ver : mcDir;
                    lunar.add(new Found("Lunar " + prof.getFileName() + " " + ver.getFileName(), game, ver.resolve("mods")));
                }
            }
        }
        if (lunar.isEmpty() && Files.isDirectory(Path.of(home, ".lunarclient")) && Files.isDirectory(mcDir)) {
            lunar.add(new Found("Lunar Client (options)", mcDir, mcDir.resolve("mods")));
        }
        add(out, "Lunar Client", lunar);

        // Dawn Client: its folder holds one game folder per profile/version
        List<Found> dawn = new ArrayList<>();
        List<Path> dawnRoots = new ArrayList<>(List.of(Path.of(home, ".dawnclient"), Path.of(home, ".dawn"), data.resolve("DawnClient")));
        if (appdata != null) {
            dawnRoots.add(Path.of(appdata, ".dawnclient"));
            dawnRoots.add(Path.of(appdata, "DawnClient"));
        }
        for (Path r : dawnRoots) {
            if (Files.isDirectory(r.resolve("mods")) || Files.exists(r.resolve("options.txt"))) dawn.add(new Found("Dawn Client", r));
            for (Path d : children(r)) {
                if (Files.isDirectory(d.resolve("mods")) || Files.exists(d.resolve("options.txt"))) dawn.add(new Found("Dawn " + d.getFileName(), d));
                for (Path d2 : children(d)) {
                    if (Files.isDirectory(d2.resolve("mods")) || Files.exists(d2.resolve("options.txt"))) {
                        dawn.add(new Found("Dawn " + d.getFileName() + " " + d2.getFileName(), d2));
                    }
                }
            }
        }
        add(out, "Dawn Client", dawn);

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
        copyTree(f.modsDir(), to.resolve("mods"), true);
        for (String dir : new String[]{"resourcepacks", "shaderpacks", "config"}) {
            copyTree(f.gameDir().resolve(dir), to.resolve(dir), false);
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

    // ------------------------------------------------------------------ Modrinth modpacks

    /**
     * Imports a Modrinth modpack (.mrpack): a new profile with every mod, pack and config it lists
     * (downloaded from Modrinth and checked), plus its overrides. {@code progress} gets a status line.
     */
    public static Profiles.Profile importMrpack(Path mrpack, java.util.function.Consumer<String> progress) throws Exception {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(mrpack.toFile())) {
            var entry = zip.getEntry("modrinth.index.json");
            if (entry == null) throw new IOException("That isn't a Modrinth modpack (.mrpack).");
            com.google.gson.JsonObject index;
            try (var in = zip.getInputStream(entry)) {
                index = com.google.gson.JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            }
            String name = index.has("name") ? index.get("name").getAsString() : mrpack.getFileName().toString().replace(".mrpack", "");
            Profiles.Profile p = Profiles.create(name, GameVersion.MODERN);
            Path game = Profiles.gameDir(p, GameVersion.MODERN);
            Files.createDirectories(game);
            var files = index.getAsJsonArray("files");
            int n = 0, total = files == null ? 0 : files.size();
            if (files != null) {
                for (var el : files) {
                    var f = el.getAsJsonObject();
                    n++;
                    if (f.has("env") && f.getAsJsonObject("env").has("client")
                            && "unsupported".equals(f.getAsJsonObject("env").get("client").getAsString())) continue;
                    String rel = f.get("path").getAsString();
                    Path dest = game.resolve(rel).normalize();
                    if (!dest.startsWith(game)) continue;                       // never outside the profile
                    String url = f.getAsJsonArray("downloads").get(0).getAsString();
                    String sha1 = f.has("hashes") && f.getAsJsonObject("hashes").has("sha1") ? f.getAsJsonObject("hashes").get("sha1").getAsString() : null;
                    progress.accept("Downloading " + n + " / " + total + ": " + dest.getFileName());
                    Files.createDirectories(dest.getParent());
                    Http.download(url, dest, sha1);
                }
            }
            // overrides/ and client-overrides/ go straight into the game folder
            var en = zip.entries();
            while (en.hasMoreElements()) {
                var ze = en.nextElement();
                String nm = ze.getName();
                String rel = nm.startsWith("overrides/") ? nm.substring(10) : nm.startsWith("client-overrides/") ? nm.substring(17) : null;
                if (rel == null || rel.isEmpty() || ze.isDirectory()) continue;
                Path dest = game.resolve(rel).normalize();
                if (!dest.startsWith(game)) continue;
                Files.createDirectories(dest.getParent());
                try (var in = zip.getInputStream(ze)) {
                    Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return p;
        }
    }

    /**
     * A modpack from a Modrinth link (modrinth.com/modpack/NAME) or just its name: the newest Fabric
     * version for this Minecraft version is downloaded and imported.
     */
    public static Profiles.Profile importModrinthLink(String link, java.util.function.Consumer<String> progress) throws Exception {
        String slug = link.trim().replaceAll("[?#].*$", "");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("modrinth\\.com/(?:modpack|project)/([^/]+)").matcher(slug);
        if (m.find()) slug = m.group(1);
        if (!slug.matches("[A-Za-z0-9_.-]{2,64}")) throw new IOException("Paste a Modrinth modpack link, e.g. modrinth.com/modpack/fabulously-optimized");
        progress.accept("Looking up " + slug + " on Modrinth…");
        String q = "https://api.modrinth.com/v2/project/" + slug + "/version?loaders=" + java.net.URLEncoder.encode("[\"fabric\"]", "UTF-8")
                + "&game_versions=" + java.net.URLEncoder.encode("[\"" + GameVersion.MODERN.mc + "\"]", "UTF-8");
        Http.Response r = Http.get(q);
        if (r.code() == 404) throw new IOException("No modpack called " + slug + " on Modrinth.");
        if (!r.ok()) throw new IOException("Modrinth answered " + r.code());
        var versions = com.google.gson.JsonParser.parseString(r.body()).getAsJsonArray();
        if (versions.isEmpty()) throw new IOException(slug + " has no Fabric version for Minecraft " + GameVersion.MODERN.mc + ".");
        var files = versions.get(0).getAsJsonObject().getAsJsonArray("files");
        com.google.gson.JsonObject file = null;
        for (var f : files) {
            var fo = f.getAsJsonObject();
            if (fo.get("filename").getAsString().endsWith(".mrpack") && (file == null || fo.get("primary").getAsBoolean())) file = fo;
        }
        if (file == null) throw new IOException("That project has no .mrpack file.");
        Path tmp = Files.createTempFile("cobra-modpack", ".mrpack");
        try {
            progress.accept("Downloading the modpack…");
            Http.download(file.get("url").getAsString(), tmp, file.getAsJsonObject("hashes").get("sha1").getAsString());
            return importMrpack(tmp, progress);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}

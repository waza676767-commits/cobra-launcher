package dev.life.launcher.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.life.launcher.content.Modrinth;
import dev.life.launcher.core.Http;
import dev.life.launcher.core.Paths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class Installer {
    private static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String RUNTIMES = "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";
    private static final String FABRIC_META = "https://meta.fabricmc.net/v2/versions/loader/";
    private static final String FABRIC_API = "P7dR8mSH";
    private static final List<String> MIRRORS = List.of(
            "https://libraries.minecraft.net/", "https://maven.minecraftforge.net/",
            "https://repo1.maven.org/maven2/", "https://maven.fabricmc.net/");

    private Installer() {}

    public static final class Prepared {
        public JsonObject version;
        public final List<Path> classpath = new ArrayList<>();
        public Path nativesDir;
        public Path javaExe;
        public Path gameDir;
        public String assetIndex;
        public String logArgument;
        public boolean lifeBundled;
        /** Something to tell you after launching (e.g. VulkanMod isn't out for this version yet). */
        public String notice;
    }

    public static Prepared prepare(GameVersion gv, Downloader.Progress p) throws IOException {
        Prepared out = new Prepared();
        out.gameDir = dev.life.launcher.core.Profiles.gameDir(gv);

        p.update("Checking " + gv.mc, 0);
        JsonObject vanilla = vanillaJson(gv.mc);
        p.update("Preparing " + gv.loaderName, 0);
        JsonObject loader = switch (gv.loader) {
            case "fabric" -> fabricProfile(gv.mc);
            case "forge" -> resourceJson("/data/forge-1.8.9.json");
            default -> null;                                  // plain vanilla
        };
        JsonObject merged = loader == null ? vanilla : merge(vanilla, loader);
        out.version = merged;

        List<Downloader.Item> items = new ArrayList<>();
        List<Path> nativeJars = new ArrayList<>();

        JsonObject client = vanilla.getAsJsonObject("downloads").getAsJsonObject("client");
        Path clientJar = Paths.VERSIONS.resolve(gv.mc).resolve(gv.mc + ".jar");
        items.add(new Downloader.Item(client.get("url").getAsString(), clientJar, str(client, "sha1"), client.get("size").getAsLong()));

        collectLibraries(merged, items, out.classpath, nativeJars);
        out.classpath.add(clientJar);

        if (merged.has("logging") && merged.getAsJsonObject("logging").has("client")) {
            JsonObject lc = merged.getAsJsonObject("logging").getAsJsonObject("client");
            JsonObject file = lc.getAsJsonObject("file");
            Path cfg = Paths.ASSETS.resolve("log_configs").resolve(file.get("id").getAsString());
            items.add(new Downloader.Item(file.get("url").getAsString(), cfg, str(file, "sha1"), file.get("size").getAsLong()));
            out.logArgument = lc.get("argument").getAsString().replace("${path}", cfg.toAbsolutePath().toString());
        }

        Downloader.run("Downloading libraries", items, p);

        out.nativesDir = Paths.VERSIONS.resolve(merged.get("id").getAsString()).resolve("natives");
        Files.createDirectories(out.nativesDir);
        for (Path jar : nativeJars) extractNatives(jar, out.nativesDir);

        out.assetIndex = downloadAssets(vanilla, p);

        String component = vanilla.has("javaVersion") ? vanilla.getAsJsonObject("javaVersion").get("component").getAsString() : "jre-legacy";
        out.javaExe = ensureJava(component, p);

        p.update("Installing Life Client", 1);
        out.lifeBundled = installBundledMods(gv, out.gameDir);
        boolean fpsProfile = dev.life.launcher.core.Profiles.current().locked();
        boolean vulkan = dev.life.launcher.core.Settings.get().superOptimization;
        if (fpsProfile) installFpsMods(gv, out.gameDir, p);
        // Settings → Super optimization (Vulkan): in every profile. Its mods come and go with the
        // switch, and renderers that can't run with VulkanMod (Sodium, Iris…) are set aside meanwhile.
        Path modsDir = out.gameDir.resolve("mods");
        if (vulkan) {
            p.update("Setting up Super optimization (Vulkan)", 1);
            syncManagedMods(gv, modsDir, VULKAN_MODS, ".life-vulkan.json");
            if (!hasModId(modsDir, "vulkanmod")) {
                out.notice = "Super optimization: VulkanMod isn't available for Minecraft " + gv.mc + " yet, so the game runs normally.";
                restoreConflicting(modsDir);
            } else {
                setAsideConflicting(modsDir);
            }
        } else {
            removeManagedMods(modsDir, ".life-vulkan.json");
            restoreConflicting(modsDir);
        }
        // Settings → Game → Voice chat: Simple Voice Chat in every Fabric profile (talks on servers that have it)
        if (!gv.vanilla() && dev.life.launcher.core.Settings.get().voiceChat) {
            p.update("Adding Simple Voice Chat", 1);
            syncManagedMods(gv, modsDir, List.of("simple-voice-chat"), ".life-voicechat.json");
        } else {
            removeManagedMods(modsDir, ".life-voicechat.json");
        }
        removeDuplicateMods(modsDir, managedMods(gv));
        return out;
    }

    // ---------------------------------------------------------------- versions

    private static JsonObject vanillaJson(String id) throws IOException {
        Path file = Paths.VERSIONS.resolve(id).resolve(id + ".json");
        if (Files.exists(file)) return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        JsonObject manifest;
        Path cached = Paths.VERSIONS.resolve("version_manifest_v2.json");
        try {
            manifest = Http.getJson(MANIFEST).getAsJsonObject();
            Files.writeString(cached, manifest.toString());
        } catch (IOException e) {
            if (!Files.exists(cached)) throw new IOException("Can't reach Mojang to download " + id + ". Check your connection.");
            manifest = JsonParser.parseString(Files.readString(cached)).getAsJsonObject();
        }
        for (JsonElement e : manifest.getAsJsonArray("versions")) {
            JsonObject v = e.getAsJsonObject();
            if (v.get("id").getAsString().equals(id)) {
                Http.download(v.get("url").getAsString(), file, str(v, "sha1"));
                return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            }
        }
        throw new IOException("Version " + id + " not found in Mojang's manifest.");
    }

    private static JsonObject fabricProfile(String mc) throws IOException {
        Path file = Paths.VERSIONS.resolve("fabric-" + mc).resolve("profile.json");
        try {
            String loader = null;
            for (JsonElement e : Http.getJson(FABRIC_META + mc).getAsJsonArray()) {
                JsonObject l = e.getAsJsonObject().getAsJsonObject("loader");
                if (l.get("stable").getAsBoolean()) {
                    loader = l.get("version").getAsString();
                    break;
                }
            }
            if (loader == null) throw new IOException("No stable Fabric loader for " + mc);
            JsonObject profile = Http.getJson(FABRIC_META + mc + "/" + loader + "/profile/json").getAsJsonObject();
            Files.createDirectories(file.getParent());
            Files.writeString(file, profile.toString());
            return profile;
        } catch (IOException e) {
            if (Files.exists(file)) return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            throw e;
        }
    }

    private static JsonObject resourceJson(String path) throws IOException {
        try (InputStream in = Installer.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Missing resource " + path);
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /** Child (loader) profile on top of the vanilla parent, like the official launcher's inheritsFrom. */
    static JsonObject merge(JsonObject parent, JsonObject child) {
        JsonObject m = parent.deepCopy();
        m.add("id", child.get("id"));
        if (child.has("mainClass")) m.add("mainClass", child.get("mainClass"));
        if (child.has("minecraftArguments")) m.add("minecraftArguments", child.get("minecraftArguments"));
        if (child.has("arguments")) {
            JsonObject pa = m.has("arguments") ? m.getAsJsonObject("arguments") : new JsonObject();
            JsonObject ca = child.getAsJsonObject("arguments");
            for (String k : new String[]{"game", "jvm"}) {
                if (!ca.has(k)) continue;
                JsonArray arr = pa.has(k) ? pa.getAsJsonArray(k) : new JsonArray();
                arr.addAll(ca.getAsJsonArray(k));
                pa.add(k, arr);
            }
            m.add("arguments", pa);
        }
        JsonArray libs = new JsonArray();
        if (child.has("libraries")) libs.addAll(child.getAsJsonArray("libraries"));
        libs.addAll(parent.getAsJsonArray("libraries"));
        m.add("libraries", libs);
        return m;
    }

    // --------------------------------------------------------------- libraries

    private static void collectLibraries(JsonObject v, List<Downloader.Item> items, List<Path> cp, List<Path> natives) {
        Set<String> seen = new HashSet<>();
        for (JsonElement e : v.getAsJsonArray("libraries")) {
            JsonObject lib = e.getAsJsonObject();
            if (!Rules.allowed(lib.getAsJsonArray("rules"), Map.of())) continue;
            String name = lib.get("name").getAsString();
            String[] parts = name.split(":");
            String key = parts[0] + ":" + parts[1] + (parts.length > 3 ? ":" + parts[3] : "");
            JsonObject downloads = lib.getAsJsonObject("downloads");

            if (lib.has("natives")) {
                JsonObject nat = lib.getAsJsonObject("natives");
                if (nat.has(Paths.OS_NAME) && downloads != null && downloads.has("classifiers")) {
                    String cls = nat.get(Paths.OS_NAME).getAsString().replace("${arch}", "64");
                    JsonObject art = downloads.getAsJsonObject("classifiers").getAsJsonObject(cls);
                    if (art != null && seen.add(key + ":" + cls)) {
                        Path path = Paths.LIBRARIES.resolve(art.get("path").getAsString());
                        items.add(new Downloader.Item(art.get("url").getAsString(), path, str(art, "sha1"), art.get("size").getAsLong()));
                        natives.add(path);
                    }
                }
                if (downloads == null || !downloads.has("artifact")) continue;
            }
            if (!seen.add(key)) continue;

            Path path;
            if (downloads != null && downloads.has("artifact")) {
                JsonObject art = downloads.getAsJsonObject("artifact");
                path = Paths.LIBRARIES.resolve(art.get("path").getAsString());
                String url = str(art, "url");
                if (!url.isEmpty()) {
                    items.add(new Downloader.Item(List.of(url), path, str(art, "sha1"), art.has("size") ? art.get("size").getAsLong() : -1));
                }
            } else {
                String rel = mavenPath(name);
                path = Paths.LIBRARIES.resolve(rel);
                List<String> urls = new ArrayList<>();
                if (lib.has("url")) urls.add(slash(lib.get("url").getAsString()) + rel);
                for (String m : MIRRORS) if (!urls.contains(m + rel)) urls.add(m + rel);
                items.add(new Downloader.Item(urls, path, str(lib, "sha1"), lib.has("size") ? lib.get("size").getAsLong() : -1));
            }
            cp.add(path);
        }
    }

    static String mavenPath(String coords) {
        String[] p = coords.split(":");
        String ext = "jar";
        String version = p[2];
        String classifier = p.length > 3 ? p[3] : null;
        if (classifier != null && classifier.contains("@")) {
            ext = classifier.substring(classifier.indexOf('@') + 1);
            classifier = classifier.substring(0, classifier.indexOf('@'));
        } else if (version.contains("@")) {
            ext = version.substring(version.indexOf('@') + 1);
            version = version.substring(0, version.indexOf('@'));
        }
        return p[0].replace('.', '/') + "/" + p[1] + "/" + version + "/" + p[1] + "-" + version
                + (classifier != null ? "-" + classifier : "") + "." + ext;
    }

    private static void extractNatives(Path jar, Path dir) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                if (ze.isDirectory() || ze.getName().startsWith("META-INF/")) continue;
                Path out = dir.resolve(ze.getName()).normalize();
                if (!out.startsWith(dir)) continue;
                Files.createDirectories(out.getParent());
                try (InputStream in = zip.getInputStream(ze)) {
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    // ------------------------------------------------------------------ assets

    private static String downloadAssets(JsonObject vanilla, Downloader.Progress p) throws IOException {
        JsonObject ai = vanilla.getAsJsonObject("assetIndex");
        String id = ai.get("id").getAsString();
        Path index = Paths.ASSETS.resolve("indexes").resolve(id + ".json");
        if (!Files.exists(index) || !str(ai, "sha1").equalsIgnoreCase(Http.sha1(index))) {
            Http.download(ai.get("url").getAsString(), index, str(ai, "sha1"));
        }
        JsonObject objects = JsonParser.parseString(Files.readString(index)).getAsJsonObject().getAsJsonObject("objects");
        List<Downloader.Item> items = new ArrayList<>();
        for (Map.Entry<String, JsonElement> en : objects.entrySet()) {
            JsonObject o = en.getValue().getAsJsonObject();
            String hash = o.get("hash").getAsString();
            String sub = hash.substring(0, 2) + "/" + hash;
            items.add(new Downloader.Item("https://resources.download.minecraft.net/" + sub,
                    Paths.ASSETS.resolve("objects").resolve(sub), hash, o.get("size").getAsLong()));
        }
        Downloader.run("Downloading assets", items, p);
        return id;
    }

    // ------------------------------------------------------------------- java

    private static Path ensureJava(String component, Downloader.Progress p) throws IOException {
        Path dir = Paths.RUNTIMES.resolve(component);
        boolean win = Paths.OS_NAME.equals("windows");
        Path javaExe = dir.resolve("bin").resolve(win ? "javaw.exe" : "java");
        Path marker = dir.resolve(".life-ok");
        if (Files.exists(marker) && Files.exists(javaExe)) return javaExe;

        p.update("Downloading Java runtime", 0);
        String platform = switch (Paths.OS_NAME) {
            case "windows" -> Paths.ARM ? "windows-arm64" : "windows-x64";
            case "osx" -> Paths.ARM ? "mac-os-arm64" : "mac-os";
            default -> "linux";
        };
        JsonObject all = Http.getJson(RUNTIMES).getAsJsonObject();
        JsonArray variants = all.getAsJsonObject(platform).getAsJsonArray(component);
        if (variants == null || variants.isEmpty()) throw new IOException("Mojang has no " + component + " Java runtime for " + platform + ".");
        String manifestUrl = variants.get(0).getAsJsonObject().getAsJsonObject("manifest").get("url").getAsString();
        JsonObject files = Http.getJson(manifestUrl).getAsJsonObject().getAsJsonObject("files");

        List<Downloader.Item> items = new ArrayList<>();
        List<Path> executables = new ArrayList<>();
        List<Map.Entry<Path, String>> links = new ArrayList<>();
        for (Map.Entry<String, JsonElement> en : files.entrySet()) {
            JsonObject f = en.getValue().getAsJsonObject();
            Path target = dir.resolve(en.getKey()).normalize();
            if (!target.startsWith(dir)) continue;
            switch (f.get("type").getAsString()) {
                case "directory" -> Files.createDirectories(target);
                case "link" -> links.add(Map.entry(target, f.get("target").getAsString()));
                case "file" -> {
                    JsonObject raw = f.getAsJsonObject("downloads").getAsJsonObject("raw");
                    items.add(new Downloader.Item(raw.get("url").getAsString(), target, str(raw, "sha1"), raw.get("size").getAsLong()));
                    if (f.has("executable") && f.get("executable").getAsBoolean()) executables.add(target);
                }
                default -> {}
            }
        }
        Downloader.run("Downloading Java", items, p);
        for (Path x : executables) x.toFile().setExecutable(true, false);
        for (Map.Entry<Path, String> l : links) {
            if (Files.exists(l.getKey(), LinkOption.NOFOLLOW_LINKS)) continue;
            try {
                Files.createDirectories(l.getKey().getParent());
                Files.createSymbolicLink(l.getKey(), Path.of(l.getValue()));
            } catch (Exception ignored) {}
        }
        if (!Files.exists(javaExe)) throw new IOException("Java runtime download is incomplete.");
        Files.writeString(marker, manifestUrl);
        return javaExe;
    }

    // ------------------------------------------------------------------- mods

    /**
     * Folder the launcher keeps Life Client and Fabric API in, outside the profile's mods folder.
     * Fabric loads it through {@code -Dfabric.addMods}, so these two are always there: they can't
     * be deleted, moved or disabled from the mods folder, and an old copy can't shadow the new one.
     */
    public static Path managedMods(GameVersion gv) {
        return dev.life.launcher.core.Paths.ROOT.resolve("client").resolve(gv.id);
    }

    /**
     * Puts the bundled Life Client jar into the launcher's client folder (every profile uses it).
     * Also called at start-up, so the Mods page shows it before the first launch.
     */
    public static synchronized boolean ensureClient(GameVersion gv) throws IOException {
        Path managed = managedMods(gv);
        Files.createDirectories(managed);
        boolean bundled = false;
        Path client = managed.resolve("life-client.jar");
        try (InputStream in = Installer.class.getResourceAsStream("/bundled/" + gv.bundledJar())) {
            if (in != null) {
                byte[] jar = in.readAllBytes();
                // rewrite only when it changed (new launcher version), so it's never half-written while running
                if (!Files.exists(client) || Files.size(client) != jar.length || !java.util.Arrays.equals(Files.readAllBytes(client), jar)) {
                    Path tmp = managed.resolve("life-client.jar.part");
                    Files.write(tmp, jar);
                    try {
                        Files.move(tmp, client, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                        Files.move(tmp, client, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                bundled = true;
            } else {
                Files.deleteIfExists(client);
            }
        }
        return bundled;
    }

    private static boolean installBundledMods(GameVersion gv, Path gameDir) throws IOException {
        if (gv.vanilla()) return false;
        Path mods = gameDir.resolve("mods");
        Path managed = managedMods(gv);
        Files.createDirectories(mods);
        Files.createDirectories(managed);
        // older launcher builds put these into the mods folder: take them out (the managed copies win)
        try (var s = Files.list(mods)) {
            for (Path f : s.toList()) {
                String fn = f.getFileName().toString();
                if (fn.startsWith("life-client") || fn.startsWith("fabric-api")) Files.deleteIfExists(f);
            }
        }
        boolean bundled = ensureClient(gv);
        if (gv.loader.equals("fabric")) {
            boolean hasApi;
            try (var s = Files.list(managed)) {
                hasApi = s.anyMatch(f -> f.getFileName().toString().startsWith("fabric-api") && f.getFileName().toString().endsWith(".jar"));
            }
            if (!hasApi) {
                try {
                    Modrinth.installLatest(FABRIC_API, gv, managed, false);
                } catch (IOException e) {
                    throw new IOException("Couldn't download Fabric API: " + e.getMessage());
                }
            }
            // Life's Sky module needs Skyboxify (custom skies from packs) and its config library;
            // they live with Life in the launcher's folder and aren't listed as your mods
            for (String slug : bundled ? List.of("skyboxify", "yacl") : List.<String>of()) {   // only Life's Sky needs these
                String prefix = slug.equals("yacl") ? "yet_another_config_lib" : slug;
                boolean have;
                try (var s = Files.list(managed)) {
                    have = s.anyMatch(f -> f.getFileName().toString().toLowerCase().startsWith(prefix) && f.getFileName().toString().endsWith(".jar"));
                }
                if (!have) {
                    try {
                        Modrinth.installLatest(slug, gv, managed, false);
                    } catch (IOException ignored) {
                        // offline: the Sky module just has no custom skies this time
                    }
                }
            }
        }
        if (bundled) installSkies(gameDir);
        return bundled;
    }

    /**
     * Sky packs for the Sky module: every .zip in the launcher's "skies" folder (plus any bundled
     * in the launcher) is copied into the profile's resourcepacks as life-sky-<name>.zip, which
     * the in-game Sky module lists and switches between.
     */
    private static void installSkies(Path gameDir) {
        try {
            Path packs = gameDir.resolve("resourcepacks");
            Files.createDirectories(packs);
            Path own = dev.life.launcher.core.Paths.ROOT.resolve("skies");
            Files.createDirectories(own);
            java.util.Set<String> want = new java.util.HashSet<>();
            try (var s = Files.list(own)) {
                for (Path z : s.toList()) {
                    String fn = z.getFileName().toString();
                    if (!fn.toLowerCase().endsWith(".zip")) continue;
                    String name = "life-sky-" + fn.replaceAll("[^A-Za-z0-9._-]", "_");
                    want.add(name);
                    Path dst = packs.resolve(name);
                    if (!Files.exists(dst) || Files.size(dst) != Files.size(z)) Files.copy(z, dst, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            try (InputStream idx = Installer.class.getResourceAsStream("/bundled/skies/index.txt")) {
                if (idx != null) {
                    for (String fn : new String(idx.readAllBytes()).split("\\R")) {
                        if (fn.isBlank()) continue;
                        String name = "life-sky-" + fn.trim().replaceAll("[^A-Za-z0-9._-]", "_");
                        want.add(name);
                        Path dst = packs.resolve(name);
                        if (Files.exists(dst)) continue;
                        try (InputStream in = Installer.class.getResourceAsStream("/bundled/skies/" + fn.trim())) {
                            if (in != null) Files.copy(in, dst);
                        }
                    }
                }
            }
            try (var s = Files.list(packs)) {                 // removed from the skies folder: remove here too
                for (Path f : s.toList()) {
                    String fn = f.getFileName().toString();
                    if (fn.startsWith("life-sky-") && !want.contains(fn)) Files.deleteIfExists(f);
                }
            }
        } catch (IOException ignored) {}
    }

    /**
     * FPS Boost profile: its mods folder holds exactly the FPS mods (plus their dependencies).
     * Anything else is taken out, missing ones are downloaded again, so it can't be broken by
     * adding or deleting files by hand.
     */
    private static void installFpsMods(GameVersion gv, Path gameDir, Downloader.Progress p) throws IOException {
        Path mods = gameDir.resolve("mods");
        Files.createDirectories(mods);
        Path lock = mods.resolve(".life-fps.json");
        java.util.Map<String, String> owned = new java.util.HashMap<>();   // slug -> file name
        try {
            if (Files.exists(lock)) {
                com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(Files.readString(lock)).getAsJsonObject();
                for (String k : o.keySet()) owned.put(k, o.get(k).getAsString());
            }
        } catch (Exception ignored) {}
        java.util.Set<String> keep = new java.util.HashSet<>();
        boolean vulkan = dev.life.launcher.core.Settings.get().superOptimization;
        for (String slug : dev.life.launcher.core.Profiles.FPS_MODS) {
            // with Super optimization the Sodium family is set aside anyway: don't download it again
            if (vulkan && (slug.startsWith("sodium") || slug.startsWith("reeses"))) {
                String had = owned.get(slug);
                if (had != null) keep.add(had);
                continue;
            }
            String have = owned.get(slug);
            if (have != null && Files.exists(mods.resolve(have))) {
                keep.add(have);
                continue;
            }
            try {
                java.util.Set<String> before = listJars(mods);
                String file = Modrinth.installLatest(slug, gv, mods, true);   // with required dependencies
                if (file != null) {
                    owned.put(slug, file);
                    keep.add(file);
                }
                java.util.Set<String> added = listJars(mods);
                added.removeAll(before);
                keep.addAll(added);   // dependencies it pulled in
            } catch (IOException e) {
                // not out for this version yet (or offline): skip, try again next launch
            }
        }
        // dependencies installed earlier: keep every jar recorded as ours
        for (String f : owned.values()) keep.add(f);
        try (var s = Files.list(mods)) {
            for (Path f : s.toList()) {
                String fn = f.getFileName().toString();
                if (fn.startsWith(".")) continue;
                // Fabric API comes from the launcher's own folder; a second copy here would clash
                if (!Files.isRegularFile(f)) continue;
                if (fn.startsWith("fabric-api") || !keep.contains(fn) && !keepDependency(fn, owned)) Files.deleteIfExists(f);
            }
        }
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        for (java.util.Map.Entry<String, String> e : owned.entrySet()) o.addProperty(e.getKey(), e.getValue());
        for (String f : keep) if (!owned.containsValue(f)) o.addProperty("dep:" + f, f);
        Files.writeString(lock, o.toString());
    }

    /** Super optimization (Vulkan): VulkanMod and the mods made to go with it. */
    public static final List<String> VULKAN_MODS = List.of(
            "vulkanmod", "not-enough-vulkan", "entityculling", "ferrite-core", "moreculling", "cloth-config", "clumps");

    /**
     * Makes sure the given Modrinth mods (with their dependencies) are in the mods folder and
     * remembers which files are ours in {@code lockName}, so turning the option off removes
     * exactly those (and never your own mods).
     */
    private static void syncManagedMods(GameVersion gv, Path mods, List<String> slugs, String lockName) throws IOException {
        Files.createDirectories(mods);
        Path lock = mods.resolve(lockName);
        java.util.Map<String, String> owned = readLock(lock);
        for (String slug : slugs) {
            String have = owned.get(slug);
            if (have != null && Files.exists(mods.resolve(have))) continue;
            try {
                java.util.Set<String> before = listJars(mods);
                String file = Modrinth.installLatest(slug, gv, mods, true);
                if (file != null) owned.put(slug, file);
                java.util.Set<String> added = listJars(mods);
                added.removeAll(before);
                for (String f : added) if (!owned.containsValue(f)) owned.put("dep:" + f, f);
            } catch (IOException e) {
                // not out for this version yet, or offline: try again next launch
            }
        }
        for (String f : new java.util.ArrayList<>(owned.values())) {   // Fabric API lives in the launcher's own folder
            if (f.startsWith("fabric-api")) Files.deleteIfExists(mods.resolve(f));
        }
        writeLock(lock, owned);
    }

    /** Takes out every file a managed set added (the switch was turned off). */
    private static void removeManagedMods(Path mods, String lockName) throws IOException {
        Path lock = mods.resolve(lockName);
        if (!Files.exists(lock)) return;
        for (String f : readLock(lock).values()) Files.deleteIfExists(mods.resolve(f));
        Files.deleteIfExists(lock);
    }

    private static java.util.Map<String, String> readLock(Path lock) {
        java.util.Map<String, String> owned = new java.util.HashMap<>();
        try {
            if (Files.exists(lock)) {
                com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(Files.readString(lock)).getAsJsonObject();
                for (String k : o.keySet()) owned.put(k, o.get(k).getAsString());
            }
        } catch (Exception ignored) {}
        return owned;
    }

    private static void writeLock(Path lock, java.util.Map<String, String> owned) throws IOException {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        for (java.util.Map.Entry<String, String> e : owned.entrySet()) o.addProperty(e.getKey(), e.getValue());
        Files.writeString(lock, o.toString());
    }

    /**
     * Two copies of the same mod (same Fabric mod id) crash the game. Keeps one: the launcher's own
     * copy (Life Client, Fabric API) always wins; otherwise the newest version, then the newest file.
     */
    private static void removeDuplicateMods(Path mods, Path managed) {
        java.util.Map<String, Path> best = new java.util.HashMap<>();
        java.util.Map<String, String> bestVersion = new java.util.HashMap<>();
        java.util.Set<String> managedIds = new java.util.HashSet<>();
        for (Path dir : new Path[]{managed, mods}) {
            if (!Files.isDirectory(dir)) continue;
            try (var s = Files.list(dir)) {
                for (Path f : s.toList()) {
                    if (!f.getFileName().toString().endsWith(".jar")) continue;
                    String[] idv = modIdAndVersion(f);
                    if (idv == null) continue;
                    String id = idv[0];
                    if (dir == managed) {
                        managedIds.add(id);
                        continue;
                    }
                    if (managedIds.contains(id)) {                      // e.g. a second Fabric API
                        Files.deleteIfExists(f);
                        continue;
                    }
                    Path cur = best.get(id);
                    if (cur == null) {
                        best.put(id, f);
                        bestVersion.put(id, idv[1]);
                        continue;
                    }
                    int cmp = compareVersions(idv[1], bestVersion.get(id));
                    boolean newer = cmp > 0 || cmp == 0 && Files.getLastModifiedTime(f).compareTo(Files.getLastModifiedTime(cur)) > 0;
                    Path loser = newer ? cur : f;
                    if (newer) {
                        best.put(id, f);
                        bestVersion.put(id, idv[1]);
                    }
                    Files.deleteIfExists(loser);
                }
            } catch (IOException ignored) {}
        }
    }

    /** Mods that can't run together with VulkanMod (their own renderers / shaders). */
    private static final java.util.Set<String> VULKAN_CONFLICTS = java.util.Set.of(
            "sodium", "sodium-extra", "reeses-sodium-options", "iris", "indium", "embeddium", "rubidium",
            "optifabric", "nvidium", "distanthorizons", "continuity", "canvas", "immersive_portals", "replaymod");
    private static final String OFF = ".life-off";

    private static boolean hasModId(Path mods, String id) {
        try (var s = Files.list(mods)) {
            for (Path f : s.toList()) {
                if (!f.getFileName().toString().endsWith(".jar")) continue;
                String[] iv = modIdAndVersion(f);
                if (iv != null && iv[0].equals(id)) return true;
            }
        } catch (IOException ignored) {}
        return false;
    }

    /** Super optimization on: renames conflicting mods to *.jar.life-off so the game skips them. */
    private static void setAsideConflicting(Path mods) {
        try (var s = Files.list(mods)) {
            for (Path f : s.toList()) {
                if (!f.getFileName().toString().endsWith(".jar")) continue;
                String[] iv = modIdAndVersion(f);
                if (iv != null && VULKAN_CONFLICTS.contains(iv[0])) {
                    Files.move(f, f.resolveSibling(f.getFileName() + OFF), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException ignored) {}
    }

    /** Super optimization off: puts those mods back. */
    private static void restoreConflicting(Path mods) {
        if (!Files.isDirectory(mods)) return;
        try (var s = Files.list(mods)) {
            for (Path f : s.toList()) {
                String n = f.getFileName().toString();
                if (n.endsWith(".jar" + OFF)) Files.move(f, f.resolveSibling(n.substring(0, n.length() - OFF.length())), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {}
    }

    /** {id, version} from a Fabric mod jar, or null if it isn't one. */
    private static String[] modIdAndVersion(Path jar) {
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(jar.toFile())) {
            java.util.zip.ZipEntry e = z.getEntry("fabric.mod.json");
            if (e == null) return null;
            try (InputStream in = z.getInputStream(e)) {
                com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                return new String[]{o.get("id").getAsString(), o.has("version") ? o.get("version").getAsString() : "0"};
            }
        } catch (Exception ex) {
            return null;
        }
    }

    /** Compares versions number by number (1.10.2 > 1.9); text parts are ignored. */
    static int compareVersions(String a, String b) {
        String[] x = a.split("[^0-9]+"), y = b.split("[^0-9]+");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            long p = i < x.length && !x[i].isEmpty() ? Long.parseLong(x[i].length() > 15 ? x[i].substring(0, 15) : x[i]) : 0;
            long q = i < y.length && !y[i].isEmpty() ? Long.parseLong(y[i].length() > 15 ? y[i].substring(0, 15) : y[i]) : 0;
            if (p != q) return Long.compare(p, q);
        }
        return 0;
    }

    private static boolean keepDependency(String file, java.util.Map<String, String> owned) {
        return owned.containsKey("dep:" + file);
    }

    private static java.util.Set<String> listJars(Path dir) throws IOException {
        try (var s = Files.list(dir)) {
            java.util.Set<String> out = new java.util.HashSet<>();
            for (Path f : s.toList()) if (f.getFileName().toString().endsWith(".jar")) out.add(f.getFileName().toString());
            return out;
        }
    }

    // ------------------------------------------------------------------- util

    static String str(JsonObject o, String k) {
        return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static String slash(String s) {
        return s.endsWith("/") ? s : s + "/";
    }
}

package dev.cobra.launcher.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.cobra.launcher.core.Paths;
import dev.cobra.launcher.game.GameVersion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class ContentManager {
    private ContentManager() {}

    public record Entry(Path path, String name, boolean enabled, long size, String lockedReason) {
        public boolean locked() { return lockedReason != null; }
    }

    public static Path dir(GameVersion gv, Modrinth.Kind kind) {
        return dev.cobra.launcher.core.Profiles.gameDir(gv).resolve(kind.folder);
    }

    public static List<Entry> list(GameVersion gv, Modrinth.Kind kind) {
        Path dir = dir(gv, kind);
        List<Entry> out = new ArrayList<>();
        List<String> active = kind == Modrinth.Kind.PACKS ? enabledPacks(gv) : List.of();
        if (kind == Modrinth.Kind.MODS) {   // the launcher-managed ones first; they live outside the mods folder
            try (Stream<Path> s = Files.list(dev.cobra.launcher.game.Installer.managedMods(gv))) {
                for (Path p : s.sorted().toList()) {
                    String fn = p.getFileName().toString();
                    if (!fn.endsWith(".jar")) continue;
                    if (!fn.startsWith("cobra-client") && !fn.startsWith("fabric-api")) continue;   // Sky helpers stay hidden
                    boolean cobra = fn.startsWith("cobra-client");
                    out.add(new Entry(p, cobra ? "Cobra Client" : fn.replaceAll("\\.jar$", ""), true, size(p), cobra ? "Built in" : "Required"));
                }
            } catch (IOException ignored) {}
        }
        try (Stream<Path> s = Files.list(dir)) {
            for (Path p : s.sorted(Comparator.comparing(x -> x.getFileName().toString().toLowerCase())).toList()) {
                String fn = p.getFileName().toString();
                if (fn.startsWith(".") || fn.endsWith(".part")) continue;
                if (kind == Modrinth.Kind.MODS) {
                    boolean on = fn.endsWith(".jar");
                    if (!on && !fn.endsWith(".jar.disabled")) continue;
                    String lock = fn.equals("cobra-client.jar") ? "Built in" : fn.startsWith("fabric-api") ? "Required" : null;
                    if (lock == null && dev.cobra.launcher.core.Profiles.current().locked()) lock = "FPS Boost";
                    String name = fn.replaceAll("\\.jar(\\.disabled)?$", "");
                    if (fn.equals("cobra-client.jar")) name = "Cobra Client";
                    out.add(new Entry(p, name, on, size(p), lock));
                } else {
                    if (!Files.isDirectory(p) && !fn.endsWith(".zip")) continue;
                    out.add(new Entry(p, fn.replaceAll("\\.zip$", ""), active.contains(packId(gv, fn)), size(p), null));
                }
            }
        } catch (IOException ignored) {}
        return out;
    }

    public static void setEnabled(GameVersion gv, Modrinth.Kind kind, Entry e, boolean on) throws IOException {
        if (e.locked()) return;
        if (kind == Modrinth.Kind.MODS) {
            String fn = e.path().getFileName().toString();
            String target = on ? fn.replaceAll("\\.disabled$", "") : (fn.endsWith(".disabled") ? fn : fn + ".disabled");
            if (!target.equals(fn)) Files.move(e.path(), e.path().resolveSibling(target), StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        String id = packId(gv, e.path().getFileName().toString());
        List<String> packs = new ArrayList<>(enabledPacks(gv));
        packs.remove(id);
        if (on) {
            if (gv.modernPacks()) packs.add(id);       // last = highest priority
            else packs.add(0, id);                             // 1.8.9: first = highest priority
        }
        writePacks(gv, packs, on ? id : null);
    }

    public static void delete(GameVersion gv, Modrinth.Kind kind, Entry e) throws IOException {
        if (e.locked()) return;
        if (kind == Modrinth.Kind.PACKS && e.enabled()) setEnabled(gv, kind, e, false);
        if (Files.isDirectory(e.path())) {
            try (Stream<Path> walk = Files.walk(e.path())) {
                for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            }
        } else {
            Files.deleteIfExists(e.path());
        }
    }

    public static int importFiles(GameVersion gv, Modrinth.Kind kind, List<Path> files) throws IOException {
        Path dir = dir(gv, kind);
        int n = 0;
        for (Path f : files) {
            String fn = f.getFileName().toString();
            boolean ok = kind == Modrinth.Kind.MODS ? fn.endsWith(".jar") : fn.endsWith(".zip");
            if (!ok || Files.isDirectory(f)) continue;
            Files.copy(f, dir.resolve(fn), StandardCopyOption.REPLACE_EXISTING);
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------ options.txt

    private static String packId(GameVersion gv, String fileName) {
        return gv.modernPacks() ? "file/" + fileName : fileName;
    }

    private static Path options(GameVersion gv) {
        return dev.cobra.launcher.core.Profiles.gameDir(gv).resolve("options.txt");
    }

    public static List<String> enabledPacks(GameVersion gv) {
        List<String> out = new ArrayList<>();
        JsonArray a = readArray(gv, "resourcePacks");
        if (a != null) for (JsonElement e : a) out.add(e.getAsString());
        return out;
    }

    private static JsonArray readArray(GameVersion gv, String key) {
        try {
            Path f = options(gv);
            if (!Files.exists(f)) return null;
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                if (line.startsWith(key + ":")) return JsonParser.parseString(line.substring(key.length() + 1)).getAsJsonArray();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static void writePacks(GameVersion gv, List<String> packs, String added) throws IOException {
        JsonArray arr = new JsonArray();
        if (gv.modernPacks() && !packs.contains("vanilla")) arr.add("vanilla");
        for (String p : packs) arr.add(p);
        List<String[]> updates = new ArrayList<>();
        updates.add(new String[]{"resourcePacks", arr.toString()});
        if (gv.modernPacks()) {
            // Accept older pack formats so Minecraft doesn't silently drop them.
            JsonArray inc = readArray(gv, "incompatibleResourcePacks");
            if (inc == null) inc = new JsonArray();
            JsonArray keep = new JsonArray();
            for (JsonElement e : inc) if (packs.contains(e.getAsString())) keep.add(e);
            if (added != null && !keep.contains(new com.google.gson.JsonPrimitive(added))) keep.add(added);
            updates.add(new String[]{"incompatibleResourcePacks", keep.toString()});
        }
        Path f = options(gv);
        List<String> lines = Files.exists(f) ? new ArrayList<>(Files.readAllLines(f, StandardCharsets.UTF_8)) : new ArrayList<>();
        for (String[] u : updates) {
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith(u[0] + ":")) {
                    lines.set(i, u[0] + ":" + u[1]);
                    found = true;
                }
            }
            if (!found) lines.add(u[0] + ":" + u[1]);
        }
        Files.write(f, lines, StandardCharsets.UTF_8);
    }

    private static long size(Path p) {
        try {
            if (!Files.isDirectory(p)) return Files.size(p);
            try (Stream<Path> w = Files.walk(p)) {
                return w.filter(Files::isRegularFile).mapToLong(x -> x.toFile().length()).sum();
            }
        } catch (IOException e) {
            return 0;
        }
    }

    public static String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.0f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}

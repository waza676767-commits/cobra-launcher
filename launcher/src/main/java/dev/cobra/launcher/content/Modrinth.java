package dev.cobra.launcher.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.cobra.launcher.core.Http;
import dev.cobra.launcher.game.GameVersion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Modrinth {
    private static final String API = "https://api.modrinth.com/v2";

    private Modrinth() {}

    public enum Kind {
        MODS("mod", "mods"), PACKS("resourcepack", "resourcepacks");
        public final String projectType, folder;

        Kind(String projectType, String folder) {
            this.projectType = projectType;
            this.folder = folder;
        }
    }

    public record Project(String id, String title, String description, String author, long downloads, String iconUrl) {}

    public static List<Project> search(String query, Kind kind, GameVersion gv) throws IOException {
        return search(query, kind, gv, 0);
    }

    /** One page (30) of results starting at {@code offset}; empty when there are no more. */
    public static List<Project> search(String query, Kind kind, GameVersion gv, int offset) throws IOException {
        JsonArray facets = new JsonArray();
        facets.add(single("project_type:" + kind.projectType));
        facets.add(single("versions:" + gv.mc));
        if (kind == Kind.MODS) facets.add(single("categories:" + gv.loader));
        String q = query == null ? "" : query.trim();
        String url = API + "/search?limit=30&offset=" + Math.max(0, offset) + "&index=" + (q.isEmpty() ? "downloads" : "relevance")
                + "&query=" + Http.enc(q) + "&facets=" + Http.enc(facets.toString());
        List<Project> out = new ArrayList<>();
        for (JsonElement e : Http.getJson(url).getAsJsonObject().getAsJsonArray("hits")) {
            JsonObject h = e.getAsJsonObject();
            out.add(new Project(s(h, "project_id"), s(h, "title"), s(h, "description"), s(h, "author"),
                    h.has("downloads") ? h.get("downloads").getAsLong() : 0, s(h, "icon_url")));
        }
        return out;
    }

    public static String installLatest(String projectId, GameVersion gv, Path dir, boolean withDeps) throws IOException {
        return installLatest(projectId, gv, dir, Kind.MODS, withDeps, new HashSet<>());
    }

    public static String installLatest(String projectId, GameVersion gv, Path dir, Kind kind, boolean withDeps) throws IOException {
        return installLatest(projectId, gv, dir, kind, withDeps, new HashSet<>());
    }

    private static String installLatest(String projectId, GameVersion gv, Path dir, Kind kind, boolean withDeps, Set<String> visited) throws IOException {
        if (!visited.add(projectId)) return null;
        String url = API + "/project/" + Http.enc(projectId) + "/version?game_versions=" + Http.enc("[\"" + gv.mc + "\"]");
        if (kind == Kind.MODS) url += "&loaders=" + Http.enc("[\"" + gv.loader + "\"]");
        JsonArray versions = Http.getJson(url).getAsJsonArray();
        if (versions.isEmpty()) throw new IOException("No " + gv.mc + (kind == Kind.MODS ? " " + gv.loaderName : "") + " version available.");
        JsonObject v = versions.get(0).getAsJsonObject();
        JsonObject file = null;
        for (JsonElement f : v.getAsJsonArray("files")) {
            JsonObject fo = f.getAsJsonObject();
            if (file == null || (fo.has("primary") && fo.get("primary").getAsBoolean())) file = fo;
        }
        if (file == null) throw new IOException("That version has no files.");
        String name = file.get("filename").getAsString().replaceAll("[\\\\/]", "_");
        Files.createDirectories(dir);
        Path target = dir.resolve(name);
        if (!Files.exists(target)) {
            String sha1 = file.has("hashes") ? s(file.getAsJsonObject("hashes"), "sha1") : null;
            Http.download(file.get("url").getAsString(), target, sha1);
        }
        if (withDeps && v.has("dependencies")) {
            for (JsonElement d : v.getAsJsonArray("dependencies")) {
                JsonObject dep = d.getAsJsonObject();
                if ("required".equals(s(dep, "dependency_type")) && !s(dep, "project_id").isEmpty()) {
                    try {
                        installLatest(s(dep, "project_id"), gv, dir, kind, true, visited);
                    } catch (IOException ignored) {}
                }
            }
        }
        return name;
    }

    public static byte[] icon(String url) {
        if (url == null || url.isEmpty()) return null;
        try {
            return Http.bytes(url);
        } catch (IOException e) {
            return null;
        }
    }

    private static JsonArray single(String v) {
        JsonArray a = new JsonArray();
        a.add(v);
        return a;
    }

    private static String s(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    /**
     * Updates every mod / pack in {@code dir} that Modrinth has a newer version of (for this
     * Minecraft version and Fabric), matching files by their SHA-1. Returns how many were updated.
     */
    public static int updateAll(java.nio.file.Path dir, Kind kind, GameVersion gv) throws IOException {
        java.util.Map<String, java.nio.file.Path> byHash = new java.util.HashMap<>();
        try (var s = java.nio.file.Files.list(dir)) {
            for (java.nio.file.Path f : s.toList()) {
                String n = f.getFileName().toString();
                if (!(n.endsWith(".jar") || n.endsWith(".zip"))) continue;
                byHash.put(sha1(f), f);
            }
        }
        if (byHash.isEmpty()) return 0;
        com.google.gson.JsonObject body = new com.google.gson.JsonObject();
        com.google.gson.JsonArray hashes = new com.google.gson.JsonArray();
        byHash.keySet().forEach(hashes::add);
        body.add("hashes", hashes);
        body.addProperty("algorithm", "sha1");
        com.google.gson.JsonArray loaders = new com.google.gson.JsonArray();
        loaders.add(kind == Kind.MODS ? "fabric" : "minecraft");
        body.add("loaders", loaders);
        com.google.gson.JsonArray versions = new com.google.gson.JsonArray();
        versions.add(gv.mc);
        body.add("game_versions", versions);
        Http.Response r = Http.postJson(API + "/version_files/update", body);
        if (!r.ok()) throw new IOException("Modrinth answered " + r.code());
        com.google.gson.JsonObject res = r.json();
        int updated = 0;
        for (String hash : res.keySet()) {
            java.nio.file.Path old = byHash.get(hash);
            com.google.gson.JsonObject ver = res.getAsJsonObject(hash);
            com.google.gson.JsonObject file = null;
            for (var e : ver.getAsJsonArray("files")) {
                com.google.gson.JsonObject fo = e.getAsJsonObject();
                if (file == null || fo.has("primary") && fo.get("primary").getAsBoolean()) file = fo;
            }
            if (old == null || file == null) continue;
            String newHash = file.getAsJsonObject("hashes").get("sha1").getAsString();
            if (newHash.equalsIgnoreCase(hash)) continue;                    // already the newest
            String name = file.get("filename").getAsString();
            java.nio.file.Path dest = dir.resolve(name);
            Http.download(file.get("url").getAsString(), dest, newHash);
            if (!dest.equals(old)) java.nio.file.Files.deleteIfExists(old);
            updated++;
        }
        return updated;
    }

    private static String sha1(java.nio.file.Path f) throws IOException {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            md.update(java.nio.file.Files.readAllBytes(f));
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}

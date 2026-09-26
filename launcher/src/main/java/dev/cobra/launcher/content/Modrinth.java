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
}

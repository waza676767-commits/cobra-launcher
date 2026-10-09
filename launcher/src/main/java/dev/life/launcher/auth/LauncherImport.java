package dev.life.launcher.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.life.launcher.core.Http;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Reuses the Minecraft session from another launcher on this computer (Lunar, Dawn, Fast Client).
 * Instead of depending on each launcher's file layout, it looks for a Minecraft access token in
 * that launcher's account files: the token itself says whose account it is and when it expires.
 * Launchers that encrypt their sign-ins can't be imported; the error says so.
 */
public final class LauncherImport {
    public enum Source {
        LUNAR("lunar", "Lunar Client", ".lunarclient/settings/game", ".lunarclient"),
        DAWN("dawn", "Dawn Client", ".dawn", ".config/dawn", ".config/Dawn", ".local/share/dawn", ".var/app/gg.dawn.Dawn",
                ".var/app/gg.dawn.launcher", ".feather", ".config/feather"),
        FAST("fast", "Fast Client", ".fastclient", ".config/fastclient", ".config/FastClient", ".local/share/fastclient", ".fast");

        public final String id, label;
        final String[] dirs;

        Source(String id, String label, String... dirs) {
            this.id = id;
            this.label = label;
            this.dirs = dirs;
        }

        public static Source of(String id) {
            for (Source s : values()) if (s.id.equals(id)) return s;
            return null;
        }
    }

    private record Found(String token, String uuid, String name, long expiry, Path file) {}

    private LauncherImport() {}

    public static boolean installed(Source s) {
        Path home = Path.of(System.getProperty("user.home"));
        for (String d : s.dirs) if (Files.isDirectory(home.resolve(d))) return true;
        return false;
    }

    public static Account load(Source s) throws MicrosoftAuth.AuthException {
        Path home = Path.of(System.getProperty("user.home"));
        List<Path> files = new ArrayList<>();
        boolean any = false;
        for (String d : s.dirs) {
            Path dir = home.resolve(d);
            if (!Files.isDirectory(dir)) continue;
            any = true;
            try (Stream<Path> walk = Files.walk(dir, 4)) {
                walk.filter(Files::isRegularFile).filter(LauncherImport::looksLikeAccounts).limit(60).forEach(files::add);
            } catch (IOException ignored) {}
        }
        if (!any) throw new MicrosoftAuth.AuthException(s.label + " isn't installed on this computer (or keeps its files somewhere unusual).");
        Found best = null;
        for (Path f : files) {
            Found x = scan(f);
            if (x != null && (best == null || x.expiry > best.expiry)) best = x;
        }
        if (best == null) {
            throw new MicrosoftAuth.AuthException("Couldn't find a Minecraft sign-in in " + s.label + ". Open " + s.label
                    + ", make sure you're signed in, launch the game once, then try again. If it still fails, "
                    + s.label + " encrypts its sign-ins and can't be shared; use Prism or Microsoft sign-in instead.");
        }
        return toAccount(best, s);
    }

    private static boolean looksLikeAccounts(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            if (Files.size(p) > 2_000_000) return false;
        } catch (IOException e) {
            return false;
        }
        return n.endsWith(".json") && (n.contains("account") || n.contains("auth") || n.contains("session") || n.contains("profile") || n.contains("launcher"));
    }

    /** Finds the freshest Minecraft access token anywhere in a JSON file. */
    private static Found scan(Path file) {
        try {
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            List<String> strings = new ArrayList<>();
            collect(root, strings);
            Found best = null;
            for (String s : strings) {
                Found f = decode(s, file);
                if (f != null && (best == null || f.expiry > best.expiry)) best = f;
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }

    private static void collect(JsonElement e, List<String> out) {
        if (e == null || e.isJsonNull()) return;
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            String s = e.getAsString();
            if (s.length() > 100 && s.chars().filter(c -> c == '.').count() == 2) out.add(s);
        } else if (e.isJsonObject()) {
            for (var en : e.getAsJsonObject().entrySet()) collect(en.getValue(), out);
        } else if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) collect(x, out);
        }
    }

    /** A Minecraft services token is a JWT whose payload lists the Java profile ("pfd") and expiry. */
    private static Found decode(String jwt, Path file) {
        try {
            String[] parts = jwt.split("\\.");
            JsonObject p = JsonParser.parseString(new String(Base64.getUrlDecoder().decode(pad(parts[1])), StandardCharsets.UTF_8)).getAsJsonObject();
            long exp = p.has("exp") ? p.get("exp").getAsLong() * 1000L : 0;
            String uuid = null, name = null;
            if (p.has("pfd") && p.get("pfd").isJsonArray()) {
                JsonArray pfd = p.getAsJsonArray("pfd");
                for (JsonElement el : pfd) {
                    JsonObject o = el.getAsJsonObject();
                    if (!o.has("type") || "mc".equals(o.get("type").getAsString())) {
                        uuid = o.has("id") ? o.get("id").getAsString() : null;
                        name = o.has("name") ? o.get("name").getAsString() : null;
                        break;
                    }
                }
            } else if (p.has("profiles") && p.get("profiles").isJsonObject() && p.getAsJsonObject("profiles").has("mc")) {
                uuid = p.getAsJsonObject("profiles").get("mc").getAsString();
            }
            if (uuid == null || exp == 0) return null;   // not a Minecraft token (e.g. Xbox/MSA or launcher API tokens)
            return new Found(jwt, uuid.replace("-", ""), name, exp, file);
        } catch (Exception e) {
            return null;
        }
    }

    private static String pad(String s) {
        return s + "=".repeat((4 - s.length() % 4) % 4);
    }

    private static Account toAccount(Found f, Source s) throws MicrosoftAuth.AuthException {
        Account a = new Account();
        a.mcToken = f.token;
        a.uuid = f.uuid;
        a.name = f.name == null ? "Player" : f.name;
        a.mcTokenExpiry = f.expiry;
        a.source = s.id;
        if (f.expiry < System.currentTimeMillis()) {
            throw new MicrosoftAuth.AuthException(s.label + " session expired. Sign in with Microsoft instead (it renews itself).");
        }
        try {   // the token decides whose account it is; Mojang confirms it still works
            Http.Response r = Http.get("https://api.minecraftservices.com/minecraft/profile", "Authorization", "Bearer " + a.mcToken);
            if (r.code() == 401) throw new MicrosoftAuth.AuthException(s.label + " session expired. Sign in with Microsoft instead (it renews itself).");
            if (r.code() == 404) throw new MicrosoftAuth.AuthException("That account doesn't own Minecraft: Java Edition.");
            if (r.ok()) {
                JsonObject pj = r.json();
                if (pj.has("name")) a.name = pj.get("name").getAsString();
                if (pj.has("id")) a.uuid = pj.get("id").getAsString();
                if (pj.has("skins") && pj.getAsJsonArray("skins").size() > 0) {
                    JsonObject sk = pj.getAsJsonArray("skins").get(0).getAsJsonObject();
                    if (sk.has("url")) a.skinUrl = sk.get("url").getAsString();
                }
            }
        } catch (MicrosoftAuth.AuthException e) {
            throw e;
        } catch (Exception ignored) {
            // offline right now: trust the token's own expiry
        }
        return a;
    }
}

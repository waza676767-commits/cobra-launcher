package dev.cobra.launcher.auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.cobra.launcher.core.Http;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Uses the Minecraft session of an account you're already signed into in Prism Launcher
 * (or PolyMC / MultiMC). No Azure app needed: Prism did the Microsoft sign-in, we only read
 * the resulting Minecraft token from your own accounts.json. The token lives ~24 hours;
 * opening Prism refreshes it and Cobra re-reads it on every launch.
 */
public final class PrismImport {
    private PrismImport() {}

    public static List<Path> candidates() {
        String home = System.getProperty("user.home");
        String xdg = System.getenv("XDG_DATA_HOME");
        String data = xdg != null && !xdg.isBlank() ? xdg : home + "/.local/share";
        String appData = System.getenv("APPDATA");
        List<Path> out = new ArrayList<>();
        out.add(Path.of(data, "PrismLauncher", "accounts.json"));
        out.add(Path.of(home, ".var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/accounts.json"));
        out.add(Path.of(data, "polymc", "accounts.json"));
        out.add(Path.of(home, ".var/app/org.polymc.PolyMC/data/PolyMC/accounts.json"));
        out.add(Path.of(home, ".local/share/multimc/accounts.json"));
        if (appData != null) out.add(Path.of(appData, "PrismLauncher", "accounts.json"));
        out.add(Path.of(home, "Library/Application Support/PrismLauncher/accounts.json"));
        return out;
    }

    public static boolean available() {
        for (Path p : candidates()) if (Files.isRegularFile(p)) return true;
        return false;
    }

    /** Reads the active (or first) Microsoft account from Prism and checks the token with Mojang. */
    public static Account load() throws MicrosoftAuth.AuthException {
        Path file = null;
        for (Path p : candidates()) {
            if (Files.isRegularFile(p)) {
                file = p;
                break;
            }
        }
        if (file == null) throw new MicrosoftAuth.AuthException("Prism Launcher isn't installed or has no accounts yet. Sign in there once, then try again.");

        JsonObject chosen = null;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("accounts")) {
                JsonObject a = e.getAsJsonObject();
                if (!"MSA".equalsIgnoreCase(str(a, "type"))) continue;
                if (chosen == null || a.has("active") && a.get("active").getAsBoolean()) chosen = a;
            }
        } catch (Exception e) {
            throw new MicrosoftAuth.AuthException("Couldn't read Prism's accounts file (" + e.getMessage() + ").");
        }
        if (chosen == null) throw new MicrosoftAuth.AuthException("Prism has no Microsoft account. Add one in Prism → Accounts first.");

        JsonObject ygg = chosen.getAsJsonObject("ygg");
        JsonObject profile = chosen.getAsJsonObject("profile");
        if (ygg == null || profile == null || str(ygg, "token").isEmpty()) {
            throw new MicrosoftAuth.AuthException("Prism's account has no Minecraft session yet. Launch any instance in Prism once.");
        }
        Account a = new Account();
        a.source = "prism";
        a.uuid = str(profile, "id").replace("-", "");
        a.name = str(profile, "name");
        a.mcToken = str(ygg, "token");
        a.mcTokenExpiry = ygg.has("exp") ? ygg.get("exp").getAsLong() * 1000L : System.currentTimeMillis() + 3600_000L;
        JsonObject skin = profile.getAsJsonObject("skin");
        if (skin != null) a.skinUrl = str(skin, "url");

        // Prism's stored expiry can be stale, so ask Mojang directly
        try {
            Http.Response r = Http.get("https://api.minecraftservices.com/minecraft/profile", "Authorization", "Bearer " + a.mcToken);
            if (r.code() == 401) {
                throw new MicrosoftAuth.AuthException("Prism session expired. Sign in with Microsoft instead (it renews itself).");
            }
            if (r.code() == 404) throw new MicrosoftAuth.AuthException("That Microsoft account doesn't own Minecraft: Java Edition.");
            if (r.ok()) {
                JsonObject pj = r.json();
                a.name = str(pj, "name");
                a.uuid = str(pj, "id");
                a.mcTokenExpiry = Math.max(a.mcTokenExpiry, System.currentTimeMillis() + 15 * 60_000L);
            }
        } catch (MicrosoftAuth.AuthException e) {
            throw e;
        } catch (Exception ignored) {
            // offline: trust the stored expiry
        }
        return a;
    }

    private static String str(JsonObject o, String k) {
        return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }
}

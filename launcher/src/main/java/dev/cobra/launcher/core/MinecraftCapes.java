package dev.cobra.launcher.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.cobra.launcher.auth.Account;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * Your official Minecraft capes (Migrator, Pan, MineCon…): which ones your account owns, and
 * switching the one everyone sees, the same as on minecraft.net.
 */
public final class MinecraftCapes {
    private static final String API = "https://api.minecraftservices.com/minecraft/profile";

    public record Cape(String id, String name, String url, boolean active) {}

    private MinecraftCapes() {}

    private static String auth(Account a) throws IOException {
        if (a == null || a.mcToken == null || "0".equals(a.mcToken)) throw new IOException("Sign in with your Microsoft account first.");
        return "Bearer " + a.mcToken;
    }

    /** The capes this account owns. */
    public static List<Cape> list(Account a) throws IOException {
        Http.Response r = Http.get(API, "Authorization", auth(a));
        if (r.code() == 401) throw new IOException("Your sign-in expired: sign out and in again.");
        if (!r.ok()) throw new IOException("Minecraft answered " + r.code());
        JsonObject j = r.json();
        List<Cape> out = new ArrayList<>();
        JsonArray capes = j.has("capes") ? j.getAsJsonArray("capes") : new JsonArray();
        for (JsonElement e : capes) {
            JsonObject c = e.getAsJsonObject();
            out.add(new Cape(c.get("id").getAsString(), c.has("alias") ? c.get("alias").getAsString() : "Cape",
                    c.has("url") ? c.get("url").getAsString() : "", c.has("state") && "ACTIVE".equals(c.get("state").getAsString())));
        }
        return out;
    }

    /** Shows this cape on your account (null = no cape). */
    public static void wear(Account a, String capeId) throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(API + "/capes/active")).header("Authorization", auth(a));
        if (capeId == null) b.DELETE();
        else b.header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString("{\"capeId\":\"" + capeId + "\"}"));
        Http.Response r = Http.send(b.build());
        if (!r.ok()) throw new IOException("Minecraft answered " + r.code() + (r.code() == 401 ? " (sign out and in again)" : ""));
    }
}

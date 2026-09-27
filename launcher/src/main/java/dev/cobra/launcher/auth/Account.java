package dev.cobra.launcher.auth;

import dev.cobra.launcher.core.Http;
import dev.cobra.launcher.core.Paths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

public final class Account {
    private static final Path FILE = Paths.ROOT.resolve("account.json");

    public String uuid;          // no dashes
    public String name;
    public String mcToken;
    public long mcTokenExpiry;   // epoch millis
    public String msRefreshToken;
    public String skinUrl;
    public String xuid = "0";
    /** For source "microsoft": "live" (no-setup sign-in) or "azure" (own client ID). Old files have null = azure. */
    public String msFlow;
    /** "microsoft" (signed in here), "prism" / "lunar" / "dawn" / "fast" (session borrowed from that launcher) or "offline". */
    public String source = "microsoft";

    public boolean offline() { return "offline".equals(source); }

    /** Offline player for "No account" mode: singleplayer only, same UUID scheme vanilla uses offline. */
    public static Account offline(String name) {
        Account a = new Account();
        a.source = "offline";
        a.name = name == null || name.isBlank() ? "Player" : name.trim();
        a.uuid = java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + a.name).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString().replace("-", "");
        a.mcToken = "0";
        a.mcTokenExpiry = Long.MAX_VALUE;
        return a;
    }

    public boolean tokenValid() {
        return mcToken != null && System.currentTimeMillis() < mcTokenExpiry - 10 * 60_000L;
    }

    /** Marker written into account.json instead of a token: the real one is in the SecureStore. */
    private static final String SECURE = "secure-store";

    public static Account load() {
        try {
            if (!Files.exists(FILE)) return null;
            Account a = Http.GSON.fromJson(Files.readString(FILE), Account.class);
            if (a == null) return null;
            boolean plain = a.mcToken != null && !SECURE.equals(a.mcToken) && !a.offline()
                    || a.msRefreshToken != null && !SECURE.equals(a.msRefreshToken);
            if (SECURE.equals(a.mcToken)) a.mcToken = dev.cobra.launcher.core.SecureStore.get("mc-token");
            if (SECURE.equals(a.msRefreshToken)) a.msRefreshToken = dev.cobra.launcher.core.SecureStore.get("ms-refresh");
            if (plain) a.save();          // older launcher saved tokens as plain text: move them now
            return a;
        } catch (Exception ignored) {}
        return null;
    }

    /** Saves the account; the Minecraft and Microsoft tokens go to the SecureStore, not the file. */
    public void save() {
        try {
            String mc = mcToken, ms = msRefreshToken;
            if (!offline()) {
                dev.cobra.launcher.core.SecureStore.put("mc-token", mc);
                dev.cobra.launcher.core.SecureStore.put("ms-refresh", ms);
                mcToken = mc == null ? null : SECURE;
                msRefreshToken = ms == null ? null : SECURE;
            }
            String json;
            try {
                json = Http.GSON.toJson(this);
            } finally {
                mcToken = mc;
                msRefreshToken = ms;
            }
            Files.writeString(FILE, json);
            try {
                Files.setPosixFilePermissions(FILE, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {}
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void delete() {
        try {
            Files.deleteIfExists(FILE);
        } catch (IOException ignored) {}
        dev.cobra.launcher.core.SecureStore.remove("mc-token");
        dev.cobra.launcher.core.SecureStore.remove("ms-refresh");
    }
}

package dev.cobra.launcher.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Optional launcher password. Only a salted PBKDF2 hash is stored (lock.json in the launcher's
 * folder). It keeps other people on this PC out of your launcher; deleting lock.json removes it
 * (that's the "Forgot?" way back in).
 */
public final class AppLock {
    public static final Path FILE = Paths.ROOT.resolve("lock.json");
    private static final int ITERATIONS = 120_000;

    private AppLock() {}

    public static boolean enabled() {
        return Files.exists(FILE);
    }

    /** Should the lock screen show now? (a password is set and "remember me" has run out) */
    public static boolean locked() {
        if (!enabled()) return false;
        try {
            JsonObject o = read();
            return !o.has("rememberUntil") || o.get("rememberUntil").getAsLong() < System.currentTimeMillis();
        } catch (Exception e) {
            return true;
        }
    }

    public static void set(char[] password) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        JsonObject o = new JsonObject();
        o.addProperty("salt", Base64.getEncoder().encodeToString(salt));
        o.addProperty("hash", Base64.getEncoder().encodeToString(hash(password, salt)));
        o.addProperty("rememberUntil", 0L);
        Files.createDirectories(FILE.getParent());
        Files.writeString(FILE, o.toString());
    }

    public static void remove() throws Exception {
        Files.deleteIfExists(FILE);
    }

    /** Checks the password; with {@code remember} it isn't asked again for 7 days on this PC. */
    public static boolean check(char[] password, boolean remember) {
        try {
            JsonObject o = read();
            byte[] salt = Base64.getDecoder().decode(o.get("salt").getAsString());
            byte[] want = Base64.getDecoder().decode(o.get("hash").getAsString());
            boolean ok = MessageDigest.isEqual(want, hash(password, salt));
            if (ok) {
                o.addProperty("rememberUntil", remember ? System.currentTimeMillis() + 7L * 86_400_000L : 0L);
                Files.writeString(FILE, o.toString());
            }
            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    private static JsonObject read() throws Exception {
        return JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
    }

    private static byte[] hash(char[] password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}

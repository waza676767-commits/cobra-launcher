package dev.cobra.launcher.core;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Keeps sign-in tokens out of plain files.
 * <ul>
 *   <li>Windows: encrypted with Windows' own DPAPI for your user account (only you, on this PC,
 *   can decrypt them).</li>
 *   <li>Linux: in your system keyring (GNOME Keyring / KWallet through Secret Service) when
 *   {@code secret-tool} is available; otherwise AES-256-GCM encrypted with a key file only your
 *   user can read.</li>
 * </ul>
 */
public final class SecureStore {
    private static final Path DIR = Paths.ROOT.resolve("secrets");
    private static final Map<String, String> CACHE = new HashMap<>();
    private static Boolean keyring;

    private SecureStore() {}

    private static boolean windows() {
        return "windows".equals(Paths.OS_NAME);
    }

    public static synchronized void put(String name, String secret) {
        if (secret == null || secret.isEmpty()) {
            remove(name);
            return;
        }
        if (secret.equals(CACHE.get(name))) return;
        try {
            Files.createDirectories(DIR);
            restrict(DIR, "rwx------");
            if (windows()) {
                String enc = powershell("Add-Type -AssemblyName System.Security;"
                        + "$s=[Console]::In.ReadToEnd();"
                        + "[Convert]::ToBase64String([Security.Cryptography.ProtectedData]::Protect([Text.Encoding]::UTF8.GetBytes($s),$null,'CurrentUser'))", secret);
                if (enc == null || enc.isBlank()) throw new IllegalStateException("DPAPI failed");
                Files.writeString(DIR.resolve(name + ".dpapi"), enc.trim());
            } else if (keyringWorks()) {
                if (run(secret, "secret-tool", "store", "--label=Cobra Launcher (" + name + ")", "service", "cobra-launcher", "key", name) == null) {
                    throw new IllegalStateException("keyring failed");
                }
                Files.deleteIfExists(DIR.resolve(name + ".aes"));
            } else {
                Path f = DIR.resolve(name + ".aes");
                Files.writeString(f, aes(true, secret));
                restrict(f, "rw-------");
            }
            CACHE.put(name, secret);
        } catch (Exception e) {
            // last resort: AES file (still not plain text)
            try {
                Path f = DIR.resolve(name + ".aes");
                Files.writeString(f, aes(true, secret));
                restrict(f, "rw-------");
                CACHE.put(name, secret);
            } catch (Exception ignored) {}
        }
    }

    public static synchronized String get(String name) {
        if (CACHE.containsKey(name)) return CACHE.get(name);
        String v = null;
        try {
            Path dp = DIR.resolve(name + ".dpapi"), ae = DIR.resolve(name + ".aes");
            if (windows() && Files.exists(dp)) {
                v = powershell("Add-Type -AssemblyName System.Security;"
                        + "$b=[Convert]::FromBase64String([Console]::In.ReadToEnd().Trim());"
                        + "[Text.Encoding]::UTF8.GetString([Security.Cryptography.ProtectedData]::Unprotect($b,$null,'CurrentUser'))",
                        Files.readString(dp));
            } else if (Files.exists(ae)) {
                v = aes(false, Files.readString(ae).trim());
            } else if (!windows() && keyringWorks()) {
                v = run(null, "secret-tool", "lookup", "service", "cobra-launcher", "key", name);
            }
        } catch (Exception ignored) {}
        if (v != null) v = v.strip();
        if (v != null && v.isEmpty()) v = null;
        CACHE.put(name, v);
        return v;
    }

    public static synchronized void remove(String name) {
        CACHE.remove(name);
        try {
            Files.deleteIfExists(DIR.resolve(name + ".dpapi"));
            Files.deleteIfExists(DIR.resolve(name + ".aes"));
            if (!windows() && keyringWorks()) run(null, "secret-tool", "clear", "service", "cobra-launcher", "key", name);
        } catch (Exception ignored) {}
    }

    /** Is a Secret Service keyring usable (secret-tool installed and answering)? */
    private static boolean keyringWorks() {
        if (keyring == null) {
            keyring = run("probe", "secret-tool", "store", "--label=Cobra Launcher (test)", "service", "cobra-launcher", "key", "probe") != null
                    && "probe".equals(strip(run(null, "secret-tool", "lookup", "service", "cobra-launcher", "key", "probe")));
            if (keyring) run(null, "secret-tool", "clear", "service", "cobra-launcher", "key", "probe");
        }
        return keyring;
    }

    private static String strip(String s) {
        return s == null ? null : s.strip();
    }

    /** Runs a command with optional stdin; its stdout, or null if it failed / took too long. */
    private static String run(String stdin, String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try (var o = p.getOutputStream()) {
                if (stdin != null) o.write(stdin.getBytes(StandardCharsets.UTF_8));
            }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(20, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            return p.exitValue() == 0 ? out : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String powershell(String script, String stdin) {
        return run(stdin, "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script);
    }

    // ------------------------------------------------------------------ AES fallback

    private static byte[] key() throws Exception {
        Path k = Paths.ROOT.resolve(".secret-key");
        if (!Files.exists(k)) {
            byte[] b = new byte[32];
            new SecureRandom().nextBytes(b);
            Files.write(k, b);
            restrict(k, "rw-------");
        }
        return Files.readAllBytes(k);
    }

    private static String aes(boolean encrypt, String text) throws Exception {
        SecretKeySpec key = new SecretKeySpec(key(), "AES");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        if (encrypt) {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(text.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[12 + ct.length];
            System.arraycopy(iv, 0, out, 0, 12);
            System.arraycopy(ct, 0, out, 12, ct.length);
            return Base64.getEncoder().encodeToString(out);
        }
        byte[] all = Base64.getDecoder().decode(text);
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
        return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
    }

    private static void restrict(Path p, String perms) {
        try {
            Files.setPosixFilePermissions(p, PosixFilePermissions.fromString(perms));
        } catch (Exception ignored) {}
    }
}

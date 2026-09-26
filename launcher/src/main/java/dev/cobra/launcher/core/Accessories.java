package dev.cobra.launcher.core;

import dev.cobra.launcher.auth.Account;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Your own skin and cape for Cobra Client. They're stored once in the launcher and copied into
 * each game folder on launch (config/cobra/skin.png, cape.png, accessories.properties), where the
 * client shows them on your player. The skin can also be uploaded to your real Minecraft account.
 */
public final class Accessories {
    private static final Path DIR = Paths.ROOT.resolve("accessories");
    public static final Path SKIN = DIR.resolve("skin.png");
    public static final Path CAPE = DIR.resolve("cape.png");
    /** Your crosshair picture (Custom Crosshair → Style: Image in game). */
    public static final Path CROSSHAIR = DIR.resolve("crosshair.png");

    private Accessories() {}

    public static boolean hasSkin() { return Files.isRegularFile(SKIN); }
    public static boolean hasCape() { return Files.isRegularFile(CAPE); }

    public static BufferedImage skin() { return read(SKIN); }
    public static BufferedImage cape() { return read(CAPE); }

    private static BufferedImage read(Path p) {
        try {
            return Files.isRegularFile(p) ? ImageIO.read(p.toFile()) : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Accepts 64×64 skins and legacy 64×32 skins (converted to 64×64 like Minecraft does). */
    public static void importSkin(Path src) throws IOException {
        BufferedImage img = ImageIO.read(src.toFile());
        if (img == null) throw new IOException("That file isn't a PNG image.");
        if (img.getWidth() != 64 || img.getHeight() != 64 && img.getHeight() != 32) {
            throw new IOException("A skin must be 64×64 (or old-style 64×32). This one is " + img.getWidth() + "×" + img.getHeight() + ".");
        }
        BufferedImage out = img.getHeight() == 32 ? upgradeLegacy(img) : toArgb(img);
        Files.createDirectories(DIR);
        ImageIO.write(out, "png", SKIN.toFile());
    }

    /**
     * Capes: 64×32 (and HD 128×64, 256×128 … kept sharp), 64×64 (cape in the top half, like
     * MinecraftCapes / animated cape sheets), 32×32 (cape-only texture, sits in the top-left of
     * a 64×32 sheet), plus the old 22×17 and OptiFine 46×22 layouts.
     */
    public static void importCape(Path src) throws IOException {
        BufferedImage img = ImageIO.read(src.toFile());
        if (img == null) throw new IOException("That file isn't a PNG image.");
        BufferedImage out = normaliseCape(img);
        Files.createDirectories(DIR);
        ImageIO.write(out, "png", CAPE.toFile());
    }

    /** Turns any supported cape layout into a 2:1 cape sheet (64×32 or an HD multiple of it). */
    public static BufferedImage normaliseCape(BufferedImage img) throws IOException {
        int w = img.getWidth(), h = img.getHeight();
        if (w <= 0 || h <= 0 || w > 4096 || h > 4096) throw new IOException("That cape image is too big.");
        if (w == h * 2 && w >= 64 && w % 64 == 0) {
            return w > 1024 ? scaleNearest(img, 1024, 512) : toArgb(img);             // 64×32, 128×64, …
        }
        if (w == h * 2 && w < 64) return placeTopLeft(img, 64, 32);                     // tiny 2:1 sheets
        if (w == h && w >= 64 && w % 64 == 0) {                                         // 64×64, 128×128: top half
            BufferedImage top = img.getSubimage(0, 0, w, w / 2);
            return w > 1024 ? scaleNearest(top, 1024, 512) : toArgb(top);
        }
        if (w == h && w == 32) return placeTopLeft(img, 64, 32);                        // 32×32 cape-only
        if (w == 22 && h == 17) return placeTopLeft(img, 64, 32);                       // pre-1.8 cape
        if (w * 22 == h * 46) {                                                         // OptiFine 46×22 × n
            int n = Math.max(1, w / 46);
            return placeTopLeft(img, 64 * n, 32 * n);
        }
        throw new IOException("Capes can be 64×32, 64×64 or 32×32 (HD 128×64 etc. work too). This one is " + w + "×" + h + ".");
    }

    private static BufferedImage placeTopLeft(BufferedImage img, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return out;
    }

    private static BufferedImage scaleNearest(BufferedImage img, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    public static void removeSkin() throws IOException { Files.deleteIfExists(SKIN); }
    public static void removeCape() throws IOException { Files.deleteIfExists(CAPE); }
    public static void removeCrosshair() throws IOException { Files.deleteIfExists(CROSSHAIR); }

    /** Any picture as a crosshair: kept as PNG (transparent parts stay see-through), max 256 px. */
    public static void importCrosshair(Path src) throws IOException {
        BufferedImage img = ImageIO.read(src.toFile());
        if (img == null) throw new IOException("That file isn't a PNG, JPG, GIF or BMP image.");
        int w = img.getWidth(), h = img.getHeight();
        if (w > 256 || h > 256) {
            double k = 256.0 / Math.max(w, h);
            BufferedImage out = new BufferedImage(Math.max(1, (int) (w * k)), Math.max(1, (int) (h * k)), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(img, 0, 0, out.getWidth(), out.getHeight(), null);
            g.dispose();
            img = out;
        }
        Files.createDirectories(DIR);
        ImageIO.write(img, "png", CROSSHAIR.toFile());
    }

    /** Copies (or clears) the accessories in a game folder right before launch. */
    public static void exportForGame(Path gameDir) {
        Path dir = gameDir.resolve("config").resolve("cobra");
        try {
            Files.createDirectories(dir);
            copyOrDelete(SKIN, dir.resolve("skin.png"));
            copyOrDelete(CAPE, dir.resolve("cape.png"));
            copyOrDelete(CROSSHAIR, dir.resolve("crosshair.png"));
            Properties p = new Properties();
            p.setProperty("slim", String.valueOf(Settings.get().skinSlim));
            try (OutputStream o = Files.newOutputStream(dir.resolve("accessories.properties"))) {
                p.store(o, "Cobra Launcher accessories");
            }
        } catch (IOException ignored) {}
    }

    private static void copyOrDelete(Path src, Path dst) throws IOException {
        if (Files.isRegularFile(src)) Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        else Files.deleteIfExists(dst);
    }

    /** Uploads the imported skin to the signed-in Minecraft account (everyone sees it then). */
    public static void uploadSkin(Account account) throws IOException {
        if (account == null || account.offline()) throw new IOException("Sign in with a Microsoft or Prism account to change your real skin.");
        if (!hasSkin()) throw new IOException("Import a skin first.");
        String boundary = "----cobra" + System.nanoTime();
        HttpURLConnection c = (HttpURLConnection) URI.create("https://api.minecraftservices.com/minecraft/profile/skins").toURL().openConnection();
        c.setDoOutput(true);
        c.setRequestMethod("POST");
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Authorization", "Bearer " + account.mcToken);
        c.setRequestProperty("User-Agent", Http.USER_AGENT);
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        try (DataOutputStream out = new DataOutputStream(c.getOutputStream())) {
            out.writeBytes("--" + boundary + "\r\nContent-Disposition: form-data; name=\"variant\"\r\n\r\n"
                    + (Settings.get().skinSlim ? "slim" : "classic") + "\r\n");
            out.writeBytes("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\nContent-Type: image/png\r\n\r\n");
            out.write(Files.readAllBytes(SKIN));
            out.writeBytes("\r\n--" + boundary + "--\r\n");
        }
        int code = c.getResponseCode();
        if (code == 401) throw new IOException("Your session expired. Sign in again (or open Prism once), then retry.");
        if (code >= 300) {
            String body;
            try (InputStream in = c.getErrorStream()) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                if (in != null) in.transferTo(b);
                body = b.toString(StandardCharsets.UTF_8);
            }
            throw new IOException("Mojang refused the skin (HTTP " + code + ")" + (body.isBlank() ? "" : ": " + body.lines().findFirst().orElse("")));
        }
    }

    private static BufferedImage toArgb(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    /** 64×32 → 64×64: mirror the right arm/leg into the new left arm/leg slots, as vanilla does. */
    private static BufferedImage upgradeLegacy(BufferedImage src) {
        BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        // {srcX, srcY, w, h, dstX, dstY} faces of leg (0,16) and arm (40,16), mirrored horizontally
        int[][] leg = {{4, 16, 4, 4, 20, 48}, {8, 16, 4, 4, 24, 48}, {0, 20, 4, 12, 24, 52}, {4, 20, 4, 12, 20, 52}, {8, 20, 4, 12, 16, 52}, {12, 20, 4, 12, 28, 52}};
        int[][] arm = {{44, 16, 4, 4, 36, 48}, {48, 16, 4, 4, 40, 48}, {40, 20, 4, 12, 40, 52}, {44, 20, 4, 12, 36, 52}, {48, 20, 4, 12, 32, 52}, {52, 20, 4, 12, 44, 52}};
        for (int[][] set : new int[][][]{leg, arm}) {
            for (int[] f : set) {
                for (int y = 0; y < f[3]; y++) {
                    for (int x = 0; x < f[2]; x++) out.setRGB(f[4] + (f[2] - 1 - x), f[5] + y, src.getRGB(f[0] + x, f[1] + y));
                }
            }
        }
        return out;
    }

    /** Where exported accessories live inside a game folder (for tests / diagnostics). */
    public static Properties readExported(Path gameDir) {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(gameDir.resolve("config/cobra/accessories.properties"))) {
            p.load(in);
        } catch (IOException ignored) {}
        return p;
    }
}

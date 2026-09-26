package dev.cobra.launcher.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Icon decoding. Modrinth serves most icons as WebP, which Java can't read on its own, so this
 * registers the bundled WebP reader and falls back to ffmpeg. Installed mods and packs get their
 * icon from inside the file (fabric.mod.json / mcmod.info / pack.png).
 */
public final class Images {
    private static final Map<String, BufferedImage> LOCAL = new ConcurrentHashMap<>();
    private static final BufferedImage NONE = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    private static final int SIZE = 96;

    static {
        try {   // TwelveMonkeys WebP reader (bundled); registered by hand so merged jars can't lose it
            Object spi = Class.forName("com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi").getDeclaredConstructor().newInstance();
            IIORegistry.getDefaultInstance().registerServiceProvider(spi);
        } catch (Throwable ignored) {}
    }

    private Images() {}

    /** Any image bytes → a smooth 96 px icon, or null. */
    public static BufferedImage decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        BufferedImage img = null;
        try {
            img = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception ignored) {}
        if (img == null) img = viaFfmpeg(bytes);
        return img == null ? null : fit(img);
    }

    private static BufferedImage viaFfmpeg(byte[] bytes) {
        try {
            Process p = new ProcessBuilder("ffmpeg", "-loglevel", "error", "-i", "pipe:0", "-frames:v", "1", "-f", "image2pipe", "-vcodec", "png", "pipe:1").start();
            Thread feed = new Thread(() -> {
                try (OutputStream o = p.getOutputStream()) {
                    o.write(bytes);
                } catch (IOException ignored) {}
            });
            feed.start();
            byte[] png = p.getInputStream().readAllBytes();
            p.waitFor();
            return png.length == 0 ? null : ImageIO.read(new ByteArrayInputStream(png));
        } catch (Exception e) {
            return null;
        }
    }

    /** Downscale in halves for a clean small icon (big logos look jagged when drawn straight to 40 px). */
    private static BufferedImage fit(BufferedImage src) {
        BufferedImage cur = src;
        if (cur.getType() != BufferedImage.TYPE_INT_ARGB) {
            BufferedImage c = new BufferedImage(cur.getWidth(), cur.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = c.createGraphics();
            g.drawImage(cur, 0, 0, null);
            g.dispose();
            cur = c;
        }
        boolean pixelArt = src.getWidth() <= 32;
        while (cur.getWidth() / 2 >= SIZE) {
            BufferedImage half = new BufferedImage(cur.getWidth() / 2, Math.max(1, cur.getHeight() / 2), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = half.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(cur, 0, 0, half.getWidth(), half.getHeight(), null);
            g.dispose();
            cur = half;
        }
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, pixelArt ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        double s = Math.min(SIZE / (double) cur.getWidth(), SIZE / (double) cur.getHeight());
        int w = (int) Math.round(cur.getWidth() * s), h = (int) Math.round(cur.getHeight() * s);
        g.drawImage(cur, (SIZE - w) / 2, (SIZE - h) / 2, w, h, null);
        g.dispose();
        return out;
    }

    /** Icon inside an installed mod jar or pack zip/folder, cached by path + modified time. Off the UI thread. */
    public static BufferedImage localIcon(Path file) {
        String key;
        try {
            key = file + "|" + Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return null;
        }
        BufferedImage hit = LOCAL.get(key);
        if (hit != null) return hit == NONE ? null : hit;
        BufferedImage img = null;
        try {
            if (Files.isDirectory(file)) {
                Path png = file.resolve("pack.png");
                if (Files.isRegularFile(png)) img = decode(Files.readAllBytes(png));
            } else {
                try (ZipFile zip = new ZipFile(file.toFile())) {
                    img = decode(read(zip, iconPath(zip)));
                }
            }
        } catch (Exception ignored) {}
        LOCAL.put(key, img == null ? NONE : img);
        return img;
    }

    private static String iconPath(ZipFile zip) {
        try {
            ZipEntry fmj = zip.getEntry("fabric.mod.json");
            if (fmj != null) {
                try (InputStream in = zip.getInputStream(fmj)) {
                    JsonElement icon = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().get("icon");
                    if (icon != null && icon.isJsonPrimitive()) return icon.getAsString();
                    if (icon != null && icon.isJsonObject()) {   // {"16": "...", "128": "..."}: take the largest
                        String best = null;
                        int bestSize = -1;
                        for (Map.Entry<String, JsonElement> e : icon.getAsJsonObject().entrySet()) {
                            int s = parse(e.getKey());
                            if (s > bestSize) {
                                bestSize = s;
                                best = e.getValue().getAsString();
                            }
                        }
                        if (best != null) return best;
                    }
                }
            }
            ZipEntry info = zip.getEntry("mcmod.info");
            if (info != null) {
                try (InputStream in = zip.getInputStream(info)) {
                    JsonElement root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    JsonArray list = root.isJsonArray() ? root.getAsJsonArray() : root.getAsJsonObject().getAsJsonArray("modList");
                    if (list != null && !list.isEmpty()) {
                        JsonObject m = list.get(0).getAsJsonObject();
                        if (m.has("logoFile") && !m.get("logoFile").getAsString().isEmpty()) return m.get("logoFile").getAsString();
                    }
                }
            }
        } catch (Exception ignored) {}
        for (String n : new String[]{"pack.png", "icon.png", "logo.png"}) if (zip.getEntry(n) != null) return n;
        return null;
    }

    private static byte[] read(ZipFile zip, String path) throws IOException {
        if (path == null) return null;
        ZipEntry e = zip.getEntry(path.startsWith("/") ? path.substring(1) : path);
        if (e == null) return null;
        try (InputStream in = zip.getInputStream(e)) {
            return in.readAllBytes();
        }
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

package dev.cobra.client.fabric.cosmetics;

import dev.cobra.client.core.CobraOnline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom capes imported in Abyss Launcher, shown through Minecraft's own cape (so they hang, swing
 * and flap exactly like a real cape, never clip into you, and also show on your elytra). Yours
 * comes from config/cobra/cape.png; other Abyss players' are downloaded from the online list.
 * Animated capes (frames stacked top to bottom) play by copying one frame at a time into the
 * texture Minecraft draws.
 */
public final class CobraCapes {
    /** A loaded cape: the texture Minecraft draws, plus the animation frames if it has any. */
    private static final class Cape {
        final Identifier asset;          // the id Minecraft's skin system uses (textures/… is added by it)
        final Identifier texture;        // where the texture is registered
        final NativeImageBackedTexture shown;
        final NativeImage strip;         // all frames (null when not animated)
        final int frames, fw, fh;
        int lastFrame = -1;

        Cape(Identifier asset, Identifier texture, NativeImageBackedTexture shown, NativeImage strip, int frames, int fw, int fh) {
            this.asset = asset;
            this.texture = texture;
            this.shown = shown;
            this.strip = strip;
            this.frames = frames;
            this.fw = fw;
            this.fh = fh;
        }
    }

    private static final Map<String, Cape> READY = new ConcurrentHashMap<>();
    private static final Map<String, String> REQUESTED = new ConcurrentHashMap<>();
    private static final Map<String, Long> ASKED_AT = new ConcurrentHashMap<>();

    private CobraCapes() {}

    /** The cape for this player and hash (loading it if needed), or null while it loads / if none. */
    private static Cape get(String uuid, String hash, boolean self) {
        String key = uuid + ":" + hash;
        Cape c = READY.get(key);
        if (c != null) return c;
        Long at = ASKED_AT.get(uuid);
        if (hash.equals(REQUESTED.get(uuid)) && at != null && System.currentTimeMillis() - at < 60_000) return null;
        REQUESTED.put(uuid, hash);
        ASKED_AT.put(uuid, System.currentTimeMillis());
        Thread t = new Thread(() -> {
            byte[] png = null;
            try {
                if (self) {
                    Path f = MinecraftClient.getInstance().runDirectory.toPath().resolve("config").resolve("cobra").resolve("cape.png");
                    if (Files.isRegularFile(f)) png = Files.readAllBytes(f);
                } else {
                    png = CobraOnline.downloadCape(uuid);
                }
            } catch (Exception ignored) {}
            if (png == null) return;
            final byte[] data = png;
            MinecraftClient.getInstance().execute(() -> {               // textures are made on the render thread
                try {
                    NativeImage img = NativeImage.read(new ByteArrayInputStream(data));
                    String name = "capes/" + uuid.replace("-", "") + "_" + hash;
                    Identifier asset = Identifier.of("cobra", name);
                    Identifier texture = Identifier.of("cobra", "textures/" + name + ".png");
                    int fw = img.getWidth(), fh = Math.max(1, fw / 2);
                    int frames = Math.max(1, img.getHeight() / fh);
                    NativeImageBackedTexture shown;
                    NativeImage strip = null;
                    if (frames > 1) {                                       // animated: show frame 0 for now
                        strip = img;
                        NativeImage one = new NativeImage(fw, fh, true);
                        copyFrame(strip, one, 0, fw, fh);
                        shown = new NativeImageBackedTexture(() -> "abyss cape", one);
                    } else {
                        shown = new NativeImageBackedTexture(() -> "abyss cape", img);
                    }
                    MinecraftClient.getInstance().getTextureManager().registerTexture(texture, shown);
                    READY.put(key, new Cape(asset, texture, shown, strip, frames, fw, fh));
                } catch (Exception ignored) {}
            });
        }, "abyss-cape");
        t.setDaemon(true);
        t.start();
        return null;
    }

    private static void copyFrame(NativeImage strip, NativeImage out, int frame, int fw, int fh) {
        int oy = frame * fh;
        for (int y = 0; y < fh; y++) for (int x = 0; x < fw; x++) out.setColorArgb(x, y, strip.getColorArgb(x, oy + y));
    }

    /** Animated capes step to their current frame (one every 90 ms). Call once per frame. */
    private static void animate(Cape c) {
        if (c.strip == null) return;
        int f = (int) ((System.currentTimeMillis() / 90) % c.frames);
        if (f == c.lastFrame) return;
        c.lastFrame = f;
        NativeImage img = c.shown.getImage();
        if (img == null) return;
        copyFrame(c.strip, img, f, c.fw, c.fh);
        c.shown.upload();
    }

    /** The cape asset for a player wearing an Abyss cape (ready to show), or null. */
    public static AssetInfo.TextureAssetInfo asset(String uuid, Map<String, String> wear, boolean self) {
        String h = wear.get("cape");
        if (h == null) return null;
        Cape c = get(uuid, h, self);
        if (c == null) return null;
        animate(c);
        return new AssetInfo.TextureAssetInfo(c.asset);
    }

    /** The registered texture for the elytra, or null. */
    public static Identifier texture(String uuid, Map<String, String> wear, boolean self) {
        String h = wear.get("cape");
        if (h == null) return null;
        Cape c = get(uuid, h, self);
        if (c == null) return null;
        animate(c);
        return c.texture;
    }
}

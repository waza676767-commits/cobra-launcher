package dev.cobra.client.fabric.cosmetics;

import dev.cobra.client.core.CobraOnline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom capes imported in Cobra Launcher. Yours comes from config/cobra/cape.png; other Cobra
 * players' are downloaded from the Cobra online list (only when their code says they have one,
 * and again when it changes). Everyone else never sees them.
 */
public final class CobraCapes {
    private static final Map<String, Identifier> READY = new ConcurrentHashMap<>();
    /** How many animation frames each cape has (stacked top to bottom, each 2:1 like a normal cape). */
    private static final Map<Identifier, Integer> FRAMES = new ConcurrentHashMap<>();

    /** Frames in this cape texture (1 for a normal cape). */
    public static int frames(Identifier id) {
        Integer f = FRAMES.get(id);
        return f == null ? 1 : f;
    }
    private static final Map<String, String> REQUESTED = new ConcurrentHashMap<>();   // uuid -> hash asked for
    private static final Map<String, Long> ASKED_AT = new ConcurrentHashMap<>();

    private CobraCapes() {}

    /** The cape texture for this player and cape hash, or null while it's loading / if there's none. */
    public static Identifier get(String uuid, String hash, boolean self) {
        String key = uuid + ":" + hash;
        Identifier id = READY.get(key);
        if (id != null) return id;
        Long at = ASKED_AT.get(uuid);
        // already on its way (or it failed less than a minute ago: try again after that)
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
                    Identifier tid = Identifier.of("cobra", "capes/" + uuid.replace("-", "") + "_" + hash);
                    int frameH = Math.max(1, img.getWidth() / 2);
                    FRAMES.put(tid, Math.max(1, img.getHeight() / frameH));     // animated capes: frames stacked
                    MinecraftClient.getInstance().getTextureManager().registerTexture(tid, new NativeImageBackedTexture(() -> "cobra cape", img));
                    READY.put(key, tid);
                } catch (Exception ignored) {}
            });
        }, "cobra-cape");
        t.setDaemon(true);
        t.start();
        return null;
    }

    /** Does this player wear a Cobra cape right now (so the normal cape isn't drawn under it)? */
    public static boolean has(String uuid, Map<String, String> wear) {
        String h = wear.get("cape");
        return h != null && READY.containsKey(uuid + ":" + h);
    }
}

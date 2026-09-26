package dev.cobra.client.fabric;

import dev.cobra.client.core.Render;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public final class FabricRender implements Render {
    private static final Set<Identifier> FILTERED = new HashSet<>();
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private DrawContext ctx;

    public FabricRender with(DrawContext ctx) {
        this.ctx = ctx;
        return this;
    }

    private TextRenderer font() { return mc.textRenderer; }

    @Override public int width() { return mc.getWindow().getScaledWidth(); }
    @Override public int height() { return mc.getWindow().getScaledHeight(); }

    @Override
    public void rect(int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
        ctx.fill(x, y, x + w, y + h, argb);
    }

    @Override
    public void text(String s, float x, float y, int argb, boolean shadow) {
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x, y);
        ctx.drawText(font(), s, 0, 0, argb, shadow);
        ctx.getMatrices().popMatrix();
    }

    @Override public int textWidth(String s) { return font().getWidth(s); }
    @Override public int fontHeight() { return font().fontHeight; }

    @Override
    public void textObj(Object t, float x, float y, int argb, boolean shadow) {
        if (!(t instanceof Text text)) {
            text(String.valueOf(t), x, y, argb, shadow);
            return;
        }
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x, y);
        ctx.drawText(font(), text, 0, 0, argb, shadow);
        ctx.getMatrices().popMatrix();
    }

    @Override
    public int widthObj(Object t) {
        return t instanceof Text text ? font().getWidth(text) : font().getWidth(String.valueOf(t));
    }

    @Override public void push() { ctx.getMatrices().pushMatrix(); }
    @Override public void pop() { ctx.getMatrices().popMatrix(); }
    @Override public void translate(float x, float y) { ctx.getMatrices().translate(x, y); }
    @Override public void scale(float s) { ctx.getMatrices().scale(s, s); }

    @Override public void scissor(int x, int y, int w, int h) { ctx.enableScissor(x, y, x + w, y + h); }
    @Override public void noScissor() { ctx.disableScissor(); }

    @Override
    public void texture(String id, float x, float y, float w, float h, int argb) {
        boolean logo = id.equals("logo");
        Identifier tex = Identifier.of("cobra", logo ? "textures/gui/logo.png" : "textures/gui/" + id + ".png");
        int size = logo ? 128 : 48;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x, y);
        ctx.getMatrices().scale(w / size, h / size);
        ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, size, size, size, size, argb);
        ctx.getMatrices().popMatrix();
    }

    @Override
    public void item(Object stack, int x, int y) {
        if (!(stack instanceof ItemStack s) || s.isEmpty()) return;
        ctx.drawItem(s, x, y);
        ctx.drawStackOverlay(font(), s, x, y);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void effectIcon(Object handle, int x, int y) {
        if (!(handle instanceof RegistryEntry<?> entry)) return;
        // effect icons moved atlases in 1.21.9+; the HUD shows names and times only
    }

    private static final Identifier WHITE = Identifier.of("cobra", "textures/gui/white.png");

    @Override
    public void rectInverted(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        // vanilla's crosshair pipeline: blends ONE_MINUS_DST_COLOR / ONE_MINUS_SRC_COLOR
        ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.CROSSHAIR, WHITE, x, y, 0f, 0f, w, h, 1, 1);
    }

    // PNG crosshair (or any picture from disk): loaded once, reloaded when the file changes
    private static Identifier fileImgId;
    private static long fileImgStamp;
    private static int fileImgW, fileImgH;

    @Override
    public boolean image(java.io.File file, float x, float y, float w, float h, int color, boolean inverted) {
        if (file == null || !file.isFile()) return false;
        long stamp = file.lastModified() * 31 + file.length();
        if (fileImgId == null || stamp != fileImgStamp) {
            try (java.io.InputStream in = new java.io.FileInputStream(file)) {
                net.minecraft.client.texture.NativeImage img = net.minecraft.client.texture.NativeImage.read(in);
                Identifier id = Identifier.of("cobra", "file_image");
                if (fileImgId != null) mc.getTextureManager().destroyTexture(fileImgId);
                mc.getTextureManager().registerTexture(id, new net.minecraft.client.texture.NativeImageBackedTexture(() -> "cobra_file_image", img));
                fileImgId = id;
                fileImgW = img.getWidth();
                fileImgH = img.getHeight();
                fileImgStamp = stamp;
            } catch (Exception e) {
                fileImgStamp = stamp;   // don't retry a broken file every frame
                fileImgId = null;
                return false;
            }
        }
        if (fileImgId == null) return false;
        ctx.drawTexture(inverted ? net.minecraft.client.gl.RenderPipelines.CROSSHAIR : net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                fileImgId, Math.round(x), Math.round(y), 0f, 0f, Math.round(w), Math.round(h), fileImgW, fileImgH, fileImgW, fileImgH, color);
        return true;
    }

    // animated launcher wallpaper: newest decoded frame, uploaded into one reused texture
    private static net.minecraft.client.texture.NativeImageBackedTexture animTex;
    private static Identifier animId;
    private static int animW, animH, animSerial = -1;

    private boolean animatedWallpaper(int w, int h) {
        dev.cobra.client.core.ui.WallpaperFrames.Frame f = dev.cobra.client.core.ui.WallpaperFrames.get(mc.runDirectory);
        if (f == null) return false;
        try {
            if (animTex == null || animW != f.w || animH != f.h) {
                if (animId != null) mc.getTextureManager().destroyTexture(animId);
                animTex = new net.minecraft.client.texture.NativeImageBackedTexture("cobra_wallpaper_anim", f.w, f.h, false);
                animId = Identifier.of("cobra", "wallpaper_anim");
                mc.getTextureManager().registerTexture(animId, animTex);
                animW = f.w;
                animH = f.h;
                animSerial = -1;
            }
            if (f.serial != animSerial) {
                net.minecraft.client.texture.NativeImage img = animTex.getImage();
                if (img == null) return false;
                int[] px = f.argb;
                for (int y = 0; y < f.h; y++) {
                    int row = y * f.w;
                    for (int x = 0; x < f.w; x++) {
                        int c = px[row + x];
                        img.setColorArgb(x, y, c);
                    }
                }
                animTex.upload();
                animSerial = f.serial;
            }
        } catch (Throwable t) {
            return false;
        }
        float scale = Math.max(w / (float) animW, h / (float) animH);
        int rw = Math.round(w / scale), rh = Math.round(h / scale);
        ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, animId, 0, 0, (animW - rw) / 2f, (animH - rh) / 2f, w, h, rw, rh, animW, animH);
        return true;
    }

    private static Identifier wallpaperId;
    private static boolean wallpaperTried;
    private static int wallpaperW, wallpaperH;

    @Override
    public boolean wallpaper(int w, int h) {
        if (animatedWallpaper(w, h)) return true;
        if (!wallpaperTried) {
            wallpaperTried = true;
            Path f = mc.runDirectory.toPath().resolve("config").resolve("cobra").resolve("wallpaper.png");
            if (Files.isRegularFile(f)) {
                try (InputStream in = Files.newInputStream(f)) {
                    net.minecraft.client.texture.NativeImage img = net.minecraft.client.texture.NativeImage.read(in);
                    wallpaperW = img.getWidth();
                    wallpaperH = img.getHeight();
                    net.minecraft.client.texture.NativeImageBackedTexture tex = new net.minecraft.client.texture.NativeImageBackedTexture(() -> "cobra_wallpaper", img);
                    wallpaperId = Identifier.of("cobra", "wallpaper");
                    mc.getTextureManager().registerTexture(wallpaperId, tex);
                } catch (Exception e) {
                    wallpaperId = null;
                }
            }
        }
        if (wallpaperId == null) return false;
        float scale = Math.max(w / (float) wallpaperW, h / (float) wallpaperH);
        int rw = Math.round(w / scale), rh = Math.round(h / scale);
        ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, wallpaperId, 0, 0, (wallpaperW - rw) / 2f, (wallpaperH - rh) / 2f, w, h, rw, rh, wallpaperW, wallpaperH);
        return true;
    }

    @Override
    public void head(int x, int y, int size) {
        try {
            texture("icon/user", x, y, size, size, 0xFFFFFFFF);
        } catch (Throwable t) {
            texture("icon/user", x, y, size, size, 0xFFFFFFFF);
        }
    }
}

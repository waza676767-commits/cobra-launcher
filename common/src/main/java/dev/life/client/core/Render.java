package dev.life.client.core;

/** Version-neutral 2D drawing in GUI-scaled pixels. Implemented once per Minecraft version. */
public interface Render {
    int width();

    int height();

    void rect(int x, int y, int w, int h, int argb);

    void text(String s, float x, float y, int argb, boolean shadow);

    int textWidth(String s);

    int fontHeight();

    /** Draws a version-native text object (Text / formatted String). */
    void textObj(Object text, float x, float y, int argb, boolean shadow);

    int widthObj(Object text);

    void push();

    void pop();

    void translate(float x, float y);

    void scale(float s);

    /** Scissor in GUI coordinates, relative to the untransformed screen. */
    void scissor(int x, int y, int w, int h);

    void noScissor();

    /** Draws a mod texture: "logo" or "icon/<name>". */
    void texture(String id, float x, float y, float w, float h, int argb);

    void item(Object stack, int x, int y);

    void effectIcon(Object handle, int x, int y);

    /**
     * Draws the launcher's wallpaper (config/life/wallpaper.png) cover-scaled over w×h.
     * Returns false when there is none, so the caller falls back to the panorama.
     */
    boolean wallpaper(int w, int h);

    /** Current player's face, or a user icon. */
    void head(int x, int y, int size);

    /**
     * Rectangle that inverts what's behind it, like the vanilla crosshair (blend
     * ONE_MINUS_DST_COLOR / ONE_MINUS_SRC_COLOR). Versions that can't do it draw white.
     */
    /**
     * Draws a PNG from disk (reloaded when the file changes), tinted with {@code color};
     * {@code inverted} uses the vanilla crosshair blend. False if it can't be drawn.
     */
    default boolean image(java.io.File file, float x, float y, float w, float h, int color, boolean inverted) {
        return false;
    }

    default void rectInverted(int x, int y, int w, int h) {
        rect(x, y, w, h, 0xFFFFFFFF);
    }

    /** Draws a PNG picture (e.g. album art) w x h at (x, y); {@code key} identifies it so it's loaded once. */
    default void imagePng(String key, byte[] png, float x, float y, float w, float h) {}
}

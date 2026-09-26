package dev.cobra.client.core.ui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;

/**
 * Animated launcher wallpaper for the in-game menus. The launcher writes
 * config/cobra/wallpaper-frames.properties pointing at its folder of JPEG frames; a background
 * thread decodes them at the wallpaper's frame rate while a Cobra/vanilla menu is on screen
 * (it idles as soon as nothing asks for frames), and the platform uploads the newest one into
 * a texture. Only one decoded frame is kept in memory.
 */
public final class WallpaperFrames {
    public static final class Frame {
        public final int[] argb;
        public final int w, h;
        public final int serial;

        Frame(int[] argb, int w, int h, int serial) {
            this.argb = argb;
            this.w = w;
            this.h = h;
            this.serial = serial;
        }
    }

    private static volatile Frame current;
    private static volatile long wantedAt;
    private static Thread thread;
    private static boolean checked, available;
    private static File[] files;
    private static int fps = 24;

    private WallpaperFrames() {}

    /** Newest frame (or null if the wallpaper isn't animated). Call every frame while a menu shows it. */
    public static synchronized Frame get(File gameDir) {
        if (!checked) {
            checked = true;
            available = setup(gameDir);
        }
        if (!available) return null;
        wantedAt = System.currentTimeMillis();
        if (thread == null) {
            thread = new Thread(WallpaperFrames::run, "cobra-wallpaper-frames");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.start();
        }
        return current;
    }

    private static boolean setup(File gameDir) {
        File props = new File(new File(new File(gameDir, "config"), "cobra"), "wallpaper-frames.properties");
        if (!props.isFile()) return false;
        Properties p = new Properties();
        InputStream in = null;
        try {
            in = new FileInputStream(props);
            p.load(in);
        } catch (Exception e) {
            return false;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ignored) {}
        }
        File dir = new File(p.getProperty("dir", ""));
        File[] list = dir.listFiles();
        if (list == null) return false;
        int n = 0;
        for (File f : list) if (f.getName().endsWith(".jpg")) list[n++] = f;
        if (n == 0) return false;
        files = Arrays.copyOf(list, n);
        Arrays.sort(files);
        try {
            fps = Math.max(1, Math.min(60, Integer.parseInt(p.getProperty("fps", "24"))));
        } catch (NumberFormatException ignored) {}
        return true;
    }

    private static void run() {
        int i = 0, serial = 0;
        long frameMs = 1000L / fps;
        while (true) {
            try {
                if (System.currentTimeMillis() - wantedAt > 1500) {   // no menu showing it: idle
                    Thread.sleep(250);
                    continue;
                }
                long start = System.currentTimeMillis();
                BufferedImage img = javax.imageio.ImageIO.read(files[i]);
                if (img != null) {
                    int w = img.getWidth(), h = img.getHeight();
                    int[] px = img.getRGB(0, 0, w, h, null, 0, w);
                    for (int k = 0; k < px.length; k++) px[k] |= 0xFF000000;
                    current = new Frame(px, w, h, ++serial);
                }
                i = (i + 1) % files.length;
                long sleep = frameMs - (System.currentTimeMillis() - start);
                if (sleep > 0) Thread.sleep(sleep);
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                i = (i + 1) % files.length;
            }
        }
    }
}

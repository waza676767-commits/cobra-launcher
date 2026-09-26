package dev.cobra.launcher.ui;

import dev.cobra.launcher.core.Paths;
import dev.cobra.launcher.core.Settings;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Custom wallpaper behind the launcher: a still image, or an animated GIF / video.
 * Videos and GIFs are turned into JPEG frames with ffmpeg on import (24 fps, 1280 px wide,
 * first 30 s) and played back from disk, so memory stays small. The first frame (or the image)
 * is also handed to the game for the Cobra main menu.
 */
public final class Wallpaper {
    private static final Path DIR = Paths.ROOT.resolve("wallpaper");
    private static final Path STILL = DIR.resolve("still.png");
    private static final Path FRAMES = DIR.resolve("frames");
    private static final int FPS = 24;
    private static final String[] VIDEO = {"mp4", "webm", "mkv", "mov", "avi", "m4v", "gif"};

    private static volatile BufferedImage still, frame;
    private static volatile List<Path> frames = List.of();
    private static volatile int version;
    private static Thread player;
    private static Component target;

    private Wallpaper() {}

    public static int version() { return version; }

    /** Forces the glass frost to rebuild (after a dim change). */
    public static void touch() { version++; }

    public static boolean animated() {
        return "video".equals(Settings.get().wallpaperType) && !frames.isEmpty();
    }

    public static boolean active() {
        return still != null && !Settings.get().wallpaperType.isEmpty();
    }

    public static void attach(Component root) {
        target = root;
        load();
    }

    /** Reads the stored wallpaper at start-up. */
    public static synchronized void load() {
        stop();
        still = null;
        frame = null;
        frames = List.of();
        String type = Settings.get().wallpaperType;
        if (type.isEmpty() || !Files.exists(STILL)) return;
        try {
            still = ImageIO.read(STILL.toFile());
        } catch (IOException e) {
            return;
        }
        if (type.equals("video")) {
            try (Stream<Path> s = Files.list(FRAMES)) {
                frames = s.filter(p -> p.toString().endsWith(".jpg")).sorted(Comparator.naturalOrder()).toList();
            } catch (IOException ignored) {}
            if (!frames.isEmpty()) play();
        }
        version++;
    }

    public static boolean isVideo(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String v : VIDEO) if (n.endsWith("." + v)) return true;
        return false;
    }

    /** Imports an image, GIF or video. Runs ffmpeg for animated files; call off the UI thread. */
    public static void importFile(Path src, Consumer<String> status) throws IOException {
        Files.createDirectories(DIR);
        stop();
        String name = src.getFileName().toString();
        if (isVideo(src) && !name.toLowerCase(Locale.ROOT).endsWith(".gif")) {
            dev.cobra.launcher.core.FFmpeg.ensure(status);   // downloads it the first time
        }
        if (isVideo(src)) {
            status.accept("Converting " + name + " (this can take a moment)");
            deleteFrames();
            Files.createDirectories(FRAMES);
            String cover = "scale=" + MainWindow.W + ":" + MainWindow.H + ":force_original_aspect_ratio=increase,crop=" + MainWindow.W + ":" + MainWindow.H;
            if (hasFfmpeg()) {
                run(dev.cobra.launcher.core.FFmpeg.command(), "-y", "-loglevel", "error", "-i", src.toString(), "-t", "30",
                        "-vf", "fps=" + FPS + "," + cover, "-q:v", "4", FRAMES.resolve("%05d.jpg").toString());
            } else if (name.toLowerCase(Locale.ROOT).endsWith(".gif")) {
                GifFrames.explode(src, FRAMES, FPS, MainWindow.W, MainWindow.H);   // no ffmpeg needed for GIFs
            } else {
                throw new IOException("Video wallpapers need ffmpeg: sudo dnf install ffmpeg (GIFs work without it)");
            }
            Path first;
            try (Stream<Path> s = Files.list(FRAMES)) {
                first = s.filter(p -> p.toString().endsWith(".jpg")).sorted().findFirst().orElse(null);
            }
            if (first == null) throw new IOException("ffmpeg produced no frames from " + name);
            ImageIO.write(ImageIO.read(first.toFile()), "png", STILL.toFile());
            Settings.get().wallpaperType = "video";
        } else {
            BufferedImage img = ImageIO.read(src.toFile());
            if (img == null && hasFfmpeg()) {       // e.g. WebP / AVIF / HEIC: let ffmpeg convert it
                run(dev.cobra.launcher.core.FFmpeg.command(), "-y", "-loglevel", "error", "-i", src.toString(), "-frames:v", "1", STILL.toString());
                img = ImageIO.read(STILL.toFile());
            }
            if (img == null) throw new IOException("Can't read " + name + ". Use PNG, JPG, GIF, BMP or a video.");
            ImageIO.write(fit(img, 1920), "png", STILL.toFile());
            deleteFrames();
            Settings.get().wallpaperType = "image";
        }
        Settings.get().wallpaperName = name;
        Settings.get().save();
        load();
    }

    public static synchronized void clear() {
        stop();
        still = null;
        frame = null;
        frames = List.of();
        Settings.get().wallpaperType = "";
        Settings.get().wallpaperName = "";
        Settings.get().save();
        try {
            deleteFrames();
            Files.deleteIfExists(STILL);
        } catch (IOException ignored) {}
        version++;
    }

    public static boolean hasFfmpeg() {
        return dev.cobra.launcher.core.FFmpeg.available();
    }

    private static void run(String... cmd) throws IOException {
        if (!hasFfmpeg()) throw new IOException("ffmpeg isn't set up yet. Choose the video again to download it.");
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (p.waitFor() != 0) {
                String last = out.isEmpty() ? "unknown error" : out.lines().reduce((a, b) -> b).orElse(out);
                throw new IOException("ffmpeg failed: " + last);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted");
        }
    }

    // ------------------------------------------------------------ playback

    private static synchronized void play() {
        stop();
        List<Path> list = frames;
        player = new Thread(() -> {
            int i = 0;
            long frameNs = 1_000_000_000L / FPS;
            long next = System.nanoTime();
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    MainWindow mw = MainWindow.get();
                    // nothing to animate while minimised, unfocused behind the game, or while Minecraft runs:
                    // the launcher must not take CPU away from the game
                    boolean visible = target != null && target.isShowing() && mw != null
                            && mw.frame.getState() != Frame.ICONIFIED
                            && !mw.running()
                            && !Settings.get().superOptimization;   // Super optimization: still wallpaper
                    if (!visible || !Settings.get().animations) {
                        Thread.sleep(400);
                        next = System.nanoTime();
                        continue;
                    }
                    BufferedImage img = ImageIO.read(list.get(i).toFile());
                    if (img != null && target != null) {
                        int w = target.getWidth(), h = target.getHeight();
                        BufferedImage c = compose(img, w, h);
                        synchronized (Wallpaper.class) {
                            composed = c;
                            composedFrom = img;
                            composedW = w;
                            composedH = h;
                            composedDim = Settings.get().wallpaperDim;
                            composedLight = Theme.isLight();
                        }
                        frame = img;
                        Glass.onFrame(c);            // glass follows the frame, blurred on this thread
                        if (target != null) target.repaint();
                    }
                    i = (i + 1) % list.size();
                    next += frameNs;
                    long sleep = next - System.nanoTime();
                    if (sleep > 0) Thread.sleep(sleep / 1_000_000L, (int) (sleep % 1_000_000L));
                    else next = System.nanoTime();
                } catch (InterruptedException e) {
                    return;
                } catch (Exception ignored) {
                    i = (i + 1) % Math.max(1, list.size());
                }
            }
        }, "cobra-wallpaper");
        player.setDaemon(true);
        player.setPriority(Thread.MIN_PRIORITY);
        player.start();
    }

    private static synchronized void stop() {
        if (player != null) player.interrupt();
        player = null;
    }

    // ------------------------------------------------------------ drawing

    public static BufferedImage current() {
        BufferedImage f = frame;
        return f != null ? f : still;
    }

    private static BufferedImage composed, composedFrom;
    private static String composedGrad = "";
    private static int composedW, composedH, composedDim = -1;
    private static boolean composedLight;

    /** Cover-scaled, dimmed frame, cached until the frame/size/dim/theme changes. */
    public static void paint(Graphics2D g0, int w, int h) {
        BufferedImage img = current();
        if (img == null) return;
        int dim = Settings.get().wallpaperDim;
        boolean light = Theme.isLight();
        BufferedImage c = composed;
        String grad = Theme.gradientKey() + "|" + Settings.get().wallpaperTint;
        if (c == null || composedFrom != img || composedW != w || composedH != h || composedDim != dim || composedLight != light
                || !grad.equals(composedGrad)) {
            composedGrad = grad;
            c = compose(img, w, h);
            composed = c;
            composedFrom = img;
            composedW = w;
            composedH = h;
            composedDim = dim;
            composedLight = light;
        }
        g0.drawImage(c, 0, 0, null);
    }

    private static BufferedImage compose(BufferedImage img, int w, int h) {
        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        BufferedImage out = gc.createCompatibleImage(Math.max(1, w), Math.max(1, h), Transparency.OPAQUE);
        Graphics2D g = out.createGraphics();
        drawCover(g, img, w, h);
        g.dispose();
        return out;
    }

    private static void drawCover(Graphics2D g0, BufferedImage img, int w, int h) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        double scale = Math.max(w / (double) img.getWidth(), h / (double) img.getHeight());
        int dw = (int) Math.ceil(img.getWidth() * scale), dh = (int) Math.ceil(img.getHeight() * scale);
        g.drawImage(img, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
        int dim = Math.max(0, Math.min(90, Settings.get().wallpaperDim));
        g.setColor(Theme.isLight() ? new Color(255, 255, 255, dim * 255 / 100) : new Color(0, 0, 0, dim * 255 / 100));
        g.fillRect(0, 0, w, h);
        if (Settings.get().gradient && Settings.get().wallpaperTint) {
            g.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, Settings.get().gradientStrength / 100f))));
            Theme.paintGradient(g, w, h);
        }
        g.dispose();
    }

    /**
     * Writes the wallpaper still for the in-game menus, or removes it. For a video / GIF wallpaper
     * it also writes wallpaper-frames.properties pointing at the launcher's frame folder, so
     * Cobra Client plays the animation on its main menu and on the Singleplayer / Multiplayer
     * screens (frames are streamed from disk, nothing is copied).
     */
    public static void exportForGame(Path gameDir) {
        Path dir = gameDir.resolve("config").resolve("cobra");
        Path out = dir.resolve("wallpaper.png");
        Path anim = dir.resolve("wallpaper-frames.properties");
        try {
            if (!active()) {
                Files.deleteIfExists(out);
                Files.deleteIfExists(anim);
                return;
            }
            Files.createDirectories(dir);
            if (!Files.exists(out) || Files.getLastModifiedTime(out).compareTo(Files.getLastModifiedTime(STILL)) < 0) {
                Files.copy(STILL, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            if (animated()) {
                java.util.Properties p = new java.util.Properties();
                p.setProperty("dir", FRAMES.toAbsolutePath().toString());
                p.setProperty("fps", String.valueOf(FPS));
                p.setProperty("count", String.valueOf(frames.size()));
                p.setProperty("dim", String.valueOf(Settings.get().wallpaperDim));
                try (java.io.OutputStream o = Files.newOutputStream(anim)) {
                    p.store(o, "Cobra Launcher animated wallpaper");
                }
            } else {
                Files.deleteIfExists(anim);
            }
        } catch (IOException ignored) {}
    }

    private static BufferedImage fit(BufferedImage src, int maxW) {
        if (src.getWidth() <= maxW) return toRgb(src);
        int h = (int) Math.round(src.getHeight() * (maxW / (double) src.getWidth()));
        BufferedImage out = new BufferedImage(maxW, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, maxW, h, null);
        g.dispose();
        return out;
    }

    private static BufferedImage toRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) return src;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    private static void deleteFrames() throws IOException {
        if (!Files.exists(FRAMES)) return;
        List<Path> all = new ArrayList<>();
        try (Stream<Path> s = Files.walk(FRAMES)) {
            s.sorted(Comparator.reverseOrder()).forEach(all::add);
        }
        for (Path p : all) Files.deleteIfExists(p);
    }
}

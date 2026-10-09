package dev.life.client.core;

import java.io.File;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Records the game into an MP4 with ffmpeg. The platform hands over finished frames (read back
 * from the GPU asynchronously, so the game doesn't wait); a writer thread feeds ffmpeg at a
 * steady 30/60/120 fps: the newest frame each tick (repeated when the game is slower), nothing
 * while paused, so pauses are cut out of the video.
 */
public final class ScreenRecorder {
    public enum State { IDLE, RECORDING, PAUSED }

    private volatile State state = State.IDLE;
    private Process ffmpeg;
    private OutputStream out;
    private Thread writer;
    private volatile int[] latest;
    private volatile int frameW, frameH;
    private int w, h, fps;
    private long startedAt, pausedAt, pausedTotal;
    private File file;
    private volatile long generation;
    private File logFile;
    private volatile String error;
    /** Newest frame number taken so far: older frames that arrive late are dropped (no jitter). */
    private long lastSeq = Long.MIN_VALUE;
    /** "Auto", "Graphics card" or "CPU (sharpest)" (the Recorder's Encoder setting). */
    public volatile String encoderPref = "Auto";

    public State state() { return state; }

    public File file() { return file; }

    public String error() { return error; }

    /** The last error once (then cleared), or null. */
    public String takeError() {
        String e = error;
        error = null;
        return e;
    }

    /** Seconds recorded so far (pauses not counted). */
    public long seconds() {
        if (state == State.IDLE) return 0;
        long now = state == State.PAUSED ? pausedAt : System.currentTimeMillis();
        return Math.max(0, (now - startedAt - pausedTotal) / 1000);
    }

    /** Frame size to ask the platform for, or 0 when not recording. */
    public boolean wantsFrames() { return state == State.RECORDING; }

    /** Starts once the first frame arrives (its size decides the video size). */
    public synchronized void start(int fps) {
        if (state != State.IDLE) return;
        this.fps = Math.max(1, Math.min(120, fps));
        generation++;
        latest = null;
        file = null;
        error = null;
        lastSeq = Long.MIN_VALUE;
        startedAt = System.currentTimeMillis();
        pausedTotal = 0;
        state = State.RECORDING;
    }

    public synchronized void togglePause() {
        if (state == State.RECORDING) {
            state = State.PAUSED;
            pausedAt = System.currentTimeMillis();
        } else if (state == State.PAUSED) {
            pausedTotal += System.currentTimeMillis() - pausedAt;
            state = State.RECORDING;
        }
    }

    /** From the platform, on any thread: a finished frame (ARGB ints, top row first). */
    public void offer(int[] argb, int width, int height, String ffmpegCmd, File dir, String scale, String quality) {
        offer(argb, width, height, ffmpegCmd, dir, scale, quality, Long.MIN_VALUE + 1);
    }

    /**
     * A finished frame with its number ({@code seq}, counting up in the order the frames were
     * drawn). Frames are copied on two threads, so one can overtake another: a frame older than
     * the newest one already here is thrown away instead of jumping back in time in the video.
     */
    public synchronized void offer(int[] argb, int width, int height, String ffmpegCmd, File dir, String scale, String quality, long seq) {
        if (state != State.RECORDING || argb == null || width <= 0 || height <= 0 || argb.length != (long) width * height) return;
        if (seq != Long.MIN_VALUE + 1) {
            if (seq <= lastSeq) return;
            lastSeq = seq;
        }
        if (ffmpeg == null) {
            try {
                open(width, height, ffmpegCmd, dir, scale, quality);
            } catch (Exception e) {
                error = "Couldn't start ffmpeg: " + e.getMessage();
                state = State.IDLE;
                return;
            }
        }
        if (width != w || height != h) argb = resize(argb, width, height, w, h);   // window resized: keep the video's size
        frameW = width;
        frameH = height;
        latest = argb;
    }

    /** Smooth (bilinear) resize, so a window that changes size mid-recording stays sharp-ish, not blocky. */
    static int[] resize(int[] src, int sw, int sh, int dw, int dh) {
        int[] out = new int[dw * dh];
        float fx = sw / (float) dw, fy = sh / (float) dh;
        for (int y = 0; y < dh; y++) {
            float sy = Math.max(0, (y + 0.5f) * fy - 0.5f);
            int y0 = Math.min(sh - 1, (int) sy), y1 = Math.min(sh - 1, y0 + 1);
            float ty = sy - y0;
            for (int x = 0; x < dw; x++) {
                float sx = Math.max(0, (x + 0.5f) * fx - 0.5f);
                int x0 = Math.min(sw - 1, (int) sx), x1 = Math.min(sw - 1, x0 + 1);
                float tx = sx - x0;
                int a = src[y0 * sw + x0], b = src[y0 * sw + x1], c = src[y1 * sw + x0], d = src[y1 * sw + x1];
                int px = 0xFF000000;
                for (int sh2 = 0; sh2 <= 16; sh2 += 8) {
                    float top = (a >> sh2 & 255) * (1 - tx) + (b >> sh2 & 255) * tx;
                    float bot = (c >> sh2 & 255) * (1 - tx) + (d >> sh2 & 255) * tx;
                    px |= Math.round(top * (1 - ty) + bot * ty) << sh2;
                }
                out[y * dw + x] = px;
            }
        }
        return out;
    }

    private static void add(List<String> cmd, String... parts) {
        for (String p : parts) cmd.add(p);
    }

    private static String encoderFor, encoderCmd, encoderPrefFor;

    /** What the last recording used (size, fps, encoder): shown when it's saved. */
    public static volatile String lastInfo = "";

    /** Picks the encoder for the "Auto" setting (graphics card first: it records almost for free). */
    static synchronized String pickEncoder(String ffmpeg) {
        return pickEncoder(ffmpeg, "Auto");
    }

    /**
     * The best encoder that actually works here, for the Encoder setting:
     * <ul>
     *   <li>Auto / Graphics card: the card's own (NVIDIA, AMD, Intel, VA-API), then libx264.</li>
     *   <li>CPU (sharpest): libx264 first (best picture for its size, costs a few CPU cores).</li>
     * </ul>
     * then SVT-AV1, OpenH264 and mpeg4 as the always-there fallback. Each one is tried on a tiny
     * test clip once (with the real quality options), so a listed-but-broken encoder is skipped.
     */
    static synchronized String pickEncoder(String ffmpeg, String pref) {
        String p = pref == null ? "Auto" : pref;
        if (ffmpeg.equals(encoderCmd) && p.equals(encoderPrefFor) && encoderFor != null) return encoderFor;
        String[] gpu = {"h264_nvenc", "h264_amf", "h264_qsv", "h264_vaapi"};
        String[] rest = {"libsvtav1", "libopenh264", "mpeg4"};
        List<String> order = new ArrayList<String>();
        if (p.startsWith("CPU")) order.add("libx264");
        for (String g : gpu) order.add(g);
        if (!p.startsWith("CPU")) order.add("libx264");
        for (String r : rest) order.add(r);
        String chosen = "mpeg4";
        for (String enc : order) {
            if (enc.equals("mpeg4") || works(ffmpeg, enc)) {
                chosen = enc;
                break;
            }
        }
        encoderCmd = ffmpeg;
        encoderPrefFor = p;
        encoderFor = chosen;
        return chosen;
    }

    /** Encoders whose full quality options failed the test: they get the plain options instead. */
    private static final java.util.Set<String> PLAIN = new java.util.HashSet<String>();

    /** CRF-like quality number for each Quality choice (lower = better). */
    static int qualityNumber(String quality) {
        return quality.equals("Small file") ? 27 : quality.equals("Balanced") ? 22 : quality.equals("Ultra") ? 15 : 18;
    }

    /**
     * Quality options for an encoder. Notes: NVENC's "constant quality" only works with -b:v 0
     * (otherwise ffmpeg's tiny default bitrate caps it and the video turns blocky); the GPU
     * encoders get their high-quality presets; x264 gets adaptive quantisation that keeps dark
     * caves and the sky free of banding.
     */
    static List<String> encoderArgs(String enc, int q, int fps, boolean plain) {
        List<String> a = new ArrayList<String>();
        int gop = Math.max(30, fps * 2);
        if (enc.equals("libx264")) {
            add(a, "-preset", fps >= 120 ? "superfast" : fps >= 60 ? "veryfast" : "faster", "-crf", String.valueOf(q));
            if (!plain) add(a, "-profile:v", "high", "-g", String.valueOf(gop), "-x264-params", "aq-mode=3:aq-strength=0.9");
        } else if (enc.endsWith("_nvenc")) {
            if (plain) add(a, "-rc", "vbr", "-cq", String.valueOf(q), "-b:v", "0", "-maxrate", "100M", "-bufsize", "200M");
            else add(a, "-preset", "p5", "-tune", "hq", "-rc", "vbr", "-cq", String.valueOf(q), "-b:v", "0",
                    "-maxrate", "120M", "-bufsize", "240M", "-spatial-aq", "1", "-aq-strength", "8", "-profile:v", "high", "-g", String.valueOf(gop));
        } else if (enc.equals("h264_vaapi")) {
            add(a, "-rc_mode", "CQP", "-qp", String.valueOf(Math.max(1, q - 1)));
            if (!plain) add(a, "-profile:v", "high", "-g", String.valueOf(gop));
        } else if (enc.endsWith("_qsv")) {
            add(a, "-global_quality", String.valueOf(q));
            if (!plain) add(a, "-preset", "medium", "-profile:v", "high", "-g", String.valueOf(gop));
        } else if (enc.endsWith("_amf")) {
            add(a, "-rc", "cqp", "-qp_i", String.valueOf(q - 1), "-qp_p", String.valueOf(q + 1));
            if (!plain) add(a, "-quality", "quality", "-profile:v", "high", "-g", String.valueOf(gop));
        } else if (enc.equals("libsvtav1")) {
            add(a, "-preset", fps >= 60 ? "10" : "8", "-crf", String.valueOf(q + 10));
        } else if (enc.equals("libopenh264")) {
            add(a, "-b:v", q <= 15 ? "50M" : q <= 18 ? "32M" : q <= 22 ? "18M" : "10M", "-maxrate", q <= 18 ? "60M" : "30M");
        } else {                                   // mpeg4: works with every ffmpeg, still an .mp4
            add(a, "-q:v", q <= 15 ? "1" : q <= 18 ? "2" : q <= 22 ? "3" : "5");
        }
        return a;
    }

    /** Correct colours: BT.709 (what every player assumes for HD video), sharp colour edges. */
    static final String COLOUR = "out_color_matrix=bt709:out_range=tv:flags=lanczos+accurate_rnd+full_chroma_int";

    static void colourTags(List<String> cmd) {
        add(cmd, "-colorspace", "bt709", "-color_primaries", "bt709", "-color_trc", "bt709", "-color_range", "tv");
    }

    /** Linux GPU encoder (AMD / Intel through VA-API): the render node, or null when there's none. */
    static String vaapiDevice() {
        File dri = new File("/dev/dri");
        String[] n = dri.list();
        if (n == null) return null;
        java.util.Arrays.sort(n);
        for (String f : n) if (f.startsWith("renderD")) return "/dev/dri/" + f;
        return null;
    }

    private static boolean works(String ffmpeg, String enc) {
        if (test(ffmpeg, enc, false)) {
            PLAIN.remove(enc);
            return true;
        }
        if (test(ffmpeg, enc, true)) {                // an older ffmpeg / card: without the extras
            PLAIN.add(enc);
            return true;
        }
        return false;
    }

    private static boolean test(String ffmpeg, String enc, boolean plain) {
        try {
            List<String> test = new ArrayList<String>();
            add(test, ffmpeg, "-hide_banner", "-loglevel", "error");
            if (enc.equals("h264_vaapi")) {
                String dev = vaapiDevice();
                if (dev == null) return false;
                add(test, "-vaapi_device", dev);
            }
            add(test, "-f", "lavfi", "-i", "color=c=black:s=256x144:r=30", "-frames:v", "5");
            if (enc.equals("h264_vaapi")) add(test, "-vf", "format=nv12,hwupload");
            add(test, "-c:v", enc);
            test.addAll(encoderArgs(enc, 18, 30, plain));
            if (!enc.equals("h264_vaapi")) add(test, "-pix_fmt", "yuv420p");
            add(test, "-f", "null", "-");
            Process p = new ProcessBuilder(test).redirectErrorStream(true).start();
            java.io.InputStream in = p.getInputStream();
            byte[] buf = new byte[4096];
            while (in.read(buf) > 0) { /* drain */ }
            if (!p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroy();
                return false;
            }
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void open(int width, int height, String ffmpegCmd, File dir, String scale, String quality) throws Exception {
        w = width;
        h = height;
        if (!dir.isDirectory() && !dir.mkdirs()) throw new Exception("can't make " + dir);
        file = new File(dir, "Life " + new SimpleDateFormat("yyyy-MM-dd HH-mm-ss-SSS").format(new Date()) + ".mp4");
        int q = qualityNumber(quality);
        List<String> cmd = new ArrayList<String>();
        String encoder = pickEncoder(ffmpegCmd, encoderPref);
        boolean vaapi = encoder.equals("h264_vaapi");
        int target = scale.equals("1440p") ? 1440 : scale.equals("1080p") ? 1080 : scale.equals("720p") ? 720 : 0;
        boolean down = target > 0 && h > target;
        lastInfo = w + "x" + h + " \u2192 " + (down ? target : h) + "p, " + fps + " fps, " + encoder + ", " + quality.toLowerCase(java.util.Locale.ROOT);
        add(cmd, ffmpegCmd, "-y", "-loglevel", "error");
        if (vaapi) add(cmd, "-vaapi_device", vaapiDevice());
        add(cmd, "-f", "rawvideo", "-pix_fmt", "bgra", "-s", w + "x" + h, "-r", String.valueOf(fps), "-i", "-");
        // never upscale (a 720p window stays 720p, sharp); scale down with lanczos. H.264 needs
        // even sizes. Then the colour conversion done properly (BT.709, full chroma precision).
        String vf = down ? "scale=-2:" + target + ":" + COLOUR
                : "pad=ceil(iw/2)*2:ceil(ih/2)*2:0:0:black,scale=iw:ih:" + COLOUR;
        vf += vaapi ? ",format=nv12,hwupload" : ",format=yuv420p";
        add(cmd, "-vf", vf, "-c:v", encoder);
        cmd.addAll(encoderArgs(encoder, q, fps, PLAIN.contains(encoder)));
        if (!vaapi) add(cmd, "-pix_fmt", "yuv420p");       // VA-API frames are already nv12 on the GPU
        colourTags(cmd);
        add(cmd, "-movflags", "+faststart", file.getAbsolutePath());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        // ffmpeg's log goes to the launcher's logs (not between your videos); kept only if it failed
        String root = System.getProperty("life.root");
        File logDir = root != null && !root.isEmpty() ? new File(new File(root, "logs"), "recorder") : new File(dir, ".logs");
        if (!logDir.isDirectory()) logDir.mkdirs();
        logFile = new File(logDir, file.getName().replace(".mp4", "") + ".ffmpeg.log");
        pb.redirectOutput(logFile);
        ffmpeg = pb.start();
        out = ffmpeg.getOutputStream();
        final long session = generation;
        final OutputStream sink = out;
        final File log = logFile;
        final int frameWidth = w, frameHeight = h, rate = fps;
        writer = new Thread(() -> writeLoop(session, sink, log, frameWidth, frameHeight, rate), "life-recorder");
        writer.setDaemon(true);
        writer.start();
    }

    private void writeLoop(long session, OutputStream sink, File log, int w, int h, int fps) {
        long frameNs = 1_000_000_000L / fps;
        long next = System.nanoTime();
        ByteBuffer buf = ByteBuffer.allocate(w * h * 4).order(ByteOrder.LITTLE_ENDIAN);   // int ARGB → bytes B,G,R,A
        try {
            while (generation == session && state != State.IDLE) {
                long now = System.nanoTime();
                if (now < next) {
                    long ms = (next - now) / 1_000_000L;
                    Thread.sleep(ms, (int) ((next - now) % 1_000_000L));
                    continue;
                }
                next += frameNs;
                if (now - next > frameNs * 10) next = now;   // fell far behind: don't burst
                if (state != State.RECORDING) continue;      // paused: no frames at all
                int[] px = latest;
                if (px == null || px.length != w * h) continue;
                buf.clear();
                buf.asIntBuffer().put(px);
                sink.write(buf.array(), 0, w * h * 4);
            }
        } catch (Exception e) {
            synchronized (this) {
                if (generation == session && state != State.IDLE) {
                    String detail = e.getMessage();
                    try {
                        String diagnostic = new String(java.nio.file.Files.readAllBytes(log.toPath()), java.nio.charset.StandardCharsets.UTF_8).trim();
                        if (!diagnostic.isEmpty()) detail = diagnostic.substring(Math.max(0, diagnostic.length() - 350));
                    } catch (Exception ignored) {}
                    error = "Recording stopped: " + detail + " (log: " + log.getName() + ")";
                    stop();
                }
            }
        } finally {
            try {
                sink.close();                   // end of input: ffmpeg finishes the MP4
            } catch (Exception ignored) {}
        }
    }

    /** Stops and finishes the file (ffmpeg writes the end of the MP4 in the background). */
    public synchronized File stop() {
        if (state == State.IDLE) return null;
        state = State.IDLE;
        generation++;
        latest = null;
        File f = file;
        final Process p = ffmpeg;
        final Thread wr = writer;
        final File log = logFile;
        ffmpeg = null;
        writer = null;
        out = null;
        new Thread(new Runnable() {
            public void run() {
                try {
                    if (wr != null) wr.join(5000);
                    if (p != null && !p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)) p.destroy();
                    else if (p != null && p.exitValue() == 0 && log != null) log.delete();   // all good: no log to keep
                } catch (InterruptedException ignored) {}
            }
        }, "life-recorder-finish").start();
        return f;
    }
}

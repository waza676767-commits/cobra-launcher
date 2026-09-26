package dev.cobra.client.core;

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
    public synchronized void offer(int[] argb, int width, int height, String ffmpegCmd, File dir, String scale, String quality) {
        if (state != State.RECORDING || argb == null || width <= 0 || height <= 0 || argb.length != (long) width * height) return;
        if (ffmpeg == null) {
            try {
                open(width, height, ffmpegCmd, dir, scale, quality);
            } catch (Exception e) {
                error = "Couldn't start ffmpeg: " + e.getMessage();
                state = State.IDLE;
                return;
            }
        }
        if (width != w || height != h) {
            // Keep the encoder's input geometry fixed across window/fullscreen changes.
            int[] resized = new int[w * h];
            for (int y = 0; y < h; y++) {
                int srcRow = (int) ((long) y * height / h) * width;
                for (int x = 0; x < w; x++) resized[y * w + x] = argb[srcRow + (int) ((long) x * width / w)];
            }
            argb = resized;
        }
        frameW = width;
        frameH = height;
        latest = argb;
    }

    private static void add(List<String> cmd, String... parts) {
        for (String p : parts) cmd.add(p);
    }

    private static String encoderFor, encoderCmd;

    /**
     * The best H.264 encoder that actually works here: libx264, then the graphics card's own
     * (NVIDIA / Intel / AMD), then OpenH264, and mpeg4 as the always-there fallback. Each one is
     * tried on a tiny test clip once, so a listed-but-broken GPU encoder is skipped.
     */
    static synchronized String pickEncoder(String ffmpeg) {
        if (ffmpeg.equals(encoderCmd) && encoderFor != null) return encoderFor;
        String[] order = {"libx264", "h264_nvenc", "h264_qsv", "h264_amf", "libopenh264", "mpeg4"};
        String chosen = "mpeg4";
        for (String enc : order) {
            if (enc.equals("mpeg4") || works(ffmpeg, enc)) {
                chosen = enc;
                break;
            }
        }
        encoderCmd = ffmpeg;
        encoderFor = chosen;
        return chosen;
    }

    private static boolean works(String ffmpeg, String enc) {
        try {
            Process p = new ProcessBuilder(ffmpeg, "-hide_banner", "-loglevel", "error", "-f", "lavfi", "-i",
                    "color=c=black:s=256x144:r=30", "-frames:v", "5", "-c:v", enc, "-pix_fmt", "yuv420p", "-f", "null", "-")
                    .redirectErrorStream(true).start();
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
        file = new File(dir, "Cobra " + new SimpleDateFormat("yyyy-MM-dd HH-mm-ss-SSS").format(new Date()) + ".mp4");
        String crf = quality.equals("Small file") ? "28" : quality.equals("Balanced") ? "23" : "18";
        String preset = fps >= 120 ? "ultrafast" : fps >= 60 ? "superfast" : "veryfast";
        List<String> cmd = new ArrayList<String>();
        String[] base = {ffmpegCmd, "-y", "-loglevel", "error",
                "-f", "rawvideo", "-pix_fmt", "bgra", "-s", w + "x" + h, "-r", String.valueOf(fps), "-i", "-"};
        for (String s : base) cmd.add(s);
        // H.264 yuv420p requires even output dimensions, including native/window mode.
        cmd.add("-vf");
        cmd.add(scale.equals("1080p") ? "scale=-2:1080:flags=bicubic"
                : "pad=ceil(iw/2)*2:ceil(ih/2)*2:0:0:black");
        // H.264 MP4 with whatever encoder this ffmpeg has (Fedora's ffmpeg has no libx264, for example)
        String encoder = pickEncoder(ffmpegCmd);
        cmd.add("-c:v");
        cmd.add(encoder);
        int q = Integer.parseInt(crf);
        if (encoder.equals("libx264")) {
            add(cmd, "-preset", preset, "-crf", crf);
        } else if (encoder.endsWith("_nvenc")) {
            add(cmd, "-preset", "p2", "-rc", "vbr", "-cq", String.valueOf(q + 2));
        } else if (encoder.endsWith("_qsv")) {
            add(cmd, "-preset", "veryfast", "-global_quality", String.valueOf(q + 4));
        } else if (encoder.endsWith("_amf")) {
            add(cmd, "-quality", "speed", "-rc", "cqp", "-qp_i", String.valueOf(q + 2), "-qp_p", String.valueOf(q + 4));
        } else if (encoder.equals("libopenh264")) {
            add(cmd, "-b:v", q <= 18 ? "16M" : q <= 23 ? "10M" : "6M", "-allow_skip_frames", "1");
        } else {                                   // mpeg4: works with every ffmpeg, still an .mp4
            add(cmd, "-q:v", q <= 18 ? "2" : q <= 23 ? "4" : "6");
        }
        add(cmd, "-pix_fmt", "yuv420p", "-movflags", "+faststart", file.getAbsolutePath());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        logFile = new File(dir, file.getName() + ".ffmpeg.log");
        pb.redirectOutput(logFile);
        ffmpeg = pb.start();
        out = ffmpeg.getOutputStream();
        final long session = generation;
        final OutputStream sink = out;
        final File log = logFile;
        final int frameWidth = w, frameHeight = h, rate = fps;
        writer = new Thread(() -> writeLoop(session, sink, log, frameWidth, frameHeight, rate), "cobra-recorder");
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
        ffmpeg = null;
        writer = null;
        out = null;
        new Thread(new Runnable() {
            public void run() {
                try {
                    if (wr != null) wr.join(5000);
                    if (p != null && !p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)) p.destroy();
                } catch (InterruptedException ignored) {}
            }
        }, "cobra-recorder-finish").start();
        return f;
    }
}

package dev.cobra.client.core;

import java.io.File;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Recording without touching the game: the screen is captured and encoded entirely by the graphics
 * card in a separate program, so the game keeps its FPS.
 * <ul>
 *   <li>Linux: gpu-screen-recorder (records the focused window on X11, or your screen through the
 *   desktop's screen-share dialog on Wayland; with game audio).</li>
 *   <li>Windows: ffmpeg with Desktop Duplication (ddagrab) and the card's encoder.</li>
 * </ul>
 * Pausing on Windows closes the part and starts a new file on resume.
 */
public final class ExternalCapture {
    private static Process proc;
    private static File file;
    private static boolean paused;
    private static long pid = -1;
    private static Boolean gsr;

    private ExternalCapture() {}

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** Can we record this way on this PC? */
    public static boolean available(String ffmpeg) {
        if (windows()) return ffmpeg != null && !ffmpeg.isEmpty();
        if (gsr == null) {
            try {
                Process p = new ProcessBuilder("gpu-screen-recorder", "--help").redirectErrorStream(true).start();
                java.io.InputStream in = p.getInputStream();
                byte[] b = new byte[4096];
                while (in.read(b) > 0) { /* drain */ }
                p.waitFor();
                gsr = true;
            } catch (Exception e) {
                gsr = false;
            }
        }
        return gsr;
    }

    public static boolean running() {
        return proc != null;
    }

    public static boolean paused() {
        return paused;
    }

    public static File start(String ffmpeg, File dir, int fps, String quality) throws Exception {
        if (proc != null) return file;
        if (!dir.isDirectory() && !dir.mkdirs()) throw new Exception("can't make " + dir);
        file = new File(dir, "Cobra " + new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date()) + ".mp4");
        List<String> cmd = new ArrayList<String>();
        if (windows()) {
            String enc = ScreenRecorder.pickEncoder(ffmpeg);
            if (enc.equals("h264_vaapi")) enc = "libx264";
            add(cmd, ffmpeg, "-y", "-loglevel", "error", "-filter_complex",
                    "ddagrab=output_idx=0:framerate=" + fps + ",hwdownload,format=bgra", "-c:v", enc);
            if (enc.equals("libx264")) add(cmd, "-preset", "veryfast", "-crf", quality.equals("High") ? "20" : "24");
            add(cmd, "-pix_fmt", "yuv420p", "-movflags", "+faststart", file.getAbsolutePath());
        } else {
            boolean wayland = System.getenv("WAYLAND_DISPLAY") != null && !"x11".equals(System.getenv("XDG_SESSION_TYPE"));
            add(cmd, "gpu-screen-recorder", "-w", wayland ? "portal" : "focused", "-f", String.valueOf(fps), "-k", "h264",
                    "-q", quality.equals("Small file") ? "medium" : quality.equals("Balanced") ? "high" : "very_high",
                    "-a", "default_output", "-o", file.getAbsolutePath());
        }
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        String root = System.getProperty("cobra.root");
        File logs = root != null ? new File(new File(root, "logs"), "recorder") : dir;
        logs.mkdirs();
        pb.redirectOutput(new File(logs, "capture-last.log"));
        proc = pb.start();
        paused = false;
        pid = pidOf(proc);
        return file;
    }

    /** Linux: pause/resume in place. Windows: ends this part; resuming starts a new file. */
    public static void togglePause(String ffmpeg, File dir, int fps, String quality) throws Exception {
        if (proc == null) return;
        if (!windows()) {
            signal("USR2");
            paused = !paused;
            return;
        }
        if (!paused) {
            stop();
            paused = true;
            proc = null;
        }
    }

    public static void resumeIfPaused(String ffmpeg, File dir, int fps, String quality) throws Exception {
        if (paused && windows()) {
            paused = false;
            start(ffmpeg, dir, fps, quality);
        }
    }

    /** Stops and lets the recorder finish the file. */
    public static File stop() {
        Process p = proc;
        File f = file;
        proc = null;
        paused = false;
        if (p == null) return f;
        try {
            if (windows()) {
                OutputStream o = p.getOutputStream();
                o.write('q');                               // ffmpeg: finish the file and quit
                o.flush();
                o.close();
            } else {
                signal("INT");                              // gpu-screen-recorder: finish the file and quit
            }
        } catch (Exception e) {
            p.destroy();
        }
        final Process wait = p;
        new Thread(new Runnable() {
            public void run() {
                try {
                    if (!wait.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) wait.destroy();
                } catch (InterruptedException ignored) {}
            }
        }, "cobra-capture-finish").start();
        return f;
    }

    private static void signal(String sig) {
        if (pid <= 0) return;
        try {
            new ProcessBuilder("kill", "-" + sig, String.valueOf(pid)).start().waitFor();
        } catch (Exception ignored) {}
    }

    private static long pidOf(Process p) {
        try {
            return (Long) Process.class.getMethod("pid").invoke(p);   // Java 9+
        } catch (Exception e) {
            return -1;
        }
    }

    private static void add(List<String> cmd, String... parts) {
        for (String s : parts) cmd.add(s);
    }
}

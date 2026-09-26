package dev.cobra.launcher.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ffmpeg for video wallpapers, set up automatically: uses one on the PATH if there is one,
 * otherwise downloads a static build (BtbN FFmpeg-Builds on GitHub) into the launcher's own
 * folder once. Windows gets ffmpeg.exe from the zip; Linux unpacks the tar.xz with the system tar.
 */
public final class FFmpeg {
    private static final String WIN = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip";
    private static final String LINUX = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux64-gpl.tar.xz";
    private static final Path DIR = Paths.ROOT.resolve("tools").resolve("ffmpeg");
    private static volatile String cached;

    private FFmpeg() {}

    private static boolean windows() {
        return "windows".equals(Paths.OS_NAME);
    }

    /** Command to run ffmpeg, or null when there's none yet. */
    public static synchronized String command() {
        if (cached != null) return cached;
        Path own = DIR.resolve(windows() ? "ffmpeg.exe" : "ffmpeg");
        if (Files.isRegularFile(own) && works(own.toString())) return cached = own.toString();
        // the system's ffmpeg only if it can make proper H.264 MP4s (Fedora's ffmpeg-free can't:
        // no libx264), otherwise we fetch our own full build
        if (works("ffmpeg") && hasX264("ffmpeg")) return cached = "ffmpeg";
        return null;
    }

    public static boolean available() {
        return command() != null;
    }

    private static boolean hasX264(String cmd) {
        try {
            Process p = new ProcessBuilder(cmd, "-hide_banner", "-encoders").redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            return out.contains("libx264");
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean works(String cmd) {
        try {
            Process p = new ProcessBuilder(cmd, "-version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Makes sure ffmpeg exists, downloading it the first time (about 100 MB). Off the UI thread. */
    public static synchronized String ensure(Consumer<String> status) throws IOException {
        String c = command();
        if (c != null) return c;
        if (!windows() && !"linux".equals(Paths.OS_NAME)) {
            throw new IOException("Video wallpapers need ffmpeg (brew install ffmpeg). GIFs work without it.");
        }
        if (status != null) status.accept("Downloading ffmpeg for video wallpapers (one time)");
        Files.createDirectories(DIR);
        Path archive = DIR.resolve(windows() ? "ffmpeg.zip" : "ffmpeg.tar.xz");
        Http.download(windows() ? WIN : LINUX, archive, null);
        try {
            if (windows()) unzipFfmpeg(archive);
            else untarFfmpeg(archive);
        } finally {
            Files.deleteIfExists(archive);
        }
        cached = null;
        c = command();
        if (c == null) throw new IOException("ffmpeg download didn't work. Check your connection and try again.");
        return c;
    }

    private static void unzipFfmpeg(Path zip) throws IOException {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                String n = e.getName().replace('\\', '/');
                if (n.endsWith("/bin/ffmpeg.exe")) {
                    Files.copy(in, DIR.resolve("ffmpeg.exe"), StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
            }
        }
        throw new IOException("ffmpeg.exe wasn't in the download.");
    }

    private static void untarFfmpeg(Path tar) throws IOException {
        Path tmp = DIR.resolve("unpack");
        Files.createDirectories(tmp);
        try {
            Process p = new ProcessBuilder("tar", "-xJf", tar.toString(), "-C", tmp.toString())
                    .redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            if (p.waitFor() != 0) throw new IOException("Couldn't unpack ffmpeg (is 'tar' with xz support installed?).");
            try (Stream<Path> s = Files.walk(tmp)) {
                Path bin = s.filter(f -> f.getFileName().toString().equals("ffmpeg") && f.getParent().getFileName().toString().equals("bin"))
                        .findFirst().orElseThrow(() -> new IOException("ffmpeg wasn't in the download."));
                Path dst = DIR.resolve("ffmpeg");
                Files.copy(bin, dst, StandardCopyOption.REPLACE_EXISTING);
                dst.toFile().setExecutable(true, false);
            }
        } catch (InterruptedException e) {
            throw new IOException("Interrupted");
        } finally {
            try (Stream<Path> s = Files.walk(tmp)) {
                s.sorted(java.util.Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
            } catch (IOException ignored) {}
        }
    }
}

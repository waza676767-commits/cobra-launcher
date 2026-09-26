package dev.cobra.launcher.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Self-update for everyone, on Linux and Windows alike.
 *
 * <p>Releases live on the project's GitHub repo (tag {@code b<number>}, asset
 * {@code cobra-launcher.jar}; the jar carries Cobra Client inside). While the launcher runs it
 * checks the latest release in the background and downloads a newer jar into its own folder.
 * The next time it starts, the old launcher only loads that jar and hands over to it, so an
 * update is: close and reopen. Nothing in the AppImage or the Windows install is touched.
 *
 * <p>{@code update.properties} (written by the GitHub build) says which repo and which build
 * number this jar is.
 */
public final class Updater {
    private static final Path DIR = Paths.ROOT.resolve("update");
    private static final Path JAR = DIR.resolve("cobra-launcher.jar");
    private static final Path INFO = DIR.resolve("build.txt");
    public static final String REPO;
    public static final int BUILD;

    static {
        Properties p = new Properties();
        try (InputStream in = Updater.class.getResourceAsStream("/update.properties")) {
            if (in != null) p.load(in);
        } catch (Exception ignored) {}
        REPO = p.getProperty("repo", "").trim();
        int b = 0;
        try {
            b = Integer.parseInt(p.getProperty("build", "0").trim());
        } catch (NumberFormatException ignored) {}
        BUILD = b;
    }

    private Updater() {}

    private static int downloadedBuild() {
        try {
            return Files.exists(INFO) && Files.exists(JAR) ? Integer.parseInt(Files.readString(INFO).trim()) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Call first thing in main: if a newer launcher was downloaded, run that one instead and
     * return true (the caller then returns). Works inside the AppImage and the Windows app,
     * because it only loads classes, it doesn't start another Java.
     */
    public static boolean handOver(String[] args) {
        if (Boolean.getBoolean("cobra.updated")) return false;          // we are the updated one
        int have = downloadedBuild();
        if (have <= BUILD) return false;
        try {
            System.setProperty("cobra.updated", "true");
            URLClassLoader loader = new URLClassLoader(new URL[]{JAR.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
            Thread.currentThread().setContextClassLoader(loader);
            Class<?> main = Class.forName("dev.cobra.launcher.CobraLauncher", true, loader);
            Method m = main.getMethod("main", String[].class);
            m.invoke(null, (Object) args);
            return true;
        } catch (Throwable t) {
            System.clearProperty("cobra.updated");
            t.printStackTrace();
            try {                                                           // broken download: forget it
                Files.deleteIfExists(JAR);
                Files.deleteIfExists(INFO);
            } catch (Exception ignored) {}
            return false;
        }
    }

    /** This launcher's build number (the downloaded one when we handed over). */
    public static int runningBuild() {
        return BUILD;
    }

    /**
     * Background: is there a newer release? Downloads it for the next start.
     * @param done called on success with the new build number (not on the UI thread)
     */
    public static void checkInBackground(java.util.function.IntConsumer done) {
        if (REPO.isEmpty()) return;
        Thread t = new Thread(() -> {
            try {
                Http.Response r = Http.get("https://api.github.com/repos/" + REPO + "/releases/latest");
                if (!r.ok()) return;
                JsonObject rel = r.json();
                String tag = rel.get("tag_name").getAsString();
                int build = Integer.parseInt(tag.replaceAll("[^0-9]", ""));
                if (build <= Math.max(BUILD, downloadedBuild())) return;
                String url = null;
                JsonArray assets = rel.getAsJsonArray("assets");
                for (JsonElement e : assets) {
                    JsonObject a = e.getAsJsonObject();
                    if (a.get("name").getAsString().equals("cobra-launcher.jar")) url = a.get("browser_download_url").getAsString();
                }
                if (url == null) return;
                Files.createDirectories(DIR);
                Path part = DIR.resolve("cobra-launcher.jar.download");
                Http.download(url, part, null);
                Files.move(part, JAR, StandardCopyOption.REPLACE_EXISTING);
                Files.writeString(INFO, String.valueOf(build));
                if (done != null) done.accept(build);
            } catch (Exception ignored) {
                // offline / rate-limited: try again next start
            }
        }, "cobra-updater");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /**
     * Swipecz's publish button: asks GitHub to build the current code and release it to everyone
     * (the "Build" workflow with publish=true). Uses the GitHub CLI that's logged in on this PC.
     * @return a message for the user
     */
    public static String publish() {
        if (REPO.isEmpty()) return "This build doesn't know its GitHub repo yet. Build it once on GitHub (git push), then use that version.";
        try {
            Process p = new ProcessBuilder("gh", "workflow", "run", "build.yml", "-R", REPO, "-f", "publish=true")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            int code = p.waitFor();
            if (code != 0) return "GitHub said: " + (out.isEmpty() ? "error " + code : out);
            return "Publishing: GitHub is building it now (about 10–15 min). Everyone gets it the next time they reopen the launcher.";
        } catch (java.io.IOException e) {
            return "Needs the GitHub CLI: sudo dnf install gh, then gh auth login.";
        } catch (InterruptedException e) {
            return "Interrupted.";
        }
    }
}

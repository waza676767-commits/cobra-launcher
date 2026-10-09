package dev.life.launcher.core;

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
 * {@code life-launcher.jar}; the jar carries Life Client inside). While the launcher runs it
 * checks the latest release in the background and downloads a newer jar into its own folder.
 * The next time it starts, the old launcher only loads that jar and hands over to it, so an
 * update is: close and reopen. Nothing in the AppImage or the Windows install is touched.
 *
 * <p>{@code update.properties} (written by the GitHub build) says which repo and which build
 * number this jar is.
 */
public final class Updater {
    private static final Path DIR = Paths.ROOT.resolve("update");
    private static final Path JAR = DIR.resolve("life-launcher.jar");
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
        if (Boolean.getBoolean("life.updated")) return false;          // we are the updated one
        int have = downloadedBuild();
        if (have <= BUILD) return false;
        try {
            System.setProperty("life.updated", "true");
            URLClassLoader loader = new URLClassLoader(new URL[]{JAR.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
            Thread.currentThread().setContextClassLoader(loader);
            Class<?> main = Class.forName("dev.life.launcher.LifeLauncher", true, loader);
            Method m = main.getMethod("main", String[].class);
            m.invoke(null, (Object) args);
            return true;
        } catch (Throwable t) {
            System.clearProperty("life.updated");
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
    /** "Update to the newest": checks now (blocking, off the UI thread) and says what happened. */
    public static String checkNow() {
        if (REPO.isEmpty()) return "This launcher wasn't built on GitHub, so it can't update itself.";
        final int[] got = {0};
        Thread t = check(b -> got[0] = b);
        try {
            t.join(120_000);
        } catch (InterruptedException ignored) {}
        if (got[0] > 0) return "Build " + got[0] + " downloaded. Close and reopen the launcher to use it.";
        int have = Math.max(BUILD, downloadedBuild());
        return have > BUILD ? "Build " + have + " is ready: close and reopen the launcher." : "You're on the newest Life (build " + BUILD + ").";
    }

    public static void checkInBackground(java.util.function.IntConsumer done) {
        if (REPO.isEmpty()) return;
        check(done);
    }

    private static Thread check(java.util.function.IntConsumer done) {
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
                    if (a.get("name").getAsString().equals("life-launcher.jar")) url = a.get("browser_download_url").getAsString();
                }
                if (url == null) return;
                Files.createDirectories(DIR);
                Path part = DIR.resolve("life-launcher.jar.download");
                Http.download(url, part, null);
                Files.move(part, JAR, StandardCopyOption.REPLACE_EXISTING);
                Files.writeString(INFO, String.valueOf(build));
                if (done != null) done.accept(build);
            } catch (Exception ignored) {
                // offline / rate-limited: try again next start
            }
        }, "life-updater");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
        return t;
    }

    /**
     * Swipecz's publish button: asks GitHub to build the current code and release it to everyone
     * (the "Build" workflow with publish=true). Uses the GitHub CLI that's logged in on this PC.
     * @return a message for the user
     */
    /**
     * Settings → Developer → Publish update (only shown to Swipecz): uploads the code in your
     * project folder to GitHub (commit + push) and asks GitHub to build and release it, with the
     * AppImage and the Windows .exe. Same as running ~/life-publish.sh.
     */
    public static String publish() {
        if (REPO.isEmpty()) return "This build doesn't know its GitHub repo yet. Build it once on GitHub (git push), then use that version.";
        java.nio.file.Path project = java.nio.file.Path.of(System.getProperty("user.home"), "LifeLauncher-new", "LifeLauncher");
        try {
            if (java.nio.file.Files.isDirectory(project.resolve(".git"))) {
                run(project, "git", "add", ".");
                run(project, "git", "commit", "-qm", "Life update " + java.time.LocalDateTime.now().withNano(0));   // "nothing to commit" is fine
                String[] push = run(project, "git", "push", "-q", "origin", "main");
                if (!"0".equals(push[0])) {
                    return "Couldn't upload the code: " + push[1] + " (in a terminal: gh auth setup-git, then try again)";
                }
            }
            String[] r = run(null, "gh", "workflow", "run", "build.yml", "-R", REPO, "-f", "publish=true");
            if (!"0".equals(r[0])) return "GitHub said: " + (r[1].isEmpty() ? "error " + r[0] : r[1]);
            return "Publishing: GitHub is building the AppImage and the .exe (10–15 min). It appears on the Releases page and "
                    + "everyone gets it the next time they reopen the launcher.";
        } catch (java.io.IOException e) {
            return "Needs git and the GitHub CLI: sudo dnf install git gh, then gh auth login.";
        } catch (InterruptedException e) {
            return "Interrupted.";
        }
    }

    /** Runs a command; {exit code, output}. */
    private static String[] run(java.nio.file.Path dir, String... cmd) throws java.io.IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        if (dir != null) pb.directory(dir.toFile());
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes()).trim();
        if (!p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return new String[]{"timeout", "took too long"};
        }
        return new String[]{String.valueOf(p.exitValue()), out.length() > 200 ? out.substring(out.length() - 200) : out};
    }
}

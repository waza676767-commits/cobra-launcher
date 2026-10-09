package dev.life.client.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The in-game "Update to the newest" button. Asks GitHub for the latest Life release and, when
 * it's newer than this one, downloads it into the launcher's update folder (the launcher tells
 * the game where that is). Close the game and reopen the launcher: it starts the new version,
 * Life Client included. Same thing the launcher does by itself in the background.
 */
public final class SelfUpdate {
    private static volatile boolean busy;

    private SelfUpdate() {}

    public static void checkAndDownload() {
        if (busy) return;
        final String repo = System.getProperty("life.repo", "");
        final String root = System.getProperty("life.root", "");
        final int build = Integer.getInteger("life.build", 0);
        if (repo.isEmpty() || root.isEmpty()) {
            Life.platform.chat("\u00a7eThis Life build can't update itself (it wasn't built on GitHub).");
            return;
        }
        busy = true;
        Life.platform.chat("Checking for a newer Life\u2026");
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    String json = get("https://api.github.com/repos/" + repo + "/releases/latest");
                    Matcher tag = Pattern.compile("\"tag_name\"\\s*:\\s*\"[^0-9\"]*(\\d+)").matcher(json);
                    Matcher url = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]*/life-launcher\\.jar)\"").matcher(json);
                    if (!tag.find() || !url.find()) {
                        Life.platform.chat("\u00a7eNo Life release found yet.");
                        return;
                    }
                    int latest = Integer.parseInt(tag.group(1));
                    if (latest <= build) {
                        Life.platform.chat("\u00a7aYou're on the newest Life (build " + build + ").");
                        return;
                    }
                    File dir = new File(root, "update");
                    if (!dir.isDirectory() && !dir.mkdirs()) throw new Exception("can't write " + dir);
                    File part = new File(dir, "life-launcher.jar.download");
                    download(url.group(1), part);
                    File jar = new File(dir, "life-launcher.jar");
                    if (jar.exists() && !jar.delete()) throw new Exception("the old update is in use");
                    if (!part.renameTo(jar)) throw new Exception("couldn't save the update");
                    OutputStream o = new FileOutputStream(new File(dir, "build.txt"));
                    try {
                        o.write(String.valueOf(latest).getBytes("UTF-8"));
                    } finally {
                        o.close();
                    }
                    Life.platform.chat("\u00a7aLife build " + latest + " downloaded. Close the game and reopen the launcher to use it.");
                } catch (Exception e) {
                    Life.platform.chat("\u00a7cUpdate failed: " + e.getMessage());
                } finally {
                    busy = false;
                }
            }
        }, "life-self-update");
        t.setDaemon(true);
        t.start();
    }

    private static HttpURLConnection open(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setRequestProperty("User-Agent", "LifeClient");
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        return c;
    }

    private static String get(String u) throws Exception {
        HttpURLConnection c = open(u);
        if (c.getResponseCode() != 200) throw new Exception("GitHub answered " + c.getResponseCode());
        InputStream in = c.getInputStream();
        try {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } finally {
            in.close();
        }
    }

    private static void download(String u, File to) throws Exception {
        HttpURLConnection c = open(u);
        // GitHub sends release files through a redirect to another host
        for (int i = 0; i < 5 && (c.getResponseCode() == 301 || c.getResponseCode() == 302 || c.getResponseCode() == 307); i++) {
            c = open(c.getHeaderField("Location"));
        }
        if (c.getResponseCode() != 200) throw new Exception("download answered " + c.getResponseCode());
        InputStream in = c.getInputStream();
        OutputStream out = new FileOutputStream(to);
        try {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            out.close();
            in.close();
        }
    }
}

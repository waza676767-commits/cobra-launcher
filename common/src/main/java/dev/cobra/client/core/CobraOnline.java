package dev.cobra.client.core;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Who else is on Cobra (for the Cobra icon in the Tab list). Every Cobra Client tells a tiny
 * server "this UUID is online with Cobra" every 30 seconds and gets back the UUIDs seen in the
 * last 90. Only Minecraft UUIDs are sent. The server is server/cobra-online.py (run it on your
 * VPS); the address can be changed with -Dcobra.online=http://host:port.
 */
public final class CobraOnline {
    private static final String URL_BASE = System.getProperty("cobra.online", "http://65.109.88.105:25581");
    private static volatile Set<String> online = new HashSet<String>();
    private static volatile java.util.Map<String, String> wearing = new java.util.HashMap<String, String>();
    /** What you wear (comma list), sent with each ping. */
    public static volatile String ownCosmetics = "";

    // ------------------------------------------------------------------ custom capes

    /** Uploads your cape PNG so other Cobra players can see it (background thread). */
    public static void uploadCape(final String uuid, final byte[] png) {
        if (uuid == null || png == null || png.length == 0 || png.length > 256 * 1024) return;
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    String b64 = java.util.Base64.getEncoder().encodeToString(png);
                    post("/cape", "{\"uuid\":\"" + uuid.toLowerCase() + "\",\"png\":\"" + b64 + "\"}");
                } catch (Exception ignored) {}
            }
        }, "cobra-cape-upload");
        t.setDaemon(true);
        t.start();
    }

    /** Downloads another Cobra player's cape PNG (blocking: call from a background thread), or null. */
    public static byte[] downloadCape(String uuid) {
        try {
            HttpURLConnection c = open("/cape/" + uuid.toLowerCase());
            if (c.getResponseCode() != 200) return null;
            InputStream in = c.getInputStream();
            try {
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0 && b.size() < 256 * 1024) b.write(buf, 0, n);
                return b.toByteArray();
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** The cosmetics another Cobra player wears (comma list), or "" if none / not a Cobra player. */
    public static String cosmetics(String uuid) {
        if (uuid == null) return "";
        String c = wearing.get(uuid.toLowerCase());
        return c == null ? "" : c;
    }
    private static long next;
    private static volatile boolean busy;

    private CobraOnline() {}

    public static boolean isCobra(String uuid) {
        return uuid != null && online.contains(uuid.toLowerCase());
    }

    /** From the client tick. {@code share} = tell others I'm on Cobra; {@code show} = fetch the list. */
    public static void tick(final String ownUuid, boolean share, boolean show) {
        long now = System.currentTimeMillis();
        if (busy || now < next || ownUuid == null || !(share || show)) return;
        next = now + 20_000;
        busy = true;
        final boolean doShare = share, doShow = show;
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    String cos = ownCosmetics == null ? "" : ownCosmetics.replaceAll("[^a-z0-9_,:]", "");
                    if (doShare) post("/ping", "{\"uuid\":\"" + ownUuid.toLowerCase() + "\",\"cosmetics\":\"" + cos + "\"}");
                    if (doShow) {
                        String body = get("/online");
                        Set<String> s = new HashSet<String>();
                        Matcher m = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}").matcher(body);
                        while (m.find()) s.add(m.group().toLowerCase());
                        if (doShare) s.add(ownUuid.toLowerCase());
                        online = s;
                        java.util.Map<String, String> w = new java.util.HashMap<String, String>();
                        Matcher cm = Pattern.compile("\"([0-9a-f-]{36})\"\\s*:\\s*\"([a-z0-9_,:]*)\"").matcher(body);
                        while (cm.find()) w.put(cm.group(1), cm.group(2));
                        wearing = w;
                    }
                } catch (Exception ignored) {
                    // server not reachable: keep the last list
                } finally {
                    busy = false;
                }
            }
        }, "cobra-online");
        t.setDaemon(true);
        t.start();
    }

    private static HttpURLConnection open(String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(URL_BASE + path).openConnection();
        c.setConnectTimeout(4000);
        c.setReadTimeout(4000);
        c.setRequestProperty("User-Agent", "CobraClient");
        return c;
    }

    private static void post(String path, String json) throws Exception {
        HttpURLConnection c = open(path);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        OutputStream o = c.getOutputStream();
        try {
            o.write(json.getBytes("UTF-8"));
        } finally {
            o.close();
        }
        c.getResponseCode();
        c.disconnect();
    }

    private static String get(String path) throws Exception {
        HttpURLConnection c = open(path);
        InputStream in = c.getInputStream();
        try {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0 && b.size() < 1 << 20) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } finally {
            in.close();
        }
    }
}

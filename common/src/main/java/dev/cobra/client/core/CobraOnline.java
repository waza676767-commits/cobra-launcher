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
        next = now + 30_000;
        busy = true;
        final boolean doShare = share, doShow = show;
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    if (doShare) post("/ping", "{\"uuid\":\"" + ownUuid.toLowerCase() + "\"}");
                    if (doShow) {
                        String body = get("/online");
                        Set<String> s = new HashSet<String>();
                        Matcher m = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}").matcher(body);
                        while (m.find()) s.add(m.group().toLowerCase());
                        if (doShare) s.add(ownUuid.toLowerCase());
                        online = s;
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

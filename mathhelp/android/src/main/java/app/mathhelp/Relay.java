package app.mathhelp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Talks to the free public relay ntfy.sh. Each user has an inbox topic derived from their username.
 * Envelopes (JSON in the ntfy message body):
 *   hello: {"t":"h","f":from,"k":fromPubKey,"a":0|1}     (a=1 means "reply to your hello")
 *   msg:   {"t":"m","f":from,"k":fromPubKey,"c":b64(AES-GCM(ECDH key, payload))}
 *   payload: {"id":clientId,"x":text,"u":attachmentUrl,"mk":b64 file key,"mt":mime}
 * The relay only ever sees ciphertext (plus usernames' hashes and public keys).
 */
final class Relay {
    static final String BASE = "https://ntfy.sh/";
    static final int MAX_FILE = 2_000_000; // ntfy.sh anonymous attachment limit is 2 MB

    static final Handler ui = new Handler(Looper.getMainLooper());
    /** All state changes happen on this one thread, in order. */
    static final ExecutorService q = Executors.newSingleThreadExecutor();

    static Context app;
    static Runnable onChange;
    static boolean foreground;

    static void init(Context c) {
        if (app != null) return;
        app = c.getApplicationContext();
        Store.init(app);
        NotificationManager nm = app.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("m", "Messages", NotificationManager.IMPORTANCE_HIGH));
    }

    static String inbox(String user) {
        return "mh_" + Crypto.sha256hex("mathhelp-inbox:" + user.toLowerCase(java.util.Locale.ROOT)).substring(0, 40);
    }

    private static void changed() { ui.post(() -> { if (onChange != null) onChange.run(); }); }

    // ---------- HTTP ----------

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        in.close();
        return bo.toByteArray();
    }

    private static JSONObject put(String method, String topic, byte[] body, String filename) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + topic).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setRequestMethod(method);
        c.setDoOutput(true);
        if (filename != null) c.setRequestProperty("Filename", filename);
        c.setFixedLengthStreamingMode(body.length);
        try (OutputStream o = c.getOutputStream()) { o.write(body); }
        int code = c.getResponseCode();
        if (code == 429) throw new IOException("Daily limit reached on the free relay, try later");
        if (code >= 400) {
            InputStream es = c.getErrorStream();
            throw new IOException(es == null ? "HTTP " + code : new String(readAll(es), "UTF-8"));
        }
        try { return new JSONObject(new String(readAll(c.getInputStream()), "UTF-8")); }
        catch (Exception e) { throw new IOException("bad relay reply"); }
    }

    private static void publish(String peer, JSONObject env) throws IOException {
        put("POST", inbox(peer), env.toString().getBytes("UTF-8"), null);
    }

    /** Uploads an (already encrypted) blob to a throwaway topic and returns its download URL. */
    private static String upload(byte[] blob) throws IOException {
        JSONObject r = put("PUT", "mh_f_" + Crypto.hex(Crypto.random(12)), blob, "f.bin");
        JSONObject a = r.optJSONObject("attachment");
        if (a == null) throw new IOException("upload failed");
        return a.optString("url");
    }

    private static byte[] download(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        if (c.getResponseCode() >= 400) throw new FileNotFoundException("expired");
        return readAll(c.getInputStream());
    }

    // ---------- sending ----------

    private static final Map<String, Long> lastHello = new HashMap<>();

    private static void hello(String peer, boolean ack) {
        String k = peer.toLowerCase(java.util.Locale.ROOT) + ack;
        Long t = lastHello.get(k);
        if (t != null && System.currentTimeMillis() - t < 10 * 60_000) return; // don't spam
        try {
            JSONObject e = new JSONObject().put("t", "h").put("f", Store.user).put("k", Store.pub).put("a", ack ? 1 : 0);
            publish(peer, e);
            lastHello.put(k, System.currentTimeMillis());
        } catch (Exception ignored) {}
    }

    static void addContact(String peer) {
        q.execute(() -> {
            Store.addContact(peer, null);
            changed();
            hello(peer, false);
        });
    }

    /** Store locally as pending, then try to deliver. mediaPath may be null. */
    static void send(String peer, String text, String mediaPath, String mime) {
        q.execute(() -> {
            Store.insertMsg(peer, true, text, mediaPath, mime, Store.PENDING, Crypto.hex(Crypto.random(12)));
            changed();
            flush(peer);
        });
    }

    /** Deliver queued messages. Needs the peer's key; if we don't have it yet, say hello and wait. */
    private static void flush(String peer) {
        Store.Contact c = Store.contact(peer);
        if (c == null) return;
        if (c.pub == null) { hello(peer, false); return; }
        if (c.bad) return;
        try {
            byte[] key = Crypto.shared(Store.priv(), c.pub);
            for (Store.Msg m : Store.pending(peer)) {
                JSONObject p = new JSONObject().put("id", m.cid).put("x", m.text);
                if (m.media != null) {
                    byte[] fk = Crypto.random(32);
                    p.put("u", upload(Crypto.seal(fk, Files.readAllBytes(new File(m.media).toPath()))));
                    p.put("mk", Crypto.b64(fk)).put("mt", m.mime);
                }
                JSONObject e = new JSONObject().put("t", "m").put("f", Store.user).put("k", Store.pub)
                        .put("c", Crypto.b64(Crypto.seal(key, p.toString().getBytes("UTF-8"))));
                publish(peer, e);
                Store.markSent(m.id);
                changed();
            }
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("limit")) ui.post(() -> MainActivity.toast(app, msg));
        }
    }

    private static void flushAll() {
        for (String p : Store.peersWithPending()) flush(p);
    }

    // ---------- receiving ----------

    /** Handle one ntfy event; returns a notification line for new incoming messages, else null. */
    private static String handle(JSONObject ev) {
        if (!"message".equals(ev.optString("event"))) return null;
        String id = ev.optString("id");
        if (id.isEmpty() || !Store.markSeen(id)) return null;
        try {
            JSONObject e = new JSONObject(ev.optString("message"));
            String from = e.getString("f"), k = e.getString("k");
            if (k.equals(Store.pub) || from.isEmpty() || from.length() > 24) return null;
            Store.Contact c = Store.contact(from);
            if (c == null) { Store.addContact(from, k); c = Store.contact(from); }
            else if (c.pub == null) { Store.setPub(from, k); c.pub = k; }
            else if (!c.pub.equals(k)) { Store.markBad(c.name); changed(); return null; }
            from = c.name;

            if ("h".equals(e.optString("t"))) {
                if (e.optInt("a") == 0) hello(from, true);
                changed();
                flush(from);
                return null;
            }
            if (!"m".equals(e.optString("t"))) return null;
            byte[] key = Crypto.shared(Store.priv(), c.pub);
            JSONObject p = new JSONObject(new String(Crypto.open(key, Crypto.unb64(e.getString("c"))), "UTF-8"));
            String text = p.optString("x"), media = null, mime = null;
            if (p.has("u")) {
                try {
                    byte[] data = Crypto.open(Crypto.unb64(p.getString("mk")), download(p.getString("u")));
                    File f = new File(Store.mediaDir, "r" + Crypto.hex(Crypto.random(10)));
                    try (FileOutputStream o = new FileOutputStream(f)) { o.write(data); }
                    media = f.getAbsolutePath();
                    mime = p.optString("mt", "image/*");
                } catch (IOException net) {
                    if (!(net instanceof FileNotFoundException)) { Store.unmarkSeen(id); return null; } // retry later
                    text = (text.isEmpty() ? "" : text + "\n") + "(image expired — the free relay only keeps images for 3 hours)";
                } catch (Exception ex) {
                    text = (text.isEmpty() ? "" : text + "\n") + "(image expired — the free relay only keeps images for 3 hours)";
                }
            }
            if (Store.insertMsg(from, false, text, media, mime, Store.SENT, "in:" + from.toLowerCase(java.util.Locale.ROOT) + ":" + p.optString("id", id)) == -1)
                return null;
            changed();
            return from + ": " + (text.isEmpty() ? "📷 image" : text);
        } catch (Exception ex) {
            return null;
        }
    }

    private static void notify(String line) {
        if (line == null || foreground) return;
        NotificationManager nm = app.getSystemService(NotificationManager.class);
        PendingIntent pi = PendingIntent.getActivity(app, 0, new Intent(app, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        int i = line.indexOf(": ");
        Notification n = new Notification.Builder(app, "m")
                .setSmallIcon(R.drawable.ic)
                .setContentTitle(line.substring(0, i))
                .setContentText(line.substring(i + 2))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        nm.notify(line.substring(0, i).toLowerCase(java.util.Locale.ROOT).hashCode(), n);
    }

    /** One-shot fetch of everything waiting in the inbox (used by the background job). Call on q. */
    static void pollOnce() {
        if (!Store.hasUser()) return;
        long start = System.currentTimeMillis() / 1000;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(BASE + inbox(Store.user) + "/json?poll=1&since=" + Store.since()).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = r.readLine()) != null) notify(handle(new JSONObject(line)));
            }
            // Overlap by 2 min: the relay can take a moment before new messages show up in fetches.
            Store.setSince(start - 120);
        } catch (Exception ignored) {}
        flushAll();
    }

    // ---------- live stream while the app is open ----------

    private static int gen = 0;
    private static HttpURLConnection live;

    static synchronized void start() {
        if (!Store.hasUser()) return;
        final int g = ++gen;
        foreground = true;
        q.execute(() -> {
            Store.pruneSeen();
            for (Store.Contact c : Store.contacts()) if (c.pub == null) hello(c.name, false);
            flushAll();
        });
        Thread t = new Thread(() -> stream(g), "live");
        t.setDaemon(true);
        t.start();
    }

    static synchronized void stop() {
        gen++;
        foreground = false;
        if (live != null) { final HttpURLConnection l = live; live = null; new Thread(l::disconnect).start(); }
    }

    private static synchronized boolean alive(int g) { return g == gen; }

    private static void stream(int g) {
        int backoff = 1000;
        while (alive(g)) {
            try {
                // Read `since` on q so it reflects everything already handled.
                String since = q.submit(Store::since).get();
                final long connected = System.currentTimeMillis() / 1000;
                HttpURLConnection c = (HttpURLConnection) new URL(BASE + inbox(Store.user) + "/json?since=" + since).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(90000); // ntfy sends a keepalive every ~45 s
                synchronized (Relay.class) { if (!alive(g)) return; live = c; }
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = r.readLine()) != null && alive(g)) {
                        backoff = 1000;
                        final JSONObject ev = new JSONObject(line);
                        String type = ev.optString("event");
                        if ("message".equals(type)) q.execute(() -> notify(handle(ev)));
                        // First keepalive (~45 s in): everything older than this connection is handled.
                        else if ("keepalive".equals(type)) q.execute(() -> Store.setSince(connected - 120));
                    }
                }
            } catch (Exception e) {
                if (!alive(g)) return;
                try { Thread.sleep(backoff); } catch (InterruptedException ignored) {}
                backoff = Math.min(backoff * 2, 30000);
            }
        }
    }

    private Relay() {}
}

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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Talks to the free public relay ntfy.sh. Each user has an inbox topic derived from their username.
 * Envelopes (JSON in the ntfy message body):
 *   friend request: {"t":"h","f":from,"k":fromPubKey,"a":0}
 *   accept:         {"t":"h","f":from,"k":fromPubKey,"a":1}
 *   message:        {"t":"m","f":from,"k":fromPubKey,"c":b64(AES-GCM(ECDH key, payload)),"a":fileId?}
 *   payload: {"id":clientId,"x":text,"u":attachmentUrl,"mk":b64 file key,"mt":mime}
 * Only friends (accepted requests) can message you. The relay only ever sees ciphertext.
 *
 * Threads: `db` = quick local reads for the screens (never touches the network),
 * `in` = incoming events in order, `out` = sending, `bg` = slow housekeeping.
 */
final class Relay {
    static final String BASE = "https://ntfy.sh/";
    /** Encrypted copies kept by the GitHub backup bot (.github/workflows/mathhelp-backup.yml on main). */
    static final String ARCHIVE = "https://raw.githubusercontent.com/waza676767-commits/cobra-launcher/main/mathhelp-archive/";
    static final String REGISTRY = "mh_registry_v1";
    static final int MAX_FILE = 2_000_000; // ntfy.sh anonymous attachment limit is 2 MB

    static final Handler ui = new Handler(Looper.getMainLooper());
    static final ExecutorService db = Executors.newSingleThreadExecutor();
    static final ExecutorService in = Executors.newSingleThreadExecutor();
    static final ExecutorService out = Executors.newSingleThreadExecutor();
    static final ExecutorService bg = Executors.newSingleThreadExecutor();

    static Context app;
    static Runnable onChange;
    private static int resumed;

    static void init(Context c) {
        if (app != null) return;
        app = c.getApplicationContext();
        Store.init(app);
        NotificationManager nm = app.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("m", "Messages", NotificationManager.IMPORTANCE_HIGH));
        NotificationChannel s = new NotificationChannel("s", "Connection (you can hide this)", NotificationManager.IMPORTANCE_MIN);
        s.setShowBadge(false);
        nm.createNotificationChannel(s);
    }

    /** Activities call these so we only notify while the app isn't on screen. */
    static void resumed() { resumed++; }
    static void paused() { resumed = Math.max(0, resumed - 1); }
    static boolean foreground() { return resumed > 0; }

    static String inbox(String user) {
        return "mh_" + Crypto.sha256hex("mathhelp-inbox:" + user.toLowerCase(Locale.ROOT)).substring(0, 40);
    }

    static void changed() { ui.post(() -> { if (onChange != null) onChange.run(); }); }

    // ---------- HTTP ----------

    static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = is.read(b)) > 0) bo.write(b, 0, n);
        is.close();
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
        if (c.getResponseCode() >= 400) throw new IOException("gone");
        return readAll(c.getInputStream());
    }

    // ---------- friends ----------

    private static void sendHello(String peer, boolean accept) throws IOException {
        try {
            publish(peer, new JSONObject().put("t", "h").put("f", Store.user).put("k", Store.pub).put("a", accept ? 1 : 0));
        } catch (org.json.JSONException e) { throw new IOException(e); }
    }

    /** Send a friend request (or re-send it). Instant: it doesn't need the other person online. */
    static void requestFriend(String peer, Runnable onSent, java.util.function.Consumer<String> onError) {
        out.execute(() -> {
            Store.Contact c = Store.contact(peer);
            if (c != null && c.state == Store.IN) { acceptFriend(c.name); ui.post(onSent); return; }
            if (c == null) Store.addContact(peer, null, Store.OUT);
            changed();
            try { sendHello(peer, false); ui.post(onSent); }
            catch (IOException e) { ui.post(() -> onError.accept(e.getMessage())); }
        });
    }

    static void acceptFriend(String peer) {
        out.execute(() -> {
            Store.setState(peer, Store.FRIEND);
            changed();
            try { sendHello(peer, true); } catch (IOException ignored) {}
            flush(peer);
        });
    }

    // ---------- sending ----------

    /** Store locally as pending, then deliver right away. mediaPath may be null. */
    static void send(String peer, String text, String mediaPath, String mime) {
        out.execute(() -> {
            Store.insertMsg(peer, true, text, mediaPath, mime, Store.PENDING, Crypto.hex(Crypto.random(12)));
            changed();
            flush(peer);
        });
    }

    /** Deliver queued messages to a friend. Runs on `out`. */
    private static void flush(String peer) {
        Store.Contact c = Store.contact(peer);
        if (c == null || c.state != Store.FRIEND || c.pub == null) return;
        try {
            byte[] key = Crypto.shared(Store.priv(), c.pub);
            for (Store.Msg m : Store.pending(peer)) {
                JSONObject p = new JSONObject().put("id", m.cid).put("x", m.text);
                String fileId = null;
                if (m.media != null) {
                    byte[] fk = Crypto.random(32);
                    String url = upload(Crypto.seal(fk, Files.readAllBytes(new File(m.media).toPath())));
                    p.put("u", url).put("mk", Crypto.b64(fk)).put("mt", m.mime);
                    fileId = fileId(url);
                }
                JSONObject e = new JSONObject().put("t", "m").put("f", Store.user).put("k", Store.pub)
                        .put("c", Crypto.b64(Crypto.seal(key, p.toString().getBytes("UTF-8"))));
                if (fileId != null) e.put("a", fileId);
                publish(peer, e);
                Store.markSent(m.id);
                changed();
            }
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("limit")) ui.post(() -> MainActivity.toast(app, msg));
        }
    }

    static void flushAll() {
        out.execute(() -> { for (String p : Store.peersWithPending()) flush(p); });
    }

    // ---------- receiving ----------

    /** Handle one relay event (on `in`); returns a notification line, or null. */
    private static String handle(JSONObject ev) {
        if (!"message".equals(ev.optString("event"))) return null;
        String id = ev.optString("id");
        if (id.isEmpty() || !Store.markSeen(id)) return null;
        try {
            JSONObject e = new JSONObject(ev.optString("message"));
            String from = e.getString("f"), k = e.getString("k");
            if (k.equals(Store.pub) || !MainActivity.validName(from)) return null;
            Store.Contact c = Store.contact(from);
            String t = e.optString("t");

            if ("h".equals(t) && e.optInt("a") == 0) {               // friend request
                if (c == null) {
                    Store.addContact(from, k, Store.IN);
                    changed();
                    return from + ": wants to be your friend";
                }
                if (c.state == Store.OUT) {                            // we both asked: friends now
                    Store.setPub(c.name, k);
                    acceptFriend(c.name);
                    return c.name + ": is now your friend";
                }
                if (c.state == Store.FRIEND) {
                    if (k.equals(c.pub)) { final String n = c.name; out.execute(() -> { try { sendHello(n, true); } catch (IOException ignored) {} }); return null; }
                    Store.setPub(c.name, k);                           // new phone / reinstall: ask again
                    Store.setState(c.name, Store.IN);
                    changed();
                    return c.name + ": wants to be your friend (new phone?)";
                }
                if (!k.equals(c.pub)) { Store.setPub(c.name, k); changed(); }
                return null;
            }
            if ("h".equals(t)) {                                       // accepted
                if (c == null || c.state == Store.IN) return null;
                if (c.state == Store.FRIEND && k.equals(c.pub)) return null;
                if (c.state == Store.FRIEND) return null;              // key changed without a request: ignore
                Store.setPub(c.name, k);
                Store.setState(c.name, Store.FRIEND);
                changed();
                final String n = c.name;
                out.execute(() -> flush(n));
                return c.name + ": accepted your friend request";
            }
            if (!"m".equals(t)) return null;
            // Messages only from friends, and only from the key we know.
            if (c == null || c.state != Store.FRIEND || c.pub == null || !c.pub.equals(k)) return null;
            from = c.name;
            byte[] key = Crypto.shared(Store.priv(), c.pub);
            JSONObject p = new JSONObject(new String(Crypto.open(key, Crypto.unb64(e.getString("c"))), "UTF-8"));
            String text = p.optString("x");
            String fetch = null;
            if (p.has("u")) {
                fetch = new JSONObject().put("u", p.getString("u")).put("mk", p.getString("mk"))
                        .put("mt", p.optString("mt", "image/*"))
                        .put("day", day(ev.optLong("time", System.currentTimeMillis() / 1000))).toString();
            }
            long row = Store.insertMsg(from, false, text, null, null, Store.SENT,
                    "in:" + from.toLowerCase(Locale.ROOT) + ":" + p.optString("id", id));
            if (row == -1) return null;
            changed();
            if (fetch != null) {
                Store.setFetch(row, fetch);
                final String f = fetch;
                bg.execute(() -> fetchImage(row, f));
            }
            return from + ": " + (text.isEmpty() ? "📷 image" : text);
        } catch (Exception ex) {
            return null;
        }
    }

    static String fileId(String url) {
        String n = url.substring(url.lastIndexOf('/') + 1);
        int dot = n.indexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    private static String day(long unixSeconds) {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return f.format(new java.util.Date(unixSeconds * 1000));
    }

    /** Try the relay, then the GitHub backup. Gives up after 3 days. Runs on `bg`. */
    private static void fetchImage(long row, String fetchJson) {
        if (fetchJson == null) return;
        try {
            JSONObject f = new JSONObject(fetchJson);
            String url = f.getString("u");
            byte[] blob;
            try { blob = download(url); }
            catch (IOException e) { blob = download(ARCHIVE + "files/" + f.getString("day") + "/" + fileId(url) + ".bin"); }
            byte[] data = Crypto.open(Crypto.unb64(f.getString("mk")), blob);
            File o = new File(Store.mediaDir, "r" + Crypto.hex(Crypto.random(10)));
            try (FileOutputStream w = new FileOutputStream(o)) { w.write(data); }
            Store.setMedia(row, o.getAbsolutePath(), f.optString("mt", "image/*"));
            changed();
        } catch (Exception e) {
            if (System.currentTimeMillis() - Store.msgTime(row) > 3 * 86400_000L) {
                Store.expireMedia(row, "(image expired)");
                changed();
            }
        }
    }

    /** Announce our inbox (a hash, not the username) so the backup bot knows to save it. */
    private static void register() {
        long last = Store.prefs.getLong("reg", 0);
        if (System.currentTimeMillis() - last < 6 * 3600_000L) return;
        try {
            put("POST", REGISTRY, inbox(Store.user).getBytes("UTF-8"), null);
            Store.prefs.edit().putLong("reg", System.currentTimeMillis()).apply();
        } catch (Exception ignored) {}
    }

    /** Read new lines of our inbox's backup file on GitHub (Range request from the last offset). */
    private static void catchUpFromArchive() {
        try {
            long off = Store.prefs.getLong("arch", 0);
            HttpURLConnection c = (HttpURLConnection) new URL(ARCHIVE + inbox(Store.user) + ".jsonl").openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            if (off > 0) c.setRequestProperty("Range", "bytes=" + off + "-");
            int code = c.getResponseCode();
            if (code != 200 && code != 206) return; // 404: nothing backed up yet, 416: nothing new
            byte[] b = readAll(c.getInputStream());
            int start = (code == 200 && off > 0) ? (int) Math.min(off, b.length) : 0;
            int end = start;
            for (int i = start; i < b.length; i++) {
                if (b[i] != '\n') continue;
                String line = new String(b, end, i - end, "UTF-8");
                end = i + 1;
                try {
                    JSONObject a = new JSONObject(line);
                    a.put("event", "message");
                    in.submit(() -> notify(handle(a))).get();
                } catch (Exception ignored) {}
            }
            long base = code == 206 ? off : 0;
            Store.prefs.edit().putLong("arch", base + end).apply();
        } catch (Exception ignored) {}
    }

    /** Slow housekeeping, off the fast paths. At most every 10 minutes. */
    static void maintenance() {
        bg.execute(() -> {
            long last = Store.prefs.getLong("maint", 0);
            if (System.currentTimeMillis() - last < 10 * 60_000L) return;
            Store.prefs.edit().putLong("maint", System.currentTimeMillis()).apply();
            Store.pruneSeen();
            register();
            catchUpFromArchive();
            for (long[] r : Store.fetchRows()) fetchImage(r[0], Store.fetchOf(r[0]));
        });
        flushAll();
    }

    private static void notify(String line) {
        if (line == null || foreground()) return;
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
        nm.notify(line.substring(0, i).toLowerCase(Locale.ROOT).hashCode(), n);
    }

    /** One-shot fetch (background job fallback when the live connection isn't running). */
    static void pollOnce() {
        if (!Store.hasUser() || running) return;
        long startT = System.currentTimeMillis() / 1000;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(BASE + inbox(Store.user) + "/json?poll=1&since=" + Store.since()).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = r.readLine()) != null) {
                    JSONObject ev = new JSONObject(line);
                    in.submit(() -> notify(handle(ev))).get();
                }
            }
            Store.setSince(startT - 120); // overlap: the relay can take a moment to show new messages
        } catch (Exception ignored) {}
        maintenance();
    }

    // ---------- the live connection (kept open by MsgService) ----------

    private static volatile boolean running;

    static synchronized void start() {
        if (!Store.hasUser()) return;
        maintenance();
        if (running) return;
        running = true;
        Thread t = new Thread(Relay::stream, "live");
        t.setDaemon(true);
        t.start();
    }

    static boolean running() { return running; }

    private static void stream() {
        int backoff = 1000;
        while (true) {
            try {
                String since = Store.since();
                final long connected = System.currentTimeMillis() / 1000;
                HttpURLConnection c = (HttpURLConnection) new URL(BASE + inbox(Store.user) + "/json?since=" + since).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(90000); // ntfy sends a keepalive every ~45 s
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        backoff = 1000;
                        final JSONObject ev = new JSONObject(line);
                        String type = ev.optString("event");
                        if ("message".equals(type)) in.execute(() -> notify(handle(ev)));
                        // First keepalive (~45 s in): everything older than this connection is handled.
                        else if ("keepalive".equals(type)) in.execute(() -> Store.setSince(connected - 120));
                    }
                }
            } catch (Exception e) {
                try { Thread.sleep(backoff); } catch (InterruptedException ignored) {}
                backoff = Math.min(backoff * 2, 30000);
            }
        }
    }

    private Relay() {}
}

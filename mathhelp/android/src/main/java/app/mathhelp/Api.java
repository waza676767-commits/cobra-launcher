package app.mathhelp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** HTTP client, session state and the single background long-poll loop. */
final class Api {
    static final Handler ui = new Handler(Looper.getMainLooper());
    static final ExecutorService io = Executors.newFixedThreadPool(3);

    static SharedPreferences prefs;
    static String server, user, token;

    /** Unread counts per peer (in-memory). */
    static final Map<String, Integer> unread = new HashMap<>();

    interface Listener { void onMessages(List<JSONObject> msgs); }
    static Listener listener;

    static void init(Context c) {
        if (prefs != null) return;
        prefs = c.getApplicationContext().getSharedPreferences("s", Context.MODE_PRIVATE);
        server = prefs.getString("server", "");
        user = prefs.getString("user", null);
        token = prefs.getString("token", null);
        Img.init(c.getApplicationContext());
        Context app = c.getApplicationContext();
        onUnauthorizedHook = () -> {
            logout();
            app.startActivity(new android.content.Intent(app, MainActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK));
        };
    }

    static boolean loggedIn() { return token != null; }

    static void saveLogin(String srv, String u, String t) {
        server = srv; user = u; token = t;
        prefs.edit().putString("server", srv).putString("user", u).putString("token", t).apply();
    }

    static void logout() {
        stopPoll();
        token = null; user = null;
        unread.clear();
        prefs.edit().remove("token").remove("user").apply();
    }

    static String normServer(String s) {
        s = s.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://" + s;
        return s;
    }

    static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        in.close();
        return bo.toByteArray();
    }

    /** Raw request. Throws IOException whose message is the server's error text. */
    static byte[] req(String base, String method, String path, byte[] body, String type, int readTimeout) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(readTimeout);
        c.setRequestMethod(method);
        if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", type);
            c.setFixedLengthStreamingMode(body.length);
            try (OutputStream o = c.getOutputStream()) { o.write(body); }
        }
        int code = c.getResponseCode();
        if (code >= 400) {
            InputStream es = c.getErrorStream();
            String m = es == null ? ("HTTP " + code) : new String(readAll(es), "UTF-8");
            if (code == 401 && token != null && !path.startsWith("/login")) ui.post(Api::onUnauthorized);
            throw new IOException(m.isEmpty() ? "HTTP " + code : m);
        }
        return readAll(c.getInputStream());
    }

    static Runnable onUnauthorizedHook;
    private static void onUnauthorized() { if (onUnauthorizedHook != null) onUnauthorizedHook.run(); }

    static String get(String path) throws IOException {
        return new String(req(server, "GET", path, null, null, 40000), "UTF-8");
    }

    static String postJson(String base, String path, JSONObject o) throws IOException {
        return new String(req(base, "POST", path, o.toString().getBytes("UTF-8"), "application/json", 20000), "UTF-8");
    }

    static String postJson(String path, JSONObject o) throws IOException { return postJson(server, path, o); }

    static JSONObject obj(Object... kv) {
        JSONObject o = new JSONObject();
        try { for (int i = 0; i < kv.length; i += 2) o.put((String) kv[i], kv[i + 1]); } catch (Exception ignored) {}
        return o;
    }

    static List<JSONObject> list(JSONArray a) {
        List<JSONObject> r = new ArrayList<>(a.length());
        for (int i = 0; i < a.length(); i++) r.add(a.optJSONObject(i));
        return r;
    }

    // ---------- long poll ----------

    private static int pollGen = 0;
    private static long last = -1;

    static synchronized void startPoll() {
        if (token == null) return;
        final int gen = ++pollGen;
        Thread t = new Thread(() -> pollLoop(gen), "poll");
        t.setDaemon(true);
        t.start();
    }

    static synchronized void stopPoll() { pollGen++; }

    private static synchronized boolean alive(int gen) { return gen == pollGen && token != null; }

    private static void pollLoop(int gen) {
        int backoff = 1000;
        while (alive(gen)) {
            try {
                String r = get(last < 0 ? "/poll" : "/poll?since=" + last);
                if (!alive(gen)) return;
                JSONObject o = new JSONObject(r);
                JSONArray ms = o.getJSONArray("msgs");
                last = Math.max(last, o.getLong("last"));
                backoff = 1000;
                if (ms.length() > 0) {
                    final List<JSONObject> l = list(ms);
                    ui.post(() -> {
                        if (!alive(gen)) return;
                        for (JSONObject m : l) {
                            String from = m.optString("from");
                            if (!from.equalsIgnoreCase(user) && !from.equalsIgnoreCase(ChatActivity.openPeer)) {
                                Integer n = unread.get(from);
                                unread.put(from, n == null ? 1 : n + 1);
                            }
                        }
                        if (listener != null) listener.onMessages(l);
                    });
                }
            } catch (Exception e) {
                try { Thread.sleep(backoff); } catch (InterruptedException ignored) {}
                backoff = Math.min(backoff * 2, 15000);
            }
        }
    }

    private Api() {}
}

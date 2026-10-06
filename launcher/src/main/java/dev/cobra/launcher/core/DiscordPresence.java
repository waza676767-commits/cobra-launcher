package dev.cobra.launcher.core;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Discord Rich Presence ("Playing Abyss Client") over Discord's local IPC socket. No library:
 * Discord listens on a Unix socket (discord-ipc-0…9) and speaks length-prefixed JSON frames.
 * Works with the normal, Flatpak and Snap Discord, and Vesktop/arRPC-style clients that expose
 * the same socket.
 *
 * <p>The launcher sets what to show; while Minecraft runs, Cobra Client writes
 * {@code config/cobra/presence.txt} (menu / singleplayer / the server address) and the launcher
 * turns that into the status line. A background thread keeps the connection alive and
 * reconnects when Discord starts later.
 */
public final class DiscordPresence {
    /** The Discord application behind the presence (its name is what shows after "Playing"). */
    public static final String DEFAULT_APP_ID = "1553693351633621163";   // the "Abyss Client" Discord application

    private static final Object LOCK = new Object();
    private static SocketChannel channel;
    /** Windows: Discord listens on a named pipe instead of a Unix socket. */
    private static java.io.RandomAccessFile pipe;
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");
    private static String details = "In the launcher", state = "Minecraft 1.21.11";
    /** What the small round icon shows: launcher, menu, singleplayer or server (art assets of the same names). */
    private static String kind = "launcher";
    private static String serverAddress = "";
    private static long startMs = System.currentTimeMillis();
    private static boolean dirty = true;
    private static Thread worker;
    private static volatile Path gameDir;
    private static String lastPresenceLine = "";

    private DiscordPresence() {}

    public static String appId() {
        String id = Settings.get().discordAppId == null ? "" : Settings.get().discordAppId.trim();
        return id.isEmpty() ? DEFAULT_APP_ID : id;
    }

    public static boolean enabled() {
        return Settings.get().discordRpc && appId().matches("\\d{15,21}");
    }

    /** Starts the background thread (idempotent). */
    public static synchronized void start() {
        if (worker != null) return;
        worker = new Thread(DiscordPresence::loop, "cobra-discord");
        worker.setDaemon(true);
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    /** In the launcher, nothing running. */
    public static void idle() {
        gameDir = null;
        kind = "launcher";
        set("In the launcher", "Getting ready to play", System.currentTimeMillis());
    }

    /** Minecraft just started from {@code dir}. */
    public static void playing(Path dir) {
        try {
            Files.deleteIfExists(dir.resolve("config").resolve("cobra").resolve("presence.txt"));   // last session's
        } catch (IOException ignored) {}
        gameDir = dir;
        lastPresenceLine = "";
        kind = "menu";
        set("Playing Minecraft 1.21.11", "In the menus", System.currentTimeMillis());
    }

    /** Settings changed (toggle / app id): drop the connection so the loop re-applies them. */
    public static void refresh() {
        synchronized (LOCK) {
            reconnect = true;
            dirty = true;
        }
    }

    private static boolean reconnect;

    private static void set(String d, String s, long start) {
        synchronized (LOCK) {
            details = d;
            state = s;
            startMs = start;
            dirty = true;
        }
    }

    // ------------------------------------------------------------------ worker

    /**
     * Only this thread talks to Discord. The UI thread just changes the fields under LOCK (never
     * waits on Discord), so a slow or silent Discord can't freeze the launcher.
     */
    private static void loop() {
        long nextConnect = 0;
        while (true) {
            try {
                Thread.sleep(2000);
                pollGame();
                boolean on, send, drop;
                synchronized (LOCK) {
                    on = enabled();
                    drop = reconnect;
                    reconnect = false;
                    send = dirty;
                    dirty = false;
                }
                if (drop) closeQuietly();
                if (!on) {
                    if (connected()) {
                        clearActivity();
                        closeQuietly();
                    }
                    continue;
                }
                if (!connected()) {
                    if (System.currentTimeMillis() < nextConnect) {
                        if (send) markDirty();
                        continue;
                    }
                    if (!connect()) {
                        nextConnect = System.currentTimeMillis() + 15_000;   // Discord not running
                        markDirty();
                        continue;
                    }
                    send = true;
                }
                if (send) {
                    sendActivity();
                    read();          // Discord answers every command; reading it keeps the pipe clear
                }
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                closeQuietly();
                markDirty();
            }
        }
    }

    private static void markDirty() {
        synchronized (LOCK) {
            dirty = true;
        }
    }

    /** Reads what Cobra Client reports from inside the game. */
    private static void pollGame() {
        Path dir = gameDir;
        if (dir == null) return;
        Path f = dir.resolve("config").resolve("cobra").resolve("presence.txt");
        String line;
        try {
            line = Files.exists(f) ? Files.readString(f).trim() : "";
        } catch (IOException e) {
            return;
        }
        if (line.equals(lastPresenceLine)) return;
        lastPresenceLine = line;
        String st, k;
        if (line.startsWith("server:")) {
            String addr = line.substring(7).trim();
            serverAddress = addr;
            st = Settings.get().discordShowServer && !addr.isEmpty() ? "On " + addr : "Playing multiplayer";
            k = "server";
        } else if (line.equals("singleplayer")) {
            st = "Playing singleplayer";
            k = "singleplayer";
        } else {
            st = "In the menus";
            k = "menu";
        }
        synchronized (LOCK) {
            if (!st.equals(state) || !k.equals(kind)) {
                state = st;
                kind = k;
                dirty = true;
            }
        }
    }

    private static List<Path> sockets() {
        List<String> bases = new ArrayList<>();
        String rt = System.getenv("XDG_RUNTIME_DIR");
        if (rt != null && !rt.isBlank()) {
            bases.add(rt);
            bases.add(rt + "/app/com.discordapp.Discord");        // Flatpak Discord
            bases.add(rt + "/app/dev.vencord.Vesktop");            // Flatpak Vesktop
            bases.add(rt + "/snap.discord");                       // Snap Discord
        }
        for (String v : new String[]{"TMPDIR", "TMP", "TEMP"}) {
            String t = System.getenv(v);
            if (t != null && !t.isBlank()) bases.add(t);
        }
        bases.add("/tmp");
        List<Path> out = new ArrayList<>();
        for (String b : bases) for (int i = 0; i < 10; i++) out.add(Path.of(b, "discord-ipc-" + i));
        return out;
    }

    private static boolean connected() {
        return channel != null || pipe != null;
    }

    private static boolean connect() {
        if (WINDOWS) {
            for (int i = 0; i < 10; i++) {
                try {
                    pipe = new java.io.RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
                    JsonObject hello = new JsonObject();
                    hello.addProperty("v", 1);
                    hello.addProperty("client_id", appId());
                    write(0, hello);
                    read();
                    return true;
                } catch (Exception e) {
                    closeQuietly();
                }
            }
            return false;
        }
        for (Path p : sockets()) {
            if (!Files.exists(p)) continue;
            try {
                SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
                ch.connect(UnixDomainSocketAddress.of(p));
                channel = ch;
                JsonObject hello = new JsonObject();
                hello.addProperty("v", 1);
                hello.addProperty("client_id", appId());
                write(0, hello);
                read();          // READY (or an error frame, which throws below)
                return true;
            } catch (Exception e) {
                closeQuietly();
            }
        }
        return false;
    }

    private static void sendActivity() throws IOException {
        String d, st;
        long start;
        synchronized (LOCK) {
            d = details;
            st = state;
            start = startMs;
        }
        Settings cfg = Settings.get();
        if (cfg.discordShowProfile) {
            try {
                String prof = Profiles.current().name;
                if (prof != null && !prof.isBlank()) st = st + " \u00b7 " + prof;
            } catch (Exception ignored) {}
        }
        if (cfg.discordDetails != null && !cfg.discordDetails.isBlank()) d = cfg.discordDetails.trim();
        if (cfg.discordState != null && !cfg.discordState.isBlank()) st = cfg.discordState.trim();
        JsonObject activity = new JsonObject();
        activity.addProperty("details", d.length() > 120 ? d.substring(0, 120) : d);
        activity.addProperty("state", st.length() > 120 ? st.substring(0, 120) : st);
        if (cfg.discordShowTime) {
            JsonObject ts = new JsonObject();
            ts.addProperty("start", start / 1000);
            activity.add("timestamps", ts);
        }
        if (cfg.discordButton && !Updater.REPO.isEmpty()) {
            com.google.gson.JsonArray buttons = new com.google.gson.JsonArray();
            JsonObject b = new JsonObject();
            b.addProperty("label", "Get Abyss Client");
            b.addProperty("url", "https://github.com/" + Updater.REPO + "/releases/latest");
            buttons.add(b);
            activity.add("buttons", buttons);
        }
        if (!cfg.discordShowActivity) activity.remove("state");
        JsonObject assets = new JsonObject();
        assets.addProperty("large_image", "logo");          // art assets uploaded to the Discord app (see README)
        assets.addProperty("large_text", "Abyss Client " + BuildInfo.VERSION);
        if (cfg.discordSmallIcon) {
            String k;
            synchronized (LOCK) {
                k = kind;
            }
            assets.addProperty("small_image", k);
            assets.addProperty("small_text", switch (k) {
                case "server" -> cfg.discordShowServer && !serverAddress.isEmpty() ? "On " + serverAddress : "Multiplayer";
                case "singleplayer" -> "Singleplayer";
                case "menu" -> "In the menus";
                default -> "Abyss Launcher";
            });
        }
        activity.add("assets", assets);
        command(activity);
    }

    private static void clearActivity() {
        try {
            command(null);
        } catch (Exception ignored) {}
    }

    private static void command(JsonObject activity) throws IOException {
        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());
        if (activity != null) args.add("activity", activity);
        JsonObject msg = new JsonObject();
        msg.addProperty("cmd", "SET_ACTIVITY");
        msg.add("args", args);
        msg.addProperty("nonce", UUID.randomUUID().toString());
        write(1, msg);
    }

    private static void write(int op, JsonObject json) throws IOException {
        byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(op).putInt(body.length).put(body).flip();
        if (pipe != null) {
            pipe.write(buf.array(), 0, buf.limit());
            return;
        }
        while (buf.hasRemaining()) channel.write(buf);
    }

    /** Blocking read of one frame; throws if Discord closed the pipe or refused the handshake. */
    private static String read() throws IOException {
        ByteBuffer head = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        readFully(head);
        head.flip();
        int op = head.getInt(), len = head.getInt();
        if (len < 0 || len > 1 << 20) throw new IOException("bad frame");
        ByteBuffer body = ByteBuffer.allocate(len);
        readFully(body);
        String s = new String(body.array(), StandardCharsets.UTF_8);
        if (op == 2) throw new IOException("Discord closed the connection: " + s);   // CLOSE (e.g. bad app id)
        return s;
    }

    private static void readFully(ByteBuffer b) throws IOException {
        if (pipe != null) {
            byte[] tmp = new byte[b.remaining()];
            pipe.readFully(tmp);
            b.put(tmp);
            return;
        }
        while (b.hasRemaining()) if (channel.read(b) < 0) throw new IOException("closed");
    }

    private static void closeQuietly() {
        if (pipe != null) {
            try {
                pipe.close();
            } catch (IOException ignored) {}
            pipe = null;
        }
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException ignored) {}
        }
        channel = null;
    }
}

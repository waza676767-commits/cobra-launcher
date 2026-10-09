package dev.life.launcher.game;

import com.google.gson.reflect.TypeToken;
import dev.life.launcher.core.Http;
import dev.life.launcher.core.Paths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public final class Playtime {
    private static final Path FILE = Paths.ROOT.resolve("playtime.json");
    private static final Path ACTIVE = Paths.ROOT.resolve("playtime-active.json");

    public static final class Session {
        public long start;
        public long end;
        public String version;

        public Session() {}

        public Session(long start, long end, String version) {
            this.start = start;
            this.end = end;
            this.version = version;
        }

        public long seconds() { return Math.max(0, (end - start) / 1000); }
    }

    private static List<Session> sessions;

    private Playtime() {}

    public static synchronized List<Session> all() {
        if (sessions == null) {
            try {
                sessions = Files.exists(FILE) ? Http.GSON.fromJson(Files.readString(FILE), new TypeToken<List<Session>>() {}.getType()) : null;
            } catch (Exception ignored) {}
            if (sessions == null) sessions = new ArrayList<>();
            recoverCrashedSession();
        }
        return sessions;
    }

    /** Called every minute while the game runs so a killed launcher still keeps the time. */
    public static synchronized void checkpoint(long start, String version) {
        try {
            Files.writeString(ACTIVE, Http.GSON.toJson(new Session(start, System.currentTimeMillis(), version)));
        } catch (Exception ignored) {}
    }

    public static synchronized void finish(long start, String version) {
        all().add(new Session(start, System.currentTimeMillis(), version));
        try {
            Files.deleteIfExists(ACTIVE);
        } catch (Exception ignored) {}
        save();
    }

    private static void recoverCrashedSession() {
        try {
            if (!Files.exists(ACTIVE)) return;
            Session s = Http.GSON.fromJson(Files.readString(ACTIVE), Session.class);
            Files.deleteIfExists(ACTIVE);
            if (s != null && s.end > s.start) {
                sessions.add(s);
                save();
            }
        } catch (Exception ignored) {}
    }

    private static void save() {
        try {
            Files.writeString(FILE, Http.GSON.toJson(sessions));
        } catch (Exception ignored) {}
    }

    /** Seconds played on each of the last {@code days} days; index {@code days-1} is today. */
    public static synchronized long[] lastDays(int days) {
        long[] out = new long[days];
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        for (int i = 0; i < days; i++) {
            LocalDate d = today.minusDays(days - 1 - i);
            long dayStart = d.atStartOfDay(zone).toInstant().toEpochMilli();
            long dayEnd = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            for (Session s : all()) {
                long a = Math.max(s.start, dayStart), b = Math.min(s.end, dayEnd);
                if (b > a) out[i] += (b - a) / 1000;
            }
        }
        return out;
    }

    public static synchronized long totalSeconds(String version, long sinceMillis) {
        long t = 0;
        for (Session s : all()) {
            if (s.end < sinceMillis) continue;
            if (version != null && !version.equals(s.version)) continue;
            t += (s.end - Math.max(s.start, sinceMillis)) / 1000;
        }
        return t;
    }

    public static synchronized int sessionsSince(long sinceMillis) {
        int n = 0;
        for (Session s : all()) if (s.end >= sinceMillis) n++;
        return n;
    }

    public static synchronized long longestSince(long sinceMillis) {
        long m = 0;
        for (Session s : all()) if (s.end >= sinceMillis) m = Math.max(m, s.seconds());
        return m;
    }

    public static String format(long seconds) {
        long h = seconds / 3600, m = (seconds % 3600) / 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m";
        return seconds > 0 ? "<1m" : "0m";
    }
}

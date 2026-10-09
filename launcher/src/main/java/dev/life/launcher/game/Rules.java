package dev.life.launcher.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.life.launcher.core.Paths;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class Rules {
    private Rules() {}

    static boolean allowed(JsonArray rules, Map<String, Boolean> features) {
        if (rules == null || rules.isEmpty()) return true;
        boolean allow = false;
        for (JsonElement e : rules) {
            JsonObject r = e.getAsJsonObject();
            if (matches(r, features)) allow = "allow".equals(r.get("action").getAsString());
        }
        return allow;
    }

    private static boolean matches(JsonObject rule, Map<String, Boolean> features) {
        if (rule.has("os")) {
            JsonObject os = rule.getAsJsonObject("os");
            if (os.has("name") && !os.get("name").getAsString().equals(Paths.OS_NAME)) return false;
            if (os.has("arch")) {
                String want = os.get("arch").getAsString();
                String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
                boolean is32 = arch.equals("x86") || arch.equals("i386") || arch.equals("i686");
                if (want.equals("x86") && !is32) return false;
                if (want.equals("arm64") && !Paths.ARM) return false;
            }
            if (os.has("version")) {
                try {
                    if (!Pattern.compile(os.get("version").getAsString()).matcher(System.getProperty("os.version", "")).find()) return false;
                } catch (Exception ignored) {}
            }
        }
        if (rule.has("features")) {
            for (Map.Entry<String, JsonElement> f : rule.getAsJsonObject("features").entrySet()) {
                if (features.getOrDefault(f.getKey(), false) != f.getValue().getAsBoolean()) return false;
            }
        }
        return true;
    }
}

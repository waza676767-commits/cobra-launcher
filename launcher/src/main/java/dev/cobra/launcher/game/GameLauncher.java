package dev.cobra.launcher.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.cobra.launcher.auth.Account;
import dev.cobra.launcher.core.BuildInfo;
import dev.cobra.launcher.core.Paths;
import dev.cobra.launcher.core.Settings;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class GameLauncher {
    private static final Pattern VAR = Pattern.compile("\\$\\{([a-zA-Z0-9_]+)}");

    private GameLauncher() {}

    public static Path logFile(GameVersion gv) {
        return Paths.LOGS.resolve(gv.id + "-latest.log");
    }

    public static Process start(GameVersion gv, Installer.Prepared prep, Account acc, Settings s) throws IOException {
        JsonObject v = prep.version;
        boolean customRes = !s.fullscreen && s.width > 0 && s.height > 0;

        Map<String, String> vars = new HashMap<>();
        vars.put("auth_player_name", acc.name);
        vars.put("version_name", v.get("id").getAsString());
        vars.put("game_directory", prep.gameDir.toAbsolutePath().toString());
        vars.put("assets_root", Paths.ASSETS.toAbsolutePath().toString());
        vars.put("game_assets", Paths.ASSETS.toAbsolutePath().toString());
        vars.put("assets_index_name", prep.assetIndex);
        vars.put("auth_uuid", acc.uuid);
        vars.put("auth_access_token", acc.mcToken);
        vars.put("auth_session", "token:" + acc.mcToken + ":" + acc.uuid);
        vars.put("auth_xuid", acc.xuid == null ? "0" : acc.xuid);
        vars.put("clientid", "0");
        vars.put("user_type", acc.offline() ? "legacy" : "msa");
        vars.put("user_properties", "{}");
        vars.put("version_type", "Cobra");
        vars.put("natives_directory", prep.nativesDir.toAbsolutePath().toString());
        vars.put("launcher_name", "CobraLauncher");
        vars.put("launcher_version", BuildInfo.VERSION);
        vars.put("library_directory", Paths.LIBRARIES.toAbsolutePath().toString());
        vars.put("classpath_separator", File.pathSeparator);
        vars.put("classpath", prep.classpath.stream().map(p -> p.toAbsolutePath().toString()).collect(Collectors.joining(File.pathSeparator)));
        vars.put("resolution_width", String.valueOf(s.width));
        vars.put("resolution_height", String.valueOf(s.height));

        Map<String, Boolean> features = new HashMap<>();
        features.put("has_custom_resolution", customRes);

        List<String> cmd = new ArrayList<>();
        cmd.add(prep.javaExe.toAbsolutePath().toString());
        cmd.add("-Xmx" + s.ramMb + "M");
        cmd.add("-Xms" + Math.min(s.ramMb, 2048) + "M");
        cmd.add("-XX:+UseG1GC");
        cmd.add("-XX:+UnlockExperimentalVMOptions");
        cmd.add("-XX:G1NewSizePercent=20");
        cmd.add("-XX:G1ReservePercent=20");
        cmd.add("-XX:MaxGCPauseMillis=50");
        cmd.add("-XX:G1HeapRegionSize=32M");
        cmd.add("-Dcobra.launcher=" + BuildInfo.VERSION);
        cmd.add("-Dcobra.theme=" + (s.lightMode ? "light" : "dark"));
        if (acc.offline()) cmd.add("-Dcobra.offline=true");
        // Screen Recorder: where videos go (shown in the launcher's Recordings page) and which ffmpeg to use
        cmd.add("-Dcobra.recordings=" + dev.cobra.launcher.ui.pages.RecordingsPage.DIR.toAbsolutePath());
        // in-game "Update to the newest": where releases come from and where the launcher picks them up
        cmd.add("-Dcobra.repo=" + dev.cobra.launcher.core.Updater.REPO);
        cmd.add("-Dcobra.build=" + dev.cobra.launcher.core.Updater.BUILD);
        cmd.add("-Dcobra.root=" + dev.cobra.launcher.core.Paths.ROOT.toAbsolutePath());
        String ffmpeg = dev.cobra.launcher.core.FFmpeg.command();
        if (ffmpeg != null) cmd.add("-Dcobra.ffmpeg=" + ffmpeg);
        else new Thread(() -> {   // not there yet: fetch it for next time
            try {
                dev.cobra.launcher.core.FFmpeg.ensure(null);
            } catch (Exception ignored) {}
        }, "cobra-ffmpeg").start();
        if (!gv.vanilla()) {   // Cobra Client + Fabric API from the launcher's own folder (see Installer.managedMods)
            cmd.add("-Dfabric.addMods=" + Installer.managedMods(gv).toAbsolutePath());
        }

        JsonObject args = v.has("arguments") ? v.getAsJsonObject("arguments") : null;
        if (args != null && args.has("jvm")) {
            addArgs(args.getAsJsonArray("jvm"), cmd, vars, features);
        } else {
            cmd.add(sub("-Djava.library.path=${natives_directory}", vars));
            cmd.add("-Dminecraft.launcher.brand=CobraLauncher");
            cmd.add("-Dminecraft.launcher.version=" + BuildInfo.VERSION);
            cmd.add("-Dfml.ignoreInvalidMinecraftCertificates=true");
            cmd.add("-Dfml.ignorePatchDiscrepancies=true");
            cmd.add("-cp");
            cmd.add(vars.get("classpath"));
        }
        if (prep.logArgument != null) cmd.add(prep.logArgument);
        if (s.jvmArgs != null && !s.jvmArgs.isBlank()) {
            for (String a : s.jvmArgs.trim().split("\\s+")) cmd.add(a);
        }
        cmd.add(v.get("mainClass").getAsString());

        if (args != null && args.has("game")) {
            addArgs(args.getAsJsonArray("game"), cmd, vars, features);
        } else if (v.has("minecraftArguments")) {
            for (String a : v.get("minecraftArguments").getAsString().split(" ")) if (!a.isEmpty()) cmd.add(sub(a, vars));
            if (customRes) {
                cmd.add("--width"); cmd.add(String.valueOf(s.width));
                cmd.add("--height"); cmd.add(String.valueOf(s.height));
            }
        }
        if (s.fullscreen) cmd.add("--fullscreen");

        ProcessBuilder pb = new ProcessBuilder(cmd).directory(prep.gameDir.toFile()).redirectErrorStream(true);
        // Don't leak AppImage runtime variables into the game.
        pb.environment().remove("LD_LIBRARY_PATH");
        pb.environment().remove("LD_PRELOAD");
        pb.environment().remove("JAVA_TOOL_OPTIONS");
        pb.environment().remove("_JAVA_OPTIONS");
        Process proc = pb.start();
        pump(proc, logFile(gv), acc.mcToken);
        return proc;
    }

    private static void addArgs(JsonArray arr, List<String> out, Map<String, String> vars, Map<String, Boolean> features) {
        for (JsonElement e : arr) {
            if (e.isJsonPrimitive()) {
                out.add(sub(e.getAsString(), vars));
                continue;
            }
            JsonObject o = e.getAsJsonObject();
            if (!Rules.allowed(o.getAsJsonArray("rules"), features)) continue;
            JsonElement val = o.get("value");
            if (val.isJsonArray()) for (JsonElement x : val.getAsJsonArray()) out.add(sub(x.getAsString(), vars));
            else out.add(sub(val.getAsString(), vars));
        }
    }

    private static String sub(String s, Map<String, String> vars) {
        Matcher m = VAR.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String val = vars.getOrDefault(m.group(1), "");
            m.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static void pump(Process proc, Path log, String secret) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8));
                 Writer w = Files.newBufferedWriter(log, StandardCharsets.UTF_8)) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (secret != null && !secret.isEmpty()) line = line.replace(secret, "<token>");
                    w.write(line);
                    w.write('\n');
                    w.flush();
                }
            } catch (IOException ignored) {}
        }, "cobra-game-log");
        t.setDaemon(true);
        t.start();
    }
}

package dev.cobra.launcher.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.StringJoiner;

public final class Http {
    public static final String USER_AGENT = "CobraLauncher/1.0.0 (Linux; +https://github.com/ItzLucaPK)";
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private Http() {}

    public record Response(int code, String body) {
        public boolean ok() { return code >= 200 && code < 300; }
        /**
         * The body as a JSON object; an empty object when it isn't JSON (an error page from a
         * proxy, a firewall or a server that's down), so callers see "missing fields", not a crash.
         */
        public JsonObject json() {
            try {
                JsonElement e = JsonParser.parseString(body.isEmpty() ? "{}" : body);
                return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
            } catch (RuntimeException notJson) {
                return new JsonObject();
            }
        }

        public JsonElement element() {
            try {
                return JsonParser.parseString(body);
            } catch (RuntimeException notJson) {
                return new JsonObject();
            }
        }
    }

    private static HttpRequest.Builder req(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("User-Agent", USER_AGENT);
    }

    public static Response send(HttpRequest request) throws IOException {
        try {
            HttpResponse<String> r = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return new Response(r.statusCode(), r.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    public static Response get(String url, String... headers) throws IOException {
        HttpRequest.Builder b = req(url).GET();
        for (int i = 0; i + 1 < headers.length; i += 2) b.header(headers[i], headers[i + 1]);
        return send(b.build());
    }

    public static JsonElement getJson(String url) throws IOException {
        Response r = get(url, "Accept", "application/json");
        if (!r.ok()) throw new IOException("HTTP " + r.code() + " for " + url);
        return r.element();
    }

    public static Response postJson(String url, JsonObject body, String... headers) throws IOException {
        HttpRequest.Builder b = req(url).header("Content-Type", "application/json").header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));
        for (int i = 0; i + 1 < headers.length; i += 2) b.header(headers[i], headers[i + 1]);
        return send(b.build());
    }

    public static Response postForm(String url, Map<String, String> form) throws IOException {
        StringJoiner j = new StringJoiner("&");
        form.forEach((k, v) -> j.add(enc(k) + "=" + enc(v)));
        return send(req(url).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(j.toString())).build());
    }

    public static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Downloads to a temp file then moves it into place. Retries 3 times. */
    public static void download(String url, Path target, String sha1) throws IOException {
        Files.createDirectories(target.getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                HttpResponse<InputStream> r = CLIENT.send(req(url).timeout(Duration.ofMinutes(5)).GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                if (r.statusCode() != 200) {
                    r.body().close();
                    throw new IOException("HTTP " + r.statusCode() + " for " + url);
                }
                try (InputStream in = r.body()) {
                    Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
                }
                if (sha1 != null && !sha1.isEmpty() && !sha1.equalsIgnoreCase(sha1(part))) {
                    throw new IOException("Checksum mismatch for " + url);
                }
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted", e);
            } catch (IOException e) {
                last = e;
            }
        }
        Files.deleteIfExists(part);
        throw last;
    }

    public static String sha1(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    public static byte[] bytes(String url) throws IOException {
        try {
            HttpResponse<byte[]> r = CLIENT.send(req(url).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode());
            return r.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }
}

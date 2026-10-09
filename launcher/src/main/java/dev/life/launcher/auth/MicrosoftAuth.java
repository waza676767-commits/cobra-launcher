package dev.life.launcher.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.life.launcher.core.Http;
import dev.life.launcher.core.Settings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Microsoft OAuth device-code flow. Needs an Azure app registration ("Personal Microsoft accounts",
 * public client flows enabled) that Mojang has approved for the Minecraft services API.
 */
public final class MicrosoftAuth {
    private static final String SCOPE = "XboxLive.signin offline_access";
    private static final SecureRandom RNG = new SecureRandom();

    /** consumers = personal Microsoft accounts (what Minecraft uses); common also accepts work/school sign-in pages. */
    private static String base() {
        String tenant = Settings.get().msTenant;
        if (tenant == null || tenant.isBlank()) tenant = "consumers";
        return "https://login.microsoftonline.com/" + tenant.trim() + "/oauth2/v2.0/";
    }

    private MicrosoftAuth() {}

    public static final class AuthException extends Exception {
        public AuthException(String msg) { super(msg); }
    }

    public record DeviceCode(String userCode, String deviceCode, String verificationUri, int interval, long expiresAt) {}

    /** @param live true for tokens from login.live.com (the no-setup sign-in), false for Azure (login.microsoftonline.com) */
    /**
     * @param live true for login.live.com tokens, false for Azure (login.microsoftonline.com)
     * @param link true for the one-click sign-in (its Xbox ticket needs the "t=" prefix)
     */
    public record MsTokens(String access, String refresh, boolean live, boolean link) {
        public MsTokens(String access, String refresh) { this(access, refresh, false, false); }

        public MsTokens(String access, String refresh, boolean live) { this(access, refresh, live, false); }
    }

    // ------------------------------------------------------------ no-setup sign-in (login.live.com)

    /**
     * Sign-in that needs no Azure app: the Microsoft account page of Minecraft's own launcher.
     * Microsoft sends the browser to a blank page whose address carries the sign-in code; the
     * launcher picks that address up from the clipboard (or the user pastes it).
     */
    public static final String LIVE_CLIENT = "00000000402b5328";
    public static final String LIVE_REDIRECT = "https://login.live.com/oauth20_desktop.srf";
    private static final String LIVE_SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";

    public static String liveAuthorizeUrl() {
        return "https://login.live.com/oauth20_authorize.srf"
                + "?client_id=" + LIVE_CLIENT
                + "&response_type=code"
                + "&scope=" + Http.enc(LIVE_SCOPE)
                + "&redirect_uri=" + Http.enc(LIVE_REDIRECT)
                + "&prompt=select_account";
    }

    /** The sign-in code from a pasted redirect address (or a bare code), or null if there's none. */
    public static String extractLiveCode(String pasted) {
        if (pasted == null) return null;
        String s = pasted.trim();
        if (s.isEmpty()) return null;
        int q = s.indexOf('?');
        if (q >= 0 || s.contains("code=")) {
            String query = q >= 0 ? s.substring(q + 1) : s;
            int hash = query.indexOf('#');
            if (hash >= 0) query = query.substring(0, hash);
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0 && pair.substring(0, eq).equals("code")) {
                    String code = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8).trim();
                    return code.isEmpty() ? null : code;
                }
            }
            return null;
        }
        // a bare code copied on its own (they look like M.C5xx_BAY.2.U.xxxxxxxx)
        return s.matches("M\\.[A-Za-z0-9_.!*$-]{10,}") ? s : null;
    }

    /** Error text if the pasted address is Microsoft saying no (e.g. the user cancelled). */
    public static String liveError(String pasted) {
        if (pasted == null || !pasted.contains("oauth20_desktop.srf")) return null;
        Map<String, String> p = queryOf("GET " + pasted.trim().replaceFirst("^[a-z]+://[^/]+", "") + " HTTP/1.1");
        String e = p.get("error_description") != null ? p.get("error_description") : p.get("error");
        return e == null || e.isBlank() ? null : e;
    }

    public static MsTokens liveRedeem(String code) throws AuthException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", LIVE_CLIENT);
        form.put("code", code);
        form.put("grant_type", "authorization_code");
        form.put("redirect_uri", LIVE_REDIRECT);
        form.put("scope", LIVE_SCOPE);
        return liveToken(form, null);
    }

    public static MsTokens liveRefresh(String refreshToken) throws AuthException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", LIVE_CLIENT);
        form.put("refresh_token", refreshToken);
        form.put("grant_type", "refresh_token");
        form.put("redirect_uri", LIVE_REDIRECT);
        form.put("scope", LIVE_SCOPE);
        return liveToken(form, refreshToken);
    }

    private static MsTokens liveToken(Map<String, String> form, String oldRefresh) throws AuthException {
        try {
            Http.Response r = Http.postForm("https://login.live.com/oauth20_token.srf", form);
            JsonObject j = r.json();
            if (!r.ok()) {
                if (oldRefresh != null) throw new AuthException("Your session expired. Sign in again.");
                String err = str(j, "error_description");
                if (err.isEmpty()) err = str(j, "error");
                throw new AuthException(err.contains("expired") || err.contains("invalid_grant") || err.isEmpty()
                        ? "That sign-in code expired or was already used. Sign in again." : err);
            }
            String refresh = str(j, "refresh_token");
            return new MsTokens(j.get("access_token").getAsString(), refresh.isEmpty() && oldRefresh != null ? oldRefresh : refresh, true);
        } catch (IOException e) {
            throw new AuthException("Can't reach Microsoft. Check your connection.");
        }
    }

    // ------------------------------------------------------------ one-click sign-in (no pasting)

    /**
     * Microsoft sign-in with nothing to copy or paste: a device code from Microsoft's consumer
     * login (the Minecraft title used by consoles and many launchers/bots), opened as
     * microsoft.com/link?otc=CODE so the code is already filled in. You sign in there and the
     * launcher notices by itself.
     */
    private static final String LINK_CLIENT = "00000000441cc96b";

    public static DeviceCode linkStart() throws AuthException {
        try {
            Map<String, String> form = new LinkedHashMap<>();
            form.put("client_id", LINK_CLIENT);
            form.put("scope", LIVE_SCOPE);
            form.put("response_type", "device_code");
            Http.Response r = Http.postForm("https://login.live.com/oauth20_connect.srf", form);
            JsonObject j = r.json();
            if (!r.ok()) throw new AuthException(msError(j));
            String code = j.get("user_code").getAsString();
            String uri = str(j, "verification_uri");
            if (uri.isEmpty()) uri = "https://www.microsoft.com/link";
            return new DeviceCode(code, j.get("device_code").getAsString(), uri,
                    j.has("interval") ? j.get("interval").getAsInt() : 5,
                    System.currentTimeMillis() + (j.has("expires_in") ? j.get("expires_in").getAsLong() : 900) * 1000L);
        } catch (IOException e) {
            throw new AuthException("Can't reach Microsoft. Check your connection.");
        }
    }

    /** Link page with the code already typed in. */
    public static String linkUrl(DeviceCode dc) {
        return "https://www.microsoft.com/link?otc=" + Http.enc(dc.userCode());
    }

    public static MsTokens linkPoll(DeviceCode dc, BooleanSupplier cancelled) throws AuthException {
        int interval = Math.max(1, dc.interval());
        while (System.currentTimeMillis() < dc.expiresAt()) {
            try {
                Thread.sleep(interval * 1000L);
            } catch (InterruptedException e) {
                throw new AuthException("Sign-in cancelled.");
            }
            if (cancelled.getAsBoolean()) throw new AuthException("Sign-in cancelled.");
            try {
                Map<String, String> form = new LinkedHashMap<>();
                form.put("client_id", LINK_CLIENT);
                form.put("device_code", dc.deviceCode());
                form.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
                Http.Response r = Http.postForm("https://login.live.com/oauth20_token.srf", form);
                JsonObject j = r.json();
                if (r.ok()) return new MsTokens(j.get("access_token").getAsString(), str(j, "refresh_token"), true, true);
                switch (str(j, "error")) {
                    case "authorization_pending": continue;
                    case "slow_down": interval += 5; continue;
                    case "authorization_declined", "access_denied": throw new AuthException("Sign-in was declined in the browser.");
                    case "expired_token": throw new AuthException("The code expired. Start sign-in again.");
                    default: throw new AuthException(msError(j));
                }
            } catch (IOException e) {
                // transient network error: keep polling
            }
        }
        throw new AuthException("The code expired. Start sign-in again.");
    }

    public static MsTokens linkRefresh(String refreshToken) throws AuthException {
        try {
            Map<String, String> form = new LinkedHashMap<>();
            form.put("client_id", LINK_CLIENT);
            form.put("refresh_token", refreshToken);
            form.put("grant_type", "refresh_token");
            form.put("scope", LIVE_SCOPE);
            Http.Response r = Http.postForm("https://login.live.com/oauth20_token.srf", form);
            JsonObject j = r.json();
            if (!r.ok()) throw new AuthException("Your session expired. Sign in again.");
            String refresh = str(j, "refresh_token");
            return new MsTokens(j.get("access_token").getAsString(), refresh.isEmpty() ? refreshToken : refresh, true, true);
        } catch (IOException e) {
            throw new AuthException("Can't reach Microsoft. Check your connection.");
        }
    }

    public static DeviceCode requestDeviceCode(String clientId) throws AuthException {
        if (clientId == null || clientId.isBlank()) {
            throw new AuthException("No Microsoft client ID set. Add yours in Settings → Account.");
        }
        try {
            Http.Response r = Http.postForm(base() + "devicecode", Map.of("client_id", clientId, "scope", SCOPE));
            JsonObject j = r.json();
            if (!r.ok()) throw new AuthException(msError(j));
            return new DeviceCode(j.get("user_code").getAsString(), j.get("device_code").getAsString(),
                    j.get("verification_uri").getAsString(), j.has("interval") ? j.get("interval").getAsInt() : 5,
                    System.currentTimeMillis() + j.get("expires_in").getAsLong() * 1000L);
        } catch (IOException e) {
            throw new AuthException("Can't reach Microsoft. Check your connection.");
        }
    }

    public static MsTokens poll(String clientId, DeviceCode dc, BooleanSupplier cancelled) throws AuthException {
        int interval = Math.max(1, dc.interval());
        while (System.currentTimeMillis() < dc.expiresAt()) {
            try {
                Thread.sleep(interval * 1000L);
            } catch (InterruptedException e) {
                throw new AuthException("Sign-in cancelled.");
            }
            if (cancelled.getAsBoolean()) throw new AuthException("Sign-in cancelled.");
            try {
                Http.Response r = Http.postForm(base() + "token", Map.of(
                        "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                        "client_id", clientId,
                        "device_code", dc.deviceCode()));
                JsonObject j = r.json();
                if (r.ok()) return new MsTokens(j.get("access_token").getAsString(), str(j, "refresh_token"));
                switch (str(j, "error")) {
                    case "authorization_pending": continue;
                    case "slow_down": interval += 5; continue;
                    case "authorization_declined": throw new AuthException("Sign-in was declined in the browser.");
                    case "expired_token": throw new AuthException("The code expired. Start sign-in again.");
                    default: throw new AuthException(msError(j));
                }
            } catch (IOException e) {
                // transient network error: keep polling
            }
        }
        throw new AuthException("The code expired. Start sign-in again.");
    }

    public static MsTokens refresh(String clientId, String refreshToken) throws AuthException {
        try {
            Http.Response r = Http.postForm(base() + "token", Map.of(
                    "grant_type", "refresh_token", "client_id", clientId,
                    "refresh_token", refreshToken, "scope", SCOPE));
            JsonObject j = r.json();
            if (!r.ok()) throw new AuthException("Your session expired. Sign in again.");
            String newRefresh = str(j, "refresh_token");
            return new MsTokens(j.get("access_token").getAsString(), newRefresh.isEmpty() ? refreshToken : newRefresh);
        } catch (IOException e) {
            throw new AuthException("Can't reach Microsoft. Check your connection.");
        }
    }

    /**
     * Normal sign-in: opens the Microsoft page in the browser and catches the redirect on a
     * loopback port (same approach as Prism/Lunar). No codes to type.
     *
     * @param onUrl receives the authorize URL so the caller can open a browser and offer a copy button
     */
    public static MsTokens browserSignIn(String clientId, Consumer<String> onUrl, BooleanSupplier cancelled) throws AuthException {
        if (clientId == null || clientId.isBlank()) {
            throw new AuthException("No Microsoft client ID set. Add yours in Settings → Account.");
        }
        String verifier = randomToken(64);
        String challenge = s256(verifier);
        String state = randomToken(16);
        try (ServerSocket server = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(500);
            String redirect = "http://localhost:" + server.getLocalPort();
            String url = base() + "authorize"
                    + "?client_id=" + Http.enc(clientId)
                    + "&response_type=code"
                    + "&redirect_uri=" + Http.enc(redirect)
                    + "&response_mode=query"
                    + "&scope=" + Http.enc(SCOPE)
                    + "&state=" + state
                    + "&prompt=select_account"
                    + "&code_challenge=" + challenge
                    + "&code_challenge_method=S256";
            onUrl.accept(url);

            long deadline = System.currentTimeMillis() + 5 * 60_000L;
            String code = null;
            while (code == null) {
                if (cancelled.getAsBoolean()) throw new AuthException("Sign-in cancelled.");
                if (System.currentTimeMillis() > deadline) throw new AuthException("Sign-in timed out. Try again.");
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    String request = in.readLine();
                    Map<String, String> params = queryOf(request);
                    String error = params.get("error_description") != null ? params.get("error_description") : params.get("error");
                    boolean done = params.containsKey("code") || error != null;
                    respond(socket, done ? (error == null ? "Signed in. You can close this tab and go back to Life Launcher."
                            : "Sign-in failed: " + error) : "Waiting for Microsoft…");
                    if (error != null) throw new AuthException(error.split("\\r?\\n")[0]);
                    if (params.containsKey("code")) {
                        if (!state.equals(params.get("state"))) throw new AuthException("Sign-in response didn't match this request. Try again.");
                        code = params.get("code");
                    }
                } catch (SocketTimeoutException ignored) {
                    // keep waiting
                }
            }

            Map<String, String> form = new LinkedHashMap<>();
            form.put("client_id", clientId);
            form.put("grant_type", "authorization_code");
            form.put("code", code);
            form.put("redirect_uri", redirect);
            form.put("code_verifier", verifier);
            form.put("scope", SCOPE);
            Http.Response r = Http.postForm(base() + "token", form);
            JsonObject j = r.json();
            if (!r.ok()) throw new AuthException(msError(j));
            return new MsTokens(j.get("access_token").getAsString(), str(j, "refresh_token"));
        } catch (IOException e) {
            throw new AuthException("Couldn't open a local port for sign-in: " + e.getMessage());
        }
    }

    private static void respond(Socket socket, String message) throws IOException {
        String html = "<!doctype html><html><head><meta charset=\"utf-8\"><title>Life Launcher</title></head>"
                + "<body style=\"margin:0;height:100vh;display:flex;align-items:center;justify-content:center;"
                + "background:#0c0c0d;color:#fff;font-family:system-ui,sans-serif\">"
                + "<p style=\"font-size:18px\">" + message + "</p></body></html>";
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        OutputStream out = socket.getOutputStream();
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + body.length
                + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    private static Map<String, String> queryOf(String requestLine) {
        Map<String, String> out = new HashMap<>();
        if (requestLine == null) return out;
        String[] parts = requestLine.split(" ");
        if (parts.length < 2 || !parts[1].contains("?")) return out;
        String query = parts[1].substring(parts[1].indexOf('?') + 1);
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            out.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return out;
    }

    private static String randomToken(int bytes) {
        byte[] b = new byte[bytes];
        RNG.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String s256(String verifier) throws AuthException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding().encodeToString(md.digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new AuthException("This Java build has no SHA-256.");
        }
    }

    /** Full chain: Microsoft token → Xbox Live → XSTS → Minecraft token → profile. */
    public static Account minecraftLogin(MsTokens ms) throws AuthException {
        try {
            // 1. Xbox Live
            JsonObject xblProps = new JsonObject();
            xblProps.addProperty("AuthMethod", "RPS");
            xblProps.addProperty("SiteName", "user.auth.xboxlive.com");
            xblProps.addProperty("RpsTicket", ms.link() ? "t=" + ms.access() : ms.live() ? ms.access() : "d=" + ms.access());   // live.com tokens go in bare
            JsonObject xblBody = new JsonObject();
            xblBody.add("Properties", xblProps);
            xblBody.addProperty("RelyingParty", "http://auth.xboxlive.com");
            xblBody.addProperty("TokenType", "JWT");
            Http.Response xbl = Http.postJson("https://user.auth.xboxlive.com/user/authenticate", xblBody);
            if (!xbl.ok()) throw new AuthException("Xbox Live sign-in failed (HTTP " + xbl.code() + ").");
            String xblToken = xbl.json().get("Token").getAsString();

            // 2. XSTS
            JsonObject xstsProps = new JsonObject();
            xstsProps.addProperty("SandboxId", "RETAIL");
            JsonArray tokens = new JsonArray();
            tokens.add(xblToken);
            xstsProps.add("UserTokens", tokens);
            JsonObject xstsBody = new JsonObject();
            xstsBody.add("Properties", xstsProps);
            xstsBody.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
            xstsBody.addProperty("TokenType", "JWT");
            Http.Response xsts = Http.postJson("https://xsts.auth.xboxlive.com/xsts/authorize", xstsBody);
            if (!xsts.ok()) throw new AuthException(xstsError(xsts));
            JsonObject xj = xsts.json();
            String xstsToken = xj.get("Token").getAsString();
            String uhs = xj.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();

            // 3. Minecraft services
            JsonObject mcBody = new JsonObject();
            mcBody.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
            Http.Response mc = Http.postJson("https://api.minecraftservices.com/authentication/login_with_xbox", mcBody);
            if (mc.code() == 403) {
                throw new AuthException(ms.live() ? "Minecraft refused the sign-in (HTTP 403). Try again in a minute."
                        : "Mojang hasn't approved this Azure app for Minecraft sign-in yet. Use the normal \"Sign in with Microsoft\" (clear the client ID in Settings) or see README.");
            }
            if (mc.code() == 429) throw new AuthException("Too many sign-in attempts. Wait a minute and try again.");
            if (!mc.ok()) throw new AuthException("Minecraft sign-in failed (HTTP " + mc.code() + ").");
            JsonObject mj = mc.json();
            String mcToken = mj.get("access_token").getAsString();
            long expires = mj.has("expires_in") ? mj.get("expires_in").getAsLong() : 86400;

            // 4. Profile
            Http.Response prof = Http.get("https://api.minecraftservices.com/minecraft/profile", "Authorization", "Bearer " + mcToken);
            if (prof.code() == 404) throw new AuthException("This Microsoft account doesn't own Minecraft: Java Edition.");
            if (!prof.ok()) throw new AuthException("Couldn't load your Minecraft profile (HTTP " + prof.code() + ").");
            JsonObject pj = prof.json();

            Account a = new Account();
            a.uuid = pj.get("id").getAsString();
            a.name = pj.get("name").getAsString();
            a.mcToken = mcToken;
            a.mcTokenExpiry = System.currentTimeMillis() + expires * 1000L;
            a.msRefreshToken = ms.refresh();
            a.msFlow = ms.link() ? "link" : ms.live() ? "live" : "azure";
            if (pj.has("skins")) {
                for (JsonElement s : pj.getAsJsonArray("skins")) {
                    JsonObject so = s.getAsJsonObject();
                    if ("ACTIVE".equals(str(so, "state"))) a.skinUrl = str(so, "url");
                }
            }
            return a;
        } catch (IOException e) {
            throw new AuthException("Network error during sign-in: " + e.getMessage());
        }
    }

    /** Makes sure the account has a valid Minecraft token, refreshing through Microsoft if needed. */
    public static Account ensureFresh(String clientId, Account a) throws AuthException {
        if (a.offline()) return a;
        if ("prism".equals(a.source)) {
            Account fresh = PrismImport.load();   // re-read: Prism may have refreshed it
            fresh.save();
            return fresh;
        }
        LauncherImport.Source borrowed = LauncherImport.Source.of(a.source);
        if (borrowed != null) {
            if (a.tokenValid()) return a;
            Account fresh = LauncherImport.load(borrowed);   // re-read: that launcher may have refreshed it
            fresh.save();
            return fresh;
        }
        if (a.tokenValid()) return a;
        if (a.msRefreshToken == null || a.msRefreshToken.isEmpty()) throw new AuthException("Your session expired. Sign in again.");
        MsTokens ms = "link".equals(a.msFlow) ? linkRefresh(a.msRefreshToken)
                : "live".equals(a.msFlow) ? liveRefresh(a.msRefreshToken) : refresh(clientId, a.msRefreshToken);
        Account fresh = minecraftLogin(ms);
        fresh.save();
        return fresh;
    }

    private static String xstsError(Http.Response r) {
        try {
            long code = r.json().get("XErr").getAsLong();
            if (code == 2148916233L) return "This Microsoft account has no Xbox profile. Sign in once at minecraft.net first.";
            if (code == 2148916235L) return "Xbox Live isn't available in your country.";
            if (code == 2148916236L || code == 2148916237L) return "This account needs adult verification on xbox.com.";
            if (code == 2148916238L) return "This is a child account. An adult must add it to a Microsoft family.";
        } catch (Exception ignored) {}
        return "Xbox sign-in failed (HTTP " + r.code() + ").";
    }

    private static String msError(JsonObject j) {
        String err = str(j, "error");
        String desc = str(j, "error_description");
        if ("unauthorized_client".equals(err) || desc.contains("AADSTS70002") || desc.contains("public client")) {
            return "Enable \"Allow public client flows\" on your Azure app (Authentication tab).";
        }
        if ("invalid_client".equals(err) || desc.contains("AADSTS700016")) return "The Microsoft client ID is wrong. Check Settings → Account.";
        if (!desc.isEmpty()) return desc.split("\\r?\\n")[0];
        return err.isEmpty() ? "Microsoft sign-in failed." : err;
    }

    private static String str(JsonObject j, String k) {
        return j.has(k) && !j.get(k).isJsonNull() ? j.get(k).getAsString() : "";
    }
}

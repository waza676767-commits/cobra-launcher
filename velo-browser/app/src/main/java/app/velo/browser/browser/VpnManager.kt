package app.velo.browser.browser

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import app.velo.browser.data.SecureStore
import app.velo.browser.data.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** A server Velo can tunnel through. [type] is "http", "https" or "socks" (SOCKS5). */
data class VpnServer(
    val id: String,
    val name: String,
    val type: String,
    val host: String,
    val port: Int,
    val user: String = "",
)

/**
 * Velo's in-browser VPN. When it is on, every page, download-free request and AI call Velo makes
 * goes through the chosen server instead of straight to the internet, so sites see the server's
 * address, not the phone's. It only covers Velo, not other apps.
 *
 * There is deliberately no "go direct if the server is down" fallback: if the server can't be
 * reached, pages fail to load rather than quietly exposing the real address.
 */
class VpnManager(private val settings: Settings, private val secure: SecureStore) {
    enum class State { OFF, CONNECTING, ON, FAILED, UNSUPPORTED }

    var state by mutableStateOf(State.OFF)
        private set
    var detail by mutableStateOf("")
        private set

    val servers = mutableStateListOf<VpnServer>()

    /** Called on the main thread whenever the tunnel is switched, so pages can be adjusted. */
    var onChanged: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { main.post(it) }
    private val worker = Executors.newSingleThreadExecutor()
    private var generation = 0

    init {
        runCatching {
            val arr = JSONArray(settings.vpnServersJson)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                servers += VpnServer(
                    o.getString("id"), o.getString("name"), o.getString("type"),
                    o.getString("host"), o.getInt("port"), o.optString("user"),
                )
            }
        }
    }

    val active: VpnServer?
        get() = servers.firstOrNull { it.id == settings.vpnActiveId } ?: servers.firstOrNull()

    val isRouting: Boolean get() = settings.vpnEnabled && active != null

    fun saveServer(server: VpnServer, password: String?) {
        val i = servers.indexOfFirst { it.id == server.id }
        if (i >= 0) servers[i] = server else servers += server
        if (password != null) {
            if (password.isEmpty()) secure.remove(passKey(server.id)) else secure.put(passKey(server.id), password)
        }
        if (settings.vpnActiveId.isEmpty()) settings.vpnActiveId = server.id
        persist()
        if (settings.vpnEnabled) apply()
    }

    fun removeServer(id: String) {
        servers.removeAll { it.id == id }
        secure.remove(passKey(id))
        if (settings.vpnActiveId == id) settings.vpnActiveId = servers.firstOrNull()?.id ?: ""
        persist()
        if (servers.isEmpty()) settings.vpnEnabled = false
        apply()
    }

    fun select(id: String) {
        settings.vpnActiveId = id
        if (settings.vpnEnabled) apply()
    }

    fun setEnabled(on: Boolean) {
        settings.vpnEnabled = on && active != null
        apply()
    }

    /** Pushes the current choice to the WebView engine. Main thread only. */
    fun apply() {
        val gen = ++generation
        val supported = runCatching {
            WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)
        }.getOrDefault(false)
        val server = active
        if (!settings.vpnEnabled || server == null) {
            if (supported) {
                runCatching { ProxyController.getInstance().clearProxyOverride(mainExecutor) {} }
            }
            state = State.OFF
            detail = ""
            onChanged?.invoke()
            return
        }
        if (!supported) {
            state = State.UNSUPPORTED
            detail = "This device's WebView is too old to route pages through a server. Update Android System WebView."
            settings.vpnEnabled = false
            onChanged?.invoke()
            return
        }
        state = State.CONNECTING
        detail = ""
        val config = ProxyConfig.Builder().addProxyRule(ruleFor(server)).build()
        val result = runCatching {
            ProxyController.getInstance().setProxyOverride(config, mainExecutor) {
                if (gen == generation) verify(server, gen)
            }
        }
        if (result.isFailure) {
            state = State.FAILED
            detail = "That server address isn't valid."
        }
        onChanged?.invoke()
    }

    /** Checks the server actually answers, so the status shown to the user is real. */
    private fun verify(server: VpnServer, gen: Int) {
        worker.execute {
            val error = runCatching {
                Socket().use { it.connect(InetSocketAddress(server.host, server.port), 7000) }
            }.exceptionOrNull()
            main.post {
                if (gen != generation) return@post
                if (error == null) {
                    state = State.ON
                    detail = ""
                } else {
                    state = State.FAILED
                    detail = "Can't reach ${server.host}:${server.port}. Pages stay blocked so your address isn't exposed."
                }
            }
        }
    }

    /** The same route for Velo's own network calls (suggestions, list updates, AI chat). */
    fun javaProxy(): Proxy? {
        if (!settings.vpnEnabled) return null
        val s = active ?: return null
        val type = if (s.type == "socks") Proxy.Type.SOCKS else Proxy.Type.HTTP
        return Proxy(type, InetSocketAddress.createUnresolved(s.host, s.port))
    }

    /** Username and password to answer an HTTP proxy's challenge, if the user saved any. */
    fun credentialsFor(host: String): Pair<String, String>? {
        val s = active ?: return null
        if (!settings.vpnEnabled || !s.host.equals(host, ignoreCase = true) || s.user.isEmpty()) return null
        return s.user to (secure.get(passKey(s.id)) ?: "")
    }

    private fun ruleFor(s: VpnServer): String {
        val host = if (s.host.contains(':') && !s.host.startsWith("[")) "[${s.host}]" else s.host
        return "${s.type}://$host:${s.port}"
    }

    private fun persist() {
        val arr = JSONArray()
        servers.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("name", it.name).put("type", it.type)
                    .put("host", it.host).put("port", it.port).put("user", it.user)
            )
        }
        settings.vpnServersJson = arr.toString()
    }

    private fun passKey(id: String) = "vpn_pass_$id"

    companion object {
        /** Stops pages discovering the real address through WebRTC while the tunnel is on. */
        const val WEBRTC_GUARD_JS =
            "(function(){try{var f=function(){throw new DOMException('WebRTC is off while Velo VPN is on'," +
                "'NotSupportedError')};['RTCPeerConnection','webkitRTCPeerConnection','RTCDataChannel']" +
                ".forEach(function(k){if(window[k]){Object.defineProperty(window,k,{value:f,writable:false," +
                "configurable:false})}})}catch(e){}})();"
    }
}

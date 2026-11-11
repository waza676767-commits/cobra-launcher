package app.velo.browser.data

import app.velo.browser.browser.VpnManager
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Velo's own network calls (search suggestions, block-list updates, AI chat). They follow the VPN
 * setting, so nothing Velo sends bypasses the tunnel while it is on.
 */
class Net(private val vpn: VpnManager) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .proxySelector(object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> = listOf(vpn.javaProxy() ?: Proxy.NO_PROXY)
            override fun connectFailed(uri: URI?, address: SocketAddress?, error: IOException?) = Unit
        })
        .proxyAuthenticator { _, response ->
            val host = vpn.active?.host ?: return@proxyAuthenticator null
            val credentials = vpn.credentialsFor(host) ?: return@proxyAuthenticator null
            if (response.request.header("Proxy-Authorization") != null) return@proxyAuthenticator null
            response.request.newBuilder()
                .header("Proxy-Authorization", Credentials.basic(credentials.first, credentials.second))
                .build()
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** Downloads [url] as text. Blocks; throws on failure. */
    fun getText(url: String): String {
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    /** DuckDuckGo's suggestions for what is being typed. Returns an empty list on any failure. */
    fun suggestions(query: String): List<String> = runCatching {
        val text = getText("https://duckduckgo.com/ac/?type=list&q=" + URLEncoder.encode(query, "UTF-8"))
        val list = JSONArray(text).getJSONArray(1)
        List(list.length()) { list.getString(it) }
    }.getOrDefault(emptyList())
}

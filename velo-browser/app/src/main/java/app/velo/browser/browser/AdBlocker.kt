package app.velo.browser.browser

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import app.velo.browser.data.Settings
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Blocks requests to advertising and tracking hosts before they leave the device, and hides the
 * empty boxes ads leave behind.
 *
 * Matching is by host: a listed host also covers its subdomains. The page itself is never blocked,
 * only what it tries to pull in.
 */
class AdBlocker(private val context: Context, private val settings: Settings) {
    @Volatile
    private var hosts: Set<String> = emptySet()

    private val extraFile = File(context.filesDir, "adblock_extra.txt")

    /** How many hosts are on the list right now. */
    var ruleCount by mutableIntStateOf(0)
        private set

    /** Reads the built-in list and any downloaded update. Slow; call off the main thread. */
    fun load() {
        val set = HashSet<String>(8192)
        runCatching {
            context.assets.open("adblock_hosts.txt").bufferedReader().useLines { parseInto(it, set) }
        }
        if (extraFile.exists()) {
            runCatching { extraFile.bufferedReader().useLines { parseInto(it, set) } }
        }
        hosts = set
        ruleCount = set.size
    }

    /** Replaces the downloaded part of the list with [text] (hosts-file or plain-domain format). */
    fun installUpdate(text: String): Int {
        val parsed = HashSet<String>(8192)
        parseInto(text.lineSequence(), parsed)
        if (parsed.size < 100) throw IllegalStateException("The downloaded list looks empty")
        extraFile.writeText(parsed.joinToString("\n"))
        load()
        return parsed.size
    }

    fun isHostListed(host: String): Boolean {
        val set = hosts
        var h = host
        while (true) {
            if (h in set) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
            if (h.indexOf('.') < 0) return false
        }
    }

    /** True when ad blocking is on and the site hasn't been excused by the user. */
    fun activeFor(pageHost: String): Boolean =
        settings.adBlock && !isAllowlisted(pageHost)

    fun isAllowlisted(pageHost: String): Boolean {
        val list = settings.adAllowlist
        if (list.isEmpty()) return false
        val h = pageHost.removePrefix("www.")
        return h in list
    }

    fun setAllowlisted(pageHost: String, allowed: Boolean) {
        val h = pageHost.removePrefix("www.")
        if (h.isEmpty()) return
        settings.adAllowlist = if (allowed) settings.adAllowlist + h else settings.adAllowlist - h
    }

    /** Decides whether a sub-resource request made by a page on [pageHost] should be dropped. */
    fun shouldBlock(url: Uri, pageHost: String): Boolean {
        if (!activeFor(pageHost)) return false
        val scheme = url.scheme ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = url.host?.lowercase() ?: return false
        if (host == pageHost) return false
        if (isHostListed(host)) return true
        val text = url.toString()
        for (pattern in PATH_PATTERNS) {
            if (text.contains(pattern)) return true
        }
        return false
    }

    fun emptyResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    companion object {
        const val UPDATE_URL =
            "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=nohtml&showintro=0&mimetype=plaintext"

        private val PATH_PATTERNS = arrayOf(
            "/pagead/",
            "adsbygoogle.js",
            "/fbevents.js",
            "facebook.com/tr?",
            "facebook.com/tr/",
            "/gampad/ads",
            "/prebid.js",
            "/adsense/",
        )

        private val validHost = Regex("^([a-z0-9_-]+\\.)+[a-z]{2,}$")

        /** Accepts `domain`, `0.0.0.0 domain`, `127.0.0.1 domain` and `||domain^` lines. */
        fun parseInto(lines: Sequence<String>, out: MutableSet<String>) {
            for (raw in lines) {
                var line = raw.substringBefore('#').trim().lowercase()
                if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) continue
                if (line.startsWith("||")) line = line.substring(2).substringBefore('^')
                val parts = line.split(' ', '\t').filter { it.isNotEmpty() }
                val host = when {
                    parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1") -> parts[1]
                    parts.size == 1 -> parts[0]
                    else -> continue
                }
                if (host == "localhost" || !validHost.matches(host)) continue
                out += host
            }
        }

        /** Hides leftover ad containers. Runs inside the page; safe to run more than once. */
        val COSMETIC_JS = """
            (function(){
              if (document.getElementById('velo-cosmetic')) return;
              var s = document.createElement('style');
              s.id = 'velo-cosmetic';
              s.textContent = 'ins.adsbygoogle,.adsbygoogle,[id^="google_ads_iframe"],[id^="div-gpt-ad"],' +
                '[id^="gpt-ad-"],iframe[id^="aswift_"],iframe[src*="doubleclick.net"],' +
                'iframe[src*="googlesyndication.com"],[data-ad-slot],[data-ad-client],amp-ad,' +
                'amp-embed[type="taboola"],.OUTBRAIN,.ob-widget,[id^="taboola-"],.trc_rbox_container,' +
                '#carbonads,.ad-slot,.ad-banner,.adsbox,.ad-placement' +
                '{display:none!important;height:0!important;min-height:0!important}';
              (document.head || document.documentElement).appendChild(s);
            })();
        """.trimIndent()
    }
}

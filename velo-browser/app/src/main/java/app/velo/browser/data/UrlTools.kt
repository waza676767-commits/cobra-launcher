package app.velo.browser.data

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** Turning what the user typed into an address, and tidying addresses for display and privacy. */
object UrlTools {
    const val SEARCH_HOME = "https://duckduckgo.com/"

    private val domainLike =
        Regex("^([a-z0-9-]+\\.)+[a-z]{2,}(:\\d{1,5})?([/?#].*)?$", RegexOption.IGNORE_CASE)
    private val ipLike = Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d{1,5})?([/?#].*)?$")
    private val localhostLike = Regex("^localhost(:\\d{1,5})?([/?#].*)?$", RegexOption.IGNORE_CASE)

    /** Endings people type as file names; they look like domains but no such domain exists. */
    private val fileEndings = hashSetOf(
        "js", "txt", "json", "xml", "html", "htm", "css", "png", "jpg", "jpeg", "gif", "webp", "svg",
        "pdf", "zip", "rar", "exe", "apk", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "mp3", "mp4",
        "mkv", "avi", "mov", "wav", "csv", "yml", "yaml", "kt", "java", "cpp", "php", "log", "ini",
    )

    private fun isDomain(text: String): Boolean {
        if (!domainLike.matches(text)) return false
        val hostPart = text.substringBefore('/').substringBefore('?').substringBefore('#')
        val bare = hostPart == text && !hostPart.contains(':')
        return !(bare && hostPart.substringAfterLast('.').lowercase() in fileEndings)
    }

    /** DuckDuckGo results for [query]. */
    fun searchUrl(query: String, privateSearch: Boolean, dark: Boolean): String {
        val b = StringBuilder(SEARCH_HOME).append("?q=").append(URLEncoder.encode(query.trim(), "UTF-8"))
        if (dark) b.append("&kae=d")
        // kd: results open through DuckDuckGo's redirect so sites don't see the search terms.
        // kg=p: follow-up searches are POSTed. k1=-1: no ads on the results page.
        if (privateSearch) b.append("&kd=1&kg=p&k1=-1")
        return b.toString()
    }

    /** The address to load for whatever was typed: a URL when it looks like one, a search otherwise. */
    fun resolve(input: String, privateSearch: Boolean, dark: Boolean): String {
        val text = input.trim()
        if (text.isEmpty()) return ""
        if (!text.any { it.isWhitespace() }) {
            val lower = text.lowercase()
            if (lower.startsWith("http://") || lower.startsWith("https://")) return text
            if (lower == "about:blank") return text
            if (localhostLike.matches(text) || ipLike.matches(text)) return "http://$text"
            if (isDomain(text)) return "https://$text"
        }
        return searchUrl(text, privateSearch, dark)
    }

    fun looksLikeUrl(input: String): Boolean {
        val text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return false
        val lower = text.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://") ||
            localhostLike.matches(text) || ipLike.matches(text) || isDomain(text)
    }

    fun host(url: String): String =
        runCatching { URI(url.trim().substringBefore('#').replace(" ", "%20")).host }.getOrNull()
            ?.lowercase() ?: fallbackHost(url)

    private fun fallbackHost(url: String): String {
        val afterScheme = url.substringAfter("://", "")
        if (afterScheme.isEmpty()) return ""
        return afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfterLast('@').substringBefore(':').lowercase()
    }

    /** Host without a leading "www." for showing in the address bar and on tiles. */
    fun displayHost(url: String): String {
        val h = host(url)
        return (if (h.startsWith("www.")) h.substring(4) else h).ifEmpty { url }
    }

    /** The search terms if [url] is a DuckDuckGo results page, otherwise null. */
    fun searchQuery(url: String): String? {
        val h = host(url)
        if (h != "duckduckgo.com" && !h.endsWith(".duckduckgo.com")) return null
        val query = url.substringAfter('?', "").substringBefore('#')
        if (query.isEmpty()) return null
        for (part in query.split('&')) {
            if (part.startsWith("q=")) {
                return runCatching { URLDecoder.decode(part.substring(2), "UTF-8") }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private val trackingParams = hashSetOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id", "utm_name",
        "utm_cid", "utm_reader", "utm_viz_id", "utm_pubreferrer", "utm_swu", "utm_brand",
        "fbclid", "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "msclkid", "mc_cid", "mc_eid",
        "igshid", "igsh", "yclid", "twclid", "ttclid", "li_fat_id", "_hsenc", "_hsmi", "__hssc",
        "__hstc", "__hsfp", "hsctatracking", "vero_id", "vero_conv", "oly_anon_id", "oly_enc_id",
        "s_cid", "mkt_tok", "_openstat", "wickedid", "rb_clickid", "srsltid", "ref_src", "ref_url",
        "_branch_match_id", "epik", "s_kwcid", "ef_id", "cmpid", "irclickid", "sc_cid",
    )

    /** Removes well-known click-tracking parameters, leaving the rest of the address untouched. */
    fun stripTracking(url: String): String {
        val q = url.indexOf('?')
        if (q < 0) return url
        val hashAt = url.indexOf('#', q)
        val base = url.substring(0, q)
        val query = if (hashAt < 0) url.substring(q + 1) else url.substring(q + 1, hashAt)
        val fragment = if (hashAt < 0) "" else url.substring(hashAt)
        if (query.isEmpty()) return url
        val kept = query.split('&').filter { part ->
            val name = part.substringBefore('=').lowercase()
            name !in trackingParams
        }
        if (kept.size == query.count { it == '&' } + 1) return url
        return if (kept.isEmpty()) base + fragment else base + "?" + kept.joinToString("&") + fragment
    }
}

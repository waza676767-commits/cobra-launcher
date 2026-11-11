package app.velo.browser.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// ── Pinned pages ───────────────────────────────────────────────────────────────────────────────

data class Pin(val id: Long, val title: String, val url: String)

/** Pages the user pinned to the start page (and to the home-screen widget). */
class PinsStore(context: Context) {
    private val sp = context.getSharedPreferences("velo_pins", Context.MODE_PRIVATE)
    val pins = mutableStateListOf<Pin>()

    /** Called after every change; used to refresh the home-screen widget. */
    var onChanged: (() -> Unit)? = null

    init {
        val raw = sp.getString("pins", null)
        if (raw == null) {
            defaults.forEachIndexed { i, (title, url) -> pins += Pin(i + 1L, title, url) }
            persist()
        } else {
            runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    pins += Pin(o.getLong("id"), o.getString("title"), o.getString("url"))
                }
            }
        }
    }

    fun isPinned(url: String): Boolean = pins.any { sameUrl(it.url, url) }

    fun add(title: String, url: String) {
        if (url.isBlank() || isPinned(url)) return
        val id = (pins.maxOfOrNull { it.id } ?: 0L) + 1L
        pins += Pin(id, title.ifBlank { UrlTools.displayHost(url) }, url)
        persist()
    }

    fun remove(url: String) {
        if (pins.removeAll { sameUrl(it.url, url) }) persist()
    }

    fun removeById(id: Long) {
        if (pins.removeAll { it.id == id }) persist()
    }

    fun update(id: Long, title: String, url: String) {
        val i = pins.indexOfFirst { it.id == id }
        if (i >= 0) {
            pins[i] = Pin(id, title.ifBlank { UrlTools.displayHost(url) }, url)
            persist()
        }
    }

    fun toggle(title: String, url: String): Boolean =
        if (isPinned(url)) {
            remove(url)
            false
        } else {
            add(title, url)
            true
        }

    private fun persist() {
        val arr = JSONArray()
        pins.forEach { arr.put(JSONObject().put("id", it.id).put("title", it.title).put("url", it.url)) }
        sp.edit().putString("pins", arr.toString()).apply()
        onChanged?.invoke()
    }

    private fun sameUrl(a: String, b: String) = a.trimEnd('/') == b.trimEnd('/')

    companion object {
        private val defaults = listOf(
            "DuckDuckGo" to "https://duckduckgo.com/",
            "Wikipedia" to "https://www.wikipedia.org/",
            "YouTube" to "https://m.youtube.com/",
            "Reddit" to "https://www.reddit.com/",
        )

        /** Reads pins without building Compose state; safe from the widget service. */
        fun readAll(context: Context): List<Pin> {
            val raw = context.getSharedPreferences("velo_pins", Context.MODE_PRIVATE)
                .getString("pins", null) ?: return defaults.mapIndexed { i, p -> Pin(i + 1L, p.first, p.second) }
            return runCatching {
                val arr = JSONArray(raw)
                List(arr.length()) {
                    val o = arr.getJSONObject(it)
                    Pin(o.getLong("id"), o.getString("title"), o.getString("url"))
                }
            }.getOrDefault(emptyList())
        }
    }
}

// ── Session ────────────────────────────────────────────────────────────────────────────────────

data class SavedTab(val url: String, val title: String)

data class SavedSession(val tabs: List<SavedTab>, val active: Int, val savedAt: Long) {
    /** Tabs that actually had a page open; blank start pages are not worth restoring. */
    val realTabs: List<SavedTab> get() = tabs.filter { it.url.isNotBlank() }
}

/**
 * Keeps the open tabs on disk so they survive the app being closed or killed.
 *
 * `session.json` is the live session. On a fresh start it is moved to `session_prev.json`, which
 * stays untouched until the user answers the restore prompt, so browsing before answering can
 * never overwrite the tabs being offered.
 */
class SessionStore(context: Context) {
    private val current = File(context.filesDir, "session.json")
    private val previous = File(context.filesDir, "session_prev.json")

    @Synchronized
    fun save(session: SavedSession) {
        val arr = JSONArray()
        session.tabs.forEach { arr.put(JSONObject().put("url", it.url).put("title", it.title)) }
        val json = JSONObject()
            .put("tabs", arr)
            .put("active", session.active)
            .put("savedAt", session.savedAt)
        val tmp = File(current.parentFile, current.name + ".tmp")
        runCatching {
            tmp.writeText(json.toString())
            if (!tmp.renameTo(current)) {
                current.writeText(json.toString())
                tmp.delete()
            }
        }
    }

    /**
     * Call once on a fresh start. Returns the session that was open when Velo last stopped, or
     * null when there is nothing worth restoring.
     */
    @Synchronized
    fun takePrevious(): SavedSession? {
        val live = read(current)
        if (live != null && live.realTabs.isNotEmpty()) {
            previous.delete()
            current.renameTo(previous)
            return live
        }
        current.delete()
        return read(previous)?.takeIf { it.realTabs.isNotEmpty() }
    }

    /** The live session, for restoring after the system recreated the activity. */
    @Synchronized
    fun readCurrent(): SavedSession? = read(current)

    @Synchronized
    fun discardPrevious() {
        previous.delete()
    }

    private fun read(file: File): SavedSession? {
        if (!file.exists()) return null
        return runCatching {
            val json = JSONObject(file.readText())
            val arr = json.getJSONArray("tabs")
            val tabs = List(arr.length()) {
                val o = arr.getJSONObject(it)
                SavedTab(o.optString("url"), o.optString("title"))
            }
            SavedSession(tabs, json.optInt("active", 0), json.optLong("savedAt", 0L))
        }.getOrNull()
    }
}

// ── Favicons ───────────────────────────────────────────────────────────────────────────────────

/** Site icons, kept small in memory and cached on disk per host. */
class FaviconCache(context: Context) {
    private val dir = File(context.filesDir, "favicons").apply { mkdirs() }
    private val memory = object : LruCache<String, Bitmap>(96) {}

    /** Bumped when a new icon arrives so composables showing icons refresh. */
    var revision by mutableIntStateOf(0)
        private set

    fun get(host: String): Bitmap? {
        if (host.isEmpty()) return null
        memory.get(host)?.let { return it }
        val file = fileFor(host)
        if (!file.exists()) return null
        val bitmap = runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() ?: return null
        memory.put(host, bitmap)
        return bitmap
    }

    fun put(host: String, icon: Bitmap) {
        if (host.isEmpty() || icon.width < 8) return
        val scaled = if (icon.width > MAX_SIZE || icon.height > MAX_SIZE) {
            Bitmap.createScaledBitmap(icon, MAX_SIZE, MAX_SIZE, true)
        } else {
            icon
        }
        val known = memory.get(host)
        memory.put(host, scaled)
        if (known == null || known.width < scaled.width) {
            runCatching {
                fileFor(host).outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        revision++
    }

    fun clear() {
        memory.evictAll()
        dir.listFiles()?.forEach { it.delete() }
        revision++
    }

    private fun fileFor(host: String) =
        File(dir, host.replace(Regex("[^a-zA-Z0-9.-]"), "_") + ".png")

    private companion object {
        const val MAX_SIZE = 96
    }
}

// ── Secrets ────────────────────────────────────────────────────────────────────────────────────

/**
 * Stores API keys encrypted with a key that lives in the Android Keystore, so the values in
 * storage are useless without this device's hardware-backed key.
 */
class SecureStore(context: Context) {
    private val sp = context.getSharedPreferences("velo_secure", Context.MODE_PRIVATE)

    /** Kept only in memory if the Keystore is unavailable; never written to disk unencrypted. */
    private val fallback = HashMap<String, String>()

    fun get(name: String): String? {
        fallback[name]?.let { return it }
        val stored = sp.getString(name, null) ?: return null
        return runCatching {
            val parts = stored.split(":")
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val data = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        }.getOrNull()
    }

    fun put(name: String, value: String) {
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val data = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val stored = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(data, Base64.NO_WRAP)
            sp.edit().putString(name, stored).apply()
            fallback.remove(name)
        }.onFailure {
            fallback[name] = value
        }
    }

    fun remove(name: String) {
        fallback.remove(name)
        sp.edit().remove(name).apply()
    }

    fun has(name: String): Boolean = fallback.containsKey(name) || sp.contains(name)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ALIAS = "velo_secrets"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

package app.velo.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class Lang(val code: String, val tag: String, val label: String) {
    EN("en", "en-US", "English"),
    RO("ro", "ro-RO", "Română"),
    ES("es", "es-ES", "Español")
}

/** The voices Velo offers. Each maps onto the best installed TTS voice of that gender, plus pitch/rate tuning. */
enum class VoiceStyle(val female: Boolean, val rank: Int, val pitch: Float, val rate: Float) {
    ARIA(true, 0, 1.0f, 1.0f),     // clear, neutral female
    NOVA(true, 1, 1.08f, 1.05f),   // brighter, upbeat female
    LEO(false, 0, 1.0f, 1.0f),     // natural male
    ATLAS(false, 1, 0.82f, 0.95f), // deep, calm male
    ECHO(true, 0, 0.92f, 1.12f)    // quick, neutral "assistant" tone
}

enum class Accent(val argb: Long) {
    ICE(0xFF6EC8FF), CYAN(0xFF3DE0E6), MINT(0xFF5BE3A8), VIOLET(0xFFA98BFF),
    LIME(0xFFC6F432), ROSE(0xFFFF7EB3), AMBER(0xFFFFC15A), WHITE(0xFFF2F4F7)
}

enum class GestureAction {
    NONE, PLAY_PAUSE, NEXT, PREVIOUS, VOLUME_UP, VOLUME_DOWN, HEY_VELO, FIND_BUDS, OPEN_APP
}

/** Signals the earbuds can send to the phone, and which Velo can remap. */
enum class GestureSlot { TAP_1, TAP_2, TAP_3, NEXT_SIGNAL, PREV_SIGNAL, HOLD }

data class Binding(val action: GestureAction, val appPackage: String? = null, val appLabel: String? = null)

data class SavedBuds(val address: String, val name: String, val lastSeen: Long)

data class Settings(
    val lang: Lang = Lang.EN,
    val voice: VoiceStyle = VoiceStyle.ARIA,
    val accent: Accent = Accent.ICE,
    val wakeEnabled: Boolean = false,
    val wakeSensitivity: Int = 1,         // 0 strict, 1 balanced, 2 relaxed
    val useBudsMic: Boolean = false,
    val voiceClarity: Boolean = true,
    val hdVoice: Boolean = true,
    val gestureControl: Boolean = false,
    val apiKey: String = "",
    val saved: List<SavedBuds> = emptyList(),
    val bindings: Map<GestureSlot, Binding> = defaultBindings(),
    val onboarded: Boolean = false
)

fun defaultBindings() = mapOf(
    GestureSlot.TAP_1 to Binding(GestureAction.PLAY_PAUSE),
    GestureSlot.TAP_2 to Binding(GestureAction.NEXT),
    GestureSlot.TAP_3 to Binding(GestureAction.PREVIOUS),
    GestureSlot.NEXT_SIGNAL to Binding(GestureAction.NEXT),
    GestureSlot.PREV_SIGNAL to Binding(GestureAction.PREVIOUS),
    GestureSlot.HOLD to Binding(GestureAction.HEY_VELO)
)

object Store {
    private lateinit var sp: SharedPreferences
    private val _s = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _s.asStateFlow()
    val now: Settings get() = _s.value

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("velo", Context.MODE_PRIVATE)
        _s.value = load()
    }

    fun update(f: (Settings) -> Settings) {
        val n = f(_s.value)
        _s.value = n
        save(n)
    }

    fun remember(address: String, name: String) = update { s ->
        val rest = s.saved.filterNot { it.address == address }
        s.copy(saved = listOf(SavedBuds(address, name, System.currentTimeMillis())) + rest)
    }

    fun forget(address: String) = update { s -> s.copy(saved = s.saved.filterNot { it.address == address }) }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, def: E): E =
        runCatching { enumValueOf<E>(name ?: "") }.getOrDefault(def)

    private fun load(): Settings {
        val d = Settings()
        val saved = runCatching {
            val a = JSONArray(sp.getString("saved", "[]"))
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                SavedBuds(o.getString("a"), o.getString("n"), o.optLong("t"))
            }
        }.getOrDefault(emptyList())
        val bindings = runCatching {
            val o = JSONObject(sp.getString("bindings", "{}"))
            val m = defaultBindings().toMutableMap()
            o.keys().forEach { k ->
                val b = o.getJSONObject(k)
                m[enumValueOf(k)] = Binding(
                    enumOr(b.optString("x"), GestureAction.NONE),
                    b.optString("p").ifBlank { null }, b.optString("l").ifBlank { null })
            }
            m.toMap()
        }.getOrDefault(defaultBindings())
        return Settings(
            lang = enumOr(sp.getString("lang", null), defaultLang()),
            voice = enumOr(sp.getString("voice", null), d.voice),
            accent = enumOr(sp.getString("accent", null), d.accent),
            wakeEnabled = sp.getBoolean("wake", d.wakeEnabled),
            wakeSensitivity = sp.getInt("sens", d.wakeSensitivity),
            useBudsMic = sp.getBoolean("budsMic", d.useBudsMic),
            voiceClarity = sp.getBoolean("clarity", d.voiceClarity),
            hdVoice = sp.getBoolean("hd", d.hdVoice),
            gestureControl = sp.getBoolean("gestures", d.gestureControl),
            apiKey = sp.getString("key", "") ?: "",
            saved = saved,
            bindings = bindings,
            onboarded = sp.getBoolean("onboarded", false)
        )
    }

    private fun save(s: Settings) {
        val saved = JSONArray().apply {
            s.saved.forEach { put(JSONObject().put("a", it.address).put("n", it.name).put("t", it.lastSeen)) }
        }
        val b = JSONObject().apply {
            s.bindings.forEach { (k, v) ->
                put(k.name, JSONObject().put("x", v.action.name).put("p", v.appPackage ?: "").put("l", v.appLabel ?: ""))
            }
        }
        sp.edit()
            .putString("lang", s.lang.name).putString("voice", s.voice.name).putString("accent", s.accent.name)
            .putBoolean("wake", s.wakeEnabled).putInt("sens", s.wakeSensitivity)
            .putBoolean("budsMic", s.useBudsMic).putBoolean("clarity", s.voiceClarity).putBoolean("hd", s.hdVoice)
            .putBoolean("gestures", s.gestureControl).putString("key", s.apiKey)
            .putString("saved", saved.toString()).putString("bindings", b.toString())
            .putBoolean("onboarded", s.onboarded)
            .apply()
    }

    private fun defaultLang(): Lang = when (java.util.Locale.getDefault().language) {
        "ro" -> Lang.RO
        "es" -> Lang.ES
        else -> Lang.EN
    }
}

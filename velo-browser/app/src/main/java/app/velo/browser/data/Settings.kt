package app.velo.browser.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import kotlin.reflect.KProperty

/**
 * Every user-facing preference. Each property is backed by Compose state, so any composable that
 * reads it updates the moment the value changes, and by SharedPreferences, so it survives restarts.
 */
class Settings(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("velo_settings", Context.MODE_PRIVATE)

    class Pref<T>(initial: T, private val save: (T) -> Unit) {
        private val state = mutableStateOf(initial)
        operator fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            if (state.value != value) {
                state.value = value
                save(value)
            }
        }
    }

    private fun bool(key: String, def: Boolean) =
        Pref(sp.getBoolean(key, def)) { sp.edit().putBoolean(key, it).apply() }

    private fun int(key: String, def: Int) =
        Pref(sp.getInt(key, def)) { sp.edit().putInt(key, it).apply() }

    private fun long(key: String, def: Long) =
        Pref(sp.getLong(key, def)) { sp.edit().putLong(key, it).apply() }

    private fun float(key: String, def: Float) =
        Pref(sp.getFloat(key, def)) { sp.edit().putFloat(key, it).apply() }

    private fun string(key: String, def: String) =
        Pref(sp.getString(key, def) ?: def) { sp.edit().putString(key, it).apply() }

    private fun stringSet(key: String) =
        Pref<Set<String>>(sp.getStringSet(key, emptySet())?.toSet() ?: emptySet()) {
            sp.edit().putStringSet(key, it).apply()
        }

    // ── Appearance ─────────────────────────────────────────────────────────────────────────────
    /** 0 = follow system, 1 = light, 2 = dark. */
    var themeMode by int("theme_mode", THEME_SYSTEM)
    var pureBlack by bool("pure_black", false)

    /** ARGB colour used for buttons and highlights. [ACCENT_INK] means black on light, white on dark. */
    var accent by int("accent", ACCENT_INK)

    /** 0 = round, 1 = soft, 2 = sharp. */
    var style by int("style", STYLE_ROUND)

    /** 0 = bottom, 1 = top. */
    var barPosition by int("bar_position", BAR_BOTTOM)
    var floatingBar by bool("floating_bar", true)
    var hideBarOnScroll by bool("hide_bar_on_scroll", false)

    // ── Start page ─────────────────────────────────────────────────────────────────────────────
    /** 0 = none, 1 = built-in gradient, 2 = picture chosen by the user. */
    var wallpaperType by int("wallpaper_type", WALLPAPER_NONE)
    var wallpaperPreset by int("wallpaper_preset", 0)
    var wallpaperDim by float("wallpaper_dim", 0.25f)

    /** Bumped whenever the custom wallpaper file is replaced so the start page reloads it. */
    var wallpaperVersion by int("wallpaper_version", 0)
    var showClock by bool("widget_clock", true)
    var showStats by bool("widget_stats", true)
    var showPins by bool("widget_pins", true)
    var showRecent by bool("widget_recent", true)

    // ── Privacy ────────────────────────────────────────────────────────────────────────────────
    var privateSearch by bool("private_search", false)
    var searchSuggestions by bool("search_suggestions", true)
    var adBlock by bool("ad_block", true)
    var adBlockCosmetic by bool("ad_block_cosmetic", true)
    var blockPopups by bool("block_popups", true)
    var adAllowlist by stringSet("ad_allowlist")
    var adListUpdatedAt by long("ad_list_updated_at", 0L)
    var adsBlockedTotal by long("ads_blocked_total", 0L)
    var blockThirdPartyCookies by bool("block_third_party_cookies", true)
    var saveHistory by bool("save_history", true)

    // ── VPN ────────────────────────────────────────────────────────────────────────────────────
    var vpnEnabled by bool("vpn_enabled", false)
    var vpnServersJson by string("vpn_servers", "[]")
    var vpnActiveId by string("vpn_active", "")
    var vpnWebRtcGuard by bool("vpn_webrtc_guard", true)

    // ── Performance and pages ──────────────────────────────────────────────────────────────────
    /** Megabytes; 0 lets Velo pick a limit from the device's total memory. */
    var ramLimitMb by int("ram_limit_mb", 0)
    var desktopSite by bool("desktop_site", false)
    var darkWebPages by bool("dark_web_pages", false)
    var javaScript by bool("javascript", true)
    var textZoom by int("text_zoom", 100)

    // ── Session ────────────────────────────────────────────────────────────────────────────────
    /** 0 = ask, 1 = always restore, 2 = start fresh. */
    var restoreMode by int("restore_mode", RESTORE_ASK)

    // ── AI ─────────────────────────────────────────────────────────────────────────────────────
    var aiProvider by string("ai_provider", "claude")
    var aiIncludePage by bool("ai_include_page", true)

    fun aiModel(provider: String): String = sp.getString("ai_model_$provider", "") ?: ""
    fun setAiModel(provider: String, model: String) {
        sp.edit().putString("ai_model_$provider", model).apply()
    }

    /** "api" for the built-in chat, "web" for the provider's own site in a panel. */
    fun aiMode(provider: String): String = sp.getString("ai_mode_$provider", "") ?: ""
    fun setAiMode(provider: String, mode: String) {
        sp.edit().putString("ai_mode_$provider", mode).apply()
    }

    var firstRunDone by bool("first_run_done", false)

    companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        const val ACCENT_INK = 0

        const val STYLE_ROUND = 0
        const val STYLE_SOFT = 1
        const val STYLE_SHARP = 2

        const val BAR_BOTTOM = 0
        const val BAR_TOP = 1

        const val WALLPAPER_NONE = 0
        const val WALLPAPER_PRESET = 1
        const val WALLPAPER_CUSTOM = 2

        const val RESTORE_ASK = 0
        const val RESTORE_ALWAYS = 1
        const val RESTORE_NEVER = 2
    }
}

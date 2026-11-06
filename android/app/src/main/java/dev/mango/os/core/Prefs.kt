package dev.mango.os.core

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Every setting Mango has, stored in one SharedPreferences file. */
object Prefs {
    lateinit var sp: SharedPreferences
        private set
    private lateinit var usage: SharedPreferences
    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()
    // SharedPreferences only keeps a weak reference, so hold this one strongly.
    private val relay = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null) listeners.forEach { it(key) }
    }

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("mango", Context.MODE_PRIVATE)
        usage = ctx.getSharedPreferences("mango_usage", Context.MODE_PRIVATE)
        sp.registerOnSharedPreferenceChangeListener(relay)
    }

    fun listen(l: (String) -> Unit) { listeners.add(l) }
    fun unlisten(l: (String) -> Unit) { listeners.remove(l) }

    // Look
    var appearance by str("appearance", "dark")          // dark | light | auto
    var wallpaper by str("wallpaper", "lagoon")           // built-in id, or custom:<stamp>
    var iconStyle by str("iconStyle", "colorful")         // colorful | dark | clear | tinted
    var tintColor by int("tintColor", 0xFF64D2FF.toInt())
    var uiScale by float("uiScale", 1f)
    var iconScale by float("iconScale", 1f)

    // Home screen
    var columns by int("columns", 4)
    var rows by int("rows", 6)
    var labels by bool("labels", true)
    var dock by bool("dock", true)
    var dockCount by int("dockCount", 4)
    var searchPill by bool("searchPill", true)
    var swipeDown by str("swipeDown", "search")          // search | none
    var badges by str("badges", "count")                  // count | dot | off
    var setupBannerHidden by bool("setupBannerHidden", false)
    var hidden by strSet("hidden")
    var layoutReset by long("layoutReset", 0L)

    // Motion & battery
    var reduceMotion by bool("reduceMotion", false)
    var reduceTransparency by bool("reduceTransparency", false)
    var autoLowPower by bool("autoLowPower", true)
    var animSpeed by float("animSpeed", 1f)

    // Lock screen
    var lockEnabled by bool("lockEnabled", false)
    var lockClock by str("lockClock", "glass")            // glass | bold | thin
    var lockWidgets by bool("lockWidgets", true)
    var lockNotifs by bool("lockNotifs", true)
    var lockPreviews by str("lockPreviews", "always")     // always | unlocked | never
    var lockShortcuts by bool("lockShortcuts", true)
    var lockFastUnlock by bool("lockFastUnlock", true)
    var wallpaperSync by bool("wallpaperSync", true)
    var wallpaperSyncedId by int("wallpaperSyncedId", -1)
    var wallpaperSyncedFor by str("wallpaperSyncedFor", "")

    // Weather
    var weatherUseLocation by bool("weatherUseLocation", true)
    var weatherCity by str("weatherCity", "")
    var weatherLat by float("weatherLat", Float.NaN)
    var weatherLon by float("weatherLon", Float.NaN)
    var weatherUnit by str("weatherUnit", "c")
    var weatherCache by str("weatherCache", "")
    var weatherTime by long("weatherTime", 0L)

    fun launchCount(key: String) = usage.getInt(key, 0)
    fun countLaunch(key: String) { usage.edit().putInt(key, usage.getInt(key, 0) + 1).apply() }
    fun topLaunched(n: Int): List<String> =
        usage.all.entries.mapNotNull { e -> (e.value as? Int)?.let { e.key to it } }
            .sortedByDescending { it.second }.take(n).map { it.first }

    private fun str(key: String, def: String) = object : ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = sp.getString(key, def) ?: def
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) { sp.edit().putString(key, value).apply() }
    }

    private fun int(key: String, def: Int) = object : ReadWriteProperty<Any?, Int> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = sp.getInt(key, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) { sp.edit().putInt(key, value).apply() }
    }

    private fun long(key: String, def: Long) = object : ReadWriteProperty<Any?, Long> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = sp.getLong(key, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) { sp.edit().putLong(key, value).apply() }
    }

    private fun float(key: String, def: Float) = object : ReadWriteProperty<Any?, Float> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = sp.getFloat(key, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) { sp.edit().putFloat(key, value).apply() }
    }

    private fun bool(key: String, def: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = sp.getBoolean(key, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) { sp.edit().putBoolean(key, value).apply() }
    }

    private fun strSet(key: String) = object : ReadWriteProperty<Any?, Set<String>> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Set<String> = HashSet(sp.getStringSet(key, emptySet()) ?: emptySet())
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Set<String>) { sp.edit().putStringSet(key, HashSet(value)).apply() }
    }
}

package dev.aura.launcher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import dev.aura.launcher.core.Prefs
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Weather from Open-Meteo (free, no key). Fetched at most every 30 minutes and only while a
 * weather widget is actually on screen, so it costs next to nothing in battery.
 */
object WeatherRepo {
    class Hour(val time: Long, val temp: Double, val code: Int, val day: Boolean)
    class Weather(
        val place: String,
        val temp: Double,
        val code: Int,
        val day: Boolean,
        val hi: Double,
        val lo: Double,
        val hours: List<Hour>,
        val fetched: Long
    )

    class City(val name: String, val detail: String, val lat: Double, val lon: Double)

    var current: Weather? = null
        private set
    var loading = false
        private set
    private val listeners = ArrayList<() -> Unit>()
    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun listen(l: () -> Unit) { listeners.add(l) }
    fun unlisten(l: () -> Unit) { listeners.remove(l) }

    fun init() {
        if (current == null && Prefs.weatherCache.isNotEmpty()) {
            current = try { parse(JSONObject(Prefs.weatherCache), Prefs.weatherCity.ifEmpty { "My Location" }, Prefs.weatherTime) } catch (e: Exception) { null }
        }
    }

    fun unit(t: Double): String {
        val v = if (Prefs.weatherUnit == "f") t * 9 / 5 + 32 else t
        return "${Math.round(v)}°"
    }

    fun refreshIfStale(ctx: Context, force: Boolean = false) {
        val age = System.currentTimeMillis() - Prefs.weatherTime
        if (!force && current != null && age < 30 * 60 * 1000L) return
        if (loading) return
        val app = ctx.applicationContext
        loading = true
        resolvePlace(app) { lat, lon, name ->
            if (lat == null || lon == null) {
                loading = false
                fire()
                return@resolvePlace
            }
            exec.execute {
                val w = try { fetch(lat, lon, name) } catch (e: Exception) { null }
                main.post {
                    loading = false
                    if (w != null) current = w
                    fire()
                }
            }
        }
    }

    private fun fire() = listeners.toList().forEach { it() }

    val hasPlace: Boolean get() = !Prefs.weatherLat.isNaN() || Prefs.weatherUseLocation

    private fun resolvePlace(ctx: Context, done: (Double?, Double?, String) -> Unit) {
        val useLoc = Prefs.weatherUseLocation &&
            ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (useLoc) {
            val lm = ctx.getSystemService(LocationManager::class.java)
            val last = bestLast(lm)
            if (last != null) {
                placeName(ctx, last.latitude, last.longitude) { done(last.latitude, last.longitude, it) }
                return
            }
            try {
                lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, exec) { loc: Location? ->
                    main.post {
                        if (loc != null) placeName(ctx, loc.latitude, loc.longitude) { done(loc.latitude, loc.longitude, it) }
                        else fallbackCity(done)
                    }
                }
                return
            } catch (e: Exception) {
            }
        }
        fallbackCity(done)
    }

    private fun fallbackCity(done: (Double?, Double?, String) -> Unit) {
        val lat = Prefs.weatherLat
        val lon = Prefs.weatherLon
        if (lat.isNaN() || lon.isNaN()) done(null, null, "") else done(lat.toDouble(), lon.toDouble(), Prefs.weatherCity)
    }

    @Suppress("MissingPermission")
    private fun bestLast(lm: LocationManager): Location? {
        var best: Location? = null
        for (p in listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            val l = try { lm.getLastKnownLocation(p) } catch (e: Exception) { null } ?: continue
            if (best == null || l.time > best.time) best = l
        }
        return best?.takeIf { System.currentTimeMillis() - it.time < 6 * 3600 * 1000L }
    }

    private fun placeName(ctx: Context, lat: Double, lon: Double, done: (String) -> Unit) {
        if (!Geocoder.isPresent()) return done("My Location")
        if (android.os.Build.VERSION.SDK_INT < 33) {
            exec.execute {
                val n = try {
                    @Suppress("DEPRECATION")
                    Geocoder(ctx, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()?.locality
                } catch (e: Exception) { null }
                main.post { done(n ?: "My Location") }
            }
            return
        }
        try {
            Geocoder(ctx, Locale.getDefault()).getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<android.location.Address>) {
                    val a = addresses.firstOrNull()
                    val n = a?.locality ?: a?.subAdminArea ?: a?.adminArea ?: "My Location"
                    main.post { done(n) }
                }
                override fun onError(errorMessage: String?) {
                    main.post { done("My Location") }
                }
            })
        } catch (e: Exception) {
            done("My Location")
        }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "AuraLauncher/1.0")
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun fetch(lat: Double, lon: Double, name: String): Weather {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,is_day" +
            "&hourly=temperature_2m,weather_code,is_day&forecast_hours=7" +
            "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto&timeformat=unixtime"
        val body = get(url)
        val now = System.currentTimeMillis()
        val w = parse(JSONObject(body), name, now)
        Prefs.weatherCache = body
        Prefs.weatherTime = now
        Prefs.weatherCity = name
        return w
    }

    private fun parse(o: JSONObject, name: String, fetched: Long): Weather {
        val cur = o.getJSONObject("current")
        val daily = o.getJSONObject("daily")
        val hours = ArrayList<Hour>()
        o.optJSONObject("hourly")?.let { h ->
            val t = h.getJSONArray("time")
            val temp = h.getJSONArray("temperature_2m")
            val code = h.getJSONArray("weather_code")
            val day = h.optJSONArray("is_day")
            for (i in 0 until t.length()) {
                hours.add(Hour(t.getLong(i) * 1000L, temp.getDouble(i), code.getInt(i), day?.optInt(i, 1) != 0))
            }
        }
        return Weather(
            name.ifEmpty { "My Location" },
            cur.getDouble("temperature_2m"),
            cur.getInt("weather_code"),
            cur.optInt("is_day", 1) == 1,
            daily.getJSONArray("temperature_2m_max").getDouble(0),
            daily.getJSONArray("temperature_2m_min").getDouble(0),
            hours,
            fetched
        )
    }

    /** City search for the Settings screen. */
    fun searchCity(q: String, done: (List<City>) -> Unit) {
        exec.execute {
            val out = ArrayList<City>()
            try {
                val body = get("https://geocoding-api.open-meteo.com/v1/search?count=8&language=${Locale.getDefault().language}&name=" + URLEncoder.encode(q, "UTF-8"))
                val arr = JSONObject(body).optJSONArray("results")
                if (arr != null) for (i in 0 until arr.length()) {
                    val r = arr.getJSONObject(i)
                    val detail = listOfNotNull(r.optString("admin1").takeIf { it.isNotEmpty() }, r.optString("country").takeIf { it.isNotEmpty() }).joinToString(", ")
                    out.add(City(r.getString("name"), detail, r.getDouble("latitude"), r.getDouble("longitude")))
                }
            } catch (e: Exception) {
            }
            main.post { done(out) }
        }
    }

    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly Clear"
        2 -> "Partly Cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Showers"
        85, 86 -> "Snow Showers"
        in 95..99 -> "Thunderstorm"
        else -> "—"
    }
}

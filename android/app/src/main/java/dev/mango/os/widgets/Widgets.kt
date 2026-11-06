package dev.mango.os.widgets

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.text.TextPaint
import android.text.TextUtils
import android.text.format.DateFormat
import android.util.SizeF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import dev.mango.os.core.Spring
import dev.mango.os.core.Theme
import dev.mango.os.core.Ui
import dev.mango.os.core.dp
import dev.mango.os.data.Item
import dev.mango.os.data.WeatherRepo
import dev.mango.os.data.WidgetItem
import dev.mango.os.gfx.GlassPainter
import dev.mango.os.gfx.Glyph
import dev.mango.os.gfx.Glyphs
import dev.mango.os.home.Editable
import dev.mango.os.home.HasGlass
import dev.mango.os.home.HomeActivity
import dev.mango.os.home.ItemTouch
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** Live device state the widgets read (filled by the home screen while it's visible). */
object DeviceState {
    var battery = -1
    var charging = false
}

/**
 * Base for Mango's own widgets: glass card, press feedback, edit-mode minus.
 * All text sizes are in "design units" ([u]) so widgets scale with icon size and GUI scale.
 */
@SuppressLint("ViewConstructor")
abstract class GlassWidget(val home: HomeActivity, final override val item: WidgetItem?, private val pSx: Int = 2, private val pSy: Int = 2) :
    View(home), HasGlass, Editable {
    protected val glass = GlassPainter()
    protected val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    protected val p = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val r = RectF()
    var interactive = true
    override var editMode = false
        set(v) { field = v; invalidate() }
    private var pressV = 0f
    private val press = Spring(1000f, 0.75f, 0.002f).apply { onUpdate = { pressV = it; invalidate() } }
    private val touch = ItemTouch(this, home) { d -> press.animateTo(if (d) 1f else 0f) }

    val sx get() = item?.sx ?: pSx
    val sy get() = item?.sy ?: pSy
    /** One design unit: a small (2×2) widget is 160 units wide. */
    protected val u get() = min(width / (sx * 80f), height / (sy * 80f))
    protected val fg get() = Theme.text
    protected val fg2 get() = Theme.text2
    open val radius get() = home.geo.widgetRadius.coerceAtLeast(16.dp)

    override fun refreshContent() = invalidate()

    override fun onDraw(c: Canvas) {
        val sc = 1f - 0.035f * pressV
        c.save()
        c.scale(sc, sc, width / 2f, height / 2f)
        drawBackground(c)
        drawContent(c, width.toFloat(), height.toFloat())
        c.restore()
        if (editMode && item != null) {
            val rr = 12.dp
            p.color = if (Theme.dark) 0xF23A3A3C.toInt() else 0xF2E5E5EA.toInt()
            c.drawCircle(rr * 0.6f, rr * 0.6f, rr, p)
            Glyphs.draw(c, Glyph.MINUS, rr * 0.6f, rr * 0.6f, rr * 1.5f, Theme.text, 3f)
        }
    }

    open fun drawBackground(c: Canvas) {
        glass.drawRoundRect(c, this, 0f, 0f, width.toFloat(), height.toFloat(), radius)
    }

    abstract fun drawContent(c: Canvas, w: Float, h: Float)

    open fun onTap() {}

    protected fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, weight: Int, color: Int, align: Paint.Align = Paint.Align.LEFT, maxW: Float = 0f) {
        tp.textSize = size
        tp.typeface = Ui.font(weight)
        tp.color = color
        tp.textAlign = align
        val t: CharSequence = if (maxW > 0f) TextUtils.ellipsize(s, tp, maxW, TextUtils.TruncateAt.END) else s
        c.drawText(t, 0, t.length, x, y, tp)
    }

    protected fun open(intent: Intent) {
        try { home.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!interactive) return false
        return touch.handle(ev) { x, y ->
            if (editMode) {
                if (item != null && hypot(x - 7.dp, y - 7.dp) < 26.dp) home.onRemoveTapped(this)
            } else onTap()
        }
    }
}

// ------------------------------------------------------------------------------ Clock

@SuppressLint("ViewConstructor")
class ClockWidget(home: HomeActivity, item: WidgetItem?, sx: Int = 2, sy: Int = 2) : GlassWidget(home, item, sx, sy) {
    override fun drawContent(c: Canvas, w: Float, h: Float) {
        val cal = Calendar.getInstance()
        if (sx >= 4) digital(c, w, h, cal) else analog(c, w / 2f, h / 2f, min(w, h) / 2f - 10 * u, cal)
    }

    private fun analog(c: Canvas, cx: Float, cy: Float, rad: Float, cal: Calendar) {
        p.style = Paint.Style.FILL
        p.color = if (Theme.dark) 0x40000000 else 0x80FFFFFF.toInt()
        c.drawCircle(cx, cy, rad, p)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 60) {
            val big = i % 5 == 0
            val a = Math.toRadians(i * 6.0)
            val r1 = rad - (if (big) 9f else 5f) * u
            val r2 = rad - 3f * u
            p.color = if (big) fg else fg2
            p.strokeWidth = (if (big) 2.2f else 1f) * u
            c.drawLine(cx + (sin(a) * r1).toFloat(), cy - (cos(a) * r1).toFloat(), cx + (sin(a) * r2).toFloat(), cy - (cos(a) * r2).toFloat(), p)
        }
        p.style = Paint.Style.FILL
        for (n in 1..12) {
            val a = Math.toRadians(n * 30.0)
            val rr = rad - 21f * u
            text(c, n.toString(), cx + (sin(a) * rr).toFloat(), cy - (cos(a) * rr).toFloat() + 5.5f * u, 15f * u, 600, fg, Paint.Align.CENTER)
        }
        val hr = cal.get(Calendar.HOUR) + cal.get(Calendar.MINUTE) / 60f
        val mn = cal.get(Calendar.MINUTE)
        hand(c, cx, cy, hr * 30f, rad * 0.5f, 5f * u, fg)
        hand(c, cx, cy, mn * 6f, rad * 0.8f, 3.5f * u, fg)
        p.color = Theme.orange
        c.drawCircle(cx, cy, 4.5f * u, p)
        p.color = if (Theme.dark) Color.BLACK else Color.WHITE
        c.drawCircle(cx, cy, 1.6f * u, p)
    }

    private fun hand(c: Canvas, cx: Float, cy: Float, deg: Float, len: Float, wdt: Float, color: Int) {
        val a = Math.toRadians(deg.toDouble())
        p.style = Paint.Style.STROKE
        p.strokeWidth = wdt
        p.strokeCap = Paint.Cap.ROUND
        p.color = color
        c.drawLine(cx - (sin(a) * len * 0.12f).toFloat(), cy + (cos(a) * len * 0.12f).toFloat(), cx + (sin(a) * len).toFloat(), cy - (cos(a) * len).toFloat(), p)
        p.style = Paint.Style.FILL
    }

    private fun digital(c: Canvas, w: Float, h: Float, cal: Calendar) {
        val is24 = DateFormat.is24HourFormat(context)
        val time = DateFormat.format(if (is24) "H:mm" else "h:mm", cal).toString()
        val pad = 18 * u
        text(c, time, pad, h * 0.56f, 64 * u, 300, fg)
        val date = DateFormat.format("EEEE, d MMMM", cal).toString().replaceFirstChar { it.titlecase(Locale.getDefault()) }
        text(c, date, pad + 2 * u, h - pad, 15 * u, 600, fg2, maxW = w * 0.55f)
        val am = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock
        val rx = w - pad
        Glyphs.draw(c, Glyph.ALARM, rx - 12 * u, pad + 12 * u, 22 * u, Theme.orange, 2.2f)
        val alarm = if (am != null) DateFormat.format(if (is24) "EEE H:mm" else "EEE h:mm a", am.triggerTime).toString() else "No alarm"
        text(c, alarm, rx, h - pad, 15 * u, 600, fg, Paint.Align.RIGHT)
    }

    override fun onTap() = open(Intent(AlarmClock.ACTION_SHOW_ALARMS))
}

// ------------------------------------------------------------------------------ Calendar

@SuppressLint("ViewConstructor")
class CalendarWidget(home: HomeActivity, item: WidgetItem?, sx: Int = 2, sy: Int = 2) : GlassWidget(home, item, sx, sy) {
    override fun drawContent(c: Canvas, w: Float, h: Float) {
        val cal = Calendar.getInstance()
        val pad = 16 * u
        when {
            sy >= 4 -> month(c, pad, pad, w - pad * 2, h - pad * 2, cal, big = true)
            sx >= 4 -> {
                day(c, pad, w / 2f - pad, h, cal)
                month(c, w / 2f, pad + 2 * u, w / 2f - pad, h - pad * 2, cal, big = false)
            }
            else -> day(c, pad, w - pad * 2, h, cal)
        }
    }

    private fun day(c: Canvas, x: Float, maxW: Float, h: Float, cal: Calendar) {
        val weekday = DateFormat.format("EEEE", cal).toString().uppercase(Locale.getDefault())
        text(c, weekday, x, 30 * u, 13 * u, 700, Theme.red, maxW = maxW)
        text(c, cal.get(Calendar.DAY_OF_MONTH).toString(), x - 3 * u, 98 * u, 66 * u, 300, fg)
        val month = DateFormat.format("MMMM", cal).toString().replaceFirstChar { it.titlecase(Locale.getDefault()) }
        text(c, month, x, h - 18 * u, 14 * u, 600, fg2, maxW = maxW)
    }

    private fun month(c: Canvas, x: Float, y: Float, w: Float, h: Float, now: Calendar, big: Boolean) {
        val cal = now.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val first = cal.firstDayOfWeek
        val offset = (cal.get(Calendar.DAY_OF_WEEK) - first + 7) % 7
        val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val today = now.get(Calendar.DAY_OF_MONTH)
        val title = DateFormat.format("MMMM", now).toString().uppercase(Locale.getDefault())
        val ts = (if (big) 15 else 11) * u
        text(c, title, x, y + ts, ts, 700, Theme.red)
        val rows = (offset + days + 6) / 7
        val top = y + ts + 8 * u
        val cw = w / 7f
        val ch = (h - (top - y)) / (rows + 1)
        val ds = min(cw, ch) * 0.52f
        val names = java.text.DateFormatSymbols.getInstance().shortWeekdays
        for (i in 0 until 7) {
            val dow = (first - 1 + i) % 7 + 1
            text(c, names[dow].take(1).uppercase(), x + cw * (i + 0.5f), top + ch * 0.62f, ds * 0.9f, 600, fg2, Paint.Align.CENTER)
        }
        for (d in 1..days) {
            val idx = offset + d - 1
            val cx = x + cw * (idx % 7 + 0.5f)
            val cy = top + ch * (idx / 7 + 1) + ch * 0.5f
            if (d == today) {
                p.color = Theme.red
                c.drawCircle(cx, cy, min(cw, ch) * 0.44f, p)
            }
            text(c, d.toString(), cx, cy + ds * 0.36f, ds, if (d == today) 700 else 500, if (d == today) Color.WHITE else fg, Paint.Align.CENTER)
        }
    }

    override fun onTap() {
        val i = Intent(Intent.ACTION_VIEW).setData(CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build())
        open(i)
    }
}

// ------------------------------------------------------------------------------ Battery

@SuppressLint("ViewConstructor")
class BatteryWidget(home: HomeActivity, item: WidgetItem?, sx: Int = 2, sy: Int = 2) : GlassWidget(home, item, sx, sy) {
    override fun drawContent(c: Canvas, w: Float, h: Float) {
        val level = if (DeviceState.battery >= 0) DeviceState.battery else readNow()
        val col = when {
            DeviceState.charging -> Theme.green
            level <= 20 -> Theme.red
            else -> Theme.green
        }
        val rad = if (sx >= 4) h * 0.32f else min(w, h) * 0.3f
        val cx = if (sx >= 4) h * 0.5f + 6 * u else w / 2f
        val cy = if (sx >= 4) h / 2f else h * 0.42f
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 9 * u
        p.color = if (Theme.dark) 0x33FFFFFF else 0x1F000000
        c.drawCircle(cx, cy, rad, p)
        p.color = col
        r.set(cx - rad, cy - rad, cx + rad, cy + rad)
        c.drawArc(r, -90f, 360f * level.coerceIn(0, 100) / 100f, false, p)
        p.style = Paint.Style.FILL
        Glyphs.draw(c, if (DeviceState.charging) Glyph.BOLT else Glyph.PHONE, cx, cy, rad * 0.9f, fg, 2f)
        if (sx >= 4) {
            val x = h + 10 * u
            text(c, "$level%", x, h * 0.52f, 52 * u, 300, fg)
            text(c, if (DeviceState.charging) "Charging" else "Galaxy battery", x + 2 * u, h * 0.52f + 26 * u, 15 * u, 600, fg2, maxW = w - x - 12 * u)
        } else {
            text(c, "$level%", w / 2f, h - 20 * u, 22 * u, 600, fg, Paint.Align.CENTER)
        }
    }

    private fun readNow(): Int = try {
        context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    } catch (e: Exception) { 0 }

    override fun onTap() = open(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
}

// ------------------------------------------------------------------------------ Weather

@SuppressLint("ViewConstructor")
class WeatherWidget(home: HomeActivity, item: WidgetItem?, sx: Int = 2, sy: Int = 2) : GlassWidget(home, item, sx, sy) {
    private val sky = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun drawBackground(c: Canvas) {
        super.drawBackground(c)
        val wx = WeatherRepo.current
        val day = wx?.day ?: true
        val top = if (day) 0x664A9DFF else 0x66162A5C
        val bot = if (day) 0x332D6BD8 else 0x330A1430
        sky.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), top, bot, Shader.TileMode.CLAMP)
        r.set(0f, 0f, width.toFloat(), height.toFloat())
        c.drawRoundRect(r, radius, radius, sky)
    }

    override fun drawContent(c: Canvas, w: Float, h: Float) {
        val wx = WeatherRepo.current
        val pad = 16 * u
        val white = Color.WHITE
        val soft = 0xD9FFFFFF.toInt()
        if (wx == null) {
            Glyphs.draw(c, Glyph.CLOUD_SUN, pad + 14 * u, pad + 16 * u, 30 * u, white, 2f)
            val msg = when {
                WeatherRepo.loading -> "Loading…"
                !WeatherRepo.hasPlace -> "Tap to choose a city"
                else -> "Weather unavailable"
            }
            text(c, "Weather", pad, h - pad - 22 * u, 17 * u, 700, white)
            text(c, msg, pad, h - pad, 13 * u, 500, soft, maxW = w - pad * 2)
            return
        }
        val top = pad + 16 * u
        text(c, wx.place, pad, top, 16 * u, 700, white, maxW = w * (if (sx >= 4) 0.45f else 1f) - pad * 1.5f)
        text(c, WeatherRepo.unit(wx.temp), pad - 2 * u, top + 50 * u, 50 * u, 300, white)
        if (sx >= 4) {
            val rx = w - pad
            Glyphs.draw(c, Glyphs.weatherGlyph(wx.code, wx.day), rx - 13 * u, top - 4 * u, 24 * u, white, 2f)
            text(c, WeatherRepo.describe(wx.code), rx, top + 28 * u, 14 * u, 600, white, Paint.Align.RIGHT)
            text(c, "H:${WeatherRepo.unit(wx.hi)}  L:${WeatherRepo.unit(wx.lo)}", rx, top + 48 * u, 14 * u, 600, soft, Paint.Align.RIGHT)
            val hours = wx.hours.filter { it.time >= System.currentTimeMillis() - 3600_000L }.take(6)
            if (hours.isNotEmpty()) {
                val cw = (w - pad * 2) / hours.size
                val y0 = h - pad
                val is24 = DateFormat.is24HourFormat(context)
                for ((i, hr) in hours.withIndex()) {
                    val cx = pad + cw * (i + 0.5f)
                    val label = if (i == 0) "Now" else DateFormat.format(if (is24) "HH" else "ha", hr.time).toString()
                    text(c, label, cx, y0 - 46 * u, 12 * u, 600, soft, Paint.Align.CENTER)
                    Glyphs.draw(c, Glyphs.weatherGlyph(hr.code, hr.day), cx, y0 - 26 * u, 18 * u, white, 2f)
                    text(c, WeatherRepo.unit(hr.temp), cx, y0, 14 * u, 600, white, Paint.Align.CENTER)
                }
            }
        } else {
            Glyphs.draw(c, Glyphs.weatherGlyph(wx.code, wx.day), pad + 10 * u, h - pad - 40 * u, 18 * u, white, 2f)
            text(c, WeatherRepo.describe(wx.code), pad, h - pad - 18 * u, 13 * u, 600, white, maxW = w - pad * 2)
            text(c, "H:${WeatherRepo.unit(wx.hi)} L:${WeatherRepo.unit(wx.lo)}", pad, h - pad, 13 * u, 600, soft, maxW = w - pad * 2)
        }
    }

    override fun onTap() {
        if (WeatherRepo.current == null && !WeatherRepo.hasPlace) home.openSettings("weather")
        else WeatherRepo.refreshIfStale(home, force = System.currentTimeMillis() - (WeatherRepo.current?.fetched ?: 0) > 5 * 60_000L)
    }
}

/** Builds Mango's own widget views (also used for picker previews). */
object WidgetFactory {
    val kinds = listOf(
        Triple("weather", "Weather", listOf(2 to 2, 4 to 2)),
        Triple("clock", "Clock", listOf(2 to 2, 4 to 2)),
        Triple("calendar", "Calendar", listOf(2 to 2, 4 to 2, 4 to 4)),
        Triple("battery", "Batteries", listOf(2 to 2, 4 to 2))
    )

    fun create(home: HomeActivity, kind: String, item: WidgetItem?, sx: Int, sy: Int): GlassWidget = when (kind) {
        "clock" -> ClockWidget(home, item, sx, sy)
        "calendar" -> CalendarWidget(home, item, sx, sy)
        "battery" -> BatteryWidget(home, item, sx, sy)
        else -> WeatherWidget(home, item, sx, sy)
    }
}

/**
 * Hosts a regular Android widget (from any app) on the grid with rounded corners.
 * Long-press is detected here so the widget's own buttons keep working on tap.
 */
@SuppressLint("ViewConstructor")
class HostedWidgetFrame(private val home: HomeActivity, override val item: WidgetItem, private val host: AppWidgetHostView?) :
    FrameLayout(home), Editable {
    override var editMode = false
        set(v) { field = v; invalidate() }
    private var longPressed = false
    private var downX = 0f
    private var downY = 0f
    private var rawX = 0f
    private var rawY = 0f
    private val slop = ViewConfiguration.get(home).scaledTouchSlop
    private val long = Runnable { longPressed = true; home.onItemLongPress(this, rawX, rawY) }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val glass = GlassPainter()

    init {
        setWillNotDraw(false)
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, home.geo.widgetRadius.coerceAtLeast(16.dp))
            }
        }
        if (host != null) addView(host, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val d = resources.displayMetrics.density
        try {
            host?.updateAppWidgetSize(android.os.Bundle(), listOf(SizeF(w / d, h / d)))
        } catch (e: Exception) {
        }
        invalidateOutline()
    }

    override fun onDraw(c: Canvas) {
        if (host == null) {
            glass.drawRoundRect(c, this, 0f, 0f, width.toFloat(), height.toFloat(), 0f)
            tp.color = Theme.text2
            tp.textSize = 13.dp
            tp.textAlign = Paint.Align.CENTER
            c.drawText("Widget unavailable", width / 2f, height / 2f, tp)
        }
    }

    override fun dispatchDraw(c: Canvas) {
        super.dispatchDraw(c)
        if (editMode) {
            val rr = 12.dp
            p.color = if (Theme.dark) 0xF23A3A3C.toInt() else 0xF2E5E5EA.toInt()
            c.drawCircle(rr + 4.dp, rr + 4.dp, rr, p)
            Glyphs.draw(c, Glyph.MINUS, rr + 4.dp, rr + 4.dp, rr * 1.5f, Theme.text, 3f)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressed = false
                downX = ev.x; downY = ev.y; rawX = ev.rawX; rawY = ev.rawY
                postDelayed(long, if (home.editMode) 160L else 450L)
                if (home.editMode) return true
            }
            MotionEvent.ACTION_MOVE -> if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) removeCallbacks(long)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(long)
        }
        return longPressed
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressed = false
                downX = ev.x; downY = ev.y; rawX = ev.rawX; rawY = ev.rawY
                removeCallbacks(long)
                postDelayed(long, if (home.editMode) 160L else 450L)
            }
            MotionEvent.ACTION_MOVE -> if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) removeCallbacks(long)
            MotionEvent.ACTION_UP -> {
                removeCallbacks(long)
                if (editMode && !longPressed && hypot(ev.x - 16.dp, ev.y - 16.dp) < 28.dp) home.onRemoveTapped(this)
            }
            MotionEvent.ACTION_CANCEL -> removeCallbacks(long)
        }
        return true
    }
}

fun Item.isMangoWidget(): Boolean = this is WidgetItem && this.kind != "android"

fun Context.tryStart(i: Intent) = try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { false }

package dev.mango.os.lock

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import dev.mango.os.core.Prefs
import dev.mango.os.core.Spring
import dev.mango.os.core.Theme
import dev.mango.os.core.Ui
import dev.mango.os.core.dp
import dev.mango.os.data.AppRepository
import dev.mango.os.data.NotifEntry
import dev.mango.os.data.NotificationHub
import dev.mango.os.data.WeatherRepo
import dev.mango.os.gfx.Backdrop
import dev.mango.os.gfx.GlassPainter
import dev.mango.os.gfx.Glyph
import dev.mango.os.gfx.Glyphs
import dev.mango.os.gfx.IconFactory
import dev.mango.os.gfx.Squircle
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/** Date line + the big clock, in Glass, Bold or Thin style. */
class ClockView(ctx: Context) : View(ctx) {
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; fontFeatureSettings = "tnum" }
    private val date = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val m = Matrix()
    private val loc = IntArray(2)
    private var shaderSrc: Bitmap? = null
    private var shader: BitmapShader? = null

    /** Width to size the clock for before the view has been laid out. */
    var availW = 0
    val clockSize: Float get() = min((if (width > 0) width else availW) * 0.27f, 118.dp)

    override fun onDraw(c: Canvas) {
        val cal = Calendar.getInstance()
        val d = DateFormat.format("EEEE, d MMMM", cal).toString().replaceFirstChar { it.titlecase(Locale.getDefault()) }
        date.textSize = 19.dp
        date.typeface = Ui.font(600)
        date.color = 0xE6FFFFFF.toInt()
        date.setShadowLayer(8.dp, 0f, 1.dp, 0x33000000)
        c.drawText(d, width / 2f, 22.dp, date)

        val time = DateFormat.format(if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm", cal).toString()
        val style = Prefs.lockClock
        tp.textSize = clockSize
        tp.letterSpacing = -0.02f
        val base = 24.dp + clockSize * 0.98f
        when (style) {
            "bold", "thin" -> {
                tp.shader = null
                tp.colorFilter = null
                tp.typeface = Ui.font(if (style == "bold") 700 else 200)
                tp.style = Paint.Style.FILL
                tp.color = Color.WHITE
                tp.setShadowLayer(14.dp, 0f, 2.dp, 0x40000000)
                c.drawText(time, width / 2f, base, tp)
                tp.clearShadowLayer()
            }
            else -> glassText(c, time, base)
        }
    }

    /** Frosted glass digits: blurred wallpaper (brightened) + white gradient + bright rim. */
    private fun glassText(c: Canvas, time: String, base: Float) {
        tp.typeface = Ui.font(600)
        tp.style = Paint.Style.FILL
        val blur = Backdrop.blurred
        if (blur != null && !Theme.reduceTransparency) {
            if (shaderSrc !== blur) { shaderSrc = blur; shader = BitmapShader(blur, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
            getLocationInWindow(loc)
            m.setScale(Backdrop.screenW / blur.width.toFloat(), Backdrop.screenH / blur.height.toFloat())
            m.postTranslate(-loc[0].toFloat(), -loc[1].toFloat())
            shader!!.setLocalMatrix(m)
            tp.shader = shader
            tp.colorFilter = LightingColorFilter(0xFFFFFFFF.toInt(), 0x4A4A4A)
            tp.setShadowLayer(18.dp, 0f, 4.dp, 0x33000000)
            c.drawText(time, width / 2f, base, tp)
            tp.clearShadowLayer()
            tp.colorFilter = null
        }
        tp.shader = LinearGradient(0f, base - clockSize * 0.75f, 0f, base, 0xB3FFFFFF.toInt(), 0x59FFFFFF, Shader.TileMode.CLAMP)
        c.drawText(time, width / 2f, base, tp)
        tp.shader = null
        // bright rim: stroke the merged outline (stroking text directly shows the font's inner overlaps)
        val key = "$time|${tp.textSize}|$width"
        if (key != outlineKey) {
            outlineKey = key
            outline.reset()
            tp.getTextPath(time, 0, time.length, width / 2f, base, outline)
            outline.op(Path(), Path.Op.UNION)
        }
        rim.strokeWidth = 1.4f.dp
        rim.shader = LinearGradient(0f, base - clockSize * 0.75f, 0f, base, 0xF2FFFFFF.toInt(), 0x33FFFFFF, Shader.TileMode.CLAMP)
        c.drawPath(outline, rim)
    }

    private val outline = Path()
    private var outlineKey = ""
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    fun preferredHeight(): Int = (24.dp + clockSize * 1.12f).toInt()
}

/** Row of small round glass widgets under the clock. */
class LockWidgets(ctx: Context) : View(ctx) {
    private val glass = GlassPainter()
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    private fun tiles(): List<String> {
        val out = ArrayList<String>()
        out.add("battery")
        if (WeatherRepo.current != null) out.add("weather")
        if (context.getSystemService(AlarmManager::class.java)?.nextAlarmClock != null) out.add("alarm")
        out.add("date")
        return out.take(4)
    }

    override fun onDraw(c: Canvas) {
        val list = tiles()
        val size = min(height.toFloat(), 64.dp)
        val gap = 14.dp
        val total = list.size * size + (list.size - 1) * gap
        var x = (width - total) / 2f
        val cy = height / 2f
        for (t in list) {
            val cx = x + size / 2f
            glass.drawRoundRect(c, this, x, cy - size / 2f, x + size, cy + size / 2f, size / 2f)
            when (t) {
                "battery" -> {
                    val lvl = battery()
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 4.dp
                    p.strokeCap = Paint.Cap.ROUND
                    p.color = 0x40FFFFFF
                    val rr = size / 2f - 8.dp
                    c.drawCircle(cx, cy, rr, p)
                    p.color = if (lvl <= 20) 0xFFFF453A.toInt() else Color.WHITE
                    r.set(cx - rr, cy - rr, cx + rr, cy + rr)
                    c.drawArc(r, -90f, 3.6f * lvl, false, p)
                    p.style = Paint.Style.FILL
                    label(c, "$lvl", cx, cy + 6.dp, 17.dp, 600)
                }
                "weather" -> {
                    val w = WeatherRepo.current!!
                    Glyphs.draw(c, Glyphs.weatherGlyph(w.code, w.day), cx, cy - 9.dp, 18.dp, Color.WHITE, 2f)
                    label(c, WeatherRepo.unit(w.temp), cx, cy + 17.dp, 15.dp, 600)
                }
                "alarm" -> {
                    val a = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock
                    Glyphs.draw(c, Glyph.ALARM, cx, cy - 9.dp, 18.dp, Color.WHITE, 2f)
                    val s = if (a == null) "--" else DateFormat.format(if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm", a.triggerTime).toString()
                    label(c, s, cx, cy + 17.dp, 13.dp, 600)
                }
                else -> {
                    val cal = Calendar.getInstance()
                    label(c, DateFormat.format("EEE", cal).toString().uppercase(Locale.getDefault()), cx, cy - 5.dp, 11.dp, 700)
                    label(c, cal.get(Calendar.DAY_OF_MONTH).toString(), cx, cy + 16.dp, 20.dp, 600)
                }
            }
            x += size + gap
        }
    }

    private fun label(c: Canvas, s: String, x: Float, y: Float, size: Float, w: Int) {
        tp.textSize = size
        tp.typeface = Ui.font(w)
        tp.color = Color.WHITE
        c.drawText(s, x, y, tp)
    }

    private fun battery(): Int = try {
        context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    } catch (e: Exception) { 0 }
}

/** One notification (or a collapsed stack of them) as a glass card. Swipe sideways to clear. */
@SuppressLint("ViewConstructor")
class NotifCard(ctx: Context, private val host: LockActivity) : View(ctx) {
    var entry: NotifEntry? = null
    var stack = 1
    var hidePreview = false
    var onTap: (() -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    private val glass = GlassPainter()
    private val back1 = GlassPainter()
    private val tTitle = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val tBody = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val tTime = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val plate = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val r = RectF()
    private var body: StaticLayout? = null
    private var icon: Bitmap? = null
    private var small: android.graphics.drawable.Drawable? = null

    private val pad get() = 14.dp
    private val iconS get() = 38.dp
    private val stackExtra get() = if (stack > 1) 14.dp else 0f
    private val radius get() = 24.dp

    fun bind(e: NotifEntry, count: Int, hide: Boolean) {
        entry = e
        small = null
        stack = count
        hidePreview = hide
        val key = AppRepository.forPackage(e.pkg)?.key
        icon = if (key != null) IconFactory.get(key) { b -> if (entry === e) { icon = b; invalidate() } } else null
        body = null
        requestLayout()
        invalidate()
    }

    private fun appName(e: NotifEntry) = AppRepository.forPackage(e.pkg)?.label ?: e.pkg

    private fun textFor(e: NotifEntry): Pair<String, String> {
        if (hidePreview) return appName(e) to "Notification"
        val title = e.title.ifEmpty { appName(e) }
        val text = if (stack > 1) "${e.text}".ifEmpty { "" } else e.text
        return title to text
    }

    private fun colors(): Triple<Int, Int, Int> = if (Theme.dark) Triple(Color.WHITE, 0xCCFFFFFF.toInt(), 0x99FFFFFF.toInt())
    else Triple(Color.BLACK, 0xD9000000.toInt(), 0x80000000.toInt())

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val e = entry
        var h = pad * 2 + iconS
        if (e != null) {
            val (_, text) = textFor(e)
            val tw = (w - pad * 3 - iconS).toInt().coerceAtLeast(10)
            tBody.textSize = 15.dp
            tBody.typeface = Ui.font(400)
            val lines = if (stack > 1) 2 else 4
            body = StaticLayout.Builder.obtain(text, 0, text.length, tBody, tw)
                .setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false).build()
            val textH = 20.dp + (if (text.isEmpty()) 0 else body!!.height)
            h = pad * 2 + maxOf(iconS, textH)
        }
        setMeasuredDimension(w, (h + stackExtra).toInt())
    }

    override fun onDraw(c: Canvas) {
        val e = entry ?: return
        val (fg, fg2, fg3) = colors()
        val tint = if (Theme.dark) 0x1FFFFFFF else 0x8CFFFFFF.toInt()
        glass.tintColor = tint
        back1.tintColor = tint
        val h = height - stackExtra
        if (stack > 1) {
            back1.alpha = 0.55f
            back1.drawRoundRect(c, this, 22.dp, h - radius, width - 22.dp, h + stackExtra, radius * 0.8f)
            back1.alpha = 0.8f
            back1.drawRoundRect(c, this, 10.dp, h - radius, width - 10.dp, h + stackExtra * 0.55f, radius * 0.9f)
        }
        glass.drawRoundRect(c, this, 0f, 0f, width.toFloat(), h, radius)
        val il = pad
        val top0 = pad
        if (IconFactory.isClear) {
            plate.color = 0x40000000
            Squircle.into(path, il, top0, iconS)
            c.drawPath(path, plate)
        }
        val ic = icon
        if (ic != null) {
            r.set(il, top0, il + iconS, top0 + iconS)
            c.drawBitmap(ic, null, r, bmpPaint)
        } else {
            // app without a launcher icon (system services): its small notification icon on a plate
            plate.color = if (Theme.dark) 0x40FFFFFF else 0x26000000
            Squircle.into(path, il, top0, iconS)
            c.drawPath(path, plate)
            val d = small ?: e.smallIcon?.loadDrawable(context)?.mutate()?.also { small = it }
            if (d != null) {
                val inset = (iconS * 0.24f).toInt()
                d.setTint(fg)
                d.setBounds((il + inset).toInt(), (top0 + inset).toInt(), (il + iconS - inset).toInt(), (top0 + iconS - inset).toInt())
                d.draw(c)
            } else Glyphs.draw(c, Glyph.BELL, il + iconS / 2f, top0 + iconS / 2f, iconS * 0.5f, fg, 2f)
        }
        val (title, _) = textFor(e)
        val tx = il + iconS + pad
        tTime.textSize = 13.dp
        tTime.color = fg3
        tTime.typeface = Ui.font(400)
        val time = if (System.currentTimeMillis() - e.time < 60_000) "now" else DateUtils.getRelativeTimeSpanString(e.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
        val tw = tTime.measureText(time)
        c.drawText(time, width - pad, top0 + 15.dp, tTime)
        tTitle.textSize = 15.dp
        tTitle.typeface = Ui.font(600)
        tTitle.color = fg
        val shown = TextUtils.ellipsize(title, tTitle, width - tx - pad - tw - 8.dp, TextUtils.TruncateAt.END)
        c.drawText(shown, 0, shown.length, tx, top0 + 15.dp, tTitle)
        body?.let { layout ->
            tBody.color = fg2
            c.save()
            c.translate(tx, top0 + 22.dp)
            layout.draw(c)
            c.restore()
        }
        if (stack > 1) {
            tTime.textAlign = Paint.Align.RIGHT
            tTime.color = fg3
            c.drawText("${stack - 1} more", width - pad, h - pad + 4.dp, tTime)
        }
    }

    // ---- swipe to dismiss / tap
    private var downX = 0f
    private var downY = 0f
    private var swiping = false
    private var tracking = false
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var tracker: android.view.VelocityTracker? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (tracker == null) tracker = android.view.VelocityTracker.obtain()
        tracker?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = ev.rawX; downY = ev.rawY; swiping = false; tracking = true; animate().cancel() }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - downX
                val dy = ev.rawY - downY
                if (!swiping && abs(dx) > slop && abs(dx) > abs(dy)) { swiping = true; parent?.requestDisallowInterceptTouchEvent(true) }
                if (swiping) {
                    translationX = dx
                    alpha = 1f - (abs(dx) / width).coerceIn(0f, 0.7f)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracker?.computeCurrentVelocity(1000)
                val vx = tracker?.xVelocity ?: 0f
                tracker?.recycle(); tracker = null
                if (swiping) {
                    val dx = ev.rawX - downX
                    if (abs(dx) > width * 0.35f || abs(vx) > 1400.dp / 2) {
                        val dir = if (dx + vx * 0.1f > 0) 1 else -1
                        animate().translationX(dir * width * 1.2f).alpha(0f).setDuration(Ui.ms(200)).withEndAction { onDismiss?.invoke() }.start()
                    } else animate().translationX(0f).alpha(1f).setDuration(Ui.ms(260)).setInterpolator(Ui.easeOut).start()
                } else if (ev.actionMasked == MotionEvent.ACTION_UP && tracking && abs(ev.rawY - downY) < slop) {
                    onTap?.invoke()
                }
                swiping = false
                tracking = false
            }
        }
        return true
    }
}

/** Now playing: artwork, title, artist and controls for the active media session. */
class MediaCard(ctx: Context) : View(ctx) {
    private val glass = GlassPainter()
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val shader = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private var pressed = -1

    val hasMedia: Boolean get() = NotificationHub.media?.metadata != null

    override fun onDraw(c: Canvas) {
        val mc = NotificationHub.media ?: return
        val md = mc.metadata ?: return
        glass.tintColor = if (Theme.dark) 0x1FFFFFFF else 0x8CFFFFFF.toInt()
        glass.drawRoundRect(c, this, 0f, 0f, width.toFloat(), height.toFloat(), 24.dp)
        val fg = if (Theme.dark) Color.WHITE else Color.BLACK
        val fg2 = if (Theme.dark) 0xB3FFFFFF.toInt() else 0x99000000.toInt()
        val pad = 14.dp
        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val s = height - pad * 2
        r.set(pad, pad, pad + s, pad + s)
        if (art != null) {
            val bs = BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val m = Matrix()
            val sc = maxOf(s / art.width, s / art.height)
            m.setScale(sc, sc)
            m.postTranslate(pad + (s - art.width * sc) / 2f, pad + (s - art.height * sc) / 2f)
            bs.setLocalMatrix(m)
            shader.shader = bs
            c.drawRoundRect(r, 12.dp, 12.dp, shader)
        } else {
            shader.shader = null
            shader.color = 0x33FFFFFF
            c.drawRoundRect(r, 12.dp, 12.dp, shader)
            Glyphs.draw(c, Glyph.PLAY, r.centerX(), r.centerY(), s * 0.4f, fg, 2f)
        }
        val tx = pad * 2 + s
        val controlsW = 132.dp
        val maxW = width - tx - controlsW
        tp.textSize = 15.dp; tp.typeface = Ui.font(600); tp.color = fg
        val title = TextUtils.ellipsize(md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "", tp, maxW, TextUtils.TruncateAt.END)
        c.drawText(title, 0, title.length, tx, height / 2f - 3.dp, tp)
        tp.textSize = 14.dp; tp.typeface = Ui.font(400); tp.color = fg2
        val artist = TextUtils.ellipsize(md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "", tp, maxW, TextUtils.TruncateAt.END)
        c.drawText(artist, 0, artist.length, tx, height / 2f + 17.dp, tp)
        val playing = mc.playbackState?.state == PlaybackState.STATE_PLAYING
        val cy = height / 2f
        val bx = width - pad - controlsW
        val glyphs = listOf(Glyph.PREV, if (playing) Glyph.PAUSE else Glyph.PLAY, Glyph.NEXT)
        for ((i, g) in glyphs.withIndex()) {
            val cx = bx + controlsW / 6f * (i * 2 + 1)
            Glyphs.draw(c, g, cx, cy, if (i == 1) 26.dp else 20.dp, if (pressed == i) fg2 else fg, 2f)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val controlsW = 132.dp
        val bx = width - 14.dp - controlsW
        val idx = if (ev.x >= bx) ((ev.x - bx) / (controlsW / 3f)).toInt().coerceIn(0, 2) else -1
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { pressed = idx; invalidate() }
            MotionEvent.ACTION_UP -> {
                val t = NotificationHub.media?.transportControls
                val playing = NotificationHub.media?.playbackState?.state == PlaybackState.STATE_PLAYING
                when (if (pressed == idx) idx else -1) {
                    0 -> t?.skipToPrevious()
                    1 -> if (playing) t?.pause() else t?.play()
                    2 -> t?.skipToNext()
                }
                pressed = -1
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { pressed = -1; invalidate() }
        }
        return true
    }
}

/** Round glass button (flashlight / camera). */
class RoundButton(ctx: Context, private val glyph: Glyph) : View(ctx) {
    private val glass = GlassPainter()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    var on = false
        set(v) { field = v; invalidate() }
    private var press = 0f
    private val spring = Spring(700f, 0.7f, 0.002f).apply { onUpdate = { press = it; invalidate() } }

    override fun onDraw(c: Canvas) {
        val s = 1f + 0.12f * press
        c.save()
        c.scale(s, s, width / 2f, height / 2f)
        if (on) {
            p.color = Color.WHITE
            c.drawCircle(width / 2f, height / 2f, width / 2f, p)
        } else glass.drawRoundRect(c, this, 0f, 0f, width.toFloat(), height.toFloat(), width / 2f)
        Glyphs.draw(c, glyph, width / 2f, height / 2f, width * 0.44f, if (on) Color.BLACK else Color.WHITE, 2f)
        c.restore()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> spring.animateTo(1f)
            MotionEvent.ACTION_UP -> { spring.animateTo(0f); Ui.thud(this); performClick() }
            MotionEvent.ACTION_CANCEL -> spring.animateTo(0f)
        }
        return true
    }
}

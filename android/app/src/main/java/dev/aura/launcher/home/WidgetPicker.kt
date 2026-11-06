package dev.aura.launcher.home

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.aura.launcher.core.Spring
import dev.aura.launcher.core.Theme
import dev.aura.launcher.core.Ui
import dev.aura.launcher.core.dp
import dev.aura.launcher.core.dpi
import dev.aura.launcher.gfx.Glyph
import dev.aura.launcher.gfx.Glyphs
import dev.aura.launcher.widgets.WidgetFactory
import kotlin.math.roundToInt

/** Bottom sheet listing Aura widgets (with live previews) and widgets from installed apps. */
@SuppressLint("ViewConstructor")
class WidgetPicker(private val home: HomeActivity) : FrameLayout(home) {
    private val sheet = GlassPanel(home)
    private val scroll = ScrollView(home)
    private val list = LinearLayout(home)
    private val spring = Spring(420f, 0.9f, 0.001f)
    private var p = 0f
    var isOpen = false
        private set

    init {
        visibility = GONE
        isClickable = true
        setOnClickListener { close() }
        sheet.radius = 30.dp
        sheet.isClickable = true
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(0, 0, 0, 40.dpi)
        scroll.addView(list)
        scroll.isVerticalScrollBarEnabled = false
        sheet.addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(sheet, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        spring.onUpdate = { v -> p = v; apply() }
        spring.onEnd = { if (!isOpen) { visibility = GONE; list.removeAllViews() } }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val g = home.geo
        val t = (g.height * 0.12f).roundToInt().coerceAtLeast(g.insetTop + 20.dpi)
        sheet.layout(6.dpi, t, width - 6.dpi, height + 30.dpi)
        scroll.layout(0, 0, sheet.width, sheet.height - 30.dpi)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val t = (home.geo.height * 0.12f).roundToInt().coerceAtLeast(home.geo.insetTop + 20.dpi)
        sheet.measure(MeasureSpec.makeMeasureSpec(w - 12.dpi, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h - t + 30.dpi, MeasureSpec.EXACTLY))
    }

    fun open() {
        build()
        isOpen = true
        visibility = VISIBLE
        spring.snapTo(0f)
        spring.animateTo(1f)
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        spring.animateTo(0f)
    }

    private fun apply() {
        setBackgroundColor(Color.argb((90 * p.coerceIn(0f, 1f)).toInt(), 0, 0, 0))
        sheet.translationY = (1f - p) * (height * 0.9f)
        sheet.invalidate()
    }

    private fun header(text: String, size: Float, top: Int): TextView = TextView(home).apply {
        this.text = text
        setTextColor(Theme.text)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        typeface = Ui.font(700)
        setPadding(22.dpi, top, 22.dpi, 8.dpi)
    }

    private fun build() {
        list.removeAllViews()
        sheet.glass.tintColor = if (Theme.dark) 0xB3141416.toInt() else 0xCCF2F2F7.toInt()
        val top = LinearLayout(home).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(header("Widgets", 30.dp, 22.dpi), LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(PillButton(home, "Done", null, bold = true).apply { setOnClickListener { close() } }, LinearLayout.LayoutParams(84.dpi, 38.dpi).apply { rightMargin = 18.dpi; topMargin = 14.dpi })
        list.addView(top)

        val g = home.geo
        val scale = 0.62f
        for ((kind, name, sizes) in WidgetFactory.kinds) {
            list.addView(header(name, 19.dp, 16.dpi))
            val row = LinearLayout(home).apply { orientation = LinearLayout.HORIZONTAL; setPadding(16.dpi, 0, 16.dpi, 6.dpi) }
            for ((sx, sy) in sizes) {
                val (w, h) = g.widgetSize(sx, sy)
                val holder = FrameLayout(home)
                val wv = WidgetFactory.create(home, kind, null, sx, sy)
                wv.interactive = false
                wv.pivotX = 0f; wv.pivotY = 0f
                wv.scaleX = scale; wv.scaleY = scale
                holder.addView(wv, LayoutParams(w, h))
                holder.clipChildren = true
                holder.setOnClickListener { close(); home.addWidget(kind, sx, sy) }
                val sizeName = when { sy >= 4 -> "Large"; sx >= 4 -> "Medium"; else -> "Small" }
                val col = LinearLayout(home).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
                col.addView(holder, LinearLayout.LayoutParams((w * scale).roundToInt(), (h * scale).roundToInt()))
                col.addView(TextView(home).apply {
                    text = sizeName
                    setTextColor(Theme.text2)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, 13.dp)
                    gravity = Gravity.CENTER
                    setPadding(0, 6.dpi, 0, 0)
                })
                row.addView(col, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = 14.dpi })
            }
            val hs = HorizontalScrollView(home)
            hs.isHorizontalScrollBarEnabled = false
            hs.addView(row)
            list.addView(hs)
        }

        // Widgets from installed apps
        val providers = try { AppWidgetManager.getInstance(home).installedProviders } catch (e: Exception) { emptyList() }
        if (providers.isNotEmpty()) {
            list.addView(header("From your apps", 22.dp, 26.dpi))
            val pm = home.packageManager
            val byPkg = providers.groupBy { it.provider.packageName }
                .map { (pkg, infos) ->
                    val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
                    Triple(pkg, label, infos)
                }.sortedBy { it.second.lowercase() }
            for ((pkg, label, infos) in byPkg) {
                val group = LinearLayout(home).apply { orientation = LinearLayout.VERTICAL }
                val rowView = AppRow(home, label, try { pm.getApplicationIcon(pkg) } catch (e: Exception) { null }, infos.size)
                group.addView(rowView, LinearLayout.LayoutParams(-1, 58.dpi))
                val details = LinearLayout(home).apply { orientation = LinearLayout.HORIZONTAL; setPadding(16.dpi, 4.dpi, 16.dpi, 12.dpi); visibility = GONE }
                rowView.setOnClickListener {
                    if (details.childCount == 0) for (info in infos) details.addView(providerTile(info), LinearLayout.LayoutParams(150.dpi, -2).apply { rightMargin = 12.dpi })
                    details.visibility = if (details.visibility == VISIBLE) GONE else VISIBLE
                    rowView.expanded = details.visibility == VISIBLE
                }
                group.addView(HorizontalScrollView(home).apply { isHorizontalScrollBarEnabled = false; addView(details) })
                list.addView(group)
            }
        }
    }

    private fun providerTile(info: AppWidgetProviderInfo): View {
        val col = LinearLayout(home).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val dpi = resources.displayMetrics.densityDpi
        val preview: Drawable? = try { info.loadPreviewImage(home, dpi) ?: info.loadIcon(home, dpi) } catch (e: Exception) { null }
        val img = object : View(home) {
            override fun onDraw(c: Canvas) {
                val d = preview ?: return
                val iw = d.intrinsicWidth.coerceAtLeast(1)
                val ih = d.intrinsicHeight.coerceAtLeast(1)
                val s = minOf(width / iw.toFloat(), height / ih.toFloat())
                val w = (iw * s).roundToInt(); val h = (ih * s).roundToInt()
                val l = (width - w) / 2; val t = (height - h) / 2
                d.setBounds(l, t, l + w, t + h)
                d.draw(c)
            }
        }
        col.addView(img, LinearLayout.LayoutParams(150.dpi, 110.dpi))
        val (sx, sy) = home.spanFor(info)
        col.addView(TextView(home).apply {
            text = "${info.loadLabel(home.packageManager)}\n$sx × $sy"
            setTextColor(Theme.text2)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 12.5f.dp)
            gravity = Gravity.CENTER
            maxLines = 3
            setPadding(0, 6.dpi, 0, 0)
        })
        col.setOnClickListener { close(); home.addAndroidWidget(info) }
        return col
    }

    @SuppressLint("ViewConstructor")
    private class AppRow(home: HomeActivity, private val label: String, private val icon: Drawable?, private val count: Int) : View(home) {
        var expanded = false
            set(v) { field = v; invalidate() }
        private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        private val sep = Paint()
        override fun onDraw(c: Canvas) {
            val s = 34.dpi
            val l = 22.dpi
            val t = (height - s) / 2
            icon?.setBounds(l, t, l + s, t + s)
            icon?.draw(c)
            tp.color = Theme.text
            tp.textSize = 17.dp
            tp.typeface = Ui.font(500)
            val name = TextUtils.ellipsize(label, tp, width - 140.dp, TextUtils.TruncateAt.END)
            c.drawText(name, 0, name.length, l + s + 14.dp, height / 2f + 6.dp, tp)
            tp.color = Theme.text2
            tp.textSize = 15.dp
            tp.textAlign = Paint.Align.RIGHT
            c.drawText(count.toString(), width - 46.dp, height / 2f + 5.dp, tp)
            tp.textAlign = Paint.Align.LEFT
            c.save()
            if (expanded) c.rotate(90f, width - 28.dp, height / 2f)
            Glyphs.draw(c, Glyph.CHEVRON_RIGHT, width - 28.dp, height / 2f, 16.dp, Theme.text3, 2.6f)
            c.restore()
            sep.color = Theme.separator
            c.drawRect(l + s + 14.dp, height - 0.6f.dp, width.toFloat(), height.toFloat(), sep)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            return true
        }
    }
}

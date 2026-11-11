package app.velo.browser.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import app.velo.browser.Browser
import app.velo.browser.MainActivity
import app.velo.browser.R
import app.velo.browser.data.Pin
import app.velo.browser.data.PinsStore
import app.velo.browser.data.UrlTools
import java.io.File
import kotlin.math.abs

private fun launch(context: Context, action: String, requestCode: Int): PendingIntent =
    PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

/** Home-screen search bar: tap to search with DuckDuckGo, or tap the sparkle to ask the assistant. */
class SearchWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_search)
        views.setOnClickPendingIntent(R.id.widget_search_bar, launch(context, Browser.ACTION_SEARCH, 1))
        views.setOnClickPendingIntent(R.id.widget_ai, launch(context, Browser.ACTION_ASK_AI, 2))
        ids.forEach { manager.updateAppWidget(it, views) }
    }
}

/** Home-screen grid of the pages pinned in Velo. */
class PinsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_pins)
            val adapter = Intent(context, PinsWidgetService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            adapter.data = Uri.parse(adapter.toUri(Intent.URI_INTENT_SCHEME))
            @Suppress("DEPRECATION")
            views.setRemoteAdapter(R.id.widget_pins_grid, adapter)
            views.setEmptyView(R.id.widget_pins_grid, R.id.widget_pins_empty)
            views.setOnClickPendingIntent(R.id.widget_pins_search, launch(context, Browser.ACTION_SEARCH, 3))
            views.setOnClickPendingIntent(R.id.widget_pins_new_tab, launch(context, Browser.ACTION_NEW_TAB, 4))
            // Each tile fills in its own address on top of this template.
            val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val template = PendingIntent.getActivity(
                context,
                5,
                Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW),
                PendingIntent.FLAG_UPDATE_CURRENT or mutable,
            )
            views.setPendingIntentTemplate(R.id.widget_pins_grid, template)
            manager.updateAppWidget(id, views)
        }
    }

    companion object {
        /** Tells any pinned-pages widgets on the home screen to reload. */
        fun refresh(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, PinsWidget::class.java))
                @Suppress("DEPRECATION")
                if (ids.isNotEmpty()) manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_pins_grid)
            }
        }
    }
}

class PinsWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val context: Context) : RemoteViewsFactory {
        private var pins: List<Pin> = emptyList()

        override fun onCreate() = Unit
        override fun onDestroy() = Unit

        override fun onDataSetChanged() {
            pins = PinsStore.readAll(context)
        }

        override fun getCount(): Int = pins.size
        override fun getItemId(position: Int): Long = pins.getOrNull(position)?.id ?: position.toLong()
        override fun hasStableIds(): Boolean = true
        override fun getViewTypeCount(): Int = 1
        override fun getLoadingView(): RemoteViews? = null

        override fun getViewAt(position: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_pin_item)
            val pin = pins.getOrNull(position) ?: return views
            views.setTextViewText(R.id.widget_pin_title, pin.title)
            views.setImageViewBitmap(R.id.widget_pin_icon, iconFor(pin))
            views.setOnClickFillInIntent(R.id.widget_pin_root, Intent().setData(Uri.parse(pin.url)))
            views.setViewVisibility(R.id.widget_pin_root, View.VISIBLE)
            return views
        }

        private fun iconFor(pin: Pin): Bitmap {
            val size = 96
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val host = UrlTools.host(pin.url)
            val file = File(File(context.filesDir, "favicons"), host.replace(Regex("[^a-zA-Z0-9.-]"), "_") + ".png")
            val favicon = if (file.exists()) runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() else null
            if (favicon != null) {
                val scaled = Bitmap.createScaledBitmap(favicon, size, size, true)
                paint.shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            } else {
                val hue = (abs(pin.title.hashCode()) % 360).toFloat()
                paint.color = Color.HSVToColor(floatArrayOf(hue, 0.28f, 0.95f))
                canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
                paint.color = Color.HSVToColor(floatArrayOf(hue, 0.7f, 0.45f))
                paint.textSize = size * 0.46f
                paint.typeface = Typeface.DEFAULT_BOLD
                paint.textAlign = Paint.Align.CENTER
                val letter = pin.title.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•"
                canvas.drawText(letter, size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2, paint)
            }
            return out
        }
    }
}

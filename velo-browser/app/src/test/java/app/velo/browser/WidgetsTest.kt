package app.velo.browser

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.velo.browser.widget.PinsWidget
import app.velo.browser.widget.PinsWidgetService
import app.velo.browser.widget.SearchWidget
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The home-screen widgets: their layouts inflate, update without error, and list the pins. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-420dpi")
class WidgetsTest {
    private val app: VeloApp get() = ApplicationProvider.getApplicationContext()

    @Test
    fun widgetLayoutsInflateAndRender() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val density = activity.resources.displayMetrics.density
        fun shot(layout: Int, widthDp: Int, heightDp: Int, name: String) {
            val host = FrameLayout(activity)
            activity.layoutInflater.inflate(layout, host, true)
            activity.setContentView(
                host, FrameLayout.LayoutParams((widthDp * density).toInt(), (heightDp * density).toInt())
            )
            shadowOf(Looper.getMainLooper()).idle()
            host.captureRoboImage("build/shots/$name.png")
        }
        shot(R.layout.widget_search, 360, 64, "60_widget_search")
        shot(R.layout.widget_pins, 360, 190, "61_widget_pins")
        val item = LayoutInflater.from(app).inflate(R.layout.widget_pin_item, null)
        assertNotNull(item.findViewById<TextView>(R.id.widget_pin_title))
    }

    @Test
    fun searchWidgetIsBuiltByItsProvider() {
        val shadow = shadowOf(AppWidgetManager.getInstance(app))
        val id = shadow.createWidget(SearchWidget::class.java, R.layout.widget_search)
        val view = shadow.getViewFor(id)
        assertNotNull(view.findViewById<View>(R.id.widget_search_bar))
        assertNotNull(view.findViewById<View>(R.id.widget_ai))
        // Refreshing with no pinned-pages widget on the home screen must be harmless.
        PinsWidget.refresh(app)
    }

    @Test
    fun pinsWidgetListsThePinnedPages() {
        app.pins.add("My site", "https://my.example/")
        val service = Robolectric.buildService(PinsWidgetService::class.java).create().get()
        val factory = service.onGetViewFactory(Intent())
        factory.onCreate()
        factory.onDataSetChanged()
        assertEquals(app.pins.pins.size, factory.count)
        assertTrue(factory.count >= 1)
        val row = factory.getViewAt(factory.count - 1).apply(app, FrameLayout(app))
        assertEquals("My site", row.findViewById<TextView>(R.id.widget_pin_title).text.toString())
    }
}

package dev.mango.os.core

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.PowerManager
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.PathInterpolator

/** Units, fonts and haptics shared by every screen. */
object Ui {
    var density = 3f
    /** "GUI scale" from settings; every dp value in Mango is multiplied by it. */
    var scale = 1f

    fun init(ctx: Context) {
        density = ctx.resources.displayMetrics.density
    }

    /** iOS-like curves. */
    val ease = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)
    val easeOut = PathInterpolator(0.16f, 1f, 0.3f, 1f)
    val easeInOut = PathInterpolator(0.45f, 0f, 0.2f, 1f)

    private val faces = HashMap<Int, Typeface>()
    fun font(weight: Int): Typeface = faces.getOrPut(weight) { Typeface.create(Typeface.DEFAULT, weight, false) }

    fun tick(v: View) {
        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun thud(v: View) {
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /** Duration helper that honours the animation-speed setting. */
    fun ms(base: Long): Long = (base / Theme.animSpeed).toLong().coerceAtLeast(1)
}

val Number.dp: Float get() = toFloat() * Ui.density * Ui.scale
val Number.dpi: Int get() = Math.round(toFloat() * Ui.density * Ui.scale)

fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

fun argb(a: Float, rgb: Int): Int = Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), Color.red(rgb), Color.green(rgb), Color.blue(rgb))

fun blendColors(a: Int, b: Int, t: Float): Int {
    val u = t.coerceIn(0f, 1f)
    return Color.argb(
        (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * u).toInt(),
        (Color.red(a) + (Color.red(b) - Color.red(a)) * u).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * u).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * u).toInt()
    )
}

/** Resolved look: light/dark, reduced effects, speed. Recomputed whenever settings change. */
object Theme {
    var dark = true
    var reduceTransparency = false
    var reduceMotion = false
    var animSpeed = 1f

    fun update(ctx: Context) {
        dark = when (Prefs.appearance) {
            "light" -> false
            "dark" -> true
            else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        val saver = Prefs.autoLowPower && (ctx.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true)
        reduceTransparency = Prefs.reduceTransparency || saver
        reduceMotion = Prefs.reduceMotion || saver
        animSpeed = Prefs.animSpeed
        Ui.scale = Prefs.uiScale
    }

    val text: Int get() = if (dark) Color.WHITE else Color.BLACK
    val text2: Int get() = if (dark) 0x99EBEBF5.toInt() else 0x993C3C43.toInt()
    val text3: Int get() = if (dark) 0x4DEBEBF5 else 0x4D3C3C43
    val accent: Int get() = if (dark) 0xFF0A84FF.toInt() else 0xFF007AFF.toInt()
    val red: Int get() = if (dark) 0xFFFF453A.toInt() else 0xFFFF3B30.toInt()
    val green: Int get() = if (dark) 0xFF30D158.toInt() else 0xFF34C759.toInt()
    val orange: Int get() = if (dark) 0xFFFF9F0A.toInt() else 0xFFFF9500.toInt()
    val separator: Int get() = if (dark) 0x30FFFFFF else 0x24000000

    /** Colour laid over the blurred wallpaper inside glass. */
    val glassTint: Int get() = if (dark) 0x26FFFFFF else 0x66FFFFFF
    /** Used instead of glass when "Reduce Transparency" is on. */
    val solidPanel: Int get() = if (dark) 0xFF2C2C2E.toInt() else 0xFFF2F2F7.toInt()
    /** Grouped rows in Settings / sheets. */
    val groupBg: Int get() = if (dark) 0xE01C1C1E.toInt() else 0xEBFFFFFF.toInt()
    val fill: Int get() = if (dark) 0x3D787880 else 0x29787880
}

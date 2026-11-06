package dev.aura.launcher.core

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import dev.aura.launcher.BuildConfig
import java.io.File

/**
 * Debug builds only: `adb shell am broadcast -a dev.aura.SNAP` writes what the current Aura
 * screen draws to files/snap-<name>.png. Lets the UI be checked on emulators that can't screenshot.
 */
object DebugSnap {
    private var downTime = 0L
    /** snapshot receiver → its touch receiver, so both get unregistered together. */
    val receivers = HashMap<BroadcastReceiver, BroadcastReceiver>()

    fun detach(a: Activity, r: BroadcastReceiver?) {
        r ?: return
        try { a.unregisterReceiver(r) } catch (e: Exception) {}
        receivers.remove(r)?.let { t ->
            try { a.unregisterReceiver(t) } catch (e: Exception) {}
            receivers.remove(t)?.let { try { a.unregisterReceiver(it) } catch (e: Exception) {} }
        }
    }

    fun attach(a: Activity, name: String): BroadcastReceiver? {
        if (!BuildConfig.DEBUG) return null
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val v = a.window.decorView
                if (v.width == 0) return
                val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                try {
                    v.draw(Canvas(b))
                    File(a.filesDir, "snap-$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } catch (e: Exception) {
                    File(a.filesDir, "snap-$name.txt").writeText(e.stackTraceToString())
                }
            }
        }
        a.registerReceiver(r, IntentFilter("dev.aura.SNAP"), Context.RECEIVER_EXPORTED)
        val t = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (!a.hasWindowFocus() && i.getStringExtra("force") == null) return
                val x = i.getStringExtra("x")?.toFloatOrNull() ?: return
                val y = i.getStringExtra("y")?.toFloatOrNull() ?: return
                val now = android.os.SystemClock.uptimeMillis()
                val action = when (i.getStringExtra("a")) {
                    "down" -> { downTime = now; android.view.MotionEvent.ACTION_DOWN }
                    "up" -> android.view.MotionEvent.ACTION_UP
                    else -> android.view.MotionEvent.ACTION_MOVE
                }
                val ev = android.view.MotionEvent.obtain(downTime, now, action, x, y, 0)
                ev.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                a.window.decorView.dispatchTouchEvent(ev)
                ev.recycle()
            }
        }
        a.registerReceiver(t, IntentFilter("dev.aura.TOUCH"), Context.RECEIVER_EXPORTED)
        receivers[r] = t
        if (name == "lock") {
            val n = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    val k = "debug:" + System.nanoTime()
                    dev.aura.launcher.data.NotificationHub.debugAdd(
                        dev.aura.launcher.data.NotifEntry(
                            k, i.getStringExtra("pkg") ?: "com.example.chat", i.getStringExtra("t") ?: "Title", i.getStringExtra("x") ?: "",
                            System.currentTimeMillis() - (i.getStringExtra("ago")?.toLongOrNull() ?: 0L) * 60000L,
                            null, true, true, k, false, null, null, false, 0
                        )
                    )
                }
            }
            a.registerReceiver(n, IntentFilter("dev.aura.NOTIFY"), Context.RECEIVER_EXPORTED)
            receivers[t] = n
        }
        return r
    }
}

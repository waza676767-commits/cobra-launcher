package dev.mango.mathy

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import java.io.File

/** Debug builds only: snapshot the screen / inject touches, for testing on headless emulators. */
object DebugHooks {
    private var downTime = 0L

    fun attach(a: Activity): List<BroadcastReceiver> {
        if ((a.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) return emptyList()
        val snap = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val v = a.window.decorView
                if (v.width == 0) return
                val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                v.draw(Canvas(b))
                File(a.filesDir, "snap.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        val touch = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val x = i.getStringExtra("x")?.toFloatOrNull() ?: return
                val y = i.getStringExtra("y")?.toFloatOrNull() ?: return
                val now = SystemClock.uptimeMillis()
                val action = when (i.getStringExtra("a")) {
                    "down" -> { downTime = now; MotionEvent.ACTION_DOWN }
                    "up" -> MotionEvent.ACTION_UP
                    else -> MotionEvent.ACTION_MOVE
                }
                val ev = MotionEvent.obtain(downTime, now, action, x, y, 0)
                ev.source = InputDevice.SOURCE_TOUCHSCREEN
                a.window.decorView.dispatchTouchEvent(ev)
                ev.recycle()
            }
        }
        a.registerReceiver(snap, IntentFilter("dev.mango.mathy.SNAP"), Context.RECEIVER_EXPORTED)
        a.registerReceiver(touch, IntentFilter("dev.mango.mathy.TOUCH"), Context.RECEIVER_EXPORTED)
        return listOf(snap, touch)
    }
}

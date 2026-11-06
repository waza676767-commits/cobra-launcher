package dev.mango.os.lock

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import dev.mango.os.core.Prefs
import dev.mango.os.home.HomeSwitch

/**
 * Puts the Mango lock screen up as the screen turns off, so it's ready the moment you wake the
 * phone. It sits on top of One UI's own lock: your PIN / fingerprint still protects the phone.
 * No polling, no service: a single broadcast receiver.
 */
object LockController {
    fun init(app: Application) {
        val f = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED)
        else app.registerReceiver(receiver, f)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_SCREEN_OFF && Prefs.lockEnabled && !inCall(c)) show(c, preview = false)
        }
    }

    private fun inCall(c: Context): Boolean {
        val m = c.getSystemService(AudioManager::class.java)?.mode ?: return false
        return m == AudioManager.MODE_IN_CALL || m == AudioManager.MODE_IN_COMMUNICATION || m == AudioManager.MODE_RINGTONE ||
            m == AudioManager.MODE_CALL_SCREENING
    }

    /** True if the system will let Mango put its lock screen up from the background. */
    fun canShow(c: Context) = HomeSwitch.isDefault(c)

    fun show(c: Context, preview: Boolean) {
        try {
            c.startActivity(
                Intent(c, LockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    .putExtra(LockActivity.EXTRA_PREVIEW, preview)
            )
        } catch (e: Exception) {
            android.util.Log.w("Mango", "lock screen start failed", e)
        }
    }
}

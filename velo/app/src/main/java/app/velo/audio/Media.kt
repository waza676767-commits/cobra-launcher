package app.velo.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.KeyEvent

/** Controls music and apps: play/pause, skip, volume, launching apps by name. */
object Media {

    fun hasListenerAccess(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        return flat.contains(ComponentName(ctx, MediaListener::class.java).flattenToString())
    }

    fun openListenerSettings(ctx: Context) {
        ctx.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** The player you're actually using (Spotify, YouTube Music…), if notification access is granted. */
    fun activePlayer(ctx: Context): MediaController? {
        if (!hasListenerAccess(ctx)) return null
        val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val list = runCatching {
            msm.getActiveSessions(ComponentName(ctx, MediaListener::class.java))
        }.getOrDefault(emptyList()).filter { it.packageName != ctx.packageName }
        return list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
    }

    fun playPause(ctx: Context) {
        val c = activePlayer(ctx)
        if (c != null) {
            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
            else c.transportControls.play()
        } else key(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    fun pause(ctx: Context) {
        activePlayer(ctx)?.transportControls?.pause() ?: key(ctx, KeyEvent.KEYCODE_MEDIA_PAUSE)
    }

    fun play(ctx: Context) {
        activePlayer(ctx)?.transportControls?.play() ?: key(ctx, KeyEvent.KEYCODE_MEDIA_PLAY)
    }

    fun next(ctx: Context) {
        activePlayer(ctx)?.transportControls?.skipToNext() ?: key(ctx, KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun previous(ctx: Context) {
        activePlayer(ctx)?.transportControls?.skipToPrevious() ?: key(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    fun volume(ctx: Context, up: Boolean, steps: Int = 2) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        repeat(steps) {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        }
    }

    fun setVolumePercent(ctx: Context, pct: Int) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * pct.coerceIn(0, 100) / 100f).toInt(), AudioManager.FLAG_SHOW_UI)
    }

    private fun key(ctx: Context, code: Int) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val t = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }

    data class AppInfo(val label: String, val pkg: String)

    fun launchableApps(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.pkg }
            .filter { it.pkg != ctx.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun launch(ctx: Context, pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
    }

    /** Finds an installed app by a spoken name ("spotify", "whats app", "youtube music"). */
    fun findApp(ctx: Context, spoken: String): AppInfo? {
        val q = norm(spoken)
        if (q.isBlank()) return null
        val apps = launchableApps(ctx)
        return apps.firstOrNull { norm(it.label) == q }
            ?: apps.firstOrNull { norm(it.label).startsWith(q) }
            ?: apps.firstOrNull { norm(it.label).contains(q) || q.contains(norm(it.label)) && norm(it.label).length > 3 }
    }

    private fun norm(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").replace(Regex("[^a-z0-9]"), "")
}

/** Notification access gives Velo the media controllers of other players (needed for gesture remapping). */
class MediaListener : NotificationListenerService()

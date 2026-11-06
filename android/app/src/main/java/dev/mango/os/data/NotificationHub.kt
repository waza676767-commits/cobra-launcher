package dev.mango.os.data

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.StatusBarNotification
import dev.mango.os.notif.MangoNotificationListener

class NotifEntry(
    val key: String,
    val pkg: String,
    val title: String,
    val text: String,
    val time: Long,
    val contentIntent: PendingIntent?,
    val autoCancel: Boolean,
    val clearable: Boolean,
    val groupKey: String,
    val isSummary: Boolean,
    val smallIcon: Icon?,
    val largeIcon: Icon?,
    val private: Boolean,
    val color: Int
)

/** Live notifications + the playing media session, fed by [MangoNotificationListener]. */
object NotificationHub {
    private var service: MangoNotificationListener? = null
    private val entries = LinkedHashMap<String, NotifEntry>()
    private val listeners = ArrayList<() -> Unit>()
    private val mediaListeners = ArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private var ownPkg = ""
    var media: MediaController? = null
        private set
    private var sessions: MediaSessionManager? = null
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> pickMedia(list) }

    val connected get() = service != null

    fun listen(l: () -> Unit) { listeners.add(l) }
    fun unlisten(l: () -> Unit) { listeners.remove(l) }
    fun listenMedia(l: () -> Unit) { mediaListeners.add(l) }
    fun unlistenMedia(l: () -> Unit) { mediaListeners.remove(l) }

    fun component(ctx: Context) = ComponentName(ctx, MangoNotificationListener::class.java)

    fun hasAccess(ctx: Context): Boolean =
        ctx.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component(ctx))

    fun requestAccess(ctx: Context) {
        val i = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(ctx).flattenToString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(i)
        } catch (e: Exception) {
            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // ------------------------------------------------------------------ fed by the listener

    internal fun attach(s: MangoNotificationListener) {
        service = s
        ownPkg = s.packageName
        entries.clear()
        try {
            s.activeNotifications?.forEach { add(it) }
        } catch (e: Exception) {
        }
        notifyChanged()
        try {
            val msm = s.getSystemService(MediaSessionManager::class.java)
            sessions = msm
            msm.addOnActiveSessionsChangedListener(sessionsListener, component(s), main)
            pickMedia(msm.getActiveSessions(component(s)))
        } catch (e: Exception) {
        }
    }

    internal fun detach() {
        try { sessions?.removeOnActiveSessionsChangedListener(sessionsListener) } catch (e: Exception) {}
        sessions = null
        service = null
        entries.clear()
        setMedia(null)
        notifyChanged()
    }

    internal fun posted(sbn: StatusBarNotification) {
        add(sbn)
        notifyChanged(sbn.key)
    }

    internal fun removed(sbn: StatusBarNotification) {
        if (entries.remove(sbn.key) != null) notifyChanged()
    }

    private var lastPosted: String? = null

    /** Key of the most recently arrived notification (for banners / arrival animation). */
    fun takeLastPosted(): String? = lastPosted.also { lastPosted = null }

    private fun add(sbn: StatusBarNotification) {
        if (sbn.packageName == ownPkg) return
        val n = sbn.notification
        if (sbn.isOngoing) return
        val ex = n.extras
        val title = (ex.getCharSequence(Notification.EXTRA_TITLE_BIG) ?: ex.getCharSequence(Notification.EXTRA_TITLE))?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val summary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (title.isBlank() && text.isBlank()) return
        entries[sbn.key] = NotifEntry(
            sbn.key, sbn.packageName, title.trim(), text.trim(),
            if (n.`when` > 0) n.`when` else sbn.postTime,
            n.contentIntent,
            (n.flags and Notification.FLAG_AUTO_CANCEL) != 0,
            sbn.isClearable,
            sbn.groupKey ?: sbn.key,
            summary,
            n.smallIcon, n.getLargeIcon(),
            n.visibility == Notification.VISIBILITY_PRIVATE || n.visibility == Notification.VISIBILITY_SECRET,
            n.color
        )
    }

    private fun notifyChanged(posted: String? = null) {
        if (posted != null) lastPosted = posted
        listeners.toList().forEach { it() }
    }

    // ------------------------------------------------------------------ queries

    /** Newest first; group summaries dropped when the group has real children. */
    fun list(): List<NotifEntry> {
        val all = entries.values.toList()
        val groupsWithChildren = all.filter { !it.isSummary }.map { it.groupKey }.toHashSet()
        return all.filter { !it.isSummary || it.groupKey !in groupsWithChildren }.sortedByDescending { it.time }
    }

    fun get(key: String) = entries[key]

    /** Debug builds: lets the lock screen be tested without real notifications. */
    fun debugAdd(e: NotifEntry) {
        entries[e.key] = e
        notifyChanged(e.key)
    }

    fun badgeCounts(): Map<String, Int> {
        val m = HashMap<String, Int>()
        for (e in list()) m[e.pkg] = (m[e.pkg] ?: 0) + 1
        return m
    }

    fun dismiss(key: String) {
        try { service?.cancelNotification(key) } catch (e: Exception) {}
    }

    fun dismissAll(keys: Collection<String>) {
        try { service?.cancelNotifications(keys.toTypedArray()) } catch (e: Exception) {}
    }

    fun open(ctx: Context, e: NotifEntry) {
        val pi = e.contentIntent
        if (pi != null) {
            try {
                val opts = ActivityOptions.makeBasic()
                if (Build.VERSION.SDK_INT >= 34) {
                    opts.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                }
                pi.send(ctx, 0, null, null, null, null, opts.toBundle())
                if (e.autoCancel) dismiss(e.key)
                return
            } catch (ex: Exception) {
            }
        }
        AppRepository.forPackage(e.pkg)?.let { AppRepository.launch(ctx, it.key, null) }
    }

    // ------------------------------------------------------------------ media

    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = mediaChanged()
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) = mediaChanged()
        override fun onSessionDestroyed() {
            setMedia(null)
        }
    }

    private fun pickMedia(list: List<MediaController>?) {
        val best = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list?.firstOrNull { it.metadata != null }
        setMedia(best)
    }

    private fun setMedia(c: MediaController?) {
        if (c?.sessionToken == media?.sessionToken && c != null) return
        media?.unregisterCallback(mediaCallback)
        media = c
        c?.registerCallback(mediaCallback, main)
        mediaChanged()
    }

    private fun mediaChanged() {
        mediaListeners.toList().forEach { it() }
    }
}

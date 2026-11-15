package app.velo.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import app.velo.data.GestureAction
import app.velo.data.GestureSlot
import app.velo.data.Store

/**
 * Remaps earbud gestures. Android delivers earbud taps as media-key signals to the app that last
 * played audio, so Velo briefly plays a silent clip to become that app, catches the signal, counts
 * quick repeated taps (1/2/3), then runs the action you picked — forwarding play/skip to your real
 * player (Spotify etc.) through its media controller. After the player starts, Velo quietly takes
 * the buttons back.
 */
object Gestures {
    private const val TAP_WINDOW = 420L
    private var session: MediaSession? = null
    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    private var taps = 0
    private var onAction: ((GestureAction, String?) -> Unit)? = null
    private val watched = HashMap<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null

    val enabled get() = session != null

    fun start(ctx: Context, handler: (GestureAction, String?) -> Unit) {
        if (session != null) return
        app = ctx.applicationContext
        onAction = handler
        val s = MediaSession(app, "VeloGestures")
        s.setCallback(object : MediaSession.Callback() {
            override fun onMediaButtonEvent(intent: Intent): Boolean {
                val ev: KeyEvent = (if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)) ?: return false
                if (ev.action != KeyEvent.ACTION_DOWN || ev.repeatCount > 0) return true
                when (ev.keyCode) {
                    KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> countTap()
                    KeyEvent.KEYCODE_MEDIA_NEXT -> fire(GestureSlot.NEXT_SIGNAL)
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> fire(GestureSlot.PREV_SIGNAL)
                    KeyEvent.KEYCODE_VOICE_ASSIST -> fire(GestureSlot.HOLD)
                    else -> return false
                }
                return true
            }
        })
        s.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
            .setState(PlaybackState.STATE_PAUSED, 0, 1f).build())
        s.isActive = true
        session = s
        watchPlayers()
        claim()
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        session?.release(); session = null
        unwatchPlayers()
    }

    private fun countTap() {
        taps++
        main.removeCallbacks(flushTaps)
        if (taps >= 3) flushTaps.run() else main.postDelayed(flushTaps, TAP_WINDOW)
    }

    private val flushTaps = Runnable {
        val slot = when (taps) { 1 -> GestureSlot.TAP_1; 2 -> GestureSlot.TAP_2; else -> GestureSlot.TAP_3 }
        taps = 0
        fire(slot)
    }

    private fun fire(slot: GestureSlot) {
        val b = Store.now.bindings[slot] ?: return
        onAction?.invoke(b.action, b.appPackage)
    }

    /** Make Velo the media-button target by playing ~150 ms of silence. */
    fun claim() {
        val s = session ?: return
        s.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
            .setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        Thread {
            runCatching {
                val rate = 22050
                val frames = rate / 7
                val t = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(rate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(frames * 2).build()
                t.write(ShortArray(frames), 0, frames)
                t.play()
                Thread.sleep(250)
                t.release()
            }
        }.start()
    }

    /** When your music app starts playing (e.g. after Velo forwarded "play"), take the buttons back. */
    private fun watchPlayers() {
        if (!Media.hasListenerAccess(app)) return
        val msm = app.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val comp = ComponentName(app, MediaListener::class.java)
        val l = MediaSessionManager.OnActiveSessionsChangedListener { list -> attach(list ?: emptyList()) }
        sessionsListener = l
        runCatching {
            msm.addOnActiveSessionsChangedListener(l, comp)
            attach(msm.getActiveSessions(comp))
        }
    }

    private fun attach(list: List<MediaController>) {
        list.filter { it.packageName != app.packageName && it.sessionToken !in watched }.forEach { c ->
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    if (state?.state == PlaybackState.STATE_PLAYING) {
                        main.removeCallbacks(reclaim); main.postDelayed(reclaim, 1200)
                    }
                }
            }
            c.registerCallback(cb, main)
            watched[c.sessionToken] = c to cb
        }
    }

    private val reclaim = Runnable { claim() }

    private fun unwatchPlayers() {
        val msm = app.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        sessionsListener?.let { runCatching { msm.removeOnActiveSessionsChangedListener(it) } }
        sessionsListener = null
        watched.values.forEach { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
        watched.clear()
    }
}

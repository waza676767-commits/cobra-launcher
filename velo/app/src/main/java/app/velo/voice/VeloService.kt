package app.velo.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import app.velo.MainActivity
import app.velo.R
import app.velo.audio.FindSound
import app.velo.audio.Gestures
import app.velo.audio.Media
import app.velo.audio.Side
import app.velo.bt.Buds
import app.velo.data.GestureAction
import app.velo.data.Store
import app.velo.data.T
import kotlin.concurrent.thread

/**
 * Background brain: listens for "Hey Velo" only while earbuds are connected, handles earbud
 * gestures, and runs a conversation turn (chime → listen → think → speak → back to listening).
 * When the earbuds disconnect, the microphone is released and the service just waits.
 */
class VeloService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var busy = false
    private var focus: AudioFocusRequest? = null

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { main.postDelayed({ sync() }, 1500) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        Speaker.init(this)
        goForeground()
        val f = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(btReceiver, f, RECEIVER_EXPORTED) else registerReceiver(btReceiver, f)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LISTEN -> converse(chime = true)
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            else -> sync()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        WakeWord.stop()
        Gestures.stop()
        Listener.cancel()
        runCatching { unregisterReceiver(btReceiver) }
        super.onDestroy()
    }

    private fun micAllowed() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Velo", NotificationManager.IMPORTANCE_MIN).apply {
            setShowBadge(false)
        })
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val talk = PendingIntent.getService(this, 1, Intent(this, VeloService::class.java).setAction(ACTION_LISTEN), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_stat_velo)
            .setContentTitle("Velo")
            .setContentText(T("idle"))
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, T("tap_talk"), talk).build())
            .setOngoing(true)
            .build()
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        if (micAllowed()) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        runCatching { startForeground(1, n, type) }.onFailure {
            runCatching { startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) }
        }
    }

    /** Start/stop the mic and gestures to match the settings and whether earbuds are connected. */
    private fun sync() {
        Buds.refresh()
        val s = Store.now
        val budsOn = Buds.current.value?.connected == true
        if (!s.wakeEnabled && !s.gestureControl) { stopSelf(); return }

        if (s.gestureControl && budsOn) Gestures.start(this, ::onGesture) else Gestures.stop()

        if (s.wakeEnabled && budsOn && micAllowed() && !busy) {
            if (!WakeWord.isModelReady(this)) {
                thread { if (WakeWord.ensureModel(this)) main.post { sync() } }
            } else WakeWord.start(this) { converse(chime = true) }
        } else if (!busy) WakeWord.stop()
    }

    private fun onGesture(a: GestureAction, pkg: String?) {
        when (a) {
            GestureAction.NONE -> {}
            GestureAction.PLAY_PAUSE -> Media.playPause(this)
            GestureAction.NEXT -> Media.next(this)
            GestureAction.PREVIOUS -> Media.previous(this)
            GestureAction.VOLUME_UP -> Media.volume(this, true, 1)
            GestureAction.VOLUME_DOWN -> Media.volume(this, false, 1)
            GestureAction.HEY_VELO -> converse(chime = true)
            GestureAction.FIND_BUDS -> if (FindSound.playing.value != null) FindSound.stop(this) else FindSound.start(this, Side.BOTH)
            GestureAction.OPEN_APP -> pkg?.let { Media.launch(this, it) }
        }
    }

    /** One conversation turn. Pauses the wake word so the recogniser can use the microphone. */
    private fun converse(chime: Boolean) {
        if (busy) return
        busy = true
        WakeWord.stop()
        Speaker.stop()
        takeFocus()
        if (chime) runCatching {
            ToneGenerator(AudioManager.STREAM_MUSIC, 70).apply {
                startTone(ToneGenerator.TONE_PROP_ACK, 120)
                main.postDelayed({ release() }, 400)
            }
        }
        main.postDelayed({
            Listener.listen(this) { text ->
                if (text.isNullOrBlank()) {
                    Assistant.setPhase(Phase.IDLE)
                    finishTurn()
                } else {
                    Assistant.handle(this, text) { finishTurn() }
                }
            }
        }, if (chime) 220 else 0)
    }

    private fun finishTurn() {
        dropFocus()
        busy = false
        main.postDelayed({ sync() }, 300)
    }

    private fun takeFocus() {
        val am = getSystemService(AudioManager::class.java)
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .build()
        focus = req
        am.requestAudioFocus(req)
    }

    private fun dropFocus() {
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
    }

    companion object {
        private const val CH = "velo_bg"
        const val ACTION_LISTEN = "app.velo.LISTEN"
        const val ACTION_STOP = "app.velo.STOP"
        @Volatile var running = false

        /** Starts or refreshes the service (call from the UI so the mic is allowed in the background). */
        fun sync(ctx: Context) {
            val s = Store.now
            if (!s.wakeEnabled && !s.gestureControl) {
                if (running) ctx.startService(Intent(ctx, VeloService::class.java).setAction(ACTION_STOP))
                return
            }
            runCatching { ctx.startForegroundService(Intent(ctx, VeloService::class.java)) }
        }

        fun listenNow(ctx: Context) {
            runCatching {
                ctx.startForegroundService(Intent(ctx, VeloService::class.java).setAction(ACTION_LISTEN))
            }
        }

        fun budsChanged(ctx: Context) {
            if (running) runCatching { ctx.startService(Intent(ctx, VeloService::class.java)) }
        }
    }
}

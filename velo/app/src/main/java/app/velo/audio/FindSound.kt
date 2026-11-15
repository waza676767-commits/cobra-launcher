package app.velo.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.sin

enum class Side { LEFT, RIGHT, BOTH }

/**
 * "Find my earbuds": a piercing chirp (2.4–4.2 kHz, the range ears locate best) at full volume,
 * routed only to the chosen side. Restores your volume when it stops; stops itself after 45 s.
 */
object FindSound {
    private const val RATE = 44100
    private var track: AudioTrack? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private var savedVolume = -1

    private val _playing = MutableStateFlow<Side?>(null)
    val playing: StateFlow<Side?> = _playing

    fun start(ctx: Context, side: Side) {
        stop(ctx)
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        savedVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)

        val buf = buildChirp(side)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(buf.size * 2)
            .build()
        track = t
        running = true
        _playing.value = side
        t.play()
        thread = Thread {
            val end = System.currentTimeMillis() + 45_000
            while (running && System.currentTimeMillis() < end) t.write(buf, 0, buf.size)
            if (running) android.os.Handler(android.os.Looper.getMainLooper()).post { stop(ctx) }
        }.apply { isDaemon = true; start() }
    }

    fun stop(ctx: Context) {
        running = false
        runCatching { track?.pause(); track?.flush(); track?.release() }
        track = null
        thread = null
        if (savedVolume >= 0) {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0)
            savedVolume = -1
        }
        _playing.value = null
    }

    /** ~1 s pattern: three rising chirps then silence, interleaved stereo with the muted side zeroed. */
    private fun buildChirp(side: Side): ShortArray {
        val chirp = (RATE * 0.12).toInt()
        val gap = (RATE * 0.06).toInt()
        val tail = (RATE * 0.35).toInt()
        val frames = 3 * (chirp + gap) + tail
        val out = ShortArray(frames * 2)
        val l = side != Side.RIGHT
        val r = side != Side.LEFT
        var f = 0
        repeat(3) { n ->
            var phase = 0.0
            for (i in 0 until chirp) {
                val k = i.toDouble() / chirp
                val freq = 2400 + 1800 * k + n * 150
                phase += 2 * PI * freq / RATE
                val env = minOf(1.0, i / 200.0, (chirp - i) / 200.0)
                // A little square-ish harmonic content makes it cut through better.
                val s = (sin(phase) * 0.8 + sin(phase * 3) * 0.2) * env * 32000
                val v = s.toInt().coerceIn(-32767, 32767).toShort()
                out[f * 2] = if (l) v else 0
                out[f * 2 + 1] = if (r) v else 0
                f++
            }
            f += gap
        }
        return out
    }
}

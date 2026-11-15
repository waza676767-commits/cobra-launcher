package app.velo.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import app.velo.data.Store
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.math.sqrt

/**
 * Offline "Hey Velo" detector.
 *
 * Battery: a cheap energy gate (voice activity detection) runs on every 20 ms frame; the speech
 * recogniser only wakes up while someone is actually talking. Nothing leaves the phone.
 *
 * Accuracy: the recogniser is restricted to a small grammar of "hey velo" and the things it
 * sounds like when said quickly or cut off ("hey vello", "hey fellow", "hey bellow"…), plus
 * look-alike decoys ("hello", "below"…) and [unk] so ordinary talk doesn't trigger it. Per-word
 * confidence decides, with the threshold set by the sensitivity slider.
 */
object WakeWord {
    private const val RATE = 16000
    private const val FRAME = 320 // 20 ms
    private const val MODEL = "vosk-model-small-en-us-0.15"
    private const val URL_MODEL = "https://alphacephei.com/vosk/models/$MODEL.zip"

    val downloading = MutableStateFlow(false)
    val downloadProgress = MutableStateFlow(0f)

    private var model: Model? = null
    @Volatile private var running = false
    private var thread: Thread? = null

    private val wakeWords = setOf("velo", "vello", "fellow", "bellow", "vela", "fellows")
    private val greet = setOf("hey", "hi", "a", "okay", "hay", "he")
    private val grammar = listOf(
        "hey velo", "hey vello", "hey fellow", "hey bellow", "hi velo", "hi fellow", "okay velo",
        "a velo", "a fellow", "hey vela", "velo", "fellow",
        // decoys: absorb similar everyday words so they don't count as the wake word
        "hello", "hey hello", "yellow", "below", "follow", "hey there", "hey you", "[unk]"
    )

    fun modelDir(ctx: Context) = File(ctx.filesDir, "vosk/$MODEL")
    fun isModelReady(ctx: Context) = File(modelDir(ctx), "am/final.mdl").exists()

    /** One-time ~40 MB download of the offline English model (the wake word is the same in every language). */
    fun ensureModel(ctx: Context): Boolean {
        if (isModelReady(ctx)) return true
        if (downloading.value) return false
        downloading.value = true
        return try {
            val root = File(ctx.filesDir, "vosk").apply { mkdirs() }
            val conn = URL(URL_MODEL).openConnection()
            val total = conn.contentLengthLong.coerceAtLeast(1)
            var read = 0L
            ZipInputStream(conn.getInputStream().buffered()).use { zip ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val e = zip.nextEntry ?: break
                    val out = File(root, e.name)
                    if (!out.canonicalPath.startsWith(root.canonicalPath)) continue
                    if (e.isDirectory) { out.mkdirs(); continue }
                    out.parentFile?.mkdirs()
                    out.outputStream().use { o ->
                        while (true) {
                            val n = zip.read(buf); if (n < 0) break
                            o.write(buf, 0, n); read += n
                            downloadProgress.value = (read.toFloat() / (total * 1.1f)).coerceAtMost(0.99f)
                        }
                    }
                }
            }
            downloadProgress.value = 1f
            isModelReady(ctx)
        } catch (e: Exception) {
            File(ctx.filesDir, "vosk").deleteRecursively()
            false
        } finally {
            downloading.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun start(ctx: Context, onWake: () -> Unit) {
        if (running) return
        if (!isModelReady(ctx)) return
        if (model == null) {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            model = Model(modelDir(ctx).absolutePath)
        }
        running = true
        thread = Thread({ loop(ctx, onWake) }, "velo-wake").apply { priority = Thread.NORM_PRIORITY - 1; start() }
    }

    fun stop() {
        running = false
        thread?.join(600)
        thread = null
    }

    val isRunning get() = running

    @SuppressLint("MissingPermission")
    private fun loop(ctx: Context, onWake: () -> Unit) {
        val s = Store.now
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val useBuds = s.useBudsMic && routeToBudsMic(am)
        val source = if (useBuds) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.VOICE_RECOGNITION
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = try {
            AudioRecord(source, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, FRAME * 8))
        } catch (e: Exception) { running = false; return }
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); running = false; return }

        var ns: NoiseSuppressor? = null
        var agc: AutomaticGainControl? = null
        if (s.voiceClarity) {
            if (NoiseSuppressor.isAvailable()) ns = runCatching { NoiseSuppressor.create(rec.audioSessionId)?.apply { enabled = true } }.getOrNull()
            if (AutomaticGainControl.isAvailable()) agc = runCatching { AutomaticGainControl.create(rec.audioSessionId)?.apply { enabled = true } }.getOrNull()
        }

        val grammarJson = org.json.JSONArray(grammar).toString()
        val recognizer = Recognizer(model, RATE.toFloat(), grammarJson).apply { setWords(true) }
        val frame = ShortArray(FRAME)
        val preRoll = ArrayDeque<ShortArray>()
        var noise = 300.0
        var hangover = 0
        var active = false
        rec.startRecording()
        try {
            while (running) {
                val n = rec.read(frame, 0, FRAME)
                if (n <= 0) continue
                val e = rms(frame, n)
                val speech = e > noise * 2.6 && e > 180
                if (!speech) noise = noise * 0.97 + e * 0.03 // track the room's noise floor
                if (speech) hangover = 35 else if (hangover > 0) hangover--

                if (hangover > 0) {
                    if (!active) {
                        active = true
                        // Feed the ~300 ms before speech started so the "h" of "hey" isn't lost.
                        preRoll.forEach { recognizer.acceptWaveForm(it, it.size) }
                        preRoll.clear()
                    }
                    if (recognizer.acceptWaveForm(frame, n)) {
                        if (check(recognizer.result)) { fire(recognizer, onWake); break }
                    } else if (s.wakeSensitivity >= 1 && checkPartial(recognizer.partialResult)) {
                        // Fast path: confident partial "hey velo" — don't wait for silence.
                        fire(recognizer, onWake); break
                    }
                } else {
                    if (active) {
                        active = false
                        if (check(recognizer.finalResult)) { fire(recognizer, onWake); break }
                        recognizer.reset()
                    }
                    preRoll.addLast(frame.copyOf(n))
                    if (preRoll.size > 15) preRoll.removeFirst()
                }
            }
        } finally {
            runCatching { rec.stop() }
            rec.release(); ns?.release(); agc?.release(); recognizer.close()
            if (useBuds) releaseBudsMic(am)
            running = false
        }
    }

    private fun fire(r: Recognizer, onWake: () -> Unit) {
        r.reset()
        running = false
        android.os.Handler(android.os.Looper.getMainLooper()).post(onWake)
    }

    private fun rms(a: ShortArray, n: Int): Double {
        var sum = 0.0
        for (i in 0 until n) sum += a[i].toDouble() * a[i]
        return sqrt(sum / n)
    }

    private fun threshold() = when (Store.now.wakeSensitivity) { 0 -> 0.88; 1 -> 0.72; else -> 0.55 }

    /** Accepts "hey velo" said cleanly, quickly, or with a clipped ending. */
    private fun check(json: String): Boolean {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return false
        val words = o.optJSONArray("result") ?: return false
        val th = threshold()
        val strict = Store.now.wakeSensitivity == 0
        for (i in 0 until words.length()) {
            val w = words.getJSONObject(i)
            val word = w.optString("word")
            if (word !in wakeWords) continue
            val conf = w.optDouble("conf", 0.0)
            val prev = if (i > 0) words.getJSONObject(i - 1) else null
            val hasGreeting = prev != null && prev.optString("word") in greet && prev.optDouble("conf", 0.0) > 0.5
            // "hey velo" → normal threshold. A lone "velo" needs to be said clearly, and never in strict mode.
            if (hasGreeting && conf >= th) return true
            if (!hasGreeting && !strict && conf >= th + 0.12 && word in setOf("velo", "vello")) return true
        }
        return false
    }

    private fun checkPartial(json: String): Boolean {
        val p = runCatching { JSONObject(json).optString("partial") }.getOrNull() ?: return false
        return p == "hey velo" || p == "hi velo" || p == "okay velo"
    }

    private fun routeToBudsMic(am: AudioManager): Boolean {
        return if (Build.VERSION.SDK_INT >= 31) {
            val dev = am.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            dev != null && am.setCommunicationDevice(dev)
        } else {
            @Suppress("DEPRECATION")
            runCatching { am.startBluetoothSco(); am.isBluetoothScoOn = true; true }.getOrDefault(false)
        }
    }

    private fun releaseBudsMic(am: AudioManager) {
        if (Build.VERSION.SDK_INT >= 31) am.clearCommunicationDevice()
        else @Suppress("DEPRECATION") runCatching { am.stopBluetoothSco(); am.isBluetoothScoOn = false }
    }
}

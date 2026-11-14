package com.cobra.audiobridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * Receives PC sound over UDP (USB tethering network) and plays it with a low-latency AudioTrack,
 * and (optionally) records the mic and sends it back to the PC.
 * Packet = 12-byte header ("AB", type, channels, sampleRate LE32, seq LE32) + 16-bit PCM.
 */
public class BridgeService extends Service {
    public static final int PORT = 50505;
    public static final String EXTRA_MIC = "mic";      // 0 = off, 1 = phone mic, 2 = headphone mic
    public static final String ACTION_STOP = "com.cobra.audiobridge.STOP";

    static final byte T_HELLO = 1, T_ACK = 2, T_AUDIO = 3, T_MIC = 4, T_BYE = 5;
    static final int HDR = 12;

    // Status shown by the activity (same process).
    public static volatile boolean running;
    public static volatile String pcAddress;
    public static volatile int queueMs;
    public static volatile long packets, dropped;
    public static volatile String micStatus = "";
    public static volatile String error;
    public static volatile int bufferMs = 40;
    public static volatile long lastAudioAt;        // elapsedRealtime of the last sound packet
    public static volatile String outputInfo = "";   // where Android is playing it
    private long lastInfoAt;   // max sound waiting in the phone; the slider changes it live

    private volatile boolean stop;
    private volatile DatagramSocket socket;
    private volatile SocketAddress pc;
    private long lastPcPacket;
    private Thread rxThread, micThread;
    private int micMode;
    private boolean commRouting;

    private AudioManager am;
    private PowerManager.WakeLock wakeLock;
    private AudioTrack track;
    private int trackRate, trackCh;
    private long framesWritten;
    private int sendSeq;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (running) return START_NOT_STICKY;

        micMode = intent == null ? 0 : intent.getIntExtra(EXTRA_MIC, 0);
        if (micMode > 0 && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            micMode = 0;
        }
        startInForeground();

        am = getSystemService(AudioManager.class);
        if (micMode == 2) routeToHeadsetMic();

        PowerManager pm = getSystemService(PowerManager.class);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "audiobridge:run");
        wakeLock.acquire();

        stop = false;
        running = true;
        error = null;
        pcAddress = null;
        packets = dropped = 0;
        lastAudioAt = 0;
        outputInfo = "";
        queueMs = 0;
        micStatus = micMode == 0 ? "Mic off" : "Starting mic…";

        rxThread = new Thread(this::receiveLoop, "bridge-rx");
        rxThread.start();
        if (micMode > 0) {
            micThread = new Thread(this::micLoop, "bridge-mic");
            micThread.start();
        }
        return START_NOT_STICKY;
    }

    private void startInForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("bridge", "Audio Bridge", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stopPi = PendingIntent.getService(this, 1,
                new Intent(this, BridgeService.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE);
        String text = micMode == 0 ? "Playing PC sound" : "Playing PC sound · mic on";
        Notification n = new Notification.Builder(this, "bridge")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Audio Bridge is running")
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder((Icon) null, "Stop", stopPi).build())
                .build();
        if (Build.VERSION.SDK_INT >= 30) {
            int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;
            if (micMode > 0) type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            startForeground(1, n, type);
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(1, n);
        }
    }

    /** Bluetooth headphones only offer their mic in "call mode" (SCO). */
    @SuppressWarnings("deprecation")
    private void routeToHeadsetMic() {
        am.setMode(AudioManager.MODE_IN_COMMUNICATION);
        commRouting = true;
        if (Build.VERSION.SDK_INT >= 31) {
            boolean ok = false;
            for (AudioDeviceInfo d : am.getAvailableCommunicationDevices()) {
                if (d.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                    ok = am.setCommunicationDevice(d);
                    break;
                }
            }
            if (!ok) micStatus = "No Bluetooth headphone mic found — using phone mic";
        } else {
            am.startBluetoothSco();
            am.setBluetoothScoOn(true);
        }
    }

    @SuppressWarnings("deprecation")
    private void undoRouting() {
        if (!commRouting || am == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                am.clearCommunicationDevice();
            } else {
                am.setBluetoothScoOn(false);
                am.stopBluetoothSco();
            }
            am.setMode(AudioManager.MODE_NORMAL);
        } catch (Exception ignored) { }
        commRouting = false;
    }

    // ---------------- network ----------------

    static void writeHeader(byte[] b, byte type, int ch, int rate, int seq) {
        b[0] = 'A'; b[1] = 'B'; b[2] = type; b[3] = (byte) ch;
        b[4] = (byte) rate; b[5] = (byte) (rate >> 8); b[6] = (byte) (rate >> 16); b[7] = (byte) (rate >> 24);
        b[8] = (byte) seq; b[9] = (byte) (seq >> 8); b[10] = (byte) (seq >> 16); b[11] = (byte) (seq >> 24);
    }

    static int readInt(byte[] b, int o) {
        return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8) | ((b[o + 2] & 0xff) << 16) | ((b[o + 3] & 0xff) << 24);
    }

    private void receiveLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        DatagramSocket s;
        try {
            s = new DatagramSocket(null);
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(PORT));
            s.setSoTimeout(500);
            s.setReceiveBufferSize(64 * 1024);
            socket = s;
        } catch (Exception e) {
            error = "Could not open port " + PORT + ": " + e.getMessage();
            return;
        }

        byte[] name = (Build.MANUFACTURER + " " + Build.MODEL).getBytes(StandardCharsets.UTF_8);
        byte[] ack = new byte[HDR + name.length];
        System.arraycopy(name, 0, ack, HDR, name.length);

        byte[] buf = new byte[8192];
        DatagramPacket p = new DatagramPacket(buf, buf.length);
        while (!stop) {
            try {
                p.setLength(buf.length);
                s.receive(p);
            } catch (SocketTimeoutException t) {
                checkPcTimeout();
                continue;
            } catch (Exception e) {
                if (stop) break;
                SystemClock.sleep(20);
                continue;
            }
            int len = p.getLength();
            if (len < HDR || buf[0] != 'A' || buf[1] != 'B') continue;
            long now = SystemClock.elapsedRealtime();
            switch (buf[2]) {
                case T_HELLO: {
                    learnPc(p.getSocketAddress(), now);
                    writeHeader(ack, T_ACK, 0, 0, 0);
                    try { s.send(new DatagramPacket(ack, ack.length, p.getSocketAddress())); } catch (Exception ignored) { }
                    break;
                }
                case T_AUDIO: {
                    learnPc(p.getSocketAddress(), now);
                    int ch = buf[3];
                    int rate = readInt(buf, 4);
                    if (ch < 1 || ch > 2 || rate < 8000 || rate > 192000) break;
                    play(buf, HDR, len - HDR, rate, ch);
                    break;
                }
                case T_BYE:
                    pc = null;
                    pcAddress = null;
                    break;
            }
            checkPcTimeout();
        }
        try { s.close(); } catch (Exception ignored) { }
    }

    private void learnPc(SocketAddress from, long now) {
        lastPcPacket = now;
        if (!from.equals(pc)) {
            pc = from;
            pcAddress = ((InetSocketAddress) from).getAddress().getHostAddress();
        }
    }

    private void checkPcTimeout() {
        if (pc != null && SystemClock.elapsedRealtime() - lastPcPacket > 4000) {
            pc = null;
            pcAddress = null;
            queueMs = 0;
        }
    }

    // ---------------- playback ----------------

    private int trackBufferMs;

    private void makeTrack(int rate, int ch) {
        releaseTrack();
        int mask = ch == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
        int frameBytes = 2 * ch;
        int min = AudioTrack.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_16BIT);
        int ms = bufferMs;
        // The buffer IS the max delay. Android only starts playing once the buffer has filled up,
        // so it must be no bigger than what we let wait in it.
        int size = Math.max(min, rate * ms / 1000 * frameBytes);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(micMode == 2 ? AudioAttributes.USAGE_VOICE_COMMUNICATION : AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        try {
            AudioTrack t = new AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(rate).setChannelMask(mask)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(size)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .build();
            if (Build.VERSION.SDK_INT >= 31) {
                // Start as soon as a little sound has arrived instead of waiting for a full buffer.
                try { t.setStartThresholdInFrames(Math.max(1, rate * Math.min(ms, 20) / 1000)); } catch (Exception ignored) { }
            }
            t.play();
            track = t;
            trackRate = rate;
            trackCh = ch;
            trackBufferMs = ms;
            framesWritten = 0;
            error = null;
        } catch (Exception e) {
            error = "Could not start playback: " + e.getMessage();
            track = null;
        }
    }

    private void play(byte[] b, int off, int n, int rate, int ch) {
        if (track == null || rate != trackRate || ch != trackCh || bufferMs != trackBufferMs) makeTrack(rate, ch);
        AudioTrack t = track;
        if (t == null) return;
        int frameBytes = 2 * ch;
        int frames = n / frameBytes;
        if (frames == 0) return;
        packets++;
        long now = SystemClock.elapsedRealtime();
        lastAudioAt = now;
        if (now - lastInfoAt > 1000) {
            lastInfoAt = now;
            String dev = "?";
            try {
                AudioDeviceInfo d = t.getRoutedDevice();
                if (d != null) dev = d.getProductName() + " (" + deviceType(d.getType()) + ")";
            } catch (Exception ignored) { }
            String state = t.getPlayState() == AudioTrack.PLAYSTATE_PLAYING ? "playing" : "NOT playing";
            String lat = "";
            try {
                android.media.AudioTimestamp ts = new android.media.AudioTimestamp();
                if (t.getTimestamp(ts)) {
                    // A sample written now reaches the headphones after: frames not yet heard minus time since the stamp.
                    long ms = (framesWritten - ts.framePosition) * 1000 / rate - (System.nanoTime() - ts.nanoTime) / 1_000_000;
                    if (ms > 0 && ms < 5000) lat = "\nPhone + Bluetooth delay: about " + ms + " ms";
                }
            } catch (Exception ignored) { }
            outputInfo = "Output: " + dev + ", " + state + ", " + rate + " Hz, buffer " + t.getBufferSizeInFrames() * 1000 / rate + " ms" + lat;
        }

        int queued = (int) framesWritten - t.getPlaybackHeadPosition();   // int maths handles the 32-bit wrap
        if (queued < 0) queued = 0;
        queueMs = queued * 1000 / rate;

        // Non-blocking: if the buffer is full (the PC's clock runs a hair fast), the extra is skipped
        // instead of letting the delay grow.
        int w = t.write(b, off, frames * frameBytes, AudioTrack.WRITE_NON_BLOCKING);
        if (w > 0) framesWritten += w / frameBytes;
        if (w < frames * frameBytes) dropped++;
        if (w < 0) makeTrack(rate, ch);   // track died (e.g. headphones reconnected): rebuild it
        if (t.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) { try { t.play(); } catch (Exception ignored) { } }
    }

    static String deviceType(int t) {
        switch (t) {
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: return "Bluetooth";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return "Bluetooth call mode";
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return "phone speaker";
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE: return "earpiece";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES: case AudioDeviceInfo.TYPE_WIRED_HEADSET: return "wired";
            case AudioDeviceInfo.TYPE_USB_DEVICE: case AudioDeviceInfo.TYPE_USB_HEADSET: case AudioDeviceInfo.TYPE_USB_ACCESSORY: return "USB";
            default: return "type " + t;
        }
    }

    private void releaseTrack() {
        AudioTrack t = track;
        track = null;
        if (t == null) return;
        try { t.pause(); t.flush(); } catch (Exception ignored) { }
        t.release();
    }

    // ---------------- mic ----------------

    private void micLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        int src = micMode == 2 ? MediaRecorder.AudioSource.VOICE_COMMUNICATION : MediaRecorder.AudioSource.MIC;
        AudioRecord rec = null;
        int rate = 48000;
        for (int r : new int[]{48000, 16000}) {
            int min = AudioRecord.getMinBufferSize(r, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) continue;
            try {
                rec = new AudioRecord(src, r, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, r / 5));
            } catch (SecurityException e) {
                micStatus = "Mic permission was denied";
                return;
            }
            if (rec.getState() == AudioRecord.STATE_INITIALIZED) { rate = r; break; }
            rec.release();
            rec = null;
        }
        if (rec == null) { micStatus = "Could not open the microphone"; return; }

        if (micMode == 1) {
            for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
                if (d.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC) { rec.setPreferredDevice(d); break; }
            }
        }

        int frames = rate / 200;   // 5 ms per packet
        byte[] pkt = new byte[HDR + frames * 2];
        rec.startRecording();
        if (!micStatus.startsWith("No Bluetooth"))
            micStatus = "Mic on (" + (micMode == 2 ? "headphones" : "phone") + ", " + rate / 1000 + " kHz)";

        while (!stop) {
            int n = rec.read(pkt, HDR, frames * 2);
            if (n <= 0) { SystemClock.sleep(10); continue; }
            SocketAddress to = pc;
            DatagramSocket s = socket;
            if (to == null || s == null) continue;
            writeHeader(pkt, T_MIC, 1, rate, sendSeq++);
            try { s.send(new DatagramPacket(pkt, HDR + n, to)); } catch (Exception ignored) { }
        }
        try { rec.stop(); } catch (Exception ignored) { }
        rec.release();
    }

    @Override
    public void onDestroy() {
        stop = true;
        DatagramSocket s = socket;
        SocketAddress to = pc;
        if (s != null && to != null) {
            Thread bye = new Thread(() -> {
                byte[] b = new byte[HDR];
                writeHeader(b, T_BYE, 0, 0, 0);
                try { s.send(new DatagramPacket(b, b.length, to)); } catch (Exception ignored) { }
            });
            bye.start();
            try { bye.join(300); } catch (InterruptedException ignored) { }
        }
        if (s != null) s.close();
        try { if (rxThread != null) rxThread.join(1500); } catch (InterruptedException ignored) { }
        try { if (micThread != null) micThread.join(1500); } catch (InterruptedException ignored) { }
        releaseTrack();
        undoRouting();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        socket = null;
        pc = null;
        pcAddress = null;
        running = false;
        super.onDestroy();
    }
}

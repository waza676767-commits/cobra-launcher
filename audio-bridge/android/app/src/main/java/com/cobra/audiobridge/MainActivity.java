package com.cobra.audiobridge;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.SystemClock;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private TextView status, details, bufferLabel;
    private RadioGroup micGroup;
    private SeekBar bufferBar;
    private Button startBtn;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 300); }
    };

    public static final String EXTRA_START = "start";
    private CheckBox autoStart;

    private static final int[] MIC_IDS = {101, 102, 103};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("bridge", MODE_PRIVATE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        int pad = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        String ver = "";
        try { ver = " v" + getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { }
        TextView title = text("Audio Bridge" + ver, 26, true);
        root.addView(title);
        root.addView(text("PC sound → phone → Bluetooth headphones, and your mic back to the PC.", 14, false));
        space(root, 16);

        status = text("Stopped", 20, true);
        root.addView(status);
        details = text("", 14, false);
        details.setTextColor(Color.GRAY);
        root.addView(details);
        space(root, 16);

        root.addView(text("Microphone for the PC", 16, true));
        micGroup = new RadioGroup(this);
        String[] labels = {
                "No mic",
                "Phone mic — headphones keep full sound quality",
                "Headphone mic — Bluetooth switches to \"call mode\", so PC sound becomes lower quality (that's a Bluetooth limit)"
        };
        for (int i = 0; i < 3; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(MIC_IDS[i]);
            rb.setText(labels[i]);
            rb.setTextSize(15);
            micGroup.addView(rb);
        }
        micGroup.check(MIC_IDS[Math.max(0, Math.min(2, prefs.getInt("mic", 1)))]);
        micGroup.setOnCheckedChangeListener((g, id) -> prefs.edit().putInt("mic", micMode()).apply());
        root.addView(micGroup);
        space(root, 16);

        bufferLabel = text("", 16, true);
        root.addView(bufferLabel);
        bufferBar = new SeekBar(this);
        bufferBar.setMax(140);   // 10 .. 150 ms
        bufferBar.setProgress(prefs.getInt("buffer", 40) - 10);
        bufferBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) { applyBuffer(); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { prefs.edit().putInt("buffer", BridgeService.bufferMs).apply(); }
        });
        root.addView(bufferBar);
        TextView bufHint = text("Lower = less delay. If the sound crackles or cuts out, move it to the right.", 13, false);
        bufHint.setTextColor(Color.GRAY);
        root.addView(bufHint);
        applyBuffer();
        space(root, 20);

        startBtn = new Button(this);
        startBtn.setTextSize(18);
        startBtn.setOnClickListener(v -> {
            if (BridgeService.running) stopBridge(); else startBridge();
        });
        root.addView(startBtn, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64)));
        Button testBtn = new Button(this);
        testBtn.setText("Play test sound on this phone");
        testBtn.setOnClickListener(v -> playLocalTest());
        root.addView(testBtn);
        autoStart = new CheckBox(this);
        autoStart.setText("Start automatically when I open the app");
        autoStart.setTextSize(15);
        autoStart.setChecked(prefs.getBoolean("auto", true));
        autoStart.setOnCheckedChangeListener((b, on) -> prefs.edit().putBoolean("auto", on).apply());
        root.addView(autoStart);
        space(root, 20);

        TextView help = text(
                "How to use:\n" +
                "1. Connect your Bluetooth headphones to this phone.\n" +
                "2. Plug the phone into the PC with USB and turn on USB tethering " +
                "(Settings → Hotspot & tethering → USB tethering).\n" +
                "3. Press Start here, then press Start in Audio Bridge on the PC.\n\n" +
                "You can turn the screen off — it keeps running. Stop it from here or from the notification.\n\n" +
                "Fastest way every day: pull down the notification shade, tap the pencil/edit button, and drag the " +
                "\"Audio Bridge\" tile into your quick settings. Then it's one tap.", 14, false);
        root.addView(help);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        if (savedInstanceState == null) maybeAutoStart(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        maybeAutoStart(intent);
    }

    private void maybeAutoStart(Intent intent) {
        boolean fromTile = intent != null && intent.getBooleanExtra(EXTRA_START, false);
        if (!BridgeService.running && (fromTile || prefs.getBoolean("auto", true))) startBridge();
    }

    /** Plays a beep straight from this phone, so we know whether phone → headphones works at all. */
    private void playLocalTest() {
        AudioManager am = getSystemService(AudioManager.class);
        if (am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
            details.setText("Your media volume is at 0. Turn it up with the volume buttons.");
        }
        new Thread(() -> {
            int rate = 48000, n = rate;
            short[] pcm = new short[n * 2];
            for (int i = 0; i < n; i++) {
                short v = (short) (Math.sin(2 * Math.PI * 660 * i / (double) rate) * 9000);
                pcm[2 * i] = v; pcm[2 * i + 1] = v;
            }
            try {
                AudioTrack t = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                        .setBufferSizeInBytes(pcm.length * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();
                t.write(pcm, 0, pcm.length);
                t.play();
                SystemClock.sleep(1300);
                t.release();
            } catch (Exception e) {
                runOnUiThread(() -> details.setText("Test sound failed: " + e.getMessage()));
            }
        }).start();
    }

    private int micMode() {
        int id = micGroup.getCheckedRadioButtonId();
        for (int i = 0; i < 3; i++) if (MIC_IDS[i] == id) return i;
        return 0;
    }

    private void applyBuffer() {
        BridgeService.bufferMs = bufferBar.getProgress() + 10;
        bufferLabel.setText("Max delay buffer: " + BridgeService.bufferMs + " ms");
    }

    private void startBridge() {
        List<String> need = new ArrayList<>();
        if (micMode() > 0 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !prefs.getBoolean("askedNotif", false)) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
            prefs.edit().putBoolean("askedNotif", true).apply();
        }
        if (!need.isEmpty()) {
            requestPermissions(need.toArray(new String[0]), 1);
            return;   // continues in onRequestPermissionsResult
        }
        launchService();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        launchService();   // without mic permission the service just runs with the mic off
    }

    private void launchService() {
        Intent i = new Intent(this, BridgeService.class);
        i.putExtra(BridgeService.EXTRA_MIC, micMode());
        startForegroundService(i);
        handler.postDelayed(this::refresh, 200);
    }

    private void stopBridge() {
        stopService(new Intent(this, BridgeService.class));
        handler.postDelayed(this::refresh, 200);
    }

    private void refresh() {
        boolean on = BridgeService.running;
        startBtn.setText(on ? "Stop" : "Start");
        for (int i = 0; i < micGroup.getChildCount(); i++) micGroup.getChildAt(i).setEnabled(!on);
        if (!on) {
            status.setText("Stopped");
            status.setTextColor(Color.LTGRAY);
            details.setText(BridgeService.error != null ? BridgeService.error : "Press Start.");
            return;
        }
        StringBuilder d = new StringBuilder();
        if (BridgeService.pcAddress != null) {
            status.setText("Connected to PC");
            status.setTextColor(Color.rgb(102, 187, 106));
            d.append("PC: ").append(BridgeService.pcAddress);
            if (BridgeService.packets == 0) {
                d.append("\nNo sound packets from the PC yet. Play something on the PC or press \"Send test beep\" there.");
            } else {
                long ago = (SystemClock.elapsedRealtime() - BridgeService.lastAudioAt) / 1000;
                d.append("\nSound packets: ").append(BridgeService.packets)
                 .append(ago <= 1 ? " (arriving now)" : " (last one " + ago + " s ago)")
                 .append("\nSound waiting on phone: ").append(BridgeService.queueMs).append(" ms");
                if (BridgeService.dropped > 0) d.append(" · skipped ").append(BridgeService.dropped);
                d.append("\n").append(BridgeService.outputInfo);
            }
        } else {
            status.setText("Waiting for the PC…");
            status.setTextColor(Color.rgb(255, 167, 38));
            d.append("Turn on USB tethering and press Start in the PC app.");
        }
        d.append("\n").append(BridgeService.micStatus);
        if (BridgeService.error != null) d.append("\n").append(BridgeService.error);
        details.setText(d);
    }

    @Override protected void onResume() { super.onResume(); handler.post(ticker); }
    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(ticker); }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setGravity(Gravity.START);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void space(LinearLayout parent, int dpHeight) {
        parent.addView(new android.view.View(this), new LinearLayout.LayoutParams(1, dp(dpHeight)));
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}

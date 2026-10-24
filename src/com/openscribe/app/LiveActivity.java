package com.openscribe.app;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.vosk.Model;
import org.vosk.Recognizer;

import java.util.Locale;

public class LiveActivity extends Activity {

    private static final int REQ_AUDIO = 10;
    private static final int SR = 16000;

    private TextView timer, status, text, hint, btnCopy, btnClear;
    private WaveformView wave;
    private PulseRingView pulse;
    private ImageView btnRecord;

    private volatile boolean recording = false;
    private Thread recThread;
    private final StringBuilder finalText = new StringBuilder();
    private String partial = "";
    private long startTime = 0;

    private final Handler h = new Handler(Looper.getMainLooper());

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!recording) return;
            long e = (System.currentTimeMillis() - startTime) / 1000L;
            timer.setText(String.format(Locale.US, "%02d:%02d:%02d", e / 3600, (e % 3600) / 60, e % 60));
            h.postDelayed(this, 200);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_live);

        timer = findViewById(R.id.timer);
        status = findViewById(R.id.status);
        text = findViewById(R.id.text);
        hint = findViewById(R.id.hint);
        wave = findViewById(R.id.wave);
        pulse = findViewById(R.id.pulse);
        btnRecord = findViewById(R.id.btn_record);
        btnCopy = findViewById(R.id.btn_copy);
        btnClear = findViewById(R.id.btn_clear);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        btnRecord.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (recording) stop(); else request(); }
        });
        btnCopy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String s = finalText.toString().trim();
                if (s.isEmpty()) return;
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("transcript", s));
                toast("Copied");
            }
        });
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                finalText.setLength(0);
                partial = "";
                render();
            }
        });

        Anim.riseIn(findViewById(R.id.timer_card), 40);
        Anim.riseIn(findViewById(R.id.wave_card), 110);
        Anim.riseIn(findViewById(R.id.btn_record), 180);
    }

    private void request() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        start();
    }

    private void start() {
        if (VoskEngine.isReady()) {
            begin();
            return;
        }
        status.setText(R.string.preparing);
        hint.setText("Unpacking the on-device model (one time)…");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final Model m = VoskEngine.model(LiveActivity.this, new VoskEngine.Progress() {
                        @Override public void onProgress(final int percent) {
                            runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    if (!recording) status.setText("PREPARING " + percent + "%");
                                }
                            });
                        }
                    });
                    runOnUiThread(new Runnable() {
                        @Override public void run() { begin(); }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            status.setText(R.string.stopped);
                            hint.setText(R.string.tap_to_start);
                            toast("Model error: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void begin() {
        if (recording) return;
        recording = true;
        startTime = System.currentTimeMillis();
        finalText.setLength(0);
        partial = "";
        render();
        status.setText(R.string.listening);
        hint.setText("Speak now — text appears live");
        wave.setActive(true);
        pulse.setActive(true);
        Anim.press(btnRecord);
        h.post(ticker);

        recThread = new Thread(new Runnable() {
            @Override public void run() {
                AudioRecord ar = null;
                Recognizer rec = null;
                try {
                    Model m = VoskEngine.model(LiveActivity.this, null);
                    int min = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                    if (min <= 0) min = SR * 2;
                    int buf = Math.max(min, 4096);
                    ar = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SR,
                            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buf);
                    rec = new Recognizer(m, (float) SR);
                    rec.setWords(false);
                    ar.startRecording();
                    short[] data = new short[2048];
                    while (recording) {
                        int n = ar.read(data, 0, data.length);
                        if (n <= 0) continue;
                        wave.setLevel(level(data, n));
                        if (rec.acceptWaveForm(data, n)) {
                            final String res = VoskEngine.textOf(rec.getResult());
                            if (!res.isEmpty()) runOnUiThread(new Runnable() {
                                @Override public void run() { appendFinal(res); }
                            });
                        } else {
                            final String p = VoskEngine.textOf(rec.getPartialResult());
                            runOnUiThread(new Runnable() {
                                @Override public void run() { partial = p; render(); }
                            });
                        }
                    }
                    final String fin = VoskEngine.textOf(rec.getFinalResult());
                    if (!fin.isEmpty()) runOnUiThread(new Runnable() {
                        @Override public void run() { appendFinal(fin); }
                    });
                } catch (Exception ignored) {
                } finally {
                    try { if (ar != null) { ar.stop(); ar.release(); } } catch (Exception ignored) {}
                    if (rec != null) rec.close();
                }
            }
        });
        recThread.start();
    }

    private void appendFinal(String s) {
        if (finalText.length() > 0) finalText.append(' ');
        finalText.append(s);
        partial = "";
        render();
    }

    private static float level(short[] d, int n) {
        long sum = 0;
        for (int i = 0; i < n; i++) sum += (long) d[i] * d[i];
        double rms = Math.sqrt(sum / (double) n) / 32768.0;
        float v = (float) (rms * 6.0);
        if (v < 0f) v = 0f;
        if (v > 1f) v = 1f;
        return v;
    }

    private void stop() {
        recording = false;
        h.removeCallbacks(ticker);
        status.setText(R.string.stopped);
        hint.setText(R.string.tap_to_start);
        wave.setActive(false);
        pulse.setActive(false);
    }

    private void render() {
        String s = finalText.toString();
        if (!partial.isEmpty()) {
            if (!s.isEmpty()) s += " ";
            s += partial;
        }
        text.setText(s);
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }

    @Override protected void onDestroy() {
        stop();
        if (recThread != null) {
            try { recThread.join(600); } catch (InterruptedException ignored) {}
        }
        super.onDestroy();
    }

    @Override public void onRequestPermissionsResult(int rc, String[] p, int[] g) {
        if (rc == REQ_AUDIO) {
            if (g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) start();
            else toast("Microphone permission is needed to dictate");
        }
    }
}

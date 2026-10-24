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

import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Live dictation. Whisper is not a streaming model, so we record in short chunks
 * and transcribe each chunk on a worker thread, appending the text as it lands.
 */
public class LiveActivity extends Activity {

    private static final int REQ_AUDIO = 10;
    private static final int SR = 16000;
    private static final int CHUNK_SECONDS = 8;

    private TextView timer, status, text, hint, btnCopy, btnClear;
    private WaveformView wave;
    private PulseRingView pulse;
    private ImageView btnRecord;

    private volatile boolean recording = false;
    private Thread recThread, worker;
    private final LinkedBlockingQueue<float[]> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean working = new AtomicBoolean(false);
    private final StringBuilder finalText = new StringBuilder();

    private long startTime = 0;
    private final Handler h = new Handler(Looper.getMainLooper());

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!recording && queue.isEmpty() && !working.get()) return;
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
                text.setText("");
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
        if (WhisperEngine.isReady()) { begin(); return; }
        status.setText(R.string.preparing);
        hint.setText("Unpacking the on-device model (one time)…");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    WhisperEngine.ctx(LiveActivity.this, new WhisperEngine.Progress() {
                        @Override public void onProgress(final int percent) {
                            runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    if (!recording) status.setText("PREPARING " + percent + "%");
                                }
                            });
                        }
                    });
                    runOnUiThread(new Runnable() { @Override public void run() { begin(); } });
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
        text.setText("");
        status.setText(R.string.listening);
        hint.setText("Speak now — text appears as each phrase is processed");
        wave.setActive(true);
        pulse.setActive(true);
        Anim.press(btnRecord);
        h.post(ticker);

        queue.clear();

        worker = new Thread(new Runnable() {
            @Override public void run() {
                while (recording || !queue.isEmpty()) {
                    float[] chunk;
                    try { chunk = queue.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS); }
                    catch (InterruptedException e) { break; }
                    if (chunk == null) continue;
                    working.set(true);
                    setStatusWorking();
                    try {
                        final String t = WhisperEngine.transcribeText(LiveActivity.this, chunk, Prefs.lang(LiveActivity.this));
                        if (t != null && !t.isEmpty()) runOnUiThread(new Runnable() {
                            @Override public void run() { appendFinal(t); }
                        });
                    } catch (Exception ignored) {
                    } finally {
                        working.set(false);
                    }
                }
                setStatusDone();
            }
        });
        worker.start();

        recThread = new Thread(new Runnable() {
            @Override public void run() {
                AudioRecord ar = null;
                try {
                    int min = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                    if (min <= 0) min = SR * 2;
                    int buf = Math.max(min, 4096);
                    ar = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SR,
                            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buf);
                    ar.startRecording();
                    short[] read = new short[2048];
                    short[] acc = new short[SR * CHUNK_SECONDS];
                    int filled = 0;
                    while (recording) {
                        int n = ar.read(read, 0, read.length);
                        if (n <= 0) continue;
                        wave.setLevel(level(read, n));
                        for (int i = 0; i < n; i++) {
                            acc[filled++] = read[i];
                            if (filled == acc.length) {
                                enqueue(acc, filled);
                                filled = 0;
                            }
                        }
                    }
                    if (filled > SR / 2) enqueue(acc, filled);
                } catch (Exception ignored) {
                } finally {
                    try { if (ar != null) { ar.stop(); ar.release(); } } catch (Exception ignored) {}
                }
            }
        });
        recThread.start();
    }

    private void enqueue(short[] acc, int n) {
        float[] f = new float[n];
        for (int i = 0; i < n; i++) f[i] = acc[i] / 32768f;
        queue.offer(f);
    }

    private void setStatusWorking() {
        runOnUiThread(new Runnable() {
            @Override public void run() { if (recording) status.setText(R.string.processing); }
        });
    }

    private void setStatusDone() {
        runOnUiThread(new Runnable() {
            @Override public void run() { if (!recording) status.setText(R.string.stopped); }
        });
    }

    private void appendFinal(String s) {
        if (finalText.length() > 0) finalText.append(' ');
        finalText.append(s);
        text.setText(finalText.toString());
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

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }

    @Override protected void onDestroy() {
        stop();
        if (recThread != null) { try { recThread.join(800); } catch (InterruptedException ignored) {} }
        if (worker != null) { try { worker.join(800); } catch (InterruptedException ignored) {} }
        super.onDestroy();
    }

    @Override public void onRequestPermissionsResult(int rc, String[] p, int[] g) {
        if (rc == REQ_AUDIO) {
            if (g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) start();
            else toast("Microphone permission is needed to dictate");
        }
    }
}

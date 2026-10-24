package com.openscribe.app;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

public class LiveActivity extends Activity implements RecognitionListener {

    private static final int REQ_AUDIO = 10;

    private SpeechRecognizer sr;
    private TextView timer, status, text, hint, btnCopy, btnClear;
    private WaveformView wave;
    private PulseRingView pulse;
    private ImageView btnRecord;

    private boolean recording = false;
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

    private final Runnable restarter = new Runnable() {
        @Override public void run() { if (recording) listen(); }
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
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("No speech recognition service is available on this device");
            return;
        }
        if (sr == null) {
            sr = SpeechRecognizer.createSpeechRecognizer(this);
            sr.setRecognitionListener(this);
        }
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
        listen();
    }

    private void listen() {
        if (sr == null) return;
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        String lang = Prefs.lang(this);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        if (Prefs.offline(this)) i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        try {
            sr.startListening(i);
        } catch (Exception e) {
            scheduleRestart(400);
        }
    }

    private void stop() {
        recording = false;
        h.removeCallbacks(ticker);
        h.removeCallbacks(restarter);
        if (sr != null) {
            try { sr.stopListening(); } catch (Exception ignored) {}
        }
        status.setText(R.string.stopped);
        hint.setText(R.string.tap_to_start);
        wave.setActive(false);
        pulse.setActive(false);
    }

    private void scheduleRestart(long ms) {
        h.removeCallbacks(restarter);
        h.postDelayed(restarter, ms);
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
        if (sr != null) {
            sr.destroy();
            sr = null;
        }
        super.onDestroy();
    }

    @Override public void onRequestPermissionsResult(int rc, String[] p, int[] g) {
        if (rc == REQ_AUDIO) {
            if (g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) start();
            else toast("Microphone permission is needed to dictate");
        }
    }

    // ---- RecognitionListener ----

    @Override public void onReadyForSpeech(Bundle params) {}

    @Override public void onBeginningOfSpeech() {}

    @Override public void onRmsChanged(float rmsdB) {
        float lvl = (rmsdB + 2f) / 12f;
        if (lvl < 0f) lvl = 0f;
        if (lvl > 1f) lvl = 1f;
        wave.setLevel(lvl);
    }

    @Override public void onBufferReceived(byte[] buffer) {}

    @Override public void onEndOfSpeech() {}

    @Override public void onError(int error) {
        if (!recording) return;
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) return;
        scheduleRestart(250);
    }

    @Override public void onResults(Bundle results) {
        if (!recording) return;
        ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list != null && !list.isEmpty()) {
            String s = list.get(0).trim();
            if (!s.isEmpty()) {
                if (finalText.length() > 0) finalText.append(' ');
                finalText.append(s);
            }
        }
        partial = "";
        render();
        scheduleRestart(120);
    }

    @Override public void onPartialResults(Bundle partialResults) {
        if (!recording) return;
        ArrayList<String> list = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list != null && !list.isEmpty()) {
            partial = list.get(0).trim();
            render();
        }
    }

    @Override public void onEvent(int eventType, Bundle params) {}
}

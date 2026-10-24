package com.openscribe.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;

public class FileActivity extends Activity {

    private static final int REQ_PICK = 20;
    private static final int REQ_CREATE = 21;

    private Uri uri;
    private String name = "";
    private String mime = "";

    private TextView fileName, fileInfo, text, srt, btnTranscribe;
    private ProgressBar progress;

    private String lastText = "";
    private String lastSrt = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_file);

        fileName = findViewById(R.id.file_name);
        fileInfo = findViewById(R.id.file_info);
        text = findViewById(R.id.text);
        srt = findViewById(R.id.srt);
        btnTranscribe = findViewById(R.id.btn_transcribe);
        progress = findViewById(R.id.progress);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        findViewById(R.id.pick_card).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Anim.press(v); pick(); }
        });
        btnTranscribe.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Anim.press(v); transcribe(); }
        });
        findViewById(R.id.btn_copy).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Anim.press(v); copy(); }
        });
        findViewById(R.id.btn_srt).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Anim.press(v); saveSrt(); }
        });
        findViewById(R.id.btn_share).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Anim.press(v); share(); }
        });
    }

    private void pick() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "audio/*", "application/ogg", "application/octet-stream", "video/mp4"
        });
        try {
            startActivityForResult(i, REQ_PICK);
        } catch (Exception e) {
            toast("No file picker available");
        }
    }

    @Override protected void onActivityResult(int rc, int res, Intent data) {
        super.onActivityResult(rc, res, data);
        if (rc == REQ_PICK && res == RESULT_OK && data != null && data.getData() != null) {
            uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            mime = getContentResolver().getType(uri);
            resolveMeta();
        } else if (rc == REQ_CREATE && res == RESULT_OK && data != null && data.getData() != null) {
            writeSrt(data.getData());
        }
    }

    private void resolveMeta() {
        name = "";
        long size = -1;
        try {
            Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                int ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int si = c.getColumnIndex(OpenableColumns.SIZE);
                if (c.moveToFirst()) {
                    if (ni >= 0) name = c.getString(ni);
                    if (si >= 0) size = c.getLong(si);
                }
                c.close();
            }
        } catch (Exception ignored) {}
        if (name == null || name.isEmpty()) name = uri.getLastPathSegment();
        fileName.setText(name);
        if (size > 0) fileInfo.setText(human(size) + (mime != null ? " · " + mime : ""));
        else fileInfo.setText(mime != null ? mime : "audio file");
        text.setText("—");
        srt.setText("—");
        lastText = "";
        lastSrt = "";
    }

    private void transcribe() {
        if (uri == null) {
            toast("Pick an audio file first");
            return;
        }
        progress.setVisibility(View.VISIBLE);
        btnTranscribe.setAlpha(0.5f);
        btnTranscribe.setClickable(false);
        text.setText(WhisperEngine.isReady() ? getString(R.string.processing) : getString(R.string.preparing));
        srt.setText("—");

        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final WhisperEngine.Result r = WhisperEngine.transcribeFile(FileActivity.this, uri);
                    runOnUiThread(new Runnable() { @Override public void run() { show(r); } });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            progress.setVisibility(View.GONE);
                            btnTranscribe.setAlpha(1f);
                            btnTranscribe.setClickable(true);
                            text.setText("—");
                            toast("Failed: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void show(WhisperEngine.Result r) {
        progress.setVisibility(View.GONE);
        btnTranscribe.setAlpha(1f);
        btnTranscribe.setClickable(true);
        lastText = r.text == null ? "" : r.text.trim();
        lastSrt = r.srt == null ? "" : r.srt.trim();
        text.setText(lastText.isEmpty() ? "(no speech detected)" : lastText);
        srt.setText(lastSrt.isEmpty() ? "—" : lastSrt);
    }

    private void copy() {
        String out = lastSrt.isEmpty() ? lastText : lastText + "\n\n" + lastSrt;
        if (out.trim().isEmpty()) { toast("Nothing to copy yet"); return; }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("transcript", out));
        toast("Copied");
    }

    private void saveSrt() {
        if (lastSrt.isEmpty()) { toast("Transcribe a file first"); return; }
        String base = name == null ? "transcript" : name;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/x-subrip");
        i.putExtra(Intent.EXTRA_TITLE, base + ".srt");
        try { startActivityForResult(i, REQ_CREATE); }
        catch (Exception e) { toast("Cannot open save dialog"); }
    }

    private void writeSrt(Uri dest) {
        try {
            OutputStream os = getContentResolver().openOutputStream(dest);
            if (os == null) throw new Exception("no stream");
            os.write(lastSrt.getBytes("UTF-8"));
            os.flush();
            os.close();
            toast("Saved SRT");
        } catch (Exception e) {
            toast("Save failed: " + e.getMessage());
        }
    }

    private void share() {
        String out = lastSrt.isEmpty() ? lastText : lastText + "\n\n" + lastSrt;
        if (out.trim().isEmpty()) { toast("Nothing to share yet"); return; }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, name);
        i.putExtra(Intent.EXTRA_TEXT, out);
        startActivity(Intent.createChooser(i, "Share transcript"));
    }

    private static String human(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return (b / 1024) + " KB";
        return String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0);
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }
}

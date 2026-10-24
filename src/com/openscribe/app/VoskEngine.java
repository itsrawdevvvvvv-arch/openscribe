package com.openscribe.app;

import android.content.Context;
import android.content.res.AssetManager;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fully on-device speech recognition. The Vosk model ships inside the APK's
 * assets and is unpacked to private storage on first use. No network, no keys.
 */
public final class VoskEngine {

    private static Model model;

    public interface Progress {
        void onProgress(int percent);
    }

    public static class Word {
        public String w;
        public double s, e;
    }

    public static class Result {
        public String text;
        public String srt;
    }

    private VoskEngine() {}

    public static synchronized boolean isReady() {
        return model != null;
    }

    /** Loads (and, on first run, unpacks) the bundled model. Safe to call from any thread. */
    public static synchronized Model model(Context ctx, Progress p) throws IOException {
        if (model != null) return model;
        File dir = new File(ctx.getFilesDir(), "model");
        File marker = new File(dir, ".ready");
        if (!marker.exists()) {
            if (dir.exists()) deleteRec(dir);
            dir.mkdirs();
            AssetManager am = ctx.getAssets();
            int total = Math.max(1, count(am, "model"));
            extract(am, "model", dir, new int[]{0}, total, p);
            try { new FileOutputStream(marker).close(); } catch (Exception ignored) {}
        }
        LibVosk.setLogLevel(LogLevel.WARNINGS);
        model = new Model(dir.getAbsolutePath());
        return model;
    }

    private static int count(AssetManager am, String path) {
        try {
            String[] k = am.list(path);
            if (k == null || k.length == 0) return 1;
            int c = 0;
            for (String s : k) c += count(am, path + "/" + s);
            return c;
        } catch (IOException e) {
            return 1;
        }
    }

    private static void extract(AssetManager am, String path, File out, int[] done, int total, Progress p)
            throws IOException {
        String[] kids = am.list(path);
        if (kids == null || kids.length == 0) {
            InputStream in = am.open(path);
            OutputStream os = new FileOutputStream(out);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            in.close();
            os.flush();
            os.close();
            done[0]++;
            if (p != null) p.onProgress(Math.min(100, done[0] * 100 / total));
            return;
        }
        out.mkdirs();
        for (String k : kids) extract(am, path + "/" + k, new File(out, k), done, total, p);
    }

    private static void deleteRec(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRec(k);
        f.delete();
    }

    // ---------- Live recognition helpers ----------

    public static String textOf(String json) {
        if (json == null) return "";
        try {
            return new JSONObject(json).optString("text", "").trim();
        } catch (Exception e) {
            return "";
        }
    }

    // ---------- File transcription ----------

    public static Result transcribeFile(Context ctx, Uri uri) throws Exception {
        Model m = model(ctx, null);

        MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(ctx, uri, null);
        int track = -1;
        MediaFormat fmt = null;
        for (int i = 0; i < ex.getTrackCount(); i++) {
            MediaFormat f = ex.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                track = i;
                fmt = f;
                break;
            }
        }
        if (track < 0) {
            ex.release();
            throw new IOException("No audio track found in this file");
        }
        ex.selectTrack(track);

        MediaCodec codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
        codec.configure(fmt, null, null, 0);
        codec.start();

        StringBuilder text = new StringBuilder();
        List<Word> words = new ArrayList<>();
        Recognizer rec = null;
        int sampleRate = 16000;
        int channels = 1;
        int pcmEnc = 2; // 16-bit

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean inputDone = false, outputDone = false;
        try {
            while (!outputDone) {
                if (!inputDone) {
                    int inIdx = codec.dequeueInputBuffer(10000);
                    if (inIdx >= 0) {
                        ByteBuffer ib = codec.getInputBuffer(inIdx);
                        int sz = ex.readSampleData(ib, 0);
                        if (sz < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sz, ex.getSampleTime(), 0);
                            ex.advance();
                        }
                    }
                }
                int outIdx = codec.dequeueOutputBuffer(info, 10000);
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat of = codec.getOutputFormat();
                    if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE))
                        sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                        channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (of.containsKey(MediaFormat.KEY_PCM_ENCODING))
                        pcmEnc = of.getInteger(MediaFormat.KEY_PCM_ENCODING);
                    rec = new Recognizer(m, (float) sampleRate);
                    rec.setWords(true);
                } else if (outIdx >= 0) {
                    if (rec != null && info.size > 0) {
                        ByteBuffer ob = codec.getOutputBuffer(outIdx);
                        ob.position(info.offset);
                        ob.limit(info.offset + info.size);
                        byte[] bytes = new byte[info.size];
                        ob.get(bytes);
                        short[] mono = (pcmEnc == 4)
                                ? floatToMono(bytes, channels)
                                : bytesToMono(bytes, channels);
                        if (rec.acceptWaveForm(mono, mono.length)) {
                            collect(rec.getResult(), text, words);
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                }
            }
            if (rec != null) collect(rec.getFinalResult(), text, words);
        } finally {
            try { codec.stop(); } catch (Exception ignored) {}
            codec.release();
            ex.release();
            if (rec != null) rec.close();
        }

        Result r = new Result();
        r.text = text.toString().trim();
        r.srt = words.isEmpty() ? SrtBuilder.fromText(r.text) : SrtBuilder.fromWords(words);
        return r;
    }

    private static void collect(String json, StringBuilder text, List<Word> words) {
        if (json == null) return;
        try {
            JSONObject o = new JSONObject(json);
            String t = o.optString("text", "").trim();
            if (!t.isEmpty()) {
                if (text.length() > 0) text.append(' ');
                text.append(t);
            }
            JSONArray a = o.optJSONArray("result");
            if (a != null) {
                for (int i = 0; i < a.length(); i++) {
                    JSONObject w = a.getJSONObject(i);
                    Word x = new Word();
                    x.w = w.optString("word", "");
                    x.s = w.optDouble("start", 0);
                    x.e = w.optDouble("end", 0);
                    if (!x.w.isEmpty()) words.add(x);
                }
            }
        } catch (Exception ignored) {}
    }

    private static short[] bytesToMono(byte[] b, int ch) {
        if (ch < 1) ch = 1;
        int frames = (b.length / 2) / ch;
        short[] out = new short[frames];
        for (int i = 0; i < frames; i++) {
            int sum = 0;
            for (int c = 0; c < ch; c++) {
                int idx = (i * ch + c) * 2;
                int lo = b[idx] & 0xFF;
                int hi = b[idx + 1];
                sum += (short) ((hi << 8) | lo);
            }
            out[i] = (short) (sum / ch);
        }
        return out;
    }

    private static short[] floatToMono(byte[] b, int ch) {
        if (ch < 1) ch = 1;
        int frames = (b.length / 4) / ch;
        short[] out = new short[frames];
        ByteBuffer bb = ByteBuffer.wrap(b);
        for (int i = 0; i < frames; i++) {
            float sum = 0;
            for (int c = 0; c < ch; c++) sum += bb.getFloat((i * ch + c) * 4);
            float v = sum / ch;
            if (v > 1f) v = 1f;
            if (v < -1f) v = -1f;
            out[i] = (short) (v * 32767f);
        }
        return out;
    }

    static String fmtTime(double sec) {
        long ms = Math.round(sec * 1000.0);
        long h = ms / 3600000; ms %= 3600000;
        long m = ms / 60000; ms %= 60000;
        long s = ms / 1000; ms %= 1000;
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, ms);
    }
}

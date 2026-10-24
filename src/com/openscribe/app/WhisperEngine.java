package com.openscribe.app;

import android.content.Context;
import android.content.res.AssetManager;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Fully on-device speech recognition powered by Whisper (whisper.cpp).
 * The model ships inside the APK and is unpacked to private storage on first use.
 * No network, no keys, no accounts.
 */
public final class WhisperEngine {

    private static final String MODEL_FILE = "ggml-small-q8_0.bin";
    private static final String ASSET = "whisper/" + MODEL_FILE;
    private static final int SR = 16000;

    private static long ctx = 0;
    private static boolean libOk = false;

    public interface Progress { void onProgress(int percent); }

    public static class Segment {
        public double start, end;
        public String text;
    }

    public static class Result {
        public String text;
        public String srt;
    }

    private WhisperEngine() {}

    private static synchronized void loadLib() {
        if (libOk) return;
        System.loadLibrary("ggml-base");
        System.loadLibrary("ggml-cpu");
        System.loadLibrary("ggml");
        System.loadLibrary("whisper");
        System.loadLibrary("whisperjni");
        libOk = true;
    }

    public static synchronized boolean isReady() { return ctx != 0; }

    /** Loads (and on first run unpacks) the bundled model. Safe from any thread. */
    public static synchronized long ctx(Context c, Progress p) throws IOException {
        loadLib();
        if (ctx != 0) return ctx;
        File dir = new File(c.getFilesDir(), "whisper");
        File model = new File(dir, MODEL_FILE);
        if (!model.exists() || model.length() == 0) {
            dir.mkdirs();
            copyAsset(c.getAssets(), ASSET, model, p);
        }
        long h = nativeInit(model.getAbsolutePath());
        if (h == 0) throw new IOException("Could not load the speech model");
        ctx = h;
        return ctx;
    }

    private static void copyAsset(AssetManager am, String path, File out, Progress p) throws IOException {
        long total = 0;
        try { total = am.openFd(path).getLength(); } catch (Exception ignored) {}
        InputStream in = am.open(path);
        OutputStream os = new FileOutputStream(out);
        byte[] buf = new byte[1 << 16];
        int n; long done = 0; int last = -1;
        while ((n = in.read(buf)) > 0) {
            os.write(buf, 0, n);
            done += n;
            if (p != null && total > 0) {
                int pc = (int) (done * 100 / total);
                if (pc != last) { last = pc; p.onProgress(pc); }
            }
        }
        in.close(); os.flush(); os.close();
    }

    private static int threads() {
        int n = Runtime.getRuntime().availableProcessors() - 1;
        if (n < 2) n = 2;
        if (n > 4) n = 4;
        return n;
    }

    /** Transcribe 16 kHz mono float PCM. */
    public static Result transcribe(Context c, float[] pcm, String lang) throws IOException {
        long h = ctx(c, null);
        String raw = nativeTranscribe(h, pcm, lang == null ? "auto" : lang, threads());
        List<Segment> segs = parse(raw);
        Result r = new Result();
        StringBuilder sb = new StringBuilder();
        for (Segment s : segs) {
            String t = s.text == null ? "" : s.text.trim();
            if (t.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(t);
        }
        r.text = sb.toString().trim();
        r.srt = SrtBuilder.fromSegments(segs);
        return r;
    }

    /** Text only, for live chunking. */
    public static String transcribeText(Context c, float[] pcm, String lang) throws IOException {
        long h = ctx(c, null);
        String raw = nativeTranscribe(h, pcm, lang == null ? "auto" : lang, threads());
        StringBuilder sb = new StringBuilder();
        for (Segment s : parse(raw)) {
            String t = s.text == null ? "" : s.text.trim();
            if (t.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(t);
        }
        return sb.toString().trim();
    }

    private static List<Segment> parse(String raw) {
        List<Segment> list = new ArrayList<>();
        if (raw == null) return list;
        for (String line : raw.split("\n")) {
            if (line.isEmpty()) continue;
            int a = line.indexOf('|');
            if (a < 0) continue;
            int b = line.indexOf('|', a + 1);
            if (b < 0) continue;
            try {
                double t0 = Long.parseLong(line.substring(0, a)) * 0.01; // centiseconds -> s
                double t1 = Long.parseLong(line.substring(a + 1, b)) * 0.01;
                Segment s = new Segment();
                s.start = t0; s.end = t1; s.text = line.substring(b + 1);
                list.add(s);
            } catch (Exception ignored) {}
        }
        return list;
    }

    // ---------- file transcription ----------

    public static Result transcribeFile(Context c, Uri uri) throws Exception {
        ctx(c, null);
        float[] pcm = decodeTo16kMono(c, uri);
        if (pcm.length < SR / 2) throw new IOException("Audio too short");
        return transcribe(c, pcm, Prefs.lang(c));
    }

    /** Decodes any audio/video file to 16 kHz mono float PCM. */
    private static float[] decodeTo16kMono(Context ctx, Uri uri) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(ctx, uri, null);
        int track = -1; MediaFormat fmt = null;
        for (int i = 0; i < ex.getTrackCount(); i++) {
            MediaFormat f = ex.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) { track = i; fmt = f; break; }
        }
        if (track < 0) { ex.release(); throw new IOException("No audio track found"); }
        ex.selectTrack(track);

        MediaCodec codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
        codec.configure(fmt, null, null, 0);
        codec.start();

        FloatBuf out = new FloatBuf(1 << 20);
        Resampler rs = new Resampler();
        int inRate = SR, channels = 1, pcmEnc = 2;
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
                        inRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                        channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (of.containsKey(MediaFormat.KEY_PCM_ENCODING))
                        pcmEnc = of.getInteger(MediaFormat.KEY_PCM_ENCODING);
                    rs.setRates(inRate, SR);
                } else if (outIdx >= 0) {
                    if (info.size > 0) {
                        ByteBuffer ob = codec.getOutputBuffer(outIdx);
                        ob.position(info.offset);
                        ob.limit(info.offset + info.size);
                        byte[] bytes = new byte[info.size];
                        ob.get(bytes);
                        float[] mono = (pcmEnc == 4) ? floatToMono(bytes, channels) : bytesToMono(bytes, channels);
                        rs.process(mono, out);
                    }
                    codec.releaseOutputBuffer(outIdx, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                }
            }
        } finally {
            try { codec.stop(); } catch (Exception ignored) {}
            codec.release();
            ex.release();
        }
        return out.toArray();
    }

    private static float[] bytesToMono(byte[] b, int ch) {
        if (ch < 1) ch = 1;
        int frames = (b.length / 2) / ch;
        float[] out = new float[frames];
        for (int i = 0; i < frames; i++) {
            float sum = 0;
            for (int c = 0; c < ch; c++) {
                int idx = (i * ch + c) * 2;
                int lo = b[idx] & 0xFF;
                int hi = b[idx + 1];
                sum += (short) ((hi << 8) | lo);
            }
            out[i] = (sum / ch) / 32768f;
        }
        return out;
    }

    private static float[] floatToMono(byte[] b, int ch) {
        if (ch < 1) ch = 1;
        int frames = (b.length / 4) / ch;
        float[] out = new float[frames];
        ByteBuffer bb = ByteBuffer.wrap(b);
        for (int i = 0; i < frames; i++) {
            float sum = 0;
            for (int c = 0; c < ch; c++) sum += bb.getFloat((i * ch + c) * 4);
            float v = sum / ch;
            if (v > 1f) v = 1f;
            if (v < -1f) v = -1f;
            out[i] = v;
        }
        return out;
    }

    /** Streaming linear resampler (carries state across buffers). */
    private static final class Resampler {
        private double ratio = 1.0;
        private double pos = 0;
        private float[] tail = new float[0];

        void setRates(int inRate, int outRate) {
            ratio = (inRate <= 0) ? 1.0 : (double) inRate / (double) outRate;
        }

        void process(float[] x, FloatBuf out) {
            int m = tail.length + x.length;
            float[] c = new float[m];
            System.arraycopy(tail, 0, c, 0, tail.length);
            System.arraycopy(x, 0, c, tail.length, x.length);
            int i = 0;
            while (pos + 1 < m) {
                int i0 = (int) pos;
                float f = (float) (pos - i0);
                out.add(c[i0] * (1 - f) + c[i0 + 1] * f);
                pos += ratio;
                i++;
                if (i > 100000000) break;
            }
            int consumed = (int) pos;
            if (consumed > 0) {
                tail = new float[m - consumed];
                System.arraycopy(c, consumed, tail, 0, tail.length);
                pos -= consumed;
            } else {
                tail = c;
            }
        }
    }

    private static final class FloatBuf {
        float[] a; int n;
        FloatBuf(int cap) { a = new float[cap]; n = 0; }
        void add(float v) {
            if (n == a.length) {
                float[] b = new float[a.length * 2];
                System.arraycopy(a, 0, b, 0, n);
                a = b;
            }
            a[n++] = v;
        }
        float[] toArray() {
            float[] r = new float[n];
            System.arraycopy(a, 0, r, 0, n);
            return r;
        }
    }

    private static native long nativeInit(String modelPath);
    private static native String nativeTranscribe(long handle, float[] pcm, String lang, int threads);
}

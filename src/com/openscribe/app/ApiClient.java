package com.openscribe.app;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class ApiClient {

    public static class Result {
        public String text;
        public String srt;
    }

    private ApiClient() {}

    public static Result transcribe(Context ctx, Uri uri, String name, String mime) throws Exception {
        String base = Prefs.base(ctx);
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String url = base + "/audio/transcriptions";

        String boundary = "----openscribe" + System.currentTimeMillis();
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(20000);
        c.setReadTimeout(600000);
        c.setRequestProperty("Authorization", "Bearer " + Prefs.key(ctx));
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(c.getOutputStream()));
        field(out, boundary, "model", Prefs.model(ctx));
        field(out, boundary, "response_format", "verbose_json");
        field(out, boundary, "timestamp_granularities[]", "segment");

        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + safe(name) + "\"\r\n");
        out.writeBytes("Content-Type: " + (mime == null || mime.isEmpty() ? "application/octet-stream" : mime) + "\r\n\r\n");
        InputStream in = ctx.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("Cannot read the selected file");
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        out.writeBytes("\r\n--" + boundary + "--\r\n");
        out.flush();
        out.close();

        int code = c.getResponseCode();
        InputStream rs = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
        String body = read(rs);
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code + " " + trim(body));

        JSONObject o = new JSONObject(body);
        Result r = new Result();
        r.text = o.optString("text", "");
        JSONArray segs = o.optJSONArray("segments");
        r.srt = (segs != null) ? SrtBuilder.fromSegments(segs) : SrtBuilder.fromText(r.text);
        if (r.srt.isEmpty()) r.srt = SrtBuilder.fromText(r.text);
        return r;
    }

    private static void field(DataOutputStream out, String boundary, String k, String v) throws IOException {
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + k + "\"\r\n\r\n");
        out.writeBytes(v + "\r\n");
    }

    private static String safe(String s) {
        if (s == null || s.trim().isEmpty()) return "audio";
        return s.replace("\"", "").replace("\n", "").replace("\r", "");
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return new String(bo.toByteArray(), "UTF-8");
    }

    private static String trim(String s) {
        if (s == null) return "";
        s = s.trim();
        return s.length() > 300 ? s.substring(0, 300) : s;
    }
}

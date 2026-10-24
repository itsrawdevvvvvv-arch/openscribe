package com.openscribe.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public final class SrtBuilder {
    private SrtBuilder() {}

    public static String fromSegments(JSONArray segs) {
        if (segs == null) return "";
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (int k = 0; k < segs.length(); k++) {
            JSONObject s = segs.optJSONObject(k);
            if (s == null) continue;
            double st = s.optDouble("start", 0);
            double en = s.optDouble("end", 0);
            String t = s.optString("text", "").trim();
            if (t.isEmpty()) continue;
            sb.append(i++).append('\n')
                    .append(fmt(st)).append(" --> ").append(fmt(en)).append('\n')
                    .append(t).append("\n\n");
        }
        return sb.toString().trim();
    }

    public static String fromText(String text) {
        if (text == null) text = "";
        String[] parts = text.trim().split("(?<=[.!?])\\s+");
        StringBuilder sb = new StringBuilder();
        int i = 1;
        double t = 0;
        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            int words = p.split("\\s+").length;
            double dur = Math.max(1.2, words / 2.6);
            sb.append(i++).append('\n')
                    .append(fmt(t)).append(" --> ").append(fmt(t + dur)).append('\n')
                    .append(p).append("\n\n");
            t += dur;
        }
        return sb.toString().trim();
    }

    private static String fmt(double sec) {
        long ms = Math.round(sec * 1000.0);
        long h = ms / 3600000; ms %= 3600000;
        long m = ms / 60000; ms %= 60000;
        long s = ms / 1000; ms %= 1000;
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, ms);
    }
}

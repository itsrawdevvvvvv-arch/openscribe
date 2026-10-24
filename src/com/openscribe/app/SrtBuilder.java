package com.openscribe.app;

import java.util.List;
import java.util.Locale;

public final class SrtBuilder {
    private SrtBuilder() {}

    /** Builds SRT cues straight from Whisper's timestamped segments. */
    public static String fromSegments(List<WhisperEngine.Segment> segs) {
        if (segs == null || segs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        for (WhisperEngine.Segment s : segs) {
            String t = s.text == null ? "" : s.text.trim();
            if (t.isEmpty()) continue;
            sb.append(idx++).append('\n')
                    .append(fmt(s.start)).append(" --> ").append(fmt(s.end)).append('\n')
                    .append(t).append("\n\n");
        }
        return sb.toString().trim();
    }

    /** Fallback when there are no segment timings: approximate cues from sentences. */
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

    static String fmt(double sec) {
        long ms = Math.round(sec * 1000.0);
        long h = ms / 3600000; ms %= 3600000;
        long m = ms / 60000; ms %= 60000;
        long s = ms / 1000; ms %= 1000;
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, ms);
    }
}

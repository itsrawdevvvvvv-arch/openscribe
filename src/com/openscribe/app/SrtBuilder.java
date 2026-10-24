package com.openscribe.app;

import java.util.List;

public final class SrtBuilder {
    private SrtBuilder() {}

    /** Builds SRT cues from recognised words with real timestamps. */
    public static String fromWords(List<VoskEngine.Word> words) {
        if (words == null || words.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        int i = 0;
        while (i < words.size()) {
            double start = words.get(i).s;
            double end = words.get(i).e;
            StringBuilder cue = new StringBuilder(words.get(i).w);
            int j = i + 1;
            while (j < words.size()) {
                double gap = words.get(j).s - end;
                if (gap > 1.0 || (words.get(j).e - start) > 7.0 || (j - i) >= 14) break;
                cue.append(' ').append(words.get(j).w);
                end = words.get(j).e;
                j++;
            }
            sb.append(idx++).append('\n')
                    .append(VoskEngine.fmtTime(start)).append(" --> ").append(VoskEngine.fmtTime(end)).append('\n')
                    .append(cue).append("\n\n");
            i = j;
        }
        return sb.toString().trim();
    }

    /** Fallback when no word timings are available: approximate cues from sentences. */
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
                    .append(VoskEngine.fmtTime(t)).append(" --> ").append(VoskEngine.fmtTime(t + dur)).append('\n')
                    .append(p).append("\n\n");
            t += dur;
        }
        return sb.toString().trim();
    }
}

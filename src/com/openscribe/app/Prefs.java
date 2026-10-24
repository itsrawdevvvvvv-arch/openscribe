package com.openscribe.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String F = "openscribe";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(F, Context.MODE_PRIVATE);
    }

    public static String base(Context c) { return sp(c).getString("base", "https://api.openai.com/v1"); }
    public static String key(Context c) { return sp(c).getString("key", ""); }
    public static String model(Context c) { return sp(c).getString("model", "whisper-1"); }
    public static String lang(Context c) { return sp(c).getString("lang", "en-IN"); }
    public static boolean offline(Context c) { return sp(c).getBoolean("offline", true); }

    public static void save(Context c, String base, String key, String model, String lang, boolean offline) {
        sp(c).edit()
                .putString("base", base.trim())
                .putString("key", key.trim())
                .putString("model", model.trim())
                .putString("lang", lang.trim())
                .putBoolean("offline", offline)
                .apply();
    }
}

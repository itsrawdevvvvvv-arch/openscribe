package com.openscribe.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Tiny local preference store — only the spoken language. No accounts, no keys. */
public final class Prefs {
    private static final String F = "openscribe";
    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(F, Context.MODE_PRIVATE);
    }

    /** "hi" (Hindi), "en" (English) or "auto". */
    public static String lang(Context c) {
        return sp(c).getString("lang", "hi");
    }

    public static void setLang(Context c, String lang) {
        sp(c).edit().putString("lang", lang).apply();
    }
}

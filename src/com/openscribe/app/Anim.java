package com.openscribe.app;

import android.view.View;
import android.view.animation.DecelerateInterpolator;

public final class Anim {
    private Anim() {}

    public static void riseIn(final View v, long delay) {
        if (v == null) return;
        v.setAlpha(0f);
        v.setTranslationY(dp(v, 18));
        v.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(460)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    public static void press(final View v) {
        if (v == null) return;
        v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90)
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                    }
                }).start();
    }

    public static float dp(View v, float x) {
        return x * v.getResources().getDisplayMetrics().density;
    }
}

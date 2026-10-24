package com.openscribe.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class PulseRingView extends View {

    private boolean active = false;
    private long t0 = 0;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (active) invalidate();
            postDelayed(this, 40);
        }
    };

    public PulseRingView(Context c) { super(c); init(); }
    public PulseRingView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        p.setStyle(Paint.Style.STROKE);
        p.setColor(0xFF1B1B1B);
        p.setStrokeWidth(3f);
        setWillNotDraw(false);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        postDelayed(ticker, 40);
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(ticker);
    }

    public void setActive(boolean a) {
        active = a;
        t0 = System.currentTimeMillis();
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        if (!active) return;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float maxR = Math.min(cx, cy) - 4f;
        if (maxR <= 0) return;
        long el = System.currentTimeMillis() - t0;
        for (int k = 0; k < 3; k++) {
            float prog = ((el / 1500f) + k / 3f) % 1f;
            float r = maxR * (0.34f + 0.66f * prog);
            int alpha = (int) (170 * (1f - prog));
            p.setAlpha(Math.max(0, alpha));
            c.drawCircle(cx, cy, r, p);
        }
    }
}

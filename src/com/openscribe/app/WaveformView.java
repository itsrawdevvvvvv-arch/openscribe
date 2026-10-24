package com.openscribe.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class WaveformView extends View {

    private float[] vals;
    private float level = 0f;
    private boolean active = false;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            update();
            invalidate();
            postDelayed(this, 55);
        }
    };

    public WaveformView(Context c) { super(c); init(); }
    public WaveformView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        vals = new float[42];
        for (int i = 0; i < vals.length; i++) vals[i] = 0.08f;
        p.setColor(0xFF1B1B1B);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStyle(Paint.Style.STROKE);
        setWillNotDraw(false);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        postDelayed(ticker, 55);
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(ticker);
    }

    public void setLevel(float v) { level = v; }
    public void setActive(boolean a) { active = a; }

    private void update() {
        long now = System.currentTimeMillis();
        for (int i = 0; i < vals.length; i++) {
            float target;
            if (active) {
                float wave = 0.45f + 0.55f * Math.abs((float) Math.sin(i * 0.7 + now / 140.0));
                target = level * wave;
            } else {
                target = 0.06f + 0.05f * (float) (0.5 + 0.5 * Math.sin(i * 0.5 + now / 700.0));
            }
            if (target < 0.03f) target = 0.03f;
            if (target > 1f) target = 1f;
            vals[i] += (target - vals[i]) * 0.35f;
        }
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth() - getPaddingLeft() - getPaddingRight();
        int h = getHeight() - getPaddingTop() - getPaddingBottom();
        if (w <= 0 || h <= 0) return;
        float stroke = Math.max(3f, w / (float) vals.length * 0.34f);
        p.setStrokeWidth(stroke);
        float gap = (w - stroke) / (vals.length - 1);
        float cy = getPaddingTop() + h / 2f;
        float maxH = h * 0.5f;
        for (int i = 0; i < vals.length; i++) {
            float x = getPaddingLeft() + i * gap + stroke / 2f;
            float hh = Math.max(stroke, vals[i] * maxH * 2f);
            c.drawLine(x, cy - hh / 2f, x, cy + hh / 2f, p);
        }
    }
}

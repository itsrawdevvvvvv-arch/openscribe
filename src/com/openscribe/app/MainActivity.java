package com.openscribe.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView chipAuto, chipEn, chipHi;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        View live = findViewById(R.id.card_live);
        View file = findViewById(R.id.card_file);
        live.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Anim.press(v); open(LiveActivity.class); }
        });
        file.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Anim.press(v); open(FileActivity.class); }
        });

        chipAuto = findViewById(R.id.chip_auto);
        chipEn = findViewById(R.id.chip_en);
        chipHi = findViewById(R.id.chip_hi);
        chipAuto.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { select("auto"); }
        });
        chipEn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { select("en"); }
        });
        chipHi.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { select("hi"); }
        });
        updateChips();

        View[] items = {
                findViewById(R.id.header),
                findViewById(R.id.lang_card),
                findViewById(R.id.card_live),
                findViewById(R.id.card_file),
                findViewById(R.id.footer)
        };
        for (int i = 0; i < items.length; i++) Anim.riseIn(items[i], 60L * i);
    }

    private void select(String lang) {
        Prefs.setLang(this, lang);
        updateChips();
    }

    private void updateChips() {
        String l = Prefs.lang(this);
        style(chipAuto, "auto".equals(l));
        style(chipEn, "en".equals(l));
        style(chipHi, "hi".equals(l));
    }

    private void style(TextView v, boolean on) {
        v.setBackgroundResource(on ? R.drawable.pill : R.drawable.pill_ghost);
        v.setTextColor(getColor(on ? R.color.card : R.color.ink));
    }

    private void open(Class<?> cls) {
        startActivity(new Intent(this, cls));
        overridePendingTransition(R.anim.slide_in, R.anim.slide_out);
    }
}

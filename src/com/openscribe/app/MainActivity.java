package com.openscribe.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends Activity {

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        View live = findViewById(R.id.card_live);
        View file = findViewById(R.id.card_file);
        View set = findViewById(R.id.card_settings);

        live.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Anim.press(v); open(LiveActivity.class); }
        });
        file.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Anim.press(v); open(FileActivity.class); }
        });
        set.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Anim.press(v); open(SettingsActivity.class); }
        });

        View[] items = {
                findViewById(R.id.header),
                findViewById(R.id.card_live),
                findViewById(R.id.card_file),
                findViewById(R.id.card_settings),
                findViewById(R.id.footer)
        };
        for (int i = 0; i < items.length; i++) Anim.riseIn(items[i], 60L * i);
    }

    private void open(Class<?> cls) {
        startActivity(new Intent(this, cls));
        overridePendingTransition(R.anim.slide_in, R.anim.slide_out);
    }
}

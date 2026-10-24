package com.openscribe.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);

        final EditText base = findViewById(R.id.base);
        final EditText key = findViewById(R.id.key);
        final EditText model = findViewById(R.id.model);
        final EditText lang = findViewById(R.id.lang);
        final Switch offline = findViewById(R.id.offline);

        base.setText(Prefs.base(this));
        key.setText(Prefs.key(this));
        model.setText(Prefs.model(this));
        lang.setText(Prefs.lang(this));
        offline.setChecked(Prefs.offline(this));

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        findViewById(R.id.btn_save).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Anim.press(v);
                Prefs.save(SettingsActivity.this,
                        base.getText().toString(),
                        key.getText().toString(),
                        model.getText().toString(),
                        lang.getText().toString(),
                        offline.isChecked());
                Toast.makeText(SettingsActivity.this, "Saved", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }
}

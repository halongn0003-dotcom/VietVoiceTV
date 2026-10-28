package vn.custom.vietvoicetv;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    public static final String PREFS_NAME = "VietVoicePrefs";
    public static final String KEY_API = "api_key";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        EditText etApiKey = findViewById(R.id.etApiKey);
        Button btnSave = findViewById(R.id.btnSaveKey);
        Button btnTest = findViewById(R.id.btnTestVoice);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        etApiKey.setText(prefs.getString(KEY_API, ""));

        btnSave.setOnClickListener(v -> {
            String key = etApiKey.getText().toString().trim();
            prefs.edit().putString(KEY_API, key).apply();
            Toast.makeText(this, "Đã lưu API Key thành công!", Toast.LENGTH_SHORT).show();
        });

        btnTest.setOnClickListener(v -> {
            Intent intent = new Intent(this, VoiceOverlayActivity.class);
            startActivity(intent);
            finish();
        });
    }
}

package vn.custom.vietvoicetv;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class VoiceOverlayActivity extends AppCompatActivity {

    private TextView tvStatusText;
    private LinearLayout infoCardContainer;
    private TextView tvInfoContent;
    private WavRecorder wavRecorder;
    private SpeechRecognizerClient sttClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_overlay);

        tvStatusText = findViewById(R.id.tvStatusText);
        infoCardContainer = findViewById(R.id.infoCardContainer);
        tvInfoContent = findViewById(R.id.tvInfoContent);

        findViewById(R.id.rootLayout).setOnClickListener(v -> finish());

        SharedPreferences prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE);
        String apiKey = prefs.getString(SettingsActivity.KEY_API, "");

        if (getIntent() != null && getIntent().hasExtra("api_key")) {
            apiKey = getIntent().getStringExtra("api_key");
            if (apiKey != null && !apiKey.isEmpty()) {
                prefs.edit().putString(SettingsActivity.KEY_API, apiKey).apply();
            }
        }

        if (apiKey == null || apiKey.isEmpty()) {
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            startActivity(settingsIntent);
            finish();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 101);
            return;
        }

        startVoiceSession(apiKey);
    }

    private void startVoiceSession(String apiKey) {
        tvStatusText.setText("Đang nghe...");
        infoCardContainer.setVisibility(View.GONE);

        wavRecorder = new WavRecorder();
        sttClient = new SpeechRecognizerClient();
        File wavFile = new File(getCacheDir(), "cmd.wav");

        wavRecorder.startRecordingWithVAD(wavFile, new WavRecorder.RecordingCallback() {
            @Override
            public void onSpeechDetected() {
                mainHandler.post(() -> tvStatusText.setText("Đang nghe..."));
            }

            @Override
            public void onRecordingFinished(File file) {
                mainHandler.post(() -> tvStatusText.setText("Đang xử lý..."));
                new Thread(() -> {
                    try {
                        String transcript = sttClient.transcribeAudio(file, apiKey);
                        String cleanText = cleanPunctuation(transcript);
                        mainHandler.post(() -> handleRecognizedCommand(cleanText));
                    } catch (Exception e) {
                        mainHandler.post(() -> {
                            tvStatusText.setText("Không nghe rõ");
                            mainHandler.postDelayed(() -> finish(), 1800);
                        });
                    }
                }).start();
            }

            @Override
            public void onError(String message) {
                mainHandler.post(() -> {
                    tvStatusText.setText(message);
                    mainHandler.postDelayed(() -> finish(), 2000);
                });
            }
        });
    }

    private void handleRecognizedCommand(String rawCommand) {
        if (rawCommand.isEmpty()) {
            tvStatusText.setText("Không nghe thấy lệnh");
            mainHandler.postDelayed(this::finish, 1500);
            return;
        }

        tvStatusText.setText(rawCommand);
        String lower = rawCommand.toLowerCase(Locale.ROOT);
        String unaccented = removeAccents(lower);

        if (unaccented.contains("gia vang")) {
            infoCardContainer.setVisibility(View.VISIBLE);
            tvInfoContent.setText("Đang lấy thông tin...");
            new Thread(() -> {
                String goldInfo = InfoScraper.fetchGoldPrice();
                mainHandler.post(() -> {
                    tvInfoContent.setText(goldInfo);
                    mainHandler.postDelayed(this::finish, 10000);
                });
            }).start();
            return;
        }

        if (unaccented.contains("gia dau") || unaccented.contains("gia xang")) {
            infoCardContainer.setVisibility(View.VISIBLE);
            tvInfoContent.setText("Đang lấy thông tin...");
            boolean onlyOil = unaccented.contains("gia dau");
            new Thread(() -> {
                String oilInfo = InfoScraper.fetchPetrolPrice(onlyOil);
                mainHandler.post(() -> {
                    tvInfoContent.setText(oilInfo);
                    mainHandler.postDelayed(this::finish, 10000);
                });
            }).start();
            return;
        }

        if (unaccented.contains("cai dat voice") || unaccented.contains("cau hinh voice")) {
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
            return;
        }

        if (unaccented.contains("vtv") || unaccented.startsWith("kenh ")) {
            mainHandler.postDelayed(() -> {
                openTvChannel(unaccented, rawCommand);
                finish();
            }, 600);
            return;
        }

        if (unaccented.startsWith("mo ") && !unaccented.startsWith("mo nhac") && !unaccented.startsWith("mo bai") && !unaccented.startsWith("mo phim") && !unaccented.startsWith("mo video") && !unaccented.startsWith("mo karaoke")) {
            String targetApp = unaccented.substring(3).trim();
            mainHandler.postDelayed(() -> {
                if (!launchAppByKeyword(targetApp)) {
                    searchOnYouTube(rawCommand);
                }
                finish();
            }, 500);
            return;
        }

        String searchQuery = rawCommand.replaceFirst("(?i)^(mở\\s+|tìm\\s+|phát\\s+)", "").trim();
        mainHandler.postDelayed(() -> {
            searchOnYouTube(searchQuery);
            finish();
        }, 600);
    }

    private boolean launchAppByKeyword(String keyword) {
        PackageManager pm = getPackageManager();
        String normalizedKey = keyword.replaceAll("\\s+", "");

        Map<String, String[]> knownApps = new HashMap<>();
        knownApps.put("tv360", new String[]{"com.viettel.tv360", "vn.viettel.tv360"});
        knownApps.put("tivi360", new String[]{"com.viettel.tv360", "vn.viettel.tv360"});
        knownApps.put("basau", new String[]{"com.viettel.tv360"});
        knownApps.put("fpt", new String[]{"com.fptplay.activity", "net.fptplay.ottbox"});
        knownApps.put("coccoc", new String[]{"com.coccoc.trinhduyet", "com.coccoc.trinhduyet_tv"});
        knownApps.put("tiktok", new String[]{"com.zhiliaoapp.musically.go", "com.ss.android.ugc.trill", "com.tiktok.tv"});
        knownApps.put("youtube", new String[]{"com.liskovsoft.smarttubetv.beta", "com.liskovsoft.smarttubetv", "org.smarttube.stable", "com.google.android.youtube.tv"});
        knownApps.put("vtvgo", new String[]{"vn.vtv.vtvgo", "vn.vtv.vtvgotv"});
        knownApps.put("mytv", new String[]{"vn.vnpt.media.mytv", "com.vnpt.mytv"});
        knownApps.put("vieon", new String[]{"vn.aha.vieon", "com.vieon.tv"});

        for (Map.Entry<String, String[]> entry : knownApps.entrySet()) {
            if (normalizedKey.contains(entry.getKey())) {
                for (String pkg : entry.getValue()) {
                    Intent launchIntent = pm.getLaunchIntentForPackage(pkg);
                    if (launchIntent == null) {
                        launchIntent = pm.getLeanbackLaunchIntentForPackage(pkg);
                    }
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(launchIntent);
                        return true;
                    }
                }
            }
        }

        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo app : apps) {
            String label = removeAccents(pm.getApplicationLabel(app).toString().toLowerCase(Locale.ROOT)).replaceAll("\\s+", "");
            if (label.contains(normalizedKey) || normalizedKey.contains(label)) {
                Intent launchIntent = pm.getLaunchIntentForPackage(app.packageName);
                if (launchIntent == null) {
                    launchIntent = pm.getLeanbackLaunchIntentForPackage(app.packageName);
                }
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(launchIntent);
                    return true;
                }
            }
        }
        return false;
    }

    private void openTvChannel(String unaccentedCmd, String rawCmd) {
        if (!launchAppByKeyword("vtvgo") && !launchAppByKeyword("tv360")) {
            searchOnYouTube(rawCmd + " trực tiếp");
        }
    }

    private void searchOnYouTube(String query) {
        PackageManager pm = getPackageManager();
        String[] ytPackages = {
                "com.liskovsoft.smarttubetv.beta",
                "com.liskovsoft.smarttubetv",
                "org.smarttube.stable",
                "com.google.android.youtube.tv",
                "com.coccoc.trinhduyet"
        };

        for (String pkg : ytPackages) {
            Intent searchIntent = new Intent(Intent.ACTION_SEARCH);
            searchIntent.setPackage(pkg);
            searchIntent.putExtra("query", query);
            searchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (searchIntent.resolveActivity(pm) != null) {
                startActivity(searchIntent);
                return;
            }
        }

        Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query)));
        webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(webIntent);
        } catch (Exception ignored) {}
    }

    private String cleanPunctuation(String str) {
        if (str == null) return "";
        return str.replaceAll("[.!?]+$", "").trim();
    }

    private String removeAccents(String s) {
        String temp = Normalizer.normalize(s, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(temp).replaceAll("").replace('đ', 'd').replace('Đ', 'D');
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (wavRecorder != null) {
            wavRecorder.stopRecorder();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}

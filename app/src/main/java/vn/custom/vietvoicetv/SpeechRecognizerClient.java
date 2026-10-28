package vn.custom.vietvoicetv;

import android.util.Base64;
import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

public class SpeechRecognizerClient {
    private final OkHttpClient client;

    public SpeechRecognizerClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build();
    }

    public String transcribeAudio(File wavFile, String apiKey) throws Exception {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new Exception("MISSING_KEY");
        }
        if (apiKey.startsWith("AIza")) {
            return transcribeWithGemini(wavFile, apiKey);
        } else {
            return transcribeWithGroqWhisper(wavFile, apiKey);
        }
    }

    private String transcribeWithGroqWhisper(File wavFile, String groqKey) throws Exception {
        RequestBody fileBody = RequestBody.create(wavFile, MediaType.parse("audio/wav"));
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "voice.wav", fileBody)
                .addFormDataPart("model", "whisper-large-v3-turbo")
                .addFormDataPart("language", "vi")
                .addFormDataPart("prompt", "Mở TV360, Mở FPT Play, Mở Cốc Cốc, Mở TikTok, Mở nhạc Remix, Giá vàng hôm nay, Giá dầu hôm nay vùng 2, Kênh VTV1, Kênh VTV3, Mở YouTube")
                .build();

        Request request = new Request.Builder()
                .url("https://api.groq.com/openai/v1/audio/transcriptions")
                .addHeader("Authorization", "Bearer " + groqKey)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String resStr = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new Exception("API Error " + response.code());
            }
            JSONObject json = new JSONObject(resStr);
            return json.optString("text", "").trim();
        }
    }

    private String transcribeWithGemini(File wavFile, String geminiKey) throws Exception {
        byte[] bytes = new byte[(int) wavFile.length()];
        try (FileInputStream fis = new FileInputStream(wavFile)) {
            fis.read(bytes);
        }
        String base64Audio = Base64.encodeToString(bytes, Base64.NO_WRAP);

        JSONObject inlineData = new JSONObject()
                .put("mime_type", "audio/wav")
                .put("data", base64Audio);

        JSONArray parts = new JSONArray()
                .put(new JSONObject().put("inline_data", inlineData))
                .put(new JSONObject().put("text", "Chuyển giọng nói tiếng Việt này thành văn bản chính xác, chỉ trả về câu lệnh, không giải thích."));

        JSONObject payload = new JSONObject()
                .put("contents", new JSONArray().put(new JSONObject().put("parts", parts)));

        RequestBody body = RequestBody.create(payload.toString(), MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + geminiKey)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String resStr = response.body() != null ? response.body().string() : "";
            JSONObject json = new JSONObject(resStr);
            return json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text").trim();
        }
    }
}

package vn.custom.vietvoicetv;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class WavRecorder {
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private boolean isRecording = false;
    private AudioRecord audioRecord;

    public interface RecordingCallback {
        void onSpeechDetected();
        void onRecordingFinished(File wavFile);
        void onError(String message);
    }

    @SuppressLint("MissingPermission")
    public void startRecordingWithVAD(File outputFile, RecordingCallback callback) {
        new Thread(() -> {
            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                bufferSize = SAMPLE_RATE * 2;
            }

            try {
                audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        bufferSize
                );

                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    callback.onError("Không truy cập được Micro điều khiển");
                    return;
                }

                audioRecord.startRecording();
                isRecording = true;

                ByteArrayOutputStream pcmOut = new ByteArrayOutputStream();
                byte[] buffer = new byte[bufferSize];

                long startTime = System.currentTimeMillis();
                long lastVoiceTime = System.currentTimeMillis();
                boolean hasSpoken = false;

                while (isRecording) {
                    int read = audioRecord.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        pcmOut.write(buffer, 0, read);
                        double rms = calculateRMS(buffer, read);

                        if (rms > 350) {
                            if (!hasSpoken) {
                                hasSpoken = true;
                                callback.onSpeechDetected();
                            }
                            lastVoiceTime = System.currentTimeMillis();
                        }
                    }

                    long now = System.currentTimeMillis();
                    if (hasSpoken && (now - lastVoiceTime > 1100)) {
                        break;
                    }
                    if (!hasSpoken && (now - startTime > 4500)) {
                        break;
                    }
                    if (now - startTime > 7000) {
                        break;
                    }
                }

                stopRecorder();
                byte[] pcmData = pcmOut.toByteArray();
                writeWavFile(pcmData, outputFile);
                callback.onRecordingFinished(outputFile);

            } catch (Exception e) {
                stopRecorder();
                callback.onError("Lỗi thu âm: " + e.getMessage());
            }
        }).start();
    }

    public void stopRecorder() {
        isRecording = false;
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception ignored) {}
            audioRecord = null;
        }
    }

    private double calculateRMS(byte[] buffer, int readBytes) {
        long sum = 0;
        int samples = readBytes / 2;
        for (int i = 0; i < readBytes - 1; i += 2) {
            short sample = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
            sum += (long) sample * sample;
        }
        return samples > 0 ? Math.sqrt((double) sum / samples) : 0;
    }

    private void writeWavFile(byte[] pcmData, File outFile) throws IOException {
        long totalAudioLen = pcmData.length;
        long totalDataLen = totalAudioLen + 36;
        long byteRate = SAMPLE_RATE * 2;

        byte[] header = new byte[44];
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0;
        header[20] = 1; header[21] = 0;
        header[22] = 1; header[23] = 0;
        header[24] = (byte) (SAMPLE_RATE & 0xff);
        header[25] = (byte) ((SAMPLE_RATE >> 8) & 0xff);
        header[26] = (byte) ((SAMPLE_RATE >> 16) & 0xff);
        header[27] = (byte) ((SAMPLE_RATE >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = 2; header[33] = 0;
        header[34] = 16; header[35] = 0;
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            fos.write(header);
            fos.write(pcmData);
        }
    }
}

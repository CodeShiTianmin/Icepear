package com.icepear.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;

/**
 * 消息提示音。内置音效用正弦波合成（对应旧版 WebAudio 版本的
 * dingdong / bubble / soft / bell），自定义音效播放导入的音频文件。
 */
public final class SoundPlayer {

    private static final int SAMPLE_RATE = 22050;
    private MediaPlayer mediaPlayer;
    private final Context context;
    private final Store store;

    public SoundPlayer(Context context, Store store) {
        this.context = context;
        this.store = store;
    }

    public void play(String type) {
        float volume = (float) Math.max(0, Math.min(1, store.data.optJSONObject("sound") != null
                ? store.data.optJSONObject("sound").optDouble("volume", 50) / 100.0 : 0.5));
        if (type != null && type.startsWith("custom:")) {
            playCustom(type.substring(7), volume);
            return;
        }
        if ("none".equals(type)) return;
        double[][] notes;
        switch (type == null ? "dingdong" : type) {
            case "bubble":
                notes = new double[][]{{523.25, 0.09}, {659.25, 0.12}};
                break;
            case "soft":
                notes = new double[][]{{392.0, 0.18}};
                break;
            case "bell":
                notes = new double[][]{{880.0, 0.1}, {1108.7, 0.16}};
                break;
            default: // dingdong
                notes = new double[][]{{659.25, 0.1}, {523.25, 0.15}};
        }
        playTones(notes, volume);
    }

    private void playTones(double[][] notes, float volume) {
        new Thread(() -> {
            try {
                int total = 0;
                for (double[] note : notes) total += (int) (SAMPLE_RATE * note[1]);
                short[] pcm = new short[total];
                int offset = 0;
                for (double[] note : notes) {
                    int count = (int) (SAMPLE_RATE * note[1]);
                    for (int i = 0; i < count; i++) {
                        double env = 1.0 - (double) i / count;
                        pcm[offset + i] = (short) (Math.sin(2 * Math.PI * note[0] * i / SAMPLE_RATE)
                                * Short.MAX_VALUE * 0.5 * env * volume);
                    }
                    offset += count;
                }
                AudioTrack track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(pcm.length * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();
                track.write(pcm, 0, pcm.length);
                track.play();
                Thread.sleep((long) (pcm.length * 1000L / SAMPLE_RATE) + 150);
                track.release();
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void playCustom(String id, float volume) {
        try {
            JSONArray sounds = store.data.optJSONArray("customSounds");
            JSONObject item = null;
            for (int i = 0; sounds != null && i < sounds.length(); i++) {
                JSONObject candidate = sounds.optJSONObject(i);
                if (candidate != null && id.equals(candidate.optString("id"))) {
                    item = candidate;
                    break;
                }
            }
            if (item == null) return;
            String dataUrl = store.resolveMedia(item.optString("src", ""));
            int comma = dataUrl.indexOf(',');
            if (!dataUrl.startsWith("data:audio") || comma < 0) return;
            byte[] bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT);
            File temp = new File(context.getCacheDir(), "sound-preview");
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(bytes);
            }
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(temp.getAbsolutePath());
            mediaPlayer.setVolume(volume, volume);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception ignored) {
        }
    }

    public void release() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}

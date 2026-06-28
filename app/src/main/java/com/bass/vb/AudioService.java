package com.bass.vb;

import android.app.*;
import android.content.Intent;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.os.Process;

public class AudioService extends Service {
    private static final int SR = 44100;
    private final IBinder binder = new Binder();
    private MediaProjection proj;
    private AudioRecord rec;
    private AudioTrack trk;
    private Thread thr;
    private volatile boolean running = false;
    private volatile float mix = 0.65f, cutoff = 200f, drive = 0.45f, vol = 0.8f;
    private float lp_x1, lp_x2, lp_y1, lp_y2;
    private float hp_x1, hp_x2, hp_y1, hp_y2;
    private float lp_b0, lp_b1, lp_b2, lp_a1, lp_a2;
    private float hp_b0, hp_b1, hp_b2, hp_a1, hp_a2;

    public class Binder extends android.os.Binder {
        AudioService getService() { return AudioService.this; }
    }

    @Override public IBinder onBind(Intent i) { return binder; }
    public boolean isRunning() { return running; }

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("vb", "Virtual Bass",
                NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        updateCoeffs();
    }

    public void start(int resultCode, Intent data) {
        if (running) return;
        Notification.Builder nb = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, "vb")
            : new Notification.Builder(this);
        startForeground(1, nb.setContentTitle("Virtual Bass")
            .setContentText("正在处理系统音频")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true).build());

        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        proj = mpm.getMediaProjection(resultCode, data);
        if (proj == null) { stop(); return; }

        AudioPlaybackCaptureConfiguration cap =
            new AudioPlaybackCaptureConfiguration.Builder(proj)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .build();

        int buf = Math.max(AudioRecord.getMinBufferSize(SR,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), 2048);

        rec = new AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(cap)
            .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
            .setBufferSizeInBytes(buf * 2).build();

        trk = new AudioTrack.Builder()
            .setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(buf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM).build();

        rec.startRecording();
        trk.play();
        running = true;

        thr = new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            short[] b = new short[512];
            while (running) {
                int n = rec.read(b, 0, b.length);
                if (n > 0) { process(b, n); trk.write(b, 0, n); }
            }
        });
        thr.start();
    }

    public void stop() {
        running = false;
        if (thr != null) { try { thr.join(1000); } catch (Exception e) {} thr = null; }
        if (rec != null) { try { rec.stop(); } catch (Exception e) {} rec.release(); rec = null; }
        if (trk != null) { try { trk.stop(); } catch (Exception e) {} trk.release(); trk = null; }
        if (proj != null) { proj.stop(); proj = null; }
        stopForeground(true);
    }

    public void setParam(int i, int v) {
        switch (i) {
            case 0: mix = v / 100f; break;
            case 1: cutoff = v; updateCoeffs(); break;
            case 2: drive = v / 100f; break;
            case 3: vol = v / 100f; break;
        }
    }

    private void updateCoeffs() {
        double w = 2 * Math.PI * cutoff / SR;
        double c = Math.cos(w), s = Math.sin(w);
        double a = s / 1.414;
        double d0 = 1 + a;
        lp_b0 = (float)((1 - c) / 2 / d0);
        lp_b1 = (float)((1 - c) / d0);
        lp_b2 = lp_b0;
        lp_a1 = (float)(-2 * c / d0);
        lp_a2 = (float)((1 - a) / d0);
        d0 = 1 + s / 2;
        hp_b0 = (float)((1 + c) / 2 / d0);
        hp_b1 = (float)(-(1 + c) / d0);
        hp_b2 = hp_b0;
        hp_a1 = (float)(-2 * c / d0);
        hp_a2 = (float)((1 - s / 2) / d0);
    }

    private void process(short[] buf, int len) {
        float m = mix, v = vol, k = 1 + drive * 19;
        for (int i = 0; i < len; i++) {
            float x = buf[i] / 32768f;
            float lp = lp_b0*x + lp_b1*lp_x1 + lp_b2*lp_x2 - lp_a1*lp_y1 - lp_a2*lp_y2;
            lp_x2 = lp_x1; lp_x1 = x; lp_y2 = lp_y1; lp_y1 = lp;
            float sh = (float)(Math.tanh(lp * k) / Math.tanh(k));
            float hp = hp_b0*sh + hp_b1*hp_x1 + hp_b2*hp_x2 - hp_a1*hp_y1 - hp_a2*hp_y2;
            hp_x2 = hp_x1; hp_x1 = sh; hp_y2 = hp_y1; hp_y1 = hp;
            float o = Math.max(-1, Math.min(1, hp * m * v));
            buf[i] = (short)(o * 32767);
        }
    }

    @Override public void onDestroy() { stop(); super.onDestroy(); }
}

package com.bass.vb;

import android.app.Activity;
import android.content.*;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    private static final int REQ = 1001;
    private AudioService svc;
    private boolean bound = false;
    private Button btn;
    private TextView status;
    private static final String[] LB = {"增强量","截止频率","谐波驱动","音量"};
    private static final int[] MX = {100,400,100,100};
    private static final int[] DF = {65,200,45,80};
    private TextView[] vl = new TextView[4];

    private ServiceConnection conn = new ServiceConnection() {
        public void onServiceConnected(ComponentName n, IBinder b) {
            svc = ((AudioService.Binder) b).getService(); bound = true;
        }
        public void onServiceDisconnected(ComponentName n) { bound = false; }
    };

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        ScrollView sv = new ScrollView(this);
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(48,60,48,48);
        r.setBackgroundColor(0xFF08080C);

        r.addView(tv("Virtual Bass", 28, 0xFFE8A43C, Gravity.CENTER, 0, 8));
        r.addView(tv("实时系统音频低音增强", 12, 0xFF6A6660, Gravity.CENTER, 0, 40));

        status = tv("等待开始", 14, 0xFF6A6660, Gravity.CENTER, 0, 24);
        r.addView(status);

        btn = new Button(this);
        btn.setText("▶ 开始捕获");
        btn.setTextSize(16);
        btn.setTextColor(0xFF08080C);
        btn.setBackgroundColor(0xFFE8A43C);
        btn.setLayoutParams(lp(true, 24, 130));
        btn.setOnClickListener(v -> toggle());
        r.addView(btn);

        r.addView(tv("点击后选择「整个屏幕」并勾选「共享音频」\n然后切换到音乐App播放\n建议佩戴有线耳机", 11, 0xFF6A6660, Gravity.CENTER, 0, 40));

        for (int i = 0; i < 4; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = 8;
            row.setLayoutParams(rp);

            TextView lbl = tv(LB[i], 13, 0xFF6A6660, Gravity.START, 0, 0);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lbl.setLayoutParams(llp);
            row.addView(lbl);

            vl[i] = tv(fmt(i, DF[i]), 13, 0xFFE8A43C, Gravity.END, 16, 0);
            row.addView(vl[i]);
            r.addView(row);

            SeekBar sb = new SeekBar(this);
            sb.setMax(MX[i]);
            sb.setProgress(DF[i]);
            final int idx = i;
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar s, int v, boolean u) {
                    vl[idx].setText(fmt(idx, v));
                    if (bound) svc.setParam(idx, v);
                }
                public void onStartTrackingTouch(SeekBar s) {}
                public void onStopTrackingTouch(SeekBar s) {}
            });
            sb.setLayoutParams(lp(true, 24, ViewGroup.LayoutParams.WRAP_CONTENT));
            r.addView(sb);
        }

        sv.addView(r);
        setContentView(sv);
        bindService(new Intent(this, AudioService.class), conn, Context.BIND_AUTO_CREATE);
    }

    private TextView tv(String t, int sz, int col, int grav, int rm, int bm) {
        TextView v = new TextView(this);
        v.setText(t); v.setTextSize(sz); v.setTextColor(col); v.setGravity(grav);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.rightMargin = rm; p.bottomMargin = bm;
        v.setLayoutParams(p);
        return v;
    }

    private LinearLayout.LayoutParams lp(boolean full, int bm, int h) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            full ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT, h);
        p.bottomMargin = bm;
        return p;
    }

    private String fmt(int i, int v) {
        return i == 1 ? v + "Hz" : v + "%";
    }

    private void toggle() {
        if (bound && svc.isRunning()) {
            svc.stop();
            btn.setText("▶ 开始捕获"); btn.setBackgroundColor(0xFFE8A43C);
            status.setText("已停止"); status.setTextColor(0xFF6A6660);
        } else {
            MediaProjectionManager m = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(m.createScreenCaptureIntent(), REQ);
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ && res == RESULT_OK && bound) {
            try {
                svc.start(res, data);
                btn.setText("■ 停止捕获"); btn.setBackgroundColor(0xFFE84040);
                status.setText("● 正在捕获系统音频"); status.setTextColor(0xFF40C878);
            } catch (Exception e) {
                status.setText("启动失败: " + e.getMessage()); status.setTextColor(0xFFE84040);
            }
        }
    }

    @Override protected void onDestroy() {
        if (bound) unbindService(conn);
        super.onDestroy();
    }
                    }

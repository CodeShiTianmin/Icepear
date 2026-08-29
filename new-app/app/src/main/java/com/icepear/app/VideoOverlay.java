package com.icepear.app;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 视频通话浮层：拨打（随机接听/拒接）、他来电、通话计时、最小化悬浮小窗
 * （可拖动、靠边贴边收起）、挂断。全部为原生 View 实现。
 */
public class VideoOverlay {

    private final MainActivity a;
    private final FrameLayout root;
    private FrameLayout fullScreen;
    private LinearLayout miniWindow;
    private TextView timerFull;
    private TextView timerMini;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable tick;
    private int seconds;
    private boolean inCall;

    public VideoOverlay(MainActivity activity) {
        this.a = activity;
        root = new FrameLayout(activity);
        root.setVisibility(View.GONE);
        root.setClickable(false);
    }

    public View rootView() {
        return root;
    }

    public boolean handleBack() {
        if (fullScreen != null && fullScreen.getVisibility() == View.VISIBLE && inCall) {
            minimize();
            return true;
        }
        if (fullScreen != null && fullScreen.getVisibility() == View.VISIBLE) {
            endCall(false);
            return true;
        }
        return false;
    }

    /* ---------- 拨打 ---------- */

    public void startVideo() {
        showCallScreen("正在等待他接听…", true);
        int wait = a.store.rand(2, 6) * 1000;
        handler.postDelayed(() -> {
            if (fullScreen == null) return;
            if (a.store.rand(0, 99) < 78) {
                connect();
            } else {
                setStatus("他现在不方便接听");
                handler.postDelayed(() -> endCall(false), 1600);
            }
        }, wait);
    }

    /* ---------- 他来电 ---------- */

    public void incomingCall() {
        showCallScreen(a.store.displayName() + " 邀请你视频通话", false);
        LinearLayout actions = Ui.row(a);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0, Ui.dp(a, 30), 0, 0);
        TextView decline = circleButton("拒绝", 0xFFE05B4E, () -> endCall(false));
        TextView accept = circleButton("接听", 0xFF3CB371, this::connect);
        actions.addView(decline);
        actions.addView(accept);
        ((LinearLayout) fullScreen.getChildAt(0)).addView(actions);
    }

    private TextView circleButton(String label, int color, Runnable onClick) {
        TextView button = Ui.boldText(a, label, 14, Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setBackground(Ui.rounded(color, Ui.dp(a, 40)));
        int size = Ui.dp(a, 72);
        button.setWidth(size);
        button.setHeight(size);
        LinearLayout.LayoutParams lp = Ui.lp(size, size);
        lp.setMargins(Ui.dp(a, 22), 0, Ui.dp(a, 22), 0);
        button.setLayoutParams(lp);
        button.setOnClickListener(v -> onClick.run());
        return button;
    }

    /* ---------- 通话界面 ---------- */

    private TextView statusView;

    private void showCallScreen(String status, boolean withHangup) {
        removeAll();
        root.setVisibility(View.VISIBLE);
        root.setClickable(true);
        fullScreen = new FrameLayout(a);
        android.graphics.Bitmap bg = Ui.decodeDataUrl(
                a.store.resolveMedia(a.store.data.optString("videoBg", "")));
        if (bg != null) {
            android.graphics.drawable.BitmapDrawable drawable =
                    new android.graphics.drawable.BitmapDrawable(a.getResources(), bg);
            drawable.setGravity(Gravity.FILL);
            fullScreen.setBackground(drawable);
            View scrim = new View(a);
            scrim.setBackgroundColor(0x66000000);
            fullScreen.addView(scrim, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            fullScreen.setBackground(Ui.gradient(0xFF2B2333, 0xFF14101B, 0));
        }
        LinearLayout box = Ui.column(a);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(0, Ui.dp(a, 90), 0, 0);
        box.addView(Ui.avatar(a, a.store, "other", 96));
        TextView name = Ui.boldText(a, a.store.displayName(), 22, Color.WHITE);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, Ui.dp(a, 16), 0, 0);
        box.addView(name);
        statusView = Ui.text(a, status, 13, 0xBBFFFFFF);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, Ui.dp(a, 8), 0, 0);
        box.addView(statusView);
        timerFull = Ui.boldText(a, "", 16, Color.WHITE);
        timerFull.setGravity(Gravity.CENTER);
        timerFull.setPadding(0, Ui.dp(a, 12), 0, 0);
        box.addView(timerFull);
        fullScreen.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (withHangup) addBottomBar(false);
        root.addView(fullScreen, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void addBottomBar(boolean connected) {
        LinearLayout bar = Ui.row(a);
        bar.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        barLp.bottomMargin = Ui.dp(a, 46);
        if (connected) {
            TextView mini = circleButton("缩小", 0xFF6D6A75, this::minimize);
            bar.addView(mini);
        }
        TextView hangup = circleButton("挂断", 0xFFE05B4E, () -> endCall(connected));
        bar.addView(hangup);
        fullScreen.addView(bar, barLp);
    }

    private void setStatus(String status) {
        if (statusView != null) statusView.setText(status);
    }

    private void connect() {
        if (fullScreen == null) return;
        inCall = true;
        seconds = 0;
        showCallScreen("通话中", false);
        addBottomBar(true);
        tick = () -> {
            seconds++;
            String label = Ui.fmtDur(seconds);
            if (timerFull != null) timerFull.setText(label);
            if (timerMini != null) timerMini.setText(label);
            handler.postDelayed(tick, 1000);
        };
        handler.postDelayed(tick, 1000);
    }

    private void endCall(boolean connected) {
        boolean was = inCall;
        int duration = seconds;
        inCall = false;
        if (tick != null) handler.removeCallbacks(tick);
        tick = null;
        removeAll();
        root.setVisibility(View.GONE);
        root.setClickable(false);
        if (was || connected) {
            a.logic.addSys("视频通话 " + Ui.fmtDur(Math.max(1, duration)));
        } else {
            a.logic.addSys("视频通话未接通");
        }
    }

    /* ---------- 最小化悬浮窗 ---------- */

    private void minimize() {
        if (fullScreen != null) fullScreen.setVisibility(View.GONE);
        if (miniWindow != null) {
            miniWindow.setVisibility(View.VISIBLE);
            return;
        }
        miniWindow = Ui.column(a);
        miniWindow.setGravity(Gravity.CENTER);
        miniWindow.setBackground(Ui.rounded(0xEE2B2333, Ui.dp(a, 16)));
        miniWindow.setPadding(Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 10));
        miniWindow.setElevation(Ui.dp(a, 10));
        miniWindow.addView(Ui.avatar(a, a.store, "other", 44));
        timerMini = Ui.boldText(a, Ui.fmtDur(seconds), 11, Color.WHITE);
        timerMini.setGravity(Gravity.CENTER);
        timerMini.setPadding(0, Ui.dp(a, 4), 0, 0);
        miniWindow.addView(timerMini);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                Ui.dp(a, 84), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        lp.topMargin = Ui.dp(a, 120);
        lp.rightMargin = Ui.dp(a, 10);
        root.addView(miniWindow, lp);
        root.setClickable(false);

        miniWindow.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY, startX, startY;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = v.getX();
                        startY = v.getY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) moved = true;
                        v.setX(Math.max(0, Math.min(root.getWidth() - v.getWidth(), startX + dx)));
                        v.setY(Math.max(0, Math.min(root.getHeight() - v.getHeight(), startY + dy)));
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            restore();
                        } else {
                            snapToEdge(v);
                        }
                        return true;
                }
                return false;
            }
        });
    }

    /** 贴边：靠近左右边缘时半隐藏 */
    private void snapToEdge(View v) {
        float centerX = v.getX() + v.getWidth() / 2f;
        boolean left = centerX < root.getWidth() / 2f;
        float target = left ? -v.getWidth() * 0.45f : root.getWidth() - v.getWidth() * 0.55f;
        v.animate().x(target).setDuration(180).start();
    }

    private void restore() {
        if (miniWindow != null) miniWindow.setVisibility(View.GONE);
        if (fullScreen != null) fullScreen.setVisibility(View.VISIBLE);
        root.setClickable(true);
    }

    private void removeAll() {
        root.removeAllViews();
        fullScreen = null;
        miniWindow = null;
        timerFull = null;
        timerMini = null;
    }
}

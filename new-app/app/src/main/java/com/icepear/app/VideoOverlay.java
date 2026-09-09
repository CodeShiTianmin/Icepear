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
 * 视频通话浮层，对应浏览器版 #videoScreen / #videoMini：
 * 背景图（可换）、头像、状态、计时；底部「背景 / 最小化 / 挂断」三键；
 * 最小化悬浮小窗（可拖动、贴边、带挂断）。
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
    private int callSeq;
    private float miniX = -1, miniY = -1;

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
        if (fullScreen != null && fullScreen.getVisibility() == View.VISIBLE) {
            minimize();
            return true;
        }
        return false;
    }

    /* ---------- 拨打 ---------- */

    public void startVideo() {
        showCallScreen("正在等待他接听…", true);
        final int seq = ++callSeq;
        int wait = a.store.rand(2, 6) * 1000;
        handler.postDelayed(() -> {
            if (fullScreen == null || seq != callSeq || inCall) return;
            if (a.store.rand(0, 99) < 78) {
                connect();
            } else {
                setStatus("他现在不方便接听");
                handler.postDelayed(() -> {
                    if (seq == callSeq && fullScreen != null) endCall(false);
                }, 1600);
            }
        }, wait);
    }

    /** 圆形 SVG 按钮 + 下方文字，等价于浏览器 #videoBtns button */
    private LinearLayout circleButton(String label, String svg, int color, Runnable onClick) {
        LinearLayout wrap = Ui.column(a);
        wrap.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout circle = new FrameLayout(a);
        int size = Ui.dp(a, 60);
        circle.setBackground(Ui.rounded(color, size / 2));
        circle.addView(SvgIcon.view(a, svg, Color.WHITE, 26), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        wrap.addView(circle, new LinearLayout.LayoutParams(size, size));
        TextView text = Ui.text(a, label, 12, 0xEEFFFFFF);
        text.setGravity(Gravity.CENTER);
        text.setPadding(0, Ui.dp(a, 6), 0, 0);
        wrap.addView(text);
        LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(a, 18), 0, Ui.dp(a, 18), 0);
        wrap.setLayoutParams(lp);
        wrap.setOnClickListener(v -> onClick.run());
        return wrap;
    }

    /* ---------- 通话界面 ---------- */

    private TextView statusView;

    private void showCallScreen(String status, boolean withHangup) {
        removeAll();
        root.setVisibility(View.VISIBLE);
        root.setClickable(true);
        fullScreen = new FrameLayout(a);
        applyBackground();
        LinearLayout box = Ui.column(a);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(0, Ui.dp(a, 90) + a.insetTop, 0, 0);
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

    /** 背景：data.videoBg 图片铺满 + 暗色遮罩；无图时为浏览器版的 #111 深色 */
    private void applyBackground() {
        if (fullScreen == null) return;
        android.graphics.Bitmap bg = Ui.decodeDataUrl(
                a.store.resolveMedia(a.store.data.optString("videoBg", "")));
        if (bg != null) {
            android.graphics.drawable.BitmapDrawable drawable =
                    new android.graphics.drawable.BitmapDrawable(a.getResources(), bg);
            drawable.setGravity(Gravity.FILL);
            android.graphics.drawable.LayerDrawable layered = new android.graphics.drawable.LayerDrawable(
                    new android.graphics.drawable.Drawable[]{
                            drawable, new android.graphics.drawable.ColorDrawable(0x55000000)});
            fullScreen.setBackground(layered);
        } else {
            fullScreen.setBackground(Ui.gradient(0xFF1A1620, 0xFF111111, 0));
        }
    }

    private void pickBackground() {
        a.pickImage((bytes, mime, name) -> {
            try {
                a.store.data.put("videoBg", a.store.importImage(bytes, mime));
                a.store.save();
                applyBackground();
            } catch (Exception ignored) {
            }
        });
    }

    private void addBottomBar(boolean connected) {
        LinearLayout bar = Ui.row(a);
        bar.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        barLp.bottomMargin = Ui.dp(a, 46) + a.insetBottom;
        bar.addView(circleButton("背景", Icons.IMAGE_BG, 0x66FFFFFF, this::pickBackground));
        bar.addView(circleButton("最小化", Icons.MINIMIZE, 0x66FFFFFF, this::minimize));
        bar.addView(circleButton(connected ? "挂断" : "取消", Icons.PHONE_OFF, 0xFFE05B4E, () -> endCall(connected)));
        fullScreen.addView(bar, barLp);
    }

    private void setStatus(String status) {
        if (statusView != null) statusView.setText(status);
        if (timerMini != null && !inCall) timerMini.setText(status);
    }

    private void connect() {
        if (fullScreen == null) return;
        boolean wasMini = miniWindow != null && miniWindow.getVisibility() == View.VISIBLE;
        inCall = true;
        seconds = 0;
        showCallScreen("通话中", false);
        addBottomBar(true);
        if (wasMini) minimize();
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
        callSeq++;
        if (tick != null) handler.removeCallbacks(tick);
        tick = null;
        removeAll();
        root.setVisibility(View.GONE);
        root.setClickable(false);
        if (was || connected) {
            a.logic.addSys("视频通话已结束 " + Ui.fmtDur(Math.max(1, duration)));
            a.logic.logVideo(duration);
        } else {
            a.logic.addSys("视频通话未接通");
        }
        a.logic.scheduleReply();
    }

    /* ---------- 最小化悬浮窗 ---------- */

    private void minimize() {
        if (fullScreen != null) fullScreen.setVisibility(View.GONE);
        root.setClickable(false);
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
        timerMini = Ui.boldText(a, inCall ? Ui.fmtDur(seconds) : "等待接听…", 11, Color.WHITE);
        timerMini.setGravity(Gravity.CENTER);
        timerMini.setPadding(0, Ui.dp(a, 4), 0, 0);
        miniWindow.addView(timerMini);
        TextView miniHang = Ui.boldText(a, inCall ? "挂断" : "取消", 11, Color.WHITE);
        miniHang.setGravity(Gravity.CENTER);
        miniHang.setBackground(Ui.rounded(0xFFE05B4E, Ui.dp(a, 10)));
        miniHang.setPadding(Ui.dp(a, 10), Ui.dp(a, 4), Ui.dp(a, 10), Ui.dp(a, 4));
        LinearLayout.LayoutParams hangLp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hangLp.topMargin = Ui.dp(a, 6);
        miniHang.setLayoutParams(hangLp);
        miniHang.setOnClickListener(v -> endCall(inCall));
        miniWindow.addView(miniHang);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                Ui.dp(a, 88), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        lp.topMargin = Ui.dp(a, 120) + a.insetTop;
        root.addView(miniWindow, lp);
        if (miniX >= 0) {
            final View mw = miniWindow;
            mw.post(() -> {
                mw.setX(miniX);
                mw.setY(miniY);
                snapToEdge(mw);
            });
        }

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
                    case MotionEvent.ACTION_CANCEL:
                        snapToEdge(v);
                        return true;
                }
                return false;
            }
        });
    }

    /** 贴边：松手后完整靠到更近的一侧屏幕边缘，不露空也不出屏 */
    private void snapToEdge(View v) {
        if (root.getWidth() == 0 || v.getWidth() == 0) return;
        float centerX = v.getX() + v.getWidth() / 2f;
        boolean left = centerX < root.getWidth() / 2f;
        float target = left ? 0 : root.getWidth() - v.getWidth();
        float y = Math.max(a.insetTop, Math.min(root.getHeight() - v.getHeight() - a.insetBottom, v.getY()));
        miniX = target;
        miniY = y;
        v.animate().x(target).y(y).setDuration(180).start();
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

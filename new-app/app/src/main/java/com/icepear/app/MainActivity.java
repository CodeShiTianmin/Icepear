package com.icepear.app;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 应用主界面：页面路由（聊天/小卖铺/字卡/信箱/设置 + 功能中心各页）、
 * 顶部栏、底部导航、开屏动画、返回键处理、视频通话悬浮层。
 */
public class MainActivity extends Activity implements ChatLogic.Host {

    private static final int REQ_PICK_FILE = 4107;
    private static final int REQ_SAVE_FILE = 4108;
    private static final int REQ_PICK_MULTI = 4109;

    public Store store;
    public SoundPlayer sound;
    public ChatLogic logic;

    private FrameLayout root;
    private LinearLayout shell;
    private FrameLayout pageHost;
    private LinearLayout bottomNav;
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private final Deque<String> backStack = new ArrayDeque<>();
    public String currentPage = "pageChat";

    public VideoOverlay videoOverlay;

    /** 当前系统栏/键盘安全区（px），供页面在需要时读取 */
    public int insetTop, insetBottom, imeBottom;

    public interface FilePicked {
        void run(byte[] bytes, String mime, String name);
    }

    public interface FileSaved {
        void run(Uri uri);
    }

    private FilePicked pendingPick;
    private FileSaved pendingSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = Store.get(this);
        sound = new SoundPlayer(this, store);
        logic = new ChatLogic(store, sound, this);

        root = new FrameLayout(this);
        shell = Ui.column(this);
        pageHost = new FrameLayout(this);
        bottomNav = Ui.row(this);

        shell.addView(pageHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        shell.addView(bottomNav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 58)));
        root.addView(shell, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setupEdgeToEdge();

        pages.put("pageChat", new ChatPage(this));
        pages.put("pageShop", new ShopPage(this));
        pages.put("pageCards", new CardsPage(this));
        pages.put("pageLetter", new LetterPage(this));
        pages.put("pageSet", new SettingsPage(this));
        pages.put("pageWeather", new DailyPage(this));
        pages.put("pageWeekly", new WeeklyPage(this));
        pages.put("pageSearch", new SearchPage(this));
        pages.put("pageMenu", new MenuPage(this));
        pages.put("pageMoments", new MomentsPage(this));
        pages.put("pageCloud", new CloudPage(this));
        pages.put("pageFav", new FavoritesPage(this));
        pages.put("pageMemo", new AnniversaryPage(this));

        videoOverlay = new VideoOverlay(this);
        root.addView(videoOverlay.rootView(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        buildBottomNav();
        applyTheme();
        goPage("pageChat", false);
        store.ensureDailyToday();
        showBootScreen();

        logic.scheduleStoredReminders();
        logic.startActiveLoop();
        logic.startStatusLoop();
        logic.checkFestival();
        logic.checkAutoNight();
    }

    @Override
    protected void onDestroy() {
        logic.stopActiveLoop();
        logic.stopStatusLoop();
        logic.handler().removeCallbacksAndMessages(null);
        sound.release();
        Page chat = pages.get("pageChat");
        if (chat instanceof ChatPage) ((ChatPage) chat).releaseTts();
        super.onDestroy();
    }

    /* ---------- 边到边安全区：状态栏 / 导航栏 / 键盘 ---------- */

    private void setupEdgeToEdge() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        } else {
            View decor = window.getDecorView();
            decor.setSystemUiVisibility(decor.getSystemUiVisibility()
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom, ime;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                android.graphics.Insets keyboard = insets.getInsets(WindowInsets.Type.ime());
                top = bars.top;
                bottom = bars.bottom;
                ime = Math.max(0, keyboard.bottom - bars.bottom);
            } else {
                top = insets.getSystemWindowInsetTop();
                int stable = insets.getStableInsetBottom();
                int full = insets.getSystemWindowInsetBottom();
                bottom = Math.min(stable, full);
                ime = Math.max(0, full - bottom);
            }
            applyInsets(top, bottom, ime);
            return insets;
        });
        root.requestApplyInsets();
    }

    private void applyInsets(int top, int bottom, int ime) {
        boolean changed = top != insetTop || bottom != insetBottom || ime != imeBottom;
        insetTop = top;
        insetBottom = bottom;
        imeBottom = ime;
        boolean keyboardOpen = ime > 0;
        shell.setPadding(0, top, 0, keyboardOpen ? bottom + ime : bottom);
        boolean mainTab = isMainTab(currentPage);
        bottomNav.setVisibility(mainTab && !keyboardOpen ? View.VISIBLE : View.GONE);
        if (changed) {
            Page page = pages.get(currentPage);
            if (page instanceof ChatPage) ((ChatPage) page).onKeyboard(keyboardOpen);
        }
    }

    public void hideKeyboard() {
        View focus = getCurrentFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow((focus != null ? focus : root).getWindowToken(), 0);
        }
        if (focus != null) focus.clearFocus();
    }

    public void showKeyboard(View target) {
        target.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT);
    }

    /* ---------- 主题 ---------- */

    public void toggleDark() {
        try {
            store.data.put("dark", !Ui.dark(store));
            store.save();
        } catch (JSONException ignored) {
        }
        applyTheme();
    }

    public void applyTheme() {
        boolean dark = Ui.dark(store);
        root.setBackgroundColor(Ui.paper(this, store));
        shell.setBackgroundColor(Ui.paper(this, store));
        bottomNav.setBackgroundColor(Ui.navBg(this, store));
        Window window = getWindow();
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (dark) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);
        for (Page page : pages.values()) page.rebuild();
        if (pageHost.getChildCount() > 0) goPage(currentPage, false);
        else buildBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (store.ensureDailyToday()) {
            Page daily = pages.get("pageWeather");
            if (daily != null && "pageWeather".equals(currentPage)) daily.refresh();
        }
    }

    /* ---------- 底部导航（图标式，对应补丁18） ---------- */

    private static final String[][] NAV = {
            {"pageChat", Icons.NAV_CHAT, "聊天"},
            {"pageMoments", Icons.NAV_MOMENTS, "动态"},
            {"pageMenu", Icons.NAV_MENU, "发现"},
            {"pageLetter", Icons.NAV_LETTER, "信箱"},
            {"pageSet", Icons.NAV_SET, "我的"},
    };

    private boolean isMainTab(String id) {
        for (String[] item : NAV) if (item[0].equals(id)) return true;
        return false;
    }

    /** 未读红点：聊天 / 朋友圈有新内容且当前不在该页时显示，进入页面后清除 */
    private final java.util.Set<String> unread = new java.util.HashSet<>();

    public void markUnread(String pageId) {
        if (pageId.equals(currentPage)) return;
        if (unread.add(pageId)) buildBottomNav();
    }

    private void buildBottomNav() {
        bottomNav.removeAllViews();
        bottomNav.setBackgroundColor(Ui.navBg(this, store));
        for (String[] item : NAV) {
            final String id = item[0];
            boolean active = id.equals(currentPage);
            int color = active ? Ui.plum(this, store) : Ui.mutedInk(this, store);
            FrameLayout button = new FrameLayout(this);
            button.setContentDescription(item[2]);
            ImageView icon = SvgIcon.view(this, item[1], color, 26);
            int pad = Ui.dp(this, 8);
            icon.setPadding(pad, pad, pad, pad);
            FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            button.addView(icon, iconLp);
            if (unread.contains(id) && !active) {
                View dot = new View(this);
                int d = Ui.dp(this, 8);
                dot.setBackground(Ui.rounded(0xFFE5484D, d / 2f));
                FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(d, d, Gravity.CENTER);
                dotLp.leftMargin = Ui.dp(this, 22);
                dotLp.bottomMargin = Ui.dp(this, 20);
                button.addView(dot, dotLp);
            }
            button.setOnClickListener(v -> goPage(id, true));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            bottomNav.addView(button, lp);
        }
    }

    /* ---------- 页面路由 ---------- */

    public void goPage(String id, boolean pushBack) {
        Page page = pages.get(id);
        if (page == null) return;
        if (pushBack && !id.equals(currentPage)) backStack.push(currentPage);
        currentPage = id;
        unread.remove(id);
        pageHost.removeAllViews();
        pageHost.addView(page.view(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        page.refresh();
        bottomNav.setVisibility(isMainTab(id) && imeBottom == 0 ? View.VISIBLE : View.GONE);
        buildBottomNav();
        if (!"pageChat".equals(id)) hideKeyboard();
    }

    public Page page(String id) {
        return pages.get(id);
    }

    public void refreshCurrentPage() {
        Page page = pages.get(currentPage);
        if (page != null) page.refresh();
    }

    @Override
    public void onBackPressed() {
        if (videoOverlay.handleBack()) return;
        Page page = pages.get(currentPage);
        if (page != null && page.handleBack()) return;
        if (!backStack.isEmpty()) {
            goPage(backStack.pop(), false);
            return;
        }
        if (!"pageChat".equals(currentPage)) {
            goPage("pageChat", false);
            return;
        }
        super.onBackPressed();
    }

    /* ---------- 开屏动画 ---------- */

    private void showBootScreen() {
        JSONObject icepearUi = store.data.optJSONObject("icepearUi");
        String anim = icepearUi != null ? icepearUi.optString("bootAnim", "hearts") : "hearts";
        if ("off".equals(anim)) return;
        FrameLayout boot = new FrameLayout(this);
        boot.setBackgroundColor(Ui.paper(this, store));
        boot.setClickable(true);
        LinearLayout center = Ui.column(this);
        center.setGravity(Gravity.CENTER_HORIZONTAL);

        FrameLayout stage = bootStage(anim, icepearUi != null ? icepearUi.optString("bootImg", "") : "");
        LinearLayout.LayoutParams stageLp = Ui.lp(Ui.dp(this, 220), Ui.dp(this, 130));
        stageLp.gravity = Gravity.CENTER_HORIZONTAL;
        stage.setLayoutParams(stageLp);
        center.addView(stage);

        TextView logo = Ui.boldText(this, "Icepear", 34, Ui.plum(this, store));
        logo.setGravity(Gravity.CENTER);
        logo.setPadding(0, Ui.dp(this, 14), 0, 0);
        TextView sub = Ui.text(this, "正在准备和" + store.displayName() + "见面…", 13, Ui.mutedInk(this, store));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, Ui.dp(this, 10), 0, 0);
        center.addView(logo);
        center.addView(sub);
        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        boot.addView(center, centerLp);
        root.addView(boot, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        center.setAlpha(0f);
        center.setTranslationY(Ui.dp(this, 16));
        center.animate().alpha(1f).translationY(0f).setDuration(520).start();
        final Runnable dismiss = () -> boot.animate().alpha(0f).setDuration(480)
                .withEndAction(() -> root.removeView(boot)).start();
        boot.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            boolean armed;

            @Override
            public boolean onPreDraw() {
                if (!armed) {
                    armed = true;
                    boot.postDelayed(dismiss, 2400);
                }
                boot.getViewTreeObserver().removeOnPreDrawListener(this);
                return true;
            }
        });
    }

    /** 开屏动画舞台（220x130dp）：动画 + 叠在上方的自定义图片，设置页预览也用它 */
    public FrameLayout bootStage(String anim, String bootImgRef) {
        FrameLayout stage = new FrameLayout(this);
        stage.setClipChildren(true);
        if (!"off".equals(anim)) renderBootAnim(anim, stage);
        String bootImg = store.resolveMedia(bootImgRef == null ? "" : bootImgRef);
        if (!bootImg.isEmpty()) {
            android.graphics.Bitmap bitmap = Ui.decodeDataUrl(bootImg);
            if (bitmap != null) {
                android.widget.ImageView image = new android.widget.ImageView(this);
                image.setImageBitmap(bitmap);
                image.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                image.setAdjustViewBounds(true);
                image.setClipToOutline(true);
                image.setBackground(Ui.rounded(0x00000000, Ui.dp(this, 12)));
                int maxW = Ui.dp(this, 200);
                int maxH = Ui.dp(this, 110);
                float scale = Math.min(maxW / (float) bitmap.getWidth(), maxH / (float) bitmap.getHeight());
                int w = Math.max(1, Math.round(bitmap.getWidth() * scale));
                int h = Math.max(1, Math.round(bitmap.getHeight() * scale));
                FrameLayout.LayoutParams imgLp = new FrameLayout.LayoutParams(w, h, Gravity.CENTER);
                stage.addView(image, imgLp);
            }
        }
        return stage;
    }

    /** 四种开屏动画：爱心飘动 / 气泡上升 / 星星闪烁 / 头像碰碰 */
    private void renderBootAnim(String anim, FrameLayout stage) {
        int stageW = Ui.dp(this, 220);
        int stageH = Ui.dp(this, 130);
        if ("bubbles".equals(anim)) {
            int[][] specs = {{8, 10, 0}, {24, 16, 500}, {42, 8, 1000}, {58, 20, 200}, {74, 12, 800}, {90, 15, 1400}};
            for (int i = 0; i < specs.length; i++) {
                android.view.View bubble = new android.view.View(this);
                int size = Ui.dp(this, specs[i][1]);
                bubble.setBackground(Ui.roundedStroke(0x00000000, size / 2,
                        i % 2 == 0 ? Ui.plum(this, store) : 0xFFE08578, Ui.dp(this, 2)));
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.START);
                lp.leftMargin = stageW * specs[i][0] / 100;
                lp.topMargin = stageH;
                stage.addView(bubble, lp);
                floatUp(bubble, stageH + size, 2400 + (i % 3) * 400, specs[i][2]);
            }
        } else if ("stars".equals(anim)) {
            int[][] specs = {{6, 12, 0}, {22, 6, 600}, {40, 14, 300}, {58, 8, 1100},
                    {74, 12, 500}, {90, 7, 1500}, {48, 16, 900}, {32, 10, 1800}};
            for (int i = 0; i < specs.length; i++) {
                TextView star = Ui.text(this, "✦", 14 + (i % 3) * 4, Ui.plum(this, store));
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
                lp.leftMargin = stageW * specs[i][0] / 100;
                lp.topMargin = stageH * specs[i][1] / 100;
                stage.addView(star, lp);
                ObjectAnimator twinkle = ObjectAnimator.ofFloat(star, "alpha", 0.25f, 1f);
                twinkle.setDuration(1300 + (i % 4) * 300);
                twinkle.setStartDelay(specs[i][2]);
                twinkle.setRepeatCount(ObjectAnimator.INFINITE);
                twinkle.setRepeatMode(ObjectAnimator.REVERSE);
                twinkle.start();
            }
        } else if ("avatar".equals(anim)) {
            JSONObject role = store.role();
            String his = store.displayName();
            String mine = role != null ? role.optString("myName", "我") : "我";
            TextView left = bootAvatar(his.isEmpty() ? "他" : his.substring(0, 1));
            TextView right = bootAvatar(mine.isEmpty() ? "我" : mine.substring(0, 1));
            int size = Ui.dp(this, 40);
            FrameLayout.LayoutParams leftLp = new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.START);
            leftLp.leftMargin = stageW / 5;
            leftLp.topMargin = stageH * 3 / 10;
            FrameLayout.LayoutParams rightLp = new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.END);
            rightLp.rightMargin = stageW / 5;
            rightLp.topMargin = stageH * 3 / 10;
            stage.addView(left, leftLp);
            stage.addView(right, rightLp);
            float shift = stageW * 0.16f;
            ObjectAnimator moveLeft = ObjectAnimator.ofFloat(left, "translationX", 0f, shift);
            ObjectAnimator moveRight = ObjectAnimator.ofFloat(right, "translationX", 0f, -shift);
            for (ObjectAnimator move : new ObjectAnimator[]{moveLeft, moveRight}) {
                move.setDuration(700);
                move.setRepeatCount(ObjectAnimator.INFINITE);
                move.setRepeatMode(ObjectAnimator.REVERSE);
                move.start();
            }
            TextView heart = Ui.text(this, "♡", 20, 0xFFE08578);
            FrameLayout.LayoutParams heartLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            heartLp.topMargin = stageH * 34 / 100;
            stage.addView(heart, heartLp);
            ObjectAnimator pop = ObjectAnimator.ofFloat(heart, "alpha", 0f, 0f, 1f, 0.9f, 0f);
            pop.setDuration(1400);
            pop.setRepeatCount(ObjectAnimator.INFINITE);
            pop.start();
        } else {
            int[][] specs = {{12, 0}, {30, 500}, {50, 1100}, {68, 200}, {86, 800}, {40, 1600}, {62, 400}};
            for (int i = 0; i < specs.length; i++) {
                TextView heart = Ui.text(this, "♡", 16 + (i % 3) * 4, 0xFFE08578);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
                lp.leftMargin = stageW * specs[i][0] / 100;
                lp.topMargin = stageH;
                stage.addView(heart, lp);
                floatUp(heart, stageH + Ui.dp(this, 30), 2200 + (i % 4) * 350, specs[i][1]);
            }
        }
    }

    private TextView bootAvatar(String label) {
        TextView avatar = Ui.boldText(this, label, 16, Ui.plum(this, store));
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(Ui.roundedStroke(Ui.surface(this, store), Ui.dp(this, 20),
                Ui.plum(this, store), Ui.dp(this, 2)));
        return avatar;
    }

    private void floatUp(android.view.View view, int distance, long duration, long delay) {
        ObjectAnimator rise = ObjectAnimator.ofFloat(view, "translationY", 0f, -distance);
        rise.setDuration(duration);
        rise.setStartDelay(delay);
        rise.setRepeatCount(ObjectAnimator.INFINITE);
        rise.start();
        ObjectAnimator fade = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f, 1f, 0f);
        fade.setDuration(duration);
        fade.setStartDelay(delay);
        fade.setRepeatCount(ObjectAnimator.INFINITE);
        fade.start();
    }

    /* ---------- 系统能力：剪贴板 / 分享 / 文件 ---------- */

    public void copyText(String text) {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText("Icepear", text));
        toast("已复制");
    }

    public void shareText(String text) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(intent, "分享"));
    }

    /** 相册选图：优先系统照片选择器（Android 13+），否则回退到相册 ACTION_PICK */
    public void pickImage(FilePicked callback) {
        pendingPick = callback;
        Intent intent;
        if (Build.VERSION.SDK_INT >= 33) {
            intent = new Intent(android.provider.MediaStore.ACTION_PICK_IMAGES);
        } else {
            intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        }
        try {
            startActivityForResult(intent, REQ_PICK_FILE);
        } catch (Exception e) {
            pendingPick = null;
            pickFile("image/*", callback);
        }
    }

    /** 相册选视频 */
    public void pickVideo(FilePicked callback) {
        pendingPick = callback;
        Intent intent;
        if (Build.VERSION.SDK_INT >= 33) {
            intent = new Intent(android.provider.MediaStore.ACTION_PICK_IMAGES);
            intent.setType("video/*");
        } else {
            intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        }
        try {
            startActivityForResult(intent, REQ_PICK_FILE);
        } catch (Exception e) {
            pendingPick = null;
            pickFile("video/*", callback);
        }
    }

    public interface FilesPicked {
        void run(java.util.List<byte[]> bytes, java.util.List<String> mimes);
    }

    private FilesPicked pendingMultiPick;

    /** 相册多选图片 */
    public void pickImages(FilesPicked callback) {
        pendingMultiPick = callback;
        Intent intent;
        if (Build.VERSION.SDK_INT >= 33) {
            intent = new Intent(android.provider.MediaStore.ACTION_PICK_IMAGES);
            intent.putExtra(android.provider.MediaStore.EXTRA_PICK_IMAGES_MAX, 50);
        } else {
            intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        try {
            startActivityForResult(intent, REQ_PICK_MULTI);
        } catch (Exception e) {
            pendingMultiPick = null;
            toast("无法打开相册");
        }
    }

    /** 应用内播放视频 */
    public void playVideo(java.io.File file) {
        if (file == null || !file.exists()) {
            toast("视频文件不存在");
            return;
        }
        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout box = new FrameLayout(this);
        box.setBackgroundColor(Color.BLACK);
        android.widget.VideoView video = new android.widget.VideoView(this);
        FrameLayout.LayoutParams vlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        box.addView(video, vlp);
        android.widget.MediaController controller = new android.widget.MediaController(this);
        controller.setAnchorView(video);
        video.setMediaController(controller);
        video.setOnErrorListener((mp, what, extra) -> {
            toast("无法播放该视频");
            dialog.dismiss();
            return true;
        });
        video.setOnCompletionListener(mp -> dialog.dismiss());
        box.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(box);
        dialog.setOnDismissListener(d -> video.stopPlayback());
        dialog.show();
        video.setVideoURI(Uri.fromFile(file));
        video.start();
    }

    public void pickFile(String mimeType, FilePicked callback) {
        pendingPick = callback;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        startActivityForResult(intent, REQ_PICK_FILE);
    }

    public void saveFile(String mimeType, String fileName, FileSaved callback) {
        pendingSave = callback;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        startActivityForResult(intent, REQ_SAVE_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_MULTI) {
            FilesPicked callback = pendingMultiPick;
            pendingMultiPick = null;
            if (resultCode != RESULT_OK || data == null || callback == null) return;
            java.util.List<Uri> uris = new java.util.ArrayList<>();
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) {
                    Uri u = clip.getItemAt(i).getUri();
                    if (u != null) uris.add(u);
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            java.util.List<byte[]> all = new java.util.ArrayList<>();
            java.util.List<String> mimes = new java.util.ArrayList<>();
            for (Uri u : uris) {
                try (InputStream in = getContentResolver().openInputStream(u)) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    String mime = getContentResolver().getType(u);
                    all.add(out.toByteArray());
                    mimes.add(mime == null ? "" : mime);
                } catch (Exception ignored) {
                }
            }
            if (!all.isEmpty()) callback.run(all, mimes);
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            pendingPick = null;
            pendingSave = null;
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQ_PICK_FILE && pendingPick != null) {
            FilePicked callback = pendingPick;
            pendingPick = null;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                String mime = getContentResolver().getType(uri);
                callback.run(out.toByteArray(), mime == null ? "" : mime, uri.getLastPathSegment());
            } catch (Exception e) {
                toast("读取文件失败");
            }
        } else if (requestCode == REQ_SAVE_FILE && pendingSave != null) {
            FileSaved callback = pendingSave;
            pendingSave = null;
            callback.run(uri);
        }
    }

    /* ---------- ChatLogic.Host ---------- */

    @Override
    public void onChatChanged(boolean scrollToBottom) {
        if (!scrollToBottom) markUnread("pageChat");
        Page chat = pages.get("pageChat");
        if (chat instanceof ChatPage) ((ChatPage) chat).renderChat(scrollToBottom);
    }

    @Override
    public void onWalletChanged() {
        refreshCurrentPage();
    }

    @Override
    public void onTyping(boolean typing) {
        Page chat = pages.get("pageChat");
        if (chat instanceof ChatPage) ((ChatPage) chat).setTyping(typing);
    }

    @Override
    public void toast(String message) {
        Dialogs.toast(this, message);
    }

    @Override
    public void onThemeMaybeChanged() {
        applyTheme();
    }

    public void resetAllData() {
        try {
            store.clearAllMedia();
            store.data = new org.json.JSONObject();
            store.ensureDefaults();
            store.save();
        } catch (Exception ignored) {
        }
        for (Page page : pages.values()) page.rebuild();
        applyTheme();
        goPage("pageChat", false);
    }

    public void afterDataImported() {
        try {
            store.ensureDefaults();
        } catch (Exception ignored) {
        }
        store.save();
        for (Page page : pages.values()) page.rebuild();
        applyTheme();
        goPage("pageChat", false);
        toast("数据已导入");
    }
}

package com.icepear.app;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

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
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 56)));
        root.addView(shell, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

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

        videoOverlay = new VideoOverlay(this);
        root.addView(videoOverlay.rootView(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        buildBottomNav();
        applyTheme();
        goPage("pageChat", false);
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

    /* ---------- 主题 ---------- */

    public void applyTheme() {
        boolean dark = Ui.dark(store);
        root.setBackgroundColor(Ui.paper(this, store));
        bottomNav.setBackgroundColor(Ui.navBg(this, store));
        Window window = getWindow();
        window.setStatusBarColor(Ui.topBg(this, store));
        window.setNavigationBarColor(Ui.navBg(this, store));
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
        buildBottomNav();
        Page page = pages.get(currentPage);
        if (page != null) page.rebuild();
    }

    /* ---------- 底部导航（图标式，对应补丁18） ---------- */

    private static final Object[][] NAV = {
            {"pageChat", R.drawable.nav_chat, "聊天"},
            {"pageMoments", R.drawable.nav_moments, "动态"},
            {"pageMenu", R.drawable.nav_menu, "发现"},
            {"pageLetter", R.drawable.nav_letter, "信箱"},
            {"pageSet", R.drawable.nav_set, "我的"},
    };

    private void buildBottomNav() {
        bottomNav.removeAllViews();
        bottomNav.setBackgroundColor(Ui.navBg(this, store));
        for (Object[] item : NAV) {
            final String id = (String) item[0];
            boolean active = id.equals(currentPage);
            LinearLayout button = new LinearLayout(this);
            button.setOrientation(LinearLayout.VERTICAL);
            button.setGravity(Gravity.CENTER);
            button.setContentDescription((String) item[2]);
            ImageView icon = new ImageView(this);
            icon.setImageResource((Integer) item[1]);
            icon.setColorFilter(active ? Ui.plum(this, store) : Ui.faintInk(this, store));
            int size = Ui.dp(this, 26);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(size, size);
            if (active) ip.bottomMargin = Ui.dp(this, 2);
            button.addView(icon, ip);
            View dot = new View(this);
            dot.setBackground(Ui.rounded(Ui.plum(this, store), Ui.dp(this, 3)));
            dot.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(Ui.dp(this, 5), Ui.dp(this, 5));
            dp.topMargin = Ui.dp(this, 3);
            button.addView(dot, dp);
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
        pageHost.removeAllViews();
        pageHost.addView(page.view(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        page.refresh();
        boolean mainTab = false;
        for (Object[] item : NAV) if (item[0].equals(id)) mainTab = true;
        bottomNav.setVisibility(mainTab ? View.VISIBLE : View.GONE);
        buildBottomNav();
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

        FrameLayout stage = new FrameLayout(this);
        LinearLayout.LayoutParams stageLp = Ui.lp(Ui.dp(this, 220), Ui.dp(this, 130));
        stageLp.gravity = Gravity.CENTER_HORIZONTAL;
        stage.setLayoutParams(stageLp);
        stage.setClipChildren(true);
        renderBootAnim(anim, stage);
        String bootImg = icepearUi != null ? store.resolveMedia(icepearUi.optString("bootImg", "")) : "";
        if (!bootImg.isEmpty()) {
            android.graphics.Bitmap bitmap = Ui.decodeDataUrl(bootImg);
            if (bitmap != null) {
                android.widget.ImageView image = new android.widget.ImageView(this);
                image.setImageBitmap(bitmap);
                image.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                image.setClipToOutline(true);
                image.setBackground(Ui.rounded(0x00000000, Ui.dp(this, 12)));
                FrameLayout.LayoutParams imgLp = new FrameLayout.LayoutParams(
                        Ui.dp(this, 128), Ui.dp(this, 50), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                imgLp.topMargin = Ui.dp(this, 4);
                stage.addView(image, imgLp);
            }
        }
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
        boot.postDelayed(() -> boot.animate().alpha(0f).setDuration(450)
                .withEndAction(() -> root.removeView(boot)).start(), 1700);
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

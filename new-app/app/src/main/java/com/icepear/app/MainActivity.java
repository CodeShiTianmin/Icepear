package com.icepear.app;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONException;

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

    private static final String[][] NAV = {
            {"pageChat", "💬", "聊天"},
            {"pageShop", "🛍", "小卖铺"},
            {"pageCards", "🗂", "字卡"},
            {"pageLetter", "✉️", "信箱"},
            {"pageSet", "⚙️", "设置"},
    };

    private void buildBottomNav() {
        bottomNav.removeAllViews();
        bottomNav.setBackgroundColor(Ui.navBg(this, store));
        for (String[] item : NAV) {
            final String id = item[0];
            TextView button = new TextView(this);
            button.setText(item[1]);
            button.setTextSize(24);
            button.setGravity(Gravity.CENTER);
            button.setContentDescription(item[2]);
            boolean active = id.equals(currentPage);
            button.setAlpha(active ? 1f : 0.55f);
            if (active) {
                button.setBackground(Ui.rounded((Ui.plum(this, store) & 0x00FFFFFF) | 0x22000000, Ui.dp(this, 16)));
            }
            button.setOnClickListener(v -> goPage(id, true));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            int m = Ui.dp(this, 6);
            lp.setMargins(m, m, m, m);
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
        for (String[] item : NAV) if (item[0].equals(id)) mainTab = true;
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
        FrameLayout boot = new FrameLayout(this);
        boot.setBackgroundColor(Ui.paper(this, store));
        boot.setClickable(true);
        LinearLayout center = Ui.column(this);
        center.setGravity(Gravity.CENTER);
        TextView logo = Ui.boldText(this, "Icepear", 34, Ui.plum(this, store));
        logo.setGravity(Gravity.CENTER);
        TextView sub = Ui.text(this, "正在准备和" + store.displayName() + "见面…", 13, Ui.mutedInk(this, store));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, Ui.dp(this, 12), 0, 0);
        TextView hearts = Ui.text(this, "♡ ♡ ♡", 22, Ui.plum(this, store));
        hearts.setGravity(Gravity.CENTER);
        hearts.setPadding(0, Ui.dp(this, 22), 0, 0);
        center.addView(logo);
        center.addView(sub);
        center.addView(hearts);
        boot.addView(center, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(boot, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ObjectAnimator pulse = ObjectAnimator.ofFloat(hearts, "alpha", 0.3f, 1f);
        pulse.setDuration(600);
        pulse.setRepeatCount(3);
        pulse.setRepeatMode(ObjectAnimator.REVERSE);
        pulse.start();
        boot.postDelayed(() -> boot.animate().alpha(0f).setDuration(450)
                .withEndAction(() -> root.removeView(boot)).start(), 1700);
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

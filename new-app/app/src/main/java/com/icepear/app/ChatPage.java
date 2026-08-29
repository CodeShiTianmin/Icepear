package com.icepear.app;

import android.graphics.Color;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * 聊天页：消息气泡、时间/已读、引用、红包/转账/礼物卡片、图片与定位消息、
 * 长按消息菜单（复制/转发/收藏/撤回/删除/多选/引用/提醒/翻译/搜一搜/朗读）、
 * 表情面板、加号面板（图片/红包/转账/礼物/定位/拍一拍/视频通话/来电）。
 */
public class ChatPage extends Page {

    private LinearLayout chatArea;
    private ScrollView chatScroll;
    private EditText textInput;
    private LinearLayout quoteBar;
    private TextView quoteText;
    private TextView typingView;
    private TextView statusView;
    private TextView nameView;
    private FrameLayout avatarBox;
    private LinearLayout panelBox;
    private LinearLayout multiBar;
    private TextView multiCount;

    private String quoteTarget;
    private String quoteRef = "";
    private final Set<Integer> selected = new TreeSet<>();
    private boolean selecting;
    private TextToSpeech tts;
    private long lastAvatarTap;
    private String lastAvatarKey = "";

    public ChatPage(MainActivity activity) {
        super(activity);
    }

    @Override
    protected View create() {
        LinearLayout page = Ui.column(a);
        page.setBackgroundColor(Ui.paper(a, a.store));

        /* 顶栏：头像 + 名字 + 状态 + 功能中心入口 */
        LinearLayout top = Ui.row(a);
        top.setBackgroundColor(Ui.topBg(a, a.store));
        top.setPadding(Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 12), Ui.dp(a, 8));
        avatarBox = new FrameLayout(a);
        top.addView(avatarBox, new LinearLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 40)));
        avatarBox.setOnClickListener(v -> onAvatarTap("other", "topAvatar"));
        LinearLayout names = Ui.column(a);
        names.setPadding(Ui.dp(a, 10), 0, 0, 0);
        nameView = Ui.boldText(a, "", 16, Ui.ink(a, a.store));
        statusView = Ui.text(a, "", 11, Ui.mutedInk(a, a.store));
        typingView = Ui.text(a, "对方正在输入…", 11, Ui.plum(a, a.store));
        typingView.setVisibility(View.GONE);
        names.addView(nameView);
        names.addView(statusView);
        names.addView(typingView);
        top.addView(names, Ui.weighted());
        TextView menuButton = Ui.boldText(a, "⊞", 24, Ui.ink(a, a.store));
        menuButton.setPadding(Ui.dp(a, 10), 0, Ui.dp(a, 4), 0);
        menuButton.setContentDescription("功能中心");
        menuButton.setOnClickListener(v -> a.goPage("pageMenu", true));
        top.addView(menuButton);
        page.addView(top);

        /* 聊天区 */
        chatScroll = new ScrollView(a);
        chatScroll.setFillViewport(true);
        chatArea = Ui.column(a);
        chatArea.setPadding(Ui.dp(a, 10), Ui.dp(a, 8), Ui.dp(a, 10), Ui.dp(a, 12));
        chatScroll.addView(chatArea);
        applyWallpaper(chatScroll);
        page.addView(chatScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        /* 多选操作栏 */
        multiBar = Ui.row(a);
        multiBar.setBackgroundColor(Ui.surface(a, a.store));
        multiBar.setPadding(Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 12), Ui.dp(a, 8));
        TextView cancel = Ui.boldText(a, "取消", 14, Ui.mutedInk(a, a.store));
        cancel.setOnClickListener(v -> exitSelection());
        multiCount = Ui.text(a, "已选 0 条", 13, Ui.ink(a, a.store));
        multiCount.setGravity(Gravity.CENTER);
        TextView forward = Ui.boldText(a, "转发", 14, Ui.plum(a, a.store));
        forward.setOnClickListener(v -> forwardSelected());
        TextView delete = Ui.boldText(a, "删除", 14, a.getColor(R.color.danger));
        delete.setPadding(Ui.dp(a, 18), 0, 0, 0);
        delete.setOnClickListener(v -> deleteSelected());
        multiBar.addView(cancel);
        multiBar.addView(multiCount, Ui.weighted());
        multiBar.addView(forward);
        multiBar.addView(delete);
        multiBar.setVisibility(View.GONE);
        page.addView(multiBar);

        /* 引用栏 */
        quoteBar = Ui.row(a);
        quoteBar.setBackgroundColor(Ui.surface(a, a.store));
        quoteBar.setPadding(Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 6));
        quoteText = Ui.text(a, "", 12, Ui.mutedInk(a, a.store));
        quoteText.setSingleLine(true);
        quoteBar.addView(quoteText, Ui.weighted());
        TextView clearQuote = Ui.boldText(a, "×", 18, Ui.mutedInk(a, a.store));
        clearQuote.setPadding(Ui.dp(a, 10), 0, 0, 0);
        clearQuote.setOnClickListener(v -> clearQuote());
        quoteBar.addView(clearQuote);
        quoteBar.setVisibility(View.GONE);
        page.addView(quoteBar);

        /* 输入栏 */
        LinearLayout input = Ui.row(a);
        input.setBackgroundColor(Ui.navBg(a, a.store));
        input.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));
        TextView emojiButton = Ui.text(a, "😊", 22, Ui.ink(a, a.store));
        emojiButton.setPadding(Ui.dp(a, 6), 0, Ui.dp(a, 6), 0);
        emojiButton.setOnClickListener(v -> togglePanel("emoji"));
        input.addView(emojiButton);
        textInput = Dialogs.makeInput(a, a.store, false);
        textInput.setHint("说点什么…");
        textInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        textInput.setMaxLines(4);
        input.addView(textInput, Ui.weighted());
        TextView plusButton = Ui.text(a, "＋", 24, Ui.ink(a, a.store));
        plusButton.setPadding(Ui.dp(a, 8), 0, Ui.dp(a, 8), 0);
        plusButton.setOnClickListener(v -> togglePanel("plus"));
        input.addView(plusButton);
        TextView sendButton = Ui.boldText(a, "发送", 14, Color.WHITE);
        sendButton.setBackground(Ui.rounded(Ui.plum(a, a.store), Ui.dp(a, 12)));
        sendButton.setPadding(Ui.dp(a, 14), Ui.dp(a, 8), Ui.dp(a, 14), Ui.dp(a, 8));
        sendButton.setOnClickListener(v -> sendUser());
        input.addView(sendButton);
        page.addView(input);

        /* 面板容器 */
        panelBox = Ui.column(a);
        panelBox.setBackgroundColor(Ui.surface(a, a.store));
        panelBox.setVisibility(View.GONE);
        page.addView(panelBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 220)));

        return page;
    }

    private void applyWallpaper(View target) {
        JSONObject wallpaper = a.store.data.optJSONObject("wallpaper");
        if (wallpaper == null) return;
        boolean dark = Ui.dark(a.store);
        String preset = wallpaper.optString("preset", "默认");
        if ("图片".equals(preset)) {
            String image = a.store.resolveMedia(wallpaper.optString("image", ""));
            if (image.startsWith("data:image")) {
                android.graphics.Bitmap bitmap = Ui.decodeDataUrl(image);
                if (bitmap != null) {
                    target.setBackground(new android.graphics.drawable.BitmapDrawable(a.getResources(), bitmap));
                    return;
                }
            }
        }
        if ("默认".equals(preset)) {
            String asset = dark ? "art/chat-paper-night.webp" : "art/chat-paper-day.webp";
            try (java.io.InputStream in = a.getAssets().open(asset)) {
                android.graphics.Bitmap paper = android.graphics.BitmapFactory.decodeStream(in);
                if (paper != null) {
                    target.setBackground(new android.graphics.drawable.BitmapDrawable(a.getResources(), paper));
                    return;
                }
            } catch (java.io.IOException ignored) {
            }
            target.setBackgroundColor(dark ? 0xFF171316 : 0xFFF8F3EF);
            return;
        }
        if (dark) {
            target.setBackgroundColor(0xFF171316);
            return;
        }
        int start, end;
        switch (preset) {
            case "粉色": start = 0xFFFFF4F4; end = 0xFFF8E7EC; break;
            case "蓝色": start = 0xFFF4F7FB; end = 0xFFE8F0F4; break;
            case "绿色": start = 0xFFF4F8F5; end = 0xFFE8F1EB; break;
            case "星空": start = 0xFF29243C; end = 0xFF151323; break;
            default: start = 0xFFF9F5F2; end = 0xFFF5EFEB;
        }
        target.setBackground(Ui.gradient(start, end, 0));
    }

    @Override
    public void refresh() {
        JSONObject role = a.store.role();
        if (role == null) return;
        nameView.setText(a.store.displayName());
        statusView.setText("[" + role.optString("statusNow", "想你") + "]");
        avatarBox.removeAllViews();
        avatarBox.addView(Ui.avatar(a, a.store, "other", 40));
        renderChat(true);
    }

    public void setTyping(boolean typing) {
        if (typingView != null) typingView.setVisibility(typing ? View.VISIBLE : View.GONE);
    }

    /* ---------- 消息渲染 ---------- */

    public void renderChat(boolean scrollToBottom) {
        if (chatArea == null) return;
        chatArea.removeAllViews();
        JSONArray chat = a.store.chat();
        JSONObject font = a.store.data.optJSONObject("font");
        int fontSize = font != null ? font.optInt("size", 15) : 15;
        float radius = font != null ? font.optInt("radius", 10) : 10;
        JSONObject theme = a.store.data.optJSONObject("theme");
        int myBg = Ui.parseColor(theme != null ? theme.optString("myBg") : "", 0xFF95EC69);
        int myText = Ui.parseColor(theme != null ? theme.optString("myText") : "", 0xFF111111);
        int hisBg = Ui.parseColor(theme != null ? theme.optString("hisBg") : "",
                Ui.dark(a.store) ? a.getColor(R.color.dark_bubble_his) : 0xFFFFFFFF);
        int hisText = Ui.parseColor(theme != null ? theme.optString("hisText") : "",
                Ui.dark(a.store) ? a.getColor(R.color.dark_bubble_his_text) : 0xFF111111);
        JSONObject chatOpt = a.store.data.optJSONObject("chatOpt");
        String timeMode = chatOpt != null ? chatOpt.optString("timeMode", "all") : "all";
        String readMode = chatOpt != null ? chatOpt.optString("readMode", "all") : "all";

        for (int i = 0; i < chat.length(); i++) {
            JSONObject msg = chat.optJSONObject(i);
            if (msg == null) continue;
            final int index = i;

            if ("sys".equals(msg.optString("type")) || msg.optBoolean("recall", false)) {
                String label = msg.optBoolean("recall", false)
                        ? ("me".equals(msg.optString("side")) ? "你" : a.store.displayName()) + "撤回了一条消息"
                        : msg.optString("text");
                TextView sys = Ui.text(a, label, 11, Ui.faintInk(a, a.store));
                sys.setGravity(Gravity.CENTER);
                sys.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                chatArea.addView(sys, lp);
                continue;
            }

            boolean mine = "me".equals(msg.optString("side"));
            LinearLayout row = Ui.row(a);
            row.setGravity(mine ? Gravity.END : Gravity.START);
            row.setPadding(0, Ui.dp(a, 5), 0, Ui.dp(a, 5));

            View avatar = Ui.avatar(a, a.store, mine ? "me" : "other", 36);
            avatar.setOnClickListener(v -> onAvatarTap(mine ? "me" : "other",
                    msg.optString("id", String.valueOf(index))));

            LinearLayout bubbleColumn = Ui.column(a);
            bubbleColumn.setGravity(mine ? Gravity.END : Gravity.START);
            int maxWidth = (int) (a.getResources().getDisplayMetrics().widthPixels * 0.68);

            View content = buildContent(msg, index, fontSize, radius,
                    mine ? myBg : hisBg, mine ? myText : hisText, maxWidth);
            content.setOnLongClickListener(v -> {
                openMessageMenu(index, v);
                return true;
            });
            if (selecting) {
                content.setOnClickListener(v -> toggleSelected(index));
                content.setAlpha(selected.contains(index) ? 0.55f : 1f);
            }
            bubbleColumn.addView(content);

            /* 时间 / 已读 */
            boolean showTime = "all".equals(timeMode) || (mine ? "me" : "his").equals(timeMode);
            boolean showRead = "all".equals(readMode) || (mine ? "me" : "his").equals(readMode);
            if (showTime || showRead) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(msg.optLong("t", System.currentTimeMillis()));
                StringBuilder meta = new StringBuilder();
                if (showTime) meta.append(Ui.pad2(cal.get(Calendar.HOUR_OF_DAY)))
                        .append(":").append(Ui.pad2(cal.get(Calendar.MINUTE)));
                if (showRead) {
                    if (meta.length() > 0) meta.append("  ");
                    meta.append(mine ? (msg.optBoolean("read", false) ? "已读" : "未读") : "已读");
                }
                TextView metaView = Ui.text(a, meta.toString(), 10, Ui.faintInk(a, a.store));
                metaView.setPadding(Ui.dp(a, 4), Ui.dp(a, 2), Ui.dp(a, 4), 0);
                bubbleColumn.addView(metaView);
            }

            if (mine) {
                LinearLayout.LayoutParams bp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                bp.rightMargin = Ui.dp(a, 8);
                row.addView(bubbleColumn, bp);
                row.addView(avatar);
            } else {
                row.addView(avatar);
                LinearLayout.LayoutParams bp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                bp.leftMargin = Ui.dp(a, 8);
                row.addView(bubbleColumn, bp);
            }
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            chatArea.addView(row, rowLp);
        }
        if (scrollToBottom) {
            chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    private View buildContent(JSONObject msg, int index, int fontSize, float radius,
                              int bg, int textColor, int maxWidth) {
        String type = msg.optString("type", "");
        if ("red".equals(type) || "zhuan".equals(type) || "gift".equals(type)) {
            return buildTxCard(msg, index);
        }
        if ("img".equals(type)) {
            String source = a.store.resolveMedia(msg.optString("src", ""));
            android.graphics.Bitmap bitmap = Ui.decodeDataUrl(source);
            if (bitmap != null) {
                ImageView image = new ImageView(a);
                image.setImageBitmap(bitmap);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                image.setAdjustViewBounds(true);
                image.setMaxWidth(Ui.dp(a, 160));
                image.setMaxHeight(Ui.dp(a, 200));
                image.setBackground(Ui.rounded(0x00000000, Ui.dp(a, radius)));
                image.setClipToOutline(true);
                image.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, android.graphics.Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Ui.dp(a, 12));
                    }
                });
                return image;
            }
            TextView missing = Ui.text(a, "[图片]", fontSize, textColor);
            missing.setBackground(Ui.rounded(bg, Ui.dp(a, radius)));
            missing.setPadding(Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 12), Ui.dp(a, 8));
            return missing;
        }
        if ("loc".equals(type)) {
            LinearLayout card = Ui.column(a);
            card.setBackground(Ui.rounded(Ui.surfaceStrong(a, a.store), Ui.dp(a, radius)));
            card.setPadding(Ui.dp(a, 14), Ui.dp(a, 10), Ui.dp(a, 14), Ui.dp(a, 10));
            card.addView(Ui.boldText(a, "📍 " + msg.optString("text"), 14, Ui.ink(a, a.store)));
            card.addView(Ui.text(a, "位置", 11, Ui.faintInk(a, a.store)));
            card.setMinimumWidth(Ui.dp(a, 170));
            return card;
        }
        LinearLayout bubble = Ui.column(a);
        bubble.setBackground(Ui.rounded(bg, Ui.dp(a, radius)));
        bubble.setPadding(Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 12), Ui.dp(a, 8));
        String quote = msg.optString("quote", "");
        if (!quote.isEmpty()) {
            TextView quoteView = Ui.text(a, "引用：" + quote, 11, textColor);
            quoteView.setAlpha(0.65f);
            quoteView.setSingleLine(true);
            quoteView.setPadding(0, 0, 0, Ui.dp(a, 4));
            String ref = msg.optString("quoteRef", "");
            if (!ref.isEmpty()) quoteView.setOnClickListener(v -> jumpToMessage(ref));
            bubble.addView(quoteView);
        }
        TextView text = Ui.text(a, msg.optString("text"), fontSize, textColor);
        text.setMaxWidth(maxWidth);
        bubble.addView(text);
        return bubble;
    }

    /** 红包/转账/礼物卡片 */
    private View buildTxCard(JSONObject msg, int index) {
        String type = msg.optString("type");
        JSONObject cardUi = a.store.data.optJSONObject("cardUi");
        JSONObject ui = cardUi != null ? cardUi.optJSONObject(type) : null;
        JSONArray colors = a.store.data.optJSONArray("cardColors");
        int colorIndex = ui != null ? ui.optInt("color", 0) : 0;
        int start = 0xFFFA9D3B, end = 0xFFF76B1C;
        if (colors != null && colorIndex < colors.length()) {
            JSONArray pair = colors.optJSONArray(colorIndex);
            if (pair != null) {
                start = Ui.parseColor(pair.optString(0), start);
                end = Ui.parseColor(pair.optString(1), end);
            }
        }
        String status = msg.optString("txStatus", "");
        boolean handled = !status.isEmpty() || msg.optBoolean("handled", false);
        LinearLayout card = Ui.row(a);
        card.setBackground(Ui.gradient(start, end, Ui.dp(a, 14)));
        card.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12));
        card.setAlpha(handled ? 0.72f : 1f);
        card.setMinimumWidth(Ui.dp(a, 210));
        TextView icon = Ui.text(a, ui != null ? ui.optString("icon", "🧧") : "🧧", 26, Color.WHITE);
        card.addView(icon);
        LinearLayout copy = Ui.column(a);
        copy.setPadding(Ui.dp(a, 10), 0, 0, 0);
        String title;
        if ("gift".equals(type)) title = msg.optString("gift", "礼物");
        else if (msg.has("note") && !msg.optString("note", "").isEmpty()) title = msg.optString("note");
        else title = ui != null ? ui.optString("t", "red".equals(type) ? "恭喜发财" : "转账") : "恭喜发财";
        copy.addView(Ui.boldText(a, title, 14, Color.WHITE));
        double amount = ChatLogic.txAmount(msg);
        String sub = "¥" + Ui.fmtMoney(amount) + " · " + txStatusLabel(msg);
        copy.addView(Ui.text(a, sub, 11, 0xDDFFFFFF));
        card.addView(copy);
        card.setOnClickListener(v -> openTxPop(msg, index));
        return card;
    }

    private String txStatusLabel(JSONObject msg) {
        if ("returned".equals(msg.optString("txStatus", ""))) return "已退还";
        if ("accepted".equals(msg.optString("txStatus", "")) || msg.optBoolean("handled", false)) return "已接收";
        return "待处理";
    }

    /** 点开红包/转账/礼物，等价于旧版 openRed() */
    private void openTxPop(JSONObject msg, int index) {
        String type = msg.optString("type");
        boolean mine = "me".equals(msg.optString("side"));
        double amount = ChatLogic.txAmount(msg);
        String kind = ChatLogic.txKindLabel(msg);
        String kindDetail = "gift".equals(type) ? kind + "：" + msg.optString("gift") : kind;
        boolean handled = !msg.optString("txStatus", "").isEmpty() || msg.optBoolean("handled", false);
        if (mine || handled) {
            Dialogs.notice(a, a.store, "🧧", kindDetail + " ¥" + Ui.fmtMoney(amount),
                    (mine ? "我发出的" : a.store.displayName() + "发来的") + " · " + txStatusLabel(msg));
            return;
        }
        String amountLabel = "red".equals(type) && !msg.has("gift") ? "待揭晓" : "¥" + Ui.fmtMoney(amount);
        Dialogs.choice(a, a.store, "🧧", kindDetail + " " + amountLabel,
                "接收后将计入你的余额", "退还", "接收",
                () -> {
                    a.logic.finishTransaction(msg, "returned");
                    a.logic.addSys("我已退还" + kind);
                },
                () -> {
                    a.logic.finishTransaction(msg, "accepted");
                    a.logic.addSys("我已接收" + kind);
                    String key = "zhuan".equals(type) ? "zhuan" : msg.has("gift") ? "gift" : "red";
                    JSONObject recv = a.store.data.optJSONObject("recv");
                    JSONObject entry = recv != null ? recv.optJSONObject(key) : null;
                    JSONArray pool = entry != null ? entry.optJSONArray("his") : null;
                    if (pool != null && pool.length() > 0) {
                        Dialogs.prompt(a, a.store, "💬", "回复" + a.store.displayName(), "",
                                "写一句回复…", pool.optString(0), value -> {
                                    a.logic.addText("me", value);
                                    a.logic.scheduleReply();
                                });
                    }
                });
    }

    public void jumpToMessage(String ref) {
        JSONArray chat = a.store.chat();
        int childIndex = 0;
        for (int i = 0; i < chat.length(); i++) {
            JSONObject msg = chat.optJSONObject(i);
            if (msg == null) continue;
            if (ref.equals(msg.optString("id"))) {
                final int index = childIndex;
                chatScroll.post(() -> {
                    if (index < chatArea.getChildCount()) {
                        chatScroll.smoothScrollTo(0, chatArea.getChildAt(index).getTop());
                    }
                });
                return;
            }
            childIndex++;
        }
    }

    /* ---------- 拍一拍 ---------- */

    private void onAvatarTap(String side, String key) {
        long now = System.currentTimeMillis();
        String tapKey = side + ":" + key;
        if (tapKey.equals(lastAvatarKey) && now - lastAvatarTap < 360) {
            lastAvatarKey = "";
            JSONObject role = a.store.role();
            JSONArray pokes = role != null ? role.optJSONArray("pokes") : null;
            String poke = pokes != null && pokes.length() > 0 ? pokes.optString(0) : "拍了拍他的头";
            if ("me".equals(side)) {
                a.logic.addSys("你" + poke.replace("他", "自己"));
            } else {
                a.logic.addSys("你" + poke.replace("他", a.store.displayName()));
                a.logic.scheduleReply();
            }
        } else {
            lastAvatarKey = tapKey;
            lastAvatarTap = now;
        }
    }

    /* ---------- 发送 ---------- */

    private void sendUser() {
        String raw = textInput.getText().toString();
        if (raw.trim().isEmpty()) return;
        try {
            JSONObject msg = new JSONObject().put("text", raw.trim());
            if (quoteTarget != null) {
                msg.put("quote", quoteTarget).put("quoteRef", quoteRef);
            }
            clearQuote();
            a.logic.addMsg("me", msg);
        } catch (JSONException ignored) {
        }
        textInput.setText("");
        a.logic.scheduleReply();
    }

    private void clearQuote() {
        quoteTarget = null;
        quoteRef = "";
        quoteBar.setVisibility(View.GONE);
    }

    private void setQuote(String text, String ref) {
        quoteTarget = text;
        quoteRef = ref;
        quoteText.setText("引用：" + text);
        quoteBar.setVisibility(View.VISIBLE);
    }

    /* ---------- 面板 ---------- */

    private String openPanel = "";

    private void togglePanel(String which) {
        if (which.equals(openPanel)) {
            panelBox.setVisibility(View.GONE);
            openPanel = "";
            return;
        }
        openPanel = which;
        panelBox.removeAllViews();
        panelBox.setVisibility(View.VISIBLE);
        if ("emoji".equals(which)) buildEmojiPanel();
        else buildPlusPanel();
    }

    private void closePanel() {
        panelBox.setVisibility(View.GONE);
        openPanel = "";
    }

    private void buildEmojiPanel() {
        ScrollView scroll = new ScrollView(a);
        LinearLayout box = Ui.column(a);
        box.setPadding(Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 10));

        JSONArray emoji = a.store.data.optJSONArray("emoji");
        List<Integer> commonEmoji = commonEmojiIndexes(emoji);
        if (!commonEmoji.isEmpty()) {
            box.addView(Ui.boldText(a, "⭐ 常用表情", 13, Ui.mutedInk(a, a.store)));
            GridLayout freqGrid = new GridLayout(a);
            freqGrid.setColumnCount(7);
            for (int index : commonEmoji) {
                freqGrid.addView(emojiCell(emoji.optString(index), index));
            }
            box.addView(freqGrid);
        }
        box.addView(Ui.boldText(a, "Emoji", 13, Ui.mutedInk(a, a.store)));
        GridLayout emojiGrid = new GridLayout(a);
        emojiGrid.setColumnCount(7);
        for (int i = 0; emoji != null && i < emoji.length(); i++) {
            emojiGrid.addView(emojiCell(emoji.optString(i), i));
        }
        box.addView(emojiGrid);

        JSONArray stickers = a.store.data.optJSONArray("stickers");
        List<Integer> commonStickers = commonStickerIndexes(stickers);
        if (!commonStickers.isEmpty()) {
            box.addView(Ui.boldText(a, "⭐ 常用", 13, Ui.mutedInk(a, a.store)));
            GridLayout freqStickerGrid = new GridLayout(a);
            freqStickerGrid.setColumnCount(4);
            for (int index : commonStickers) {
                View cell = stickerCell(stickers.optString(index), index);
                if (cell != null) freqStickerGrid.addView(cell);
            }
            box.addView(freqStickerGrid);
        }
        box.addView(Ui.boldText(a, "表情包", 13, Ui.mutedInk(a, a.store)));
        GridLayout stickerGrid = new GridLayout(a);
        stickerGrid.setColumnCount(4);
        for (int i = 0; stickers != null && i < stickers.length(); i++) {
            View cell = stickerCell(stickers.optString(i), i);
            if (cell != null) stickerGrid.addView(cell);
        }
        if (stickers == null || stickers.length() == 0) {
            box.addView(hint("暂无表情包，去“字卡 → 表情”添加"));
        }
        box.addView(stickerGrid);
        scroll.addView(box);
        panelBox.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private TextView emojiCell(String value, int index) {
        TextView cell = Ui.text(a, value, 20, Ui.ink(a, a.store));
        cell.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));
        cell.setOnClickListener(v -> {
            textInput.append(value);
            JSONObject freq = a.store.data.optJSONObject("freq");
            if (freq != null) {
                try {
                    freq.put("e" + index, freq.optInt("e" + index) + 1);
                    a.store.save();
                } catch (JSONException ignored) {
                }
            }
        });
        return cell;
    }

    private ImageView stickerCell(String ref, int index) {
        android.graphics.Bitmap bitmap = Ui.decodeDataUrl(a.store.resolveMedia(ref));
        if (bitmap == null) return null;
        ImageView image = new ImageView(a);
        image.setImageBitmap(bitmap);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = Ui.dp(a, 64);
        lp.height = Ui.dp(a, 64);
        lp.setMargins(Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6));
        image.setLayoutParams(lp);
        image.setOnClickListener(v -> {
            try {
                a.logic.addMsg("me", new JSONObject().put("type", "img").put("src", ref));
                JSONObject stFreq = a.store.data.optJSONObject("stFreq");
                if (stFreq != null) stFreq.put(String.valueOf(index), stFreq.optInt(String.valueOf(index)) + 1);
                JSONObject freq = a.store.data.optJSONObject("freq");
                if (freq != null) freq.put("st" + index, freq.optInt("st" + index) + 1);
                a.store.save();
                closePanel();
                a.logic.scheduleReply();
            } catch (JSONException ignored) {
            }
        });
        return image;
    }

    /** 常用 Emoji：按 freq[e+i] 降序取前 8，等价于旧版 renderEmojiPanel() */
    private List<Integer> commonEmojiIndexes(JSONArray emoji) {
        List<Integer> result = new ArrayList<>();
        JSONObject freq = a.store.data.optJSONObject("freq");
        if (freq == null || emoji == null) return result;
        List<int[]> counted = new ArrayList<>();
        for (int i = 0; i < emoji.length(); i++) {
            int count = freq.optInt("e" + i, 0);
            if (count > 0) counted.add(new int[]{i, count});
        }
        counted.sort((x, y) -> y[1] - x[1]);
        for (int i = 0; i < Math.min(8, counted.size()); i++) result.add(counted.get(i)[0]);
        return result;
    }

    /** 常用表情包：按 freq[st+i] + stFreq[i] 降序取前 6，等价于旧版 renderStickers() */
    private List<Integer> commonStickerIndexes(JSONArray stickers) {
        List<Integer> result = new ArrayList<>();
        if (stickers == null) return result;
        JSONObject freq = a.store.data.optJSONObject("freq");
        JSONObject stFreq = a.store.data.optJSONObject("stFreq");
        List<int[]> counted = new ArrayList<>();
        for (int i = 0; i < stickers.length(); i++) {
            int count = (freq != null ? freq.optInt("st" + i, 0) : 0)
                    + (stFreq != null ? stFreq.optInt(String.valueOf(i), 0) : 0);
            if (count > 0) counted.add(new int[]{i, count});
        }
        counted.sort((x, y) -> y[1] - x[1]);
        for (int i = 0; i < Math.min(6, counted.size()); i++) result.add(counted.get(i)[0]);
        return result;
    }

    private void buildPlusPanel() {
        GridLayout grid = new GridLayout(a);
        grid.setColumnCount(4);
        grid.setPadding(Ui.dp(a, 10), Ui.dp(a, 14), Ui.dp(a, 10), Ui.dp(a, 10));
        String[][] items = {
                {"🖼", "图片"}, {"🧧", "红包"}, {"💸", "转账"}, {"🎁", "礼物"},
                {"📍", "位置"}, {"👋", "拍一拍"}, {"📹", "视频通话"}, {"📞", "他来电"},
        };
        for (String[] item : items) {
            final String label = item[1];
            LinearLayout cell = Ui.column(a);
            cell.setGravity(Gravity.CENTER);
            TextView icon = Ui.text(a, item[0], 26, Ui.ink(a, a.store));
            icon.setGravity(Gravity.CENTER);
            int size = Ui.dp(a, 56);
            icon.setBackground(Ui.rounded(Ui.surfaceStrong(a, a.store), Ui.dp(a, 16)));
            icon.setWidth(size);
            icon.setHeight(size);
            TextView name = Ui.text(a, label, 11, Ui.mutedInk(a, a.store));
            name.setGravity(Gravity.CENTER);
            name.setPadding(0, Ui.dp(a, 4), 0, 0);
            cell.addView(icon);
            cell.addView(name);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.setMargins(Ui.dp(a, 4), Ui.dp(a, 8), Ui.dp(a, 4), Ui.dp(a, 8));
            cell.setLayoutParams(lp);
            cell.setOnClickListener(v -> {
                closePanel();
                onPlusAction(label);
            });
            grid.addView(cell);
        }
        panelBox.addView(grid);
    }

    private void onPlusAction(String label) {
        switch (label) {
            case "图片":
                a.pickFile("image/*", (bytes, mime, name) -> {
                    String ref = a.store.importImage(bytes, mime);
                    if (ref.isEmpty()) {
                        a.toast("图片导入失败");
                        return;
                    }
                    try {
                        a.logic.addMsg("me", new JSONObject().put("type", "img").put("src", ref));
                        a.logic.scheduleReply();
                    } catch (JSONException ignored) {
                    }
                });
                break;
            case "红包":
                sendTxDialog("red", "发红包");
                break;
            case "转账":
                sendTxDialog("zhuan", "转账");
                break;
            case "礼物":
                a.goPage("pageShop", true);
                break;
            case "位置":
                Dialogs.prompt(a, a.store, "📍", "发送位置", "位置名称", "例如：家里", "", value -> {
                    try {
                        a.logic.addMsg("me", new JSONObject().put("type", "loc").put("text", value));
                        a.logic.scheduleReply();
                    } catch (JSONException ignored) {
                    }
                });
                break;
            case "拍一拍": {
                JSONObject role = a.store.role();
                JSONArray pokes = role != null ? role.optJSONArray("pokes") : null;
                String poke = pokes != null && pokes.length() > 0
                        ? pokes.optString(a.store.rand(0, pokes.length() - 1)) : "拍了拍他的头";
                a.logic.addSys("你" + poke.replace("他", a.store.displayName()));
                a.logic.scheduleReply();
                break;
            }
            case "视频通话":
                a.videoOverlay.startVideo();
                break;
            case "他来电":
                a.videoOverlay.incomingCall();
                break;
        }
    }

    private void sendTxDialog(String type, String title) {
        List<Dialogs.Field> fields = new ArrayList<>();
        Dialogs.Field amount = new Dialogs.Field("amount", "金额");
        amount.number = true;
        amount.placeholder = "0.00";
        fields.add(amount);
        Dialogs.Field note = new Dialogs.Field("note", "留言（可选）");
        note.placeholder = "red".equals(type) ? "恭喜发财" : "转账给你";
        fields.add(note);
        Dialogs.form(a, a.store, "red".equals(type) ? "🧧" : "💸", title, null, "发送", fields, values -> {
            double value;
            try {
                value = Double.parseDouble(values.get("amount"));
            } catch (Exception e) {
                a.toast("金额无效");
                return;
            }
            if (value <= 0) {
                a.toast("金额无效");
                return;
            }
            try {
                JSONObject role = a.store.role();
                JSONObject wallet = role.getJSONObject("wallet");
                double mine = wallet.optDouble("mine", 0);
                if (value > mine) {
                    a.toast("余额不足");
                    return;
                }
                commitTx(type, value, values.get("note"));
            } catch (JSONException ignored) {
            }
        });
    }

    private void commitTx(String type, double amount, String note) {
        try {
            JSONObject role = a.store.role();
            JSONObject wallet = role.getJSONObject("wallet");
            wallet.put("mine", wallet.optDouble("mine", 0) - amount);
            JSONObject msg = new JSONObject().put("type", type).put("amount", amount)
                    .put("txVersion", 2).put("txStatus", "");
            if (note != null && !note.trim().isEmpty()) msg.put("note", note.trim());
            JSONObject sent = a.logic.addMsg("me", msg);
            a.store.save();
            a.onWalletChanged();
            a.logic.scheduleHisDecision(sent);
        } catch (JSONException ignored) {
        }
    }

    /* ---------- 长按消息菜单 ---------- */

    private void openMessageMenu(int index, View anchor) {
        JSONObject msg = a.store.chat().optJSONObject(index);
        if (msg == null || selecting) return;
        String text = messageText(msg);
        boolean mine = "me".equals(msg.optString("side"));

        LinearLayout menu = Ui.column(a);
        menu.setBackground(Ui.rounded(Ui.surfaceStrong(a, a.store), Ui.dp(a, 14)));
        menu.setPadding(Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6));
        menu.setElevation(Ui.dp(a, 8));
        GridLayout grid = new GridLayout(a);
        grid.setColumnCount(4);
        menu.addView(grid);

        PopupWindow popup = new PopupWindow(menu,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);

        List<String[]> actions = new ArrayList<>();
        if (!text.isEmpty()) actions.add(new String[]{"copy", "复制"});
        if (!text.isEmpty()) actions.add(new String[]{"forward", "转发"});
        actions.add(new String[]{"favorite", msg.optBoolean("favorite", false) ? "取消收藏" : "收藏"});
        if (mine && !msg.optBoolean("recall", false)) actions.add(new String[]{"recall", "撤回"});
        actions.add(new String[]{"delete", "删除"});
        actions.add(new String[]{"multi", "多选"});
        if (!msg.optBoolean("recall", false) && !"sys".equals(msg.optString("type"))) {
            actions.add(new String[]{"quote", "引用"});
        }
        actions.add(new String[]{"remind", "提醒"});
        if (!mine) actions.add(new String[]{"translate", "翻译"});
        if (!text.isEmpty() && !text.startsWith("[")) actions.add(new String[]{"search", "搜一搜"});
        if (!text.isEmpty()) actions.add(new String[]{"read", "连续朗读"});

        for (String[] action : actions) {
            final String id = action[0];
            TextView item = Ui.text(a, action[1], 12,
                    "delete".equals(id) ? a.getColor(R.color.danger) : Ui.ink(a, a.store));
            item.setGravity(Gravity.CENTER);
            item.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 10));
            item.setOnClickListener(v -> {
                popup.dismiss();
                runMessageAction(id, index, msg);
            });
            grid.addView(item);
        }
        popup.setElevation(Ui.dp(a, 8));
        popup.showAsDropDown(anchor, 0, -anchor.getHeight() - Ui.dp(a, 90));
    }

    private String messageText(JSONObject msg) {
        if (msg == null) return "";
        if (msg.optBoolean("recall", false)) return "已撤回的消息";
        String type = msg.optString("type", "");
        switch (type) {
            case "img": return "[图片]";
            case "loc": return "[位置] " + msg.optString("text");
            case "red": return "[红包] ¥" + Ui.fmtMoney(msg.optDouble("amount", 0));
            case "zhuan": return "[转账] ¥" + Ui.fmtMoney(msg.optDouble("amount", 0));
            case "gift": return "[礼物] " + msg.optString("gift");
            default: return msg.optString("text", "");
        }
    }

    private void runMessageAction(String id, int index, JSONObject msg) {
        String text = messageText(msg);
        switch (id) {
            case "copy":
                a.copyText(text);
                break;
            case "forward":
                a.shareText(text);
                break;
            case "favorite":
                try {
                    msg.put("favorite", !msg.optBoolean("favorite", false));
                    a.store.save();
                    a.toast(msg.optBoolean("favorite") ? "已收藏这条消息" : "已取消收藏");
                } catch (JSONException ignored) {
                }
                break;
            case "recall":
                a.logic.recall(index);
                break;
            case "delete":
                Dialogs.confirm(a, a.store, "⌫", "删除这条消息？", "删除后无法恢复", "删除", true, () -> {
                    a.store.chat().remove(index);
                    a.store.save();
                    renderChat(false);
                });
                break;
            case "multi":
                enterSelection(index);
                break;
            case "quote":
                setQuote(text, msg.optString("id", ""));
                break;
            case "remind":
                remindDialog(msg, text);
                break;
            case "translate":
                if (text.isEmpty() || text.startsWith("[")) {
                    Dialogs.notice(a, a.store, "译", "这类消息无法翻译", "请选择一条文字消息。");
                } else {
                    Dialogs.confirm(a, a.store, "译", "使用在线翻译？", "将在系统浏览器中打开翻译服务",
                            "继续", false, () -> {
                                android.content.Intent intent = new android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://translate.google.com/?sl=auto&tl=zh-CN&text="
                                                + android.net.Uri.encode(text) + "&op=translate"));
                                try {
                                    a.startActivity(intent);
                                } catch (Exception e) {
                                    a.toast("无法打开浏览器");
                                }
                            });
                }
                break;
            case "search": {
                Page search = a.page("pageSearch");
                if (search instanceof SearchPage) {
                    ((SearchPage) search).presetKeyword(text.replaceFirst("^\\[[^\\]]+\\]\\s*", "").trim());
                }
                a.goPage("pageSearch", true);
                break;
            }
            case "read":
                readAloudFrom(index);
                break;
        }
    }

    private void remindDialog(JSONObject msg, String text) {
        Dialogs.Field minutes = new Dialogs.Field("minutes", "提醒时间");
        minutes.optionValues = new String[]{"10", "30", "60", "1440"};
        minutes.optionLabels = new String[]{"10 分钟后", "30 分钟后", "1 小时后", "明天此时"};
        minutes.value = "30";
        Dialogs.form(a, a.store, "⏱", "提醒我", text.length() > 46 ? text.substring(0, 46) : text,
                "设定提醒", Dialogs.fields(minutes), values -> {
                    try {
                        JSONArray reminders = a.store.data.optJSONArray("messageReminders");
                        JSONObject reminder = new JSONObject()
                                .put("id", Store.uid("reminder"))
                                .put("roleId", a.store.data.optString("activeRole"))
                                .put("text", text.isEmpty() ? "查看这条消息" : text)
                                .put("at", System.currentTimeMillis()
                                        + Long.parseLong(values.getOrDefault("minutes", "30")) * 60000L)
                                .put("done", false);
                        reminders.put(reminder);
                        a.store.save();
                        a.logic.scheduleReminder(reminder);
                        a.toast("提醒已设定");
                    } catch (Exception ignored) {
                    }
                });
    }

    private void readAloudFrom(int index) {
        JSONArray chat = a.store.chat();
        List<String> lines = new ArrayList<>();
        for (int i = index; i < chat.length() && lines.size() < 20; i++) {
            JSONObject msg = chat.optJSONObject(i);
            if (msg == null || msg.optBoolean("recall", false)) continue;
            String text = messageText(msg);
            if (!text.isEmpty()) lines.add(text);
        }
        if (lines.isEmpty()) return;
        String joined = String.join("。", lines);
        if (tts == null) {
            tts = new TextToSpeech(a, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
                    tts.setSpeechRate(0.96f);
                    tts.speak(joined, TextToSpeech.QUEUE_FLUSH, null, "read");
                    a.toast("开始连续朗读");
                } else {
                    a.toast("当前设备不支持朗读");
                }
            });
        } else {
            tts.stop();
            tts.speak(joined, TextToSpeech.QUEUE_FLUSH, null, "read");
            a.toast("开始连续朗读");
        }
    }

    public void releaseTts() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }

    /* ---------- 多选 ---------- */

    private void enterSelection(int index) {
        selecting = true;
        selected.clear();
        selected.add(index);
        multiBar.setVisibility(View.VISIBLE);
        syncSelection();
    }

    private void exitSelection() {
        selecting = false;
        selected.clear();
        multiBar.setVisibility(View.GONE);
        renderChat(false);
    }

    private void toggleSelected(int index) {
        if (selected.contains(index)) selected.remove(index);
        else selected.add(index);
        syncSelection();
    }

    private void syncSelection() {
        multiCount.setText("已选 " + selected.size() + " 条");
        renderChat(false);
    }

    private void forwardSelected() {
        StringBuilder sb = new StringBuilder();
        for (int index : selected) {
            String text = messageText(a.store.chat().optJSONObject(index));
            if (!text.isEmpty()) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(text);
            }
        }
        if (sb.length() == 0) return;
        a.shareText(sb.toString());
        exitSelection();
    }

    private void deleteSelected() {
        if (selected.isEmpty()) return;
        int count = selected.size();
        Dialogs.confirm(a, a.store, "⌫", "删除选中的消息？", "共 " + count + " 条，删除后无法恢复",
                "全部删除", true, () -> {
                    List<Integer> indexes = new ArrayList<>(selected);
                    Collections.sort(indexes, Collections.reverseOrder());
                    for (int index : indexes) a.store.chat().remove(index);
                    a.store.save();
                    exitSelection();
                });
    }

    @Override
    public boolean handleBack() {
        if (selecting) {
            exitSelection();
            return true;
        }
        if (panelBox != null && panelBox.getVisibility() == View.VISIBLE) {
            closePanel();
            return true;
        }
        return false;
    }
}

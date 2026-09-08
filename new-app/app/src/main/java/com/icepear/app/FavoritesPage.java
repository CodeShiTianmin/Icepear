package com.icepear.app;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 珍藏时刻（对应浏览器补丁 pageMemories）：
 * 多选珍藏的整段对话（role.memories）+ 单条收藏的消息（msg.favorite）。
 * 支持正倒序、折叠、删除。
 */
public class FavoritesPage extends Page {

    private LinearLayout content;
    private boolean newestFirst = true;

    public FavoritesPage(MainActivity activity) {
        super(activity);
    }

    @Override
    protected View create() {
        content = Ui.column(a);
        return pageWithBar("珍藏时刻", content);
    }

    @Override
    public void refresh() {
        if (content == null) return;
        content.removeAllViews();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);

        JSONArray memories = a.store.memories();
        LinearLayout head = Ui.row(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 8));
        head.addView(SvgIcon.view(a, Icons.HEART, Ui.plum(a, a.store), 18));
        TextView title = Ui.boldText(a, "珍藏时刻 · " + memories.length(), 15, Ui.ink(a, a.store));
        title.setPadding(Ui.dp(a, 6), 0, 0, 0);
        head.addView(title, Ui.weighted());
        TextView order = Ui.boldText(a, newestFirst ? "最新在前" : "最早在前", 12, Ui.plum(a, a.store));
        order.setOnClickListener(v -> {
            newestFirst = !newestFirst;
            refresh();
        });
        head.addView(order);
        content.addView(head);

        if (memories.length() == 0) {
            content.addView(hint("长按聊天消息 → 多选 → 点「珍藏」，整段对话会保存在这里。"));
        }
        for (int n = 0; n < memories.length(); n++) {
            int i = newestFirst ? memories.length() - 1 - n : n;
            final int index = i;
            JSONObject mem = memories.optJSONObject(i);
            if (mem == null) continue;
            JSONArray msgs = mem.optJSONArray("msgs");
            LinearLayout box = card(null);
            LinearLayout top = Ui.row(a);
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(Ui.text(a, fmt.format(new Date(mem.optLong("t"))) + " · " + (msgs != null ? msgs.length() : 0) + " 条",
                    12, Ui.mutedInk(a, a.store)), Ui.weighted());
            View trash = SvgIcon.view(a, Icons.TRASH, a.getColor(R.color.danger), 18);
            trash.setPadding(Ui.dp(a, 8), Ui.dp(a, 4), 0, Ui.dp(a, 4));
            trash.setOnClickListener(v -> Dialogs.confirm(a, a.store, "🗑", "删除这条珍藏？", "删除后无法恢复",
                    "删除", true, () -> {
                        memories.remove(index);
                        a.store.save();
                        refresh();
                    }));
            top.addView(trash);
            box.addView(top);
            for (int k = 0; msgs != null && k < msgs.length(); k++) {
                JSONObject m = msgs.optJSONObject(k);
                if (m == null) continue;
                boolean me = "me".equals(m.optString("side"));
                LinearLayout line = Ui.row(a);
                line.setGravity(me ? Gravity.END : Gravity.START);
                line.setPadding(0, Ui.dp(a, 3), 0, Ui.dp(a, 3));
                TextView bubble = Ui.text(a, describe(m), 13, me ? Color.WHITE : Ui.ink(a, a.store));
                bubble.setBackground(Ui.rounded(me ? Ui.plum(a, a.store) : Ui.surfaceStrong(a, a.store), Ui.dp(a, 12)));
                bubble.setPadding(Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 6));
                bubble.setMaxWidth(Ui.dp(a, 240));
                line.addView(bubble);
                box.addView(line);
            }
            content.addView(box);
        }

        /* 单条收藏 */
        JSONArray chat = a.store.chat();
        int count = 0;
        for (int i = 0; i < chat.length(); i++) {
            final JSONObject msg = chat.optJSONObject(i);
            if (msg == null || !msg.optBoolean("favorite", false) || msg.optBoolean("recall", false)) continue;
            if (count == 0) {
                TextView sub = Ui.boldText(a, "单条收藏", 14, Ui.ink(a, a.store));
                sub.setPadding(Ui.dp(a, 4), Ui.dp(a, 14), 0, Ui.dp(a, 6));
                content.addView(sub);
            }
            count++;
            LinearLayout row = card(null);
            LinearLayout who = Ui.row(a);
            who.setGravity(Gravity.CENTER_VERTICAL);
            who.addView(SvgIcon.view(a, Icons.STAR, Ui.plum(a, a.store), 14));
            TextView whoText = Ui.boldText(a, "me".equals(msg.optString("side")) ? "我" : a.store.displayName(), 12, Ui.plum(a, a.store));
            whoText.setPadding(Ui.dp(a, 4), 0, 0, 0);
            who.addView(whoText);
            row.addView(who);
            TextView body = Ui.text(a, describe(msg), 14, Ui.ink(a, a.store));
            body.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 4));
            row.addView(body);
            LinearLayout actions = Ui.row(a);
            TextView time = Ui.text(a, fmt.format(new Date(msg.optLong("t"))), 11, Ui.faintInk(a, a.store));
            actions.addView(time, Ui.weighted());
            TextView jump = Ui.boldText(a, "定位到聊天", 12, Ui.plum(a, a.store));
            final String id = msg.optString("id", "");
            jump.setOnClickListener(v -> {
                a.goPage("pageChat", false);
                Page chatPage = a.page("pageChat");
                if (chatPage instanceof ChatPage && !id.isEmpty()) {
                    ((ChatPage) chatPage).jumpToMessage(id);
                }
            });
            actions.addView(jump);
            TextView unfav = Ui.boldText(a, "取消收藏", 12, a.getColor(R.color.danger));
            unfav.setPadding(Ui.dp(a, 14), 0, 0, 0);
            unfav.setOnClickListener(v -> {
                try {
                    msg.put("favorite", false);
                    a.store.save();
                    refresh();
                    a.toast("已取消收藏");
                } catch (JSONException ignored) {
                }
            });
            actions.addView(unfav);
            row.addView(actions);
            content.addView(row);
        }
    }

    private String describe(JSONObject msg) {
        switch (msg.optString("type", "")) {
            case "img": return "[图片]";
            case "loc": return "[位置] " + msg.optString("text");
            case "red": return "[红包] ¥" + Ui.fmtMoney(ChatLogic.txAmount(msg));
            case "zhuan": return "[转账] ¥" + Ui.fmtMoney(ChatLogic.txAmount(msg));
            case "gift": return "[礼物] " + msg.optString("gift");
            case "sys": return "[系统] " + msg.optString("text");
            default: return msg.optString("text", "");
        }
    }
}

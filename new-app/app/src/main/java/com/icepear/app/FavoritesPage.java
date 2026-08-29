package com.icepear.app;

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
 * 收藏消息汇总页：列出当前角色所有收藏的消息，可跳转到聊天位置或取消收藏。
 */
public class FavoritesPage extends Page {

    private LinearLayout content;

    public FavoritesPage(MainActivity activity) {
        super(activity);
    }

    @Override
    protected View create() {
        content = Ui.column(a);
        return pageWithBar("收藏", content);
    }

    @Override
    public void refresh() {
        if (content == null) return;
        content.removeAllViews();
        JSONArray chat = a.store.chat();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
        int count = 0;
        for (int i = 0; i < chat.length(); i++) {
            final JSONObject msg = chat.optJSONObject(i);
            if (msg == null || !msg.optBoolean("favorite", false) || msg.optBoolean("recall", false)) continue;
            count++;
            LinearLayout row = card(null);
            String who = "me".equals(msg.optString("side")) ? "我" : a.store.displayName();
            row.addView(Ui.boldText(a, "⭐ " + who, 12, Ui.plum(a, a.store)));
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
        if (count == 0) {
            content.addView(hint("长按聊天中的消息选择「收藏」，收藏的消息会显示在这里。"));
        }
    }

    private String describe(JSONObject msg) {
        switch (msg.optString("type", "")) {
            case "img": return "[图片]";
            case "loc": return "[位置] " + msg.optString("text");
            case "red": return "[红包] ¥" + Ui.fmtMoney(msg.optDouble("amount", 0));
            case "zhuan": return "[转账] ¥" + Ui.fmtMoney(msg.optDouble("amount", 0));
            case "gift": return "[礼物] " + msg.optString("gift");
            case "sys": return "[系统] " + msg.optString("text");
            default: return msg.optString("text", "");
        }
    }
}

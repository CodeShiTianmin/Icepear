package com.icepear.app;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

/**
 * 互动周报：统计近 7 天消息数、我发/他发、红包转账礼物金额、
 * 最活跃时段与关键词摘要。
 */
public class WeeklyPage extends Page {

    private LinearLayout content;

    public WeeklyPage(MainActivity activity) {
        super(activity);
    }

    @Override
    protected View create() {
        content = Ui.column(a);
        return pageWithBar("互动周报", content);
    }

    @Override
    public void refresh() {
        if (content == null) return;
        content.removeAllViews();
        JSONArray chat = a.store.chat();
        long weekAgo = System.currentTimeMillis() - 7L * 86400000L;
        int total = 0, mine = 0, his = 0;
        double redAmount = 0;
        int redCount = 0;
        int[] hourBuckets = new int[24];
        StringBuilder textAll = new StringBuilder();
        for (int i = 0; i < chat.length(); i++) {
            JSONObject msg = chat.optJSONObject(i);
            if (msg == null || msg.optLong("t") < weekAgo) continue;
            String type = msg.optString("type", "");
            if ("sys".equals(type)) continue;
            total++;
            if ("me".equals(msg.optString("side"))) mine++;
            else his++;
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(msg.optLong("t"));
            hourBuckets[cal.get(Calendar.HOUR_OF_DAY)]++;
            if ("red".equals(type) || "zhuan".equals(type) || "gift".equals(type)) {
                redAmount += msg.optDouble("amount", msg.optDouble("price", 0));
                redCount++;
            } else {
                textAll.append(msg.optString("text", "")).append(' ');
            }
        }
        int bestHour = 0;
        for (int i = 1; i < 24; i++) if (hourBuckets[i] > hourBuckets[bestHour]) bestHour = i;

        LinearLayout headCard = card(null);
        headCard.setBackground(Ui.gradient(Ui.plum(a, a.store), 0xFFE77D73, Ui.dp(a, 20)));
        headCard.addView(Ui.text(a, "近 7 天", 12, 0xCCFFFFFF));
        headCard.addView(Ui.boldText(a, total + " 条消息", 24, 0xFFFFFFFF));
        headCard.addView(Ui.text(a, "我发了 " + mine + " 条 · 他发了 " + his + " 条", 12, 0xCCFFFFFF));
        content.addView(headCard);

        LinearLayout stats = card("互动数据");
        stats.addView(statRow("红包/转账/礼物", redCount + " 次，共 ¥" + Ui.fmtMoney(redAmount)));
        stats.addView(statRow("最活跃时段", total > 0 ? Ui.pad2(bestHour) + ":00 - " + Ui.pad2((bestHour + 1) % 24) + ":00" : "暂无"));
        stats.addView(statRow("平均每天", total > 0 ? String.valueOf(Math.round(total / 7.0 * 10) / 10.0) + " 条" : "0 条"));
        content.addView(stats);

        LinearLayout words = card("本周关键词");
        java.util.Map<String, Integer> freq = CloudPage.keywordFrequency(textAll.toString());
        java.util.List<java.util.Map.Entry<String, Integer>> top =
                new java.util.ArrayList<>(freq.entrySet());
        top.sort((x, y) -> y.getValue() - x.getValue());
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < Math.min(10, top.size()); i++) {
            if (line.length() > 0) line.append(" · ");
            line.append(top.get(i).getKey());
        }
        words.addView(Ui.text(a, line.length() > 0 ? line.toString() : "本周还没有聊出关键词",
                13, Ui.ink(a, a.store)));
        content.addView(words);
    }

    private View statRow(String label, String value) {
        LinearLayout row = Ui.row(a);
        row.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
        row.addView(Ui.text(a, label, 13, Ui.mutedInk(a, a.store)), Ui.weighted());
        row.addView(Ui.boldText(a, value, 13, Ui.ink(a, a.store)));
        return row;
    }
}

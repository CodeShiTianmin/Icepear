package com.icepear.app;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 互动周报：近 7 天按「我 / 他」两栏分别统计——
 * 消息数与日均、红包 / 转账 / 礼物次数与金额、最活跃时段、定位、拍一拍、关键词；
 * 视频通话为双方共同数据单独成卡。
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

    /** 单人统计桶 */
    private static class Side {
        int total;
        int redCount, zhuanCount, giftCount;
        double redAmount, zhuanAmount, giftAmount;
        final int[] hours = new int[24];
        int locCount;
        final Map<String, Integer> locs = new HashMap<>();
        int pokeCount;
        final Map<String, Integer> pokes = new HashMap<>();
        final StringBuilder text = new StringBuilder();

        String bestHour() {
            if (total == 0) return "—";
            int best = 0;
            for (int i = 1; i < 24; i++) if (hours[i] > hours[best]) best = i;
            return Ui.pad2(best) + ":00-" + Ui.pad2((best + 1) % 24) + ":00";
        }

        String daily() {
            return total == 0 ? "0 条" : (Math.round(total / 7.0 * 10) / 10.0) + " 条";
        }

        String tx(int count, double amount) {
            return count == 0 ? "0 次" : count + " 次 · ¥" + Ui.fmtMoney(amount);
        }

        static String top(Map<String, Integer> map) {
            String best = null;
            int n = 0;
            for (Map.Entry<String, Integer> e : map.entrySet()) {
                if (e.getValue() > n) {
                    n = e.getValue();
                    best = e.getKey();
                }
            }
            return best;
        }

        String locSummary() {
            if (locCount == 0) return "0 次";
            String best = top(locs);
            return locCount + " 次" + (best == null ? "" : "\n常去：" + best);
        }

        String pokeSummary() {
            if (pokeCount == 0) return "0 次";
            String best = top(pokes);
            return pokeCount + " 次" + (best == null || best.isEmpty() ? "" : "\n常用：" + best);
        }

        String keywords() {
            Map<String, Integer> freq = CloudPage.keywordFrequency(text.toString());
            List<Map.Entry<String, Integer>> topList = new ArrayList<>(freq.entrySet());
            topList.sort((x, y) -> y.getValue() - x.getValue());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(6, topList.size()); i++) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(topList.get(i).getKey());
            }
            return sb.length() == 0 ? "—" : sb.toString();
        }
    }

    @Override
    public void refresh() {
        if (content == null) return;
        content.removeAllViews();
        JSONArray chat = a.store.chat();
        long weekAgo = System.currentTimeMillis() - 7L * 86400000L;
        Side me = new Side(), he = new Side();
        int total = 0;
        for (int i = 0; i < chat.length(); i++) {
            JSONObject msg = chat.optJSONObject(i);
            if (msg == null || msg.optLong("t") < weekAgo || msg.optBoolean("recall", false)) continue;
            String type = msg.optString("type", "");
            if ("sys".equals(type)) {
                String by = msg.optString("pokeBy", "");
                if (by.isEmpty()) continue;
                Side s = "me".equals(by) ? me : he;
                s.pokeCount++;
                s.pokes.merge(msg.optString("poke", ""), 1, Integer::sum);
                continue;
            }
            Side s = "me".equals(msg.optString("side")) ? me : he;
            total++;
            s.total++;
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(msg.optLong("t"));
            s.hours[cal.get(Calendar.HOUR_OF_DAY)]++;
            double amount = msg.optDouble("amount", msg.optDouble("price", 0));
            switch (type) {
                case "red":
                    s.redCount++;
                    s.redAmount += amount;
                    break;
                case "zhuan":
                    s.zhuanCount++;
                    s.zhuanAmount += amount;
                    break;
                case "gift":
                    s.giftCount++;
                    s.giftAmount += amount;
                    break;
                case "loc":
                    s.locCount++;
                    String place = msg.optString("text", msg.optString("name", ""));
                    if (!place.isEmpty()) s.locs.merge(place, 1, Integer::sum);
                    break;
                case "":
                    s.text.append(msg.optString("text", "")).append(' ');
                    break;
                default:
                    break;
            }
        }
        int videoCount = 0;
        int videoSeconds = 0;
        JSONObject role = a.store.role();
        JSONArray videoLog = role == null ? null : role.optJSONArray("videoLog");
        for (int i = 0; videoLog != null && i < videoLog.length(); i++) {
            JSONObject v = videoLog.optJSONObject(i);
            if (v == null || v.optLong("t") < weekAgo) continue;
            videoCount++;
            videoSeconds += v.optInt("duration", 0);
        }

        String hisName = a.store.displayName();
        LinearLayout headCard = card(null);
        headCard.setBackground(Ui.gradient(Ui.plum(a, a.store), 0xFFE77D73, Ui.dp(a, 20)));
        headCard.addView(Ui.text(a, "近 7 天", 12, 0xCCFFFFFF));
        headCard.addView(Ui.boldText(a, total + " 条消息", 24, 0xFFFFFFFF));
        headCard.addView(Ui.text(a, "我发了 " + me.total + " 条 · " + hisName + "发了 " + he.total + " 条", 12, 0xCCFFFFFF));
        content.addView(headCard);

        LinearLayout table = card("双人互动数据");
        table.addView(headerRow("我", hisName));
        table.addView(pairRow("消息条数", me.total + " 条", he.total + " 条"));
        table.addView(pairRow("日均条数", me.daily(), he.daily()));
        table.addView(pairRow("最活跃时段", me.bestHour(), he.bestHour()));
        table.addView(pairRow("红包", me.tx(me.redCount, me.redAmount), he.tx(he.redCount, he.redAmount)));
        table.addView(pairRow("转账", me.tx(me.zhuanCount, me.zhuanAmount), he.tx(he.zhuanCount, he.zhuanAmount)));
        table.addView(pairRow("礼物", me.tx(me.giftCount, me.giftAmount), he.tx(he.giftCount, he.giftAmount)));
        table.addView(pairRow("定位", me.locSummary(), he.locSummary()));
        table.addView(pairRow("拍一拍", me.pokeSummary(), he.pokeSummary()));
        table.addView(pairRow("关键词", me.keywords(), he.keywords()));
        content.addView(table);

        LinearLayout video = card("视频通话");
        video.addView(statRow("通话次数", videoCount + " 次"));
        video.addView(statRow("总时长", videoCount == 0 ? "—" : Ui.fmtDur(videoSeconds)));
        video.addView(statRow("平均时长", videoCount == 0 ? "—" : Ui.fmtDur(videoSeconds / videoCount)));
        content.addView(video);

        double meMoney = me.redAmount + me.zhuanAmount + me.giftAmount;
        double heMoney = he.redAmount + he.zhuanAmount + he.giftAmount;
        LinearLayout money = card("本周金额小结");
        money.addView(statRow("我送出", "¥" + Ui.fmtMoney(meMoney)));
        money.addView(statRow(hisName + "送出", "¥" + Ui.fmtMoney(heMoney)));
        content.addView(money);
    }

    private View headerRow(String left, String right) {
        LinearLayout row = Ui.row(a);
        row.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 8));
        row.addView(Ui.text(a, "", 12, Ui.mutedInk(a, a.store)), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f));
        row.addView(colHead(left));
        row.addView(colHead(right));
        return row;
    }

    private TextView colHead(String label) {
        TextView t = Ui.boldText(a, label, 13, Ui.plum(a, a.store));
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return t;
    }

    private View pairRow(String label, String mine, String his) {
        LinearLayout row = Ui.row(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
        TextView l = Ui.text(a, label, 12, Ui.mutedInk(a, a.store));
        row.addView(l, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f));
        row.addView(cell(mine));
        row.addView(cell(his));
        View line = new View(a);
        line.setBackgroundColor(Ui.line(a, a.store));
        LinearLayout wrap = Ui.column(a);
        wrap.addView(row);
        wrap.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return wrap;
    }

    private TextView cell(String value) {
        TextView t = Ui.boldText(a, value, 12, Ui.ink(a, a.store));
        t.setGravity(Gravity.CENTER);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return t;
    }

    private View statRow(String label, String value) {
        LinearLayout row = Ui.row(a);
        row.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
        row.addView(Ui.text(a, label, 13, Ui.mutedInk(a, a.store)), Ui.weighted());
        row.addView(Ui.boldText(a, value, 13, Ui.ink(a, a.store)));
        return row;
    }
}

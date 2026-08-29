package com.icepear.app;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

/**
 * 他的日常：按日期生成他的天气、位置和一天动态（早中晚各一条），
 * 数据来自天气池、位置池和字卡，同一天内容稳定不变。
 */
public class DailyPage extends Page {

    private LinearLayout content;

    public DailyPage(MainActivity activity) {
        super(activity);
    }

    @Override
    protected View create() {
        content = Ui.column(a);
        return pageWithBar("他的日常", content);
    }

    @Override
    public void refresh() {
        if (content == null) return;
        content.removeAllViews();
        try {
            JSONObject daily = ensureToday();
            LinearLayout headCard = card(null);
            headCard.setBackground(Ui.gradient(Ui.plum(a, a.store), 0xFF9A6B87, Ui.dp(a, 20)));
            headCard.addView(Ui.boldText(a, daily.optString("date"), 13, 0xCCFFFFFF));
            headCard.addView(Ui.boldText(a, daily.optString("weather") + " · " + daily.optString("loc"),
                    20, 0xFFFFFFFF));
            headCard.addView(Ui.text(a, "他今天在" + daily.optString("loc") + "，天气" + daily.optString("weather"),
                    12, 0xCCFFFFFF));
            content.addView(headCard);

            LinearLayout timeline = card("他的一天");
            JSONArray events = daily.optJSONArray("events");
            String[] slots = {"上午", "下午", "晚上"};
            for (int i = 0; events != null && i < events.length(); i++) {
                LinearLayout row = Ui.row(a);
                row.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
                TextView slot = Ui.boldText(a, i < slots.length ? slots[i] : "深夜", 12, Ui.plum(a, a.store));
                slot.setWidth(Ui.dp(a, 48));
                row.addView(slot);
                row.addView(Ui.text(a, events.optString(i), 13, Ui.ink(a, a.store)), Ui.weighted());
                timeline.addView(row);
            }
            content.addView(timeline);
            content.addView(hint("每天的内容会自动更新"));
        } catch (JSONException ignored) {
        }
    }

    private JSONObject ensureToday() throws JSONException {
        Calendar now = Calendar.getInstance();
        String key = now.get(Calendar.YEAR) + "-" + (now.get(Calendar.MONTH) + 1)
                + "-" + now.get(Calendar.DAY_OF_MONTH);
        JSONObject prefs = a.store.data.optJSONObject("icepearUi");
        if (prefs == null) {
            prefs = new JSONObject();
            a.store.data.put("icepearUi", prefs);
        }
        JSONObject daily = prefs.optJSONObject("dailyCache");
        if (daily == null) {
            daily = new JSONObject();
            prefs.put("dailyCache", daily);
        }
        JSONObject today = daily.optJSONObject(key);
        if (today != null) return today;
        JSONArray weatherPool = a.store.data.optJSONArray("weatherPool");
        JSONArray locs = a.store.data.optJSONArray("hisLocs");
        java.util.List<String> cards = a.store.allCards();
        today = new JSONObject();
        today.put("date", key.replace("-", " / "));
        today.put("weather", weatherPool != null && weatherPool.length() > 0
                ? weatherPool.optString(a.store.rand(0, weatherPool.length() - 1)) : "晴");
        today.put("loc", locs != null && locs.length() > 0
                ? locs.optString(a.store.rand(0, locs.length() - 1)) : "家里");
        JSONArray events = new JSONArray();
        String[] fallback = {"想你了", "在忙，也在想你", "等你消息"};
        for (int i = 0; i < 3; i++) {
            events.put(cards.isEmpty() ? fallback[i] : cards.get(a.store.rand(0, cards.size() - 1)));
        }
        today.put("events", events);
        daily.put(key, today);
        a.store.save();
        return today;
    }
}

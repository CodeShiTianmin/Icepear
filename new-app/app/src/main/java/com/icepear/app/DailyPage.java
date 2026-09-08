package com.icepear.app;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 他的日常，对应浏览器补丁 renderDaily()：
 * 今日卡片（头像 + 「XX的日常」+ 日期·emoji + 天气/身体/心情/做了什么/吃了什么/计划 六行），
 * 下方「历史日常」可折叠列表，每天一张卡片、右上角垃圾桶删除（写回 role.dailyHistory 并刷新）。
 * 历史只在用户点删除时移除，不会自动清理。
 */
public class DailyPage extends Page {

    private static final String[][] ROWS = {
            {"weather", "天气", Icons.WX},
            {"body", "身体", Icons.BODY},
            {"mood", "心情", Icons.MOOD},
            {"did", "做了什么", Icons.DID},
            {"ate", "吃了什么", Icons.ATE},
            {"plan", "计划", Icons.PLAN},
    };

    private LinearLayout content;

    public DailyPage(MainActivity activity) {
        super(activity);
    }

    @Override
    protected View create() {
        content = Ui.column(a);
        return pageWithBar("他的日常", content);
    }

    private boolean folded() {
        JSONObject prefs = a.store.data.optJSONObject("icepearUi");
        return prefs == null || prefs.optBoolean("dailyFold", true);
    }

    private void setFolded(boolean fold) {
        try {
            JSONObject prefs = a.store.data.optJSONObject("icepearUi");
            if (prefs == null) {
                prefs = new JSONObject();
                a.store.data.put("icepearUi", prefs);
            }
            prefs.put("dailyFold", fold);
            a.store.save();
        } catch (org.json.JSONException ignored) {
        }
    }

    @Override
    public void refresh() {
        if (content == null) return;
        content.removeAllViews();
        a.store.ensureDailyToday();
        JSONArray history = a.store.dailyHistory();
        String todayKey = Store.dateKey(java.util.Calendar.getInstance());
        JSONObject today = null;
        for (int i = 0; i < history.length(); i++) {
            JSONObject item = history.optJSONObject(i);
            if (item != null && todayKey.equals(item.optString("date"))) {
                today = item;
                break;
            }
        }
        if (today == null) return;

        /* 今日卡片 */
        LinearLayout rpt = card(null);
        LinearLayout head = Ui.row(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.avatar(a, a.store, "other", 44));
        LinearLayout titles = Ui.column(a);
        titles.setPadding(Ui.dp(a, 10), 0, 0, 0);
        titles.addView(Ui.boldText(a, a.store.displayName() + "的日常", 16, Ui.ink(a, a.store)));
        titles.addView(Ui.text(a, new SimpleDateFormat("yyyy/M/d", Locale.CHINA).format(new Date())
                + " · " + today.optString("emoji", "✨"), 12, Ui.mutedInk(a, a.store)));
        head.addView(titles);
        rpt.addView(head);
        for (String[] def : ROWS) {
            LinearLayout row = Ui.row(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(a, 9), 0, Ui.dp(a, 9));
            row.addView(SvgIcon.view(a, def[2], Ui.plum(a, a.store), 18));
            TextView label = Ui.text(a, def[1], 13, Ui.mutedInk(a, a.store));
            label.setPadding(Ui.dp(a, 8), 0, Ui.dp(a, 12), 0);
            row.addView(label);
            TextView value = Ui.text(a, today.optString(def[0], "—"), 14, Ui.ink(a, a.store));
            value.setGravity(Gravity.END);
            row.addView(value, Ui.weighted());
            rpt.addView(row);
            View line = new View(a);
            line.setBackgroundColor(Ui.line(a, a.store));
            rpt.addView(line, new LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
        content.addView(rpt);

        /* 历史日常 */
        int past = 0;
        for (int i = 0; i < history.length(); i++) {
            JSONObject item = history.optJSONObject(i);
            if (item != null && !todayKey.equals(item.optString("date"))) past++;
        }
        if (past == 0) return;
        boolean fold = folded();
        LinearLayout section = card(null);
        LinearLayout foldHead = Ui.row(a);
        foldHead.setGravity(Gravity.CENTER_VERTICAL);
        foldHead.addView(Ui.boldText(a, "历史日常", 14, Ui.ink(a, a.store)), Ui.weighted());
        TextView count = Ui.text(a, past + "天", 12, Ui.mutedInk(a, a.store));
        count.setPadding(0, 0, Ui.dp(a, 6), 0);
        foldHead.addView(count);
        View chevron = SvgIcon.view(a, Icons.CHEVRON, Ui.mutedInk(a, a.store), 18);
        chevron.setRotation(fold ? 0 : 180);
        foldHead.addView(chevron);
        foldHead.setOnClickListener(v -> {
            setFolded(!fold);
            refresh();
        });
        section.addView(foldHead);
        if (!fold) {
            for (int i = history.length() - 1; i >= 0; i--) {
                JSONObject item = history.optJSONObject(i);
                if (item == null || todayKey.equals(item.optString("date"))) continue;
                final String date = item.optString("date");
                LinearLayout box = Ui.column(a);
                box.setBackground(Ui.rounded(Ui.surfaceStrong(a, a.store), Ui.dp(a, 14)));
                box.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 10));
                LinearLayout.LayoutParams boxLp = Ui.lp(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                boxLp.topMargin = Ui.dp(a, 10);
                box.setLayoutParams(boxLp);
                LinearLayout dateRow = Ui.row(a);
                dateRow.setGravity(Gravity.CENTER_VERTICAL);
                dateRow.addView(Ui.boldText(a, date + "  " + item.optString("emoji", "✨"), 13, Ui.ink(a, a.store)), Ui.weighted());
                View trash = SvgIcon.view(a, Icons.TRASH, a.getColor(R.color.danger), 18);
                trash.setPadding(Ui.dp(a, 8), Ui.dp(a, 4), 0, Ui.dp(a, 4));
                trash.setContentDescription("删除这一天");
                trash.setOnClickListener(v -> Dialogs.confirm(a, a.store, "🗑", "删除这一天的日常？", "删除后无法恢复",
                        "删除", true, () -> {
                            JSONArray list = a.store.dailyHistory();
                            for (int k = list.length() - 1; k >= 0; k--) {
                                JSONObject x = list.optJSONObject(k);
                                if (x != null && date.equals(x.optString("date"))) list.remove(k);
                            }
                            a.store.save();
                            refresh();
                        }));
                dateRow.addView(trash);
                box.addView(dateRow);
                for (String[] def : ROWS) {
                    LinearLayout row = Ui.row(a);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 4));
                    row.addView(SvgIcon.view(a, def[2], Ui.mutedInk(a, a.store), 14));
                    TextView label = Ui.text(a, def[1], 12, Ui.mutedInk(a, a.store));
                    label.setPadding(Ui.dp(a, 6), 0, Ui.dp(a, 10), 0);
                    label.setMinWidth(Ui.dp(a, 64));
                    row.addView(label);
                    row.addView(Ui.text(a, item.optString(def[0], "—"), 13, Ui.ink(a, a.store)), Ui.weighted());
                    box.addView(row);
                }
                section.addView(box);
            }
        }
        content.addView(section);
    }
}

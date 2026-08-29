package com.icepear.app;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * 信箱：写信（草稿/寄出）、他的自动回信与主动来信、读信、删信、纪念日。
 */
public class LetterPage extends Page {

    private LinearLayout content;

    public LetterPage(MainActivity activity) {
        super(activity);
    }

    @Override
    protected View create() {
        content = Ui.column(a);
        return pageWithBar("信箱", content);
    }

    @Override
    public void refresh() {
        if (content == null) return;
        content.removeAllViews();
        JSONObject role = a.store.role();
        if (role == null) return;

        content.addView(button("✉ 写一封信", true, this::newLetter));

        LinearLayout listCard = card("往来信件");
        JSONArray letters = role.optJSONArray("letters");
        boolean any = false;
        for (int i = (letters != null ? letters.length() : 0) - 1; i >= 0; i--) {
            JSONObject letter = letters.optJSONObject(i);
            if (letter == null) continue;
            any = true;
            final int index = i;
            LinearLayout row = Ui.row(a);
            row.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
            LinearLayout copy = Ui.column(a);
            boolean mine = letter.optBoolean("mine", true);
            String status = letter.optString("status", "sent");
            String badge = mine ? ("draft".equals(status) ? "草稿" : "已寄出") : "他的来信";
            copy.addView(Ui.boldText(a, letter.optString("title", "无标题"), 14, Ui.ink(a, a.store)));
            copy.addView(Ui.text(a, badge + " · " + letter.optString("date", ""), 11, Ui.faintInk(a, a.store)));
            row.addView(copy, Ui.weighted());
            row.setOnClickListener(v -> openLetter(index));
            row.setOnLongClickListener(v -> {
                deleteLetter(index);
                return true;
            });
            listCard.addView(row);
        }
        if (!any) listCard.addView(hint("还没有信件，写下第一封吧。"));
        listCard.addView(hint("点击查看，长按删除"));
        content.addView(listCard);

        /* 纪念日 */
        LinearLayout memoCard = card("纪念日");
        JSONArray memos = a.store.data.optJSONArray("memos");
        for (int i = 0; memos != null && i < memos.length(); i++) {
            final int index = i;
            JSONObject memo = memos.optJSONObject(i);
            if (memo == null) continue;
            LinearLayout row = Ui.row(a);
            row.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
            LinearLayout copy = Ui.column(a);
            copy.addView(Ui.boldText(a, memo.optString("name"), 14, Ui.ink(a, a.store)));
            copy.addView(Ui.text(a, memoDays(memo), 12, Ui.plum(a, a.store)));
            row.addView(copy, Ui.weighted());
            TextView del = Ui.boldText(a, "删除", 12, a.getColor(R.color.danger));
            del.setOnClickListener(v -> Dialogs.confirm(a, a.store, "⌫", "删除这个纪念日？", null,
                    "删除", true, () -> {
                        memos.remove(index);
                        a.store.save();
                        refresh();
                    }));
            row.addView(del);
            memoCard.addView(row);
        }
        if (memos == null || memos.length() == 0) memoCard.addView(hint("暂无纪念日"));
        content.addView(memoCard);
        content.addView(button("◇ 添加纪念日", false, this::addMemo));
    }

    private String memoDays(JSONObject memo) {
        try {
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            long target = fmt.parse(memo.optString("date")).getTime();
            long days = (System.currentTimeMillis() - target) / 86400000L;
            if ("countdown".equals(memo.optString("type"))) {
                long remain = -days;
                return remain >= 0 ? "还有 " + remain + " 天" : "已过去 " + days + " 天";
            }
            return "已经 " + Math.max(0, days) + " 天";
        } catch (Exception e) {
            return memo.optString("date");
        }
    }

    private void addMemo() {
        Dialogs.Field name = new Dialogs.Field("name", "纪念日名称");
        name.placeholder = "例如：第一次见面";
        Dialogs.Field date = new Dialogs.Field("date", "日期");
        date.date = true;
        Dialogs.Field type = new Dialogs.Field("type", "计时方式");
        type.optionValues = new String[]{"countup", "countdown"};
        type.optionLabels = new String[]{"从这天开始累计", "倒数到这一天"};
        type.value = "countup";
        Dialogs.form(a, a.store, "◇", "添加纪念日", null, "保存", Dialogs.fields(name, date, type), values -> {
            if (values.getOrDefault("name", "").trim().isEmpty()
                    || values.getOrDefault("date", "").trim().isEmpty()) {
                Dialogs.notice(a, a.store, "!", "内容不完整", "请填写名称和日期。");
                return;
            }
            try {
                a.store.data.optJSONArray("memos").put(new JSONObject()
                        .put("name", values.get("name").trim())
                        .put("date", values.get("date").trim())
                        .put("type", values.get("type")).put("bg", ""));
                a.store.save();
                refresh();
            } catch (JSONException ignored) {
            }
        });
    }

    /* ---------- 写信 / 读信 ---------- */

    private void newLetter() {
        Dialogs.Field title = new Dialogs.Field("title", "信的标题");
        title.placeholder = "给这封信起个名字";
        Dialogs.Field body = new Dialogs.Field("content", "信的内容");
        body.textarea = true;
        body.placeholder = "把想说的话写在这里…";
        Dialogs.form(a, a.store, "✉", "写一封信", "可以先保存成草稿，之后再寄出。", "保存草稿",
                Dialogs.fields(title, body), values -> {
                    if (values.getOrDefault("title", "").trim().isEmpty()
                            || values.getOrDefault("content", "").trim().isEmpty()) {
                        Dialogs.notice(a, a.store, "!", "内容不完整", "标题和内容都要填写。");
                        return;
                    }
                    try {
                        a.store.role().getJSONArray("letters").put(new JSONObject()
                                .put("mine", true)
                                .put("title", values.get("title").trim())
                                .put("content", values.get("content").trim())
                                .put("date", new java.text.SimpleDateFormat("yyyy/M/d HH:mm:ss",
                                        java.util.Locale.CHINA).format(new java.util.Date()))
                                .put("status", "draft"));
                        a.store.save();
                        refresh();
                        a.toast("草稿已保存");
                    } catch (JSONException ignored) {
                    }
                });
    }

    private void openLetter(int index) {
        JSONArray letters = a.store.role().optJSONArray("letters");
        JSONObject letter = letters != null ? letters.optJSONObject(index) : null;
        if (letter == null) return;
        boolean mine = letter.optBoolean("mine", true);
        boolean draft = mine && "draft".equals(letter.optString("status"));
        if (draft) {
            Dialogs.confirm(a, a.store, "✉", letter.optString("title"),
                    letter.optString("content"), "寄出这封信", false, () -> sendLetter(index));
        } else {
            Dialogs.notice(a, a.store, "✉", letter.optString("title"),
                    letter.optString("content") + "\n\n— " + (mine ? "我" : a.store.displayName())
                            + " · " + letter.optString("date"));
        }
    }

    private void sendLetter(int index) {
        try {
            JSONArray letters = a.store.role().getJSONArray("letters");
            JSONObject letter = letters.getJSONObject(index);
            letter.put("status", "sent");
            a.store.save();
            refresh();
            a.toast("信已寄出，等他回信吧");
            /* 自动回信：1~3 分钟后他回一封 */
            a.logic.handler().postDelayed(this::hisReplyLetter, a.store.rand(60, 180) * 1000L);
        } catch (JSONException ignored) {
        }
    }

    private void hisReplyLetter() {
        try {
            List<String> pool = a.store.allCards();
            StringBuilder body = new StringBuilder("你的信我认真读完了。\n");
            for (int i = 0; i < Math.min(3, pool.size()); i++) {
                body.append(pool.get(a.store.rand(0, pool.size() - 1))).append('\n');
            }
            body.append("等你回信。");
            a.store.role().getJSONArray("letters").put(new JSONObject()
                    .put("mine", false)
                    .put("title", "给" + a.store.role().optString("myName", "你") + "的回信")
                    .put("content", body.toString())
                    .put("date", new java.text.SimpleDateFormat("yyyy/M/d HH:mm:ss",
                            java.util.Locale.CHINA).format(new java.util.Date()))
                    .put("status", "recv"));
            a.store.save();
            if ("pageLetter".equals(a.currentPage)) refresh();
            a.toast("收到一封他的回信");
        } catch (JSONException ignored) {
        }
    }

    private void deleteLetter(int index) {
        Dialogs.confirm(a, a.store, "⌫", "删除这封信？", "删除后无法恢复", "删除", true, () -> {
            JSONArray letters = a.store.role().optJSONArray("letters");
            if (letters != null) {
                letters.remove(index);
                a.store.save();
                refresh();
            }
        });
    }
}

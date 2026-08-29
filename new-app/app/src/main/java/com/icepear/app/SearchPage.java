package com.icepear.app;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

/**
 * 搜索聊天记录：关键词搜索，结果倒序展示，点击可查看上下文。
 */
public class SearchPage extends Page {

    private EditText input;
    private LinearLayout results;
    private String presetKeyword = "";

    public SearchPage(MainActivity activity) {
        super(activity);
    }

    public void presetKeyword(String keyword) {
        presetKeyword = keyword == null ? "" : keyword;
        if (input != null) {
            input.setText(presetKeyword);
            doSearch();
        }
    }

    @Override
    protected View create() {
        LinearLayout content = Ui.column(a);
        LinearLayout searchRow = Ui.row(a);
        input = Dialogs.makeInput(a, a.store, false);
        input.setHint("输入关键词…");
        searchRow.addView(input, Ui.weighted());
        TextView go = Ui.boldText(a, "搜索", 14, 0xFFFFFFFF);
        go.setBackground(Ui.rounded(Ui.plum(a, a.store), Ui.dp(a, 12)));
        go.setPadding(Ui.dp(a, 16), Ui.dp(a, 9), Ui.dp(a, 16), Ui.dp(a, 9));
        go.setOnClickListener(v -> doSearch());
        LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(a, 8);
        go.setLayoutParams(lp);
        searchRow.addView(go);
        content.addView(searchRow);
        results = Ui.column(a);
        content.addView(results);
        return pageWithBar("搜索聊天", content);
    }

    @Override
    public void refresh() {
        if (!presetKeyword.isEmpty() && input != null) {
            input.setText(presetKeyword);
            presetKeyword = "";
            doSearch();
        }
    }

    private void doSearch() {
        results.removeAllViews();
        String keyword = input.getText().toString().trim();
        if (keyword.isEmpty()) return;
        JSONArray chat = a.store.chat();
        int count = 0;
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA);
        for (int i = chat.length() - 1; i >= 0 && count < 100; i--) {
            JSONObject msg = chat.optJSONObject(i);
            if (msg == null || msg.optBoolean("recall", false)) continue;
            String text = msg.optString("text", "");
            if (!text.contains(keyword)) continue;
            count++;
            LinearLayout row = card(null);
            boolean mine = "me".equals(msg.optString("side"));
            row.addView(Ui.boldText(a, (mine ? "我" : a.store.displayName()) + " · "
                    + fmt.format(new java.util.Date(msg.optLong("t"))), 11, Ui.faintInk(a, a.store)));
            row.addView(Ui.text(a, text, 13, Ui.ink(a, a.store)));
            final int index = i;
            row.setOnClickListener(v -> showContext(index));
            results.addView(row);
        }
        if (count == 0) results.addView(hint("没有找到包含“" + keyword + "”的消息"));
        else results.addView(hint("共 " + count + " 条结果 · 倒序显示 · 点击可查看上下文"), 0);
    }

    private void showContext(int index) {
        JSONArray chat = a.store.chat();
        StringBuilder sb = new StringBuilder();
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA);
        for (int i = Math.max(0, index - 3); i < Math.min(chat.length(), index + 4); i++) {
            JSONObject msg = chat.optJSONObject(i);
            if (msg == null) continue;
            String who = "me".equals(msg.optString("side")) ? "我" : a.store.displayName();
            String text = msg.optBoolean("recall", false) ? "（已撤回）" : msg.optString("text", "[非文字消息]");
            sb.append(i == index ? "» " : "  ")
                    .append(fmt.format(new java.util.Date(msg.optLong("t"))))
                    .append(' ').append(who).append("：").append(text).append('\n');
        }
        Dialogs.notice(a, a.store, "🔍", "消息上下文", sb.toString());
    }
}

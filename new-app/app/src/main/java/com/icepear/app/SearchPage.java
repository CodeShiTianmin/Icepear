package com.icepear.app;

import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 搜索聊天记录：输入即筛选，结果倒序展示；点击结果跳到聊天页原消息并可直接引用；
 * 页面实例常驻，返回时关键词与结果原样保留。
 */
public class SearchPage extends Page {

    private EditText input;
    private LinearLayout results;
    private TextView summary;
    private String presetKeyword = "";
    private String lastKeyword = "";
    private String activeRef = "";

    public SearchPage(MainActivity activity) {
        super(activity);
    }

    public void presetKeyword(String keyword) {
        presetKeyword = keyword == null ? "" : keyword;
        if (input != null) {
            input.setText(presetKeyword);
            input.setSelection(input.getText().length());
            presetKeyword = "";
        }
    }

    @Override
    protected View create() {
        LinearLayout content = Ui.column(a);
        LinearLayout searchRow = Ui.row(a);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        View icon = SvgIcon.view(a, Icons.SEARCH, Ui.mutedInk(a, a.store), 20);
        icon.setPadding(0, 0, Ui.dp(a, 8), 0);
        searchRow.addView(icon);
        input = Dialogs.makeInput(a, a.store, false);
        input.setHint("输入关键词，边打边筛…");
        input.setText(lastKeyword);
        searchRow.addView(input, Ui.weighted());
        TextView clear = Ui.boldText(a, "×", 20, Ui.mutedInk(a, a.store));
        clear.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 4), 0);
        clear.setOnClickListener(v -> input.setText(""));
        searchRow.addView(clear);
        content.addView(searchRow);
        summary = hint("");
        content.addView(summary);
        results = Ui.column(a);
        content.addView(results);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                doSearch(s.toString());
            }
        });
        if (!lastKeyword.isEmpty()) doSearch(lastKeyword);
        return pageWithBar("搜索聊天", content);
    }

    @Override
    public void refresh() {
        if (input == null) return;
        if (!presetKeyword.isEmpty()) {
            input.setText(presetKeyword);
            input.setSelection(input.getText().length());
            presetKeyword = "";
            return;
        }
        // 返回时聊天可能有变化（撤回/删除），按原关键词静默重算但不改输入框
        if (!lastKeyword.isEmpty()) doSearch(lastKeyword);
    }

    private void doSearch(String raw) {
        String keyword = raw == null ? "" : raw.trim();
        lastKeyword = keyword;
        results.removeAllViews();
        if (keyword.isEmpty()) {
            summary.setText("");
            return;
        }
        JSONArray chat = a.store.chat();
        String lower = keyword.toLowerCase();
        int count = 0;
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA);
        for (int i = chat.length() - 1; i >= 0 && count < 200; i--) {
            JSONObject msg = chat.optJSONObject(i);
            if (msg == null || msg.optBoolean("recall", false)) continue;
            String text = msg.optString("text", "");
            if (text.isEmpty() || !text.toLowerCase().contains(lower)) continue;
            count++;
            final String ref = msg.optString("id", "");
            LinearLayout row = card(null);
            boolean sys = "sys".equals(msg.optString("type"));
            boolean mine = "me".equals(msg.optString("side"));
            String who = sys ? "系统" : mine ? "我" : a.store.displayName();
            row.addView(Ui.boldText(a, who + " · " + fmt.format(new java.util.Date(msg.optLong("t"))),
                    11, Ui.faintInk(a, a.store)));
            TextView body = Ui.text(a, "", 13, Ui.ink(a, a.store));
            body.setText(highlight(text, lower));
            row.addView(body);
            if (ref.equals(activeRef)) {
                row.setBackground(Ui.roundedStroke(Ui.surface(a, a.store), Ui.dp(a, 18), Ui.plum(a, a.store), Ui.dp(a, 1)));
            }
            row.setOnClickListener(v -> jump(ref));
            results.addView(row);
        }
        summary.setText(count == 0 ? "没有找到包含“" + keyword + "”的消息"
                : "共 " + count + " 条结果 · 倒序 · 点击跳到原消息，可直接引用");
    }

    private CharSequence highlight(String text, String lower) {
        SpannableString span = new SpannableString(text);
        String hay = text.toLowerCase();
        int from = 0;
        int color = (Ui.plum(a, a.store) & 0x00FFFFFF) | 0x33000000;
        while (true) {
            int at = hay.indexOf(lower, from);
            if (at < 0 || lower.isEmpty()) break;
            span.setSpan(new BackgroundColorSpan(color), at, at + lower.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            from = at + lower.length();
        }
        return span;
    }

    private void jump(String ref) {
        if (ref.isEmpty()) {
            a.toast("这条消息没有编号，无法定位");
            return;
        }
        activeRef = ref;
        a.hideKeyboard();
        Page chat = a.page("pageChat");
        a.goPage("pageChat", true);
        if (chat instanceof ChatPage) ((ChatPage) chat).jumpAndOfferQuote(ref);
    }

    /** 供主题切换后 rebuild 时保留关键词 */
    @Override
    public void rebuild() {
        if (input != null) lastKeyword = input.getText().toString();
        super.rebuild();
    }
}

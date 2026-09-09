package com.icepear.app;

import android.app.Dialog;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 信箱：写信（草稿/寄出）、他的回信、往来记录翻页、回信、删信。
 * 信件通过 id / replyTo 串成链：A -> B -> C -> D。列表只显示链的起点（根信），
 * 点开后按页依次翻看 B、C、D。两个分组（我的信 / 他的回信）可折叠。
 */
public class LetterPage extends Page {

    private LinearLayout content;
    private boolean mineOpen = true;
    private boolean hisOpen = true;

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
        ensureIds(role.optJSONArray("letters"));

        content.addView(button("✉ 写一封信", true, this::newLetter));

        JSONArray letters = role.optJSONArray("letters");
        List<Integer> mineRoots = new ArrayList<>();
        List<Integer> hisRoots = new ArrayList<>();
        for (int i = (letters != null ? letters.length() : 0) - 1; i >= 0; i--) {
            JSONObject letter = letters.optJSONObject(i);
            if (letter == null) continue;
            if (isRoot(letters, letter)) {
                if (letter.optBoolean("mine", true)) mineRoots.add(i);
                else hisRoots.add(i);
            }
        }

        LinearLayout mineCard = section("我的信", null, mineOpen, open -> mineOpen = open);
        LinearLayout mineBody = sectionBody(mineCard);
        for (int index : mineRoots) mineBody.addView(threadRow(letters, index));
        if (mineRoots.isEmpty()) mineBody.addView(hint("还没有信件，写下第一封吧。"));
        content.addView(mineCard);

        LinearLayout hisCard = section("他的来信", null, hisOpen, open -> hisOpen = open);
        LinearLayout hisBody = sectionBody(hisCard);
        for (int index : hisRoots) hisBody.addView(threadRow(letters, index));
        if (hisRoots.isEmpty()) hisBody.addView(hint("他还没有主动写信给你。"));
        content.addView(hisCard);
    }

    private View threadRow(JSONArray letters, int index) {
        JSONObject letter = letters.optJSONObject(index);
        List<Integer> chain = chain(letters, index);
        LinearLayout row = Ui.row(a);
        row.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
        View icon = SvgIcon.view(a, Icons.MAIL, Ui.plum(a, a.store), 20);
        icon.setBackground(Ui.rounded(Ui.surfaceStrong(a, a.store), Ui.dp(a, 12)));
        icon.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));
        row.addView(icon);
        LinearLayout copy = Ui.column(a);
        copy.setPadding(Ui.dp(a, 10), 0, Ui.dp(a, 6), 0);
        boolean mine = letter.optBoolean("mine", true);
        String status = letter.optString("status", "sent");
        String badge = mine ? ("draft".equals(status) ? "草稿" : (chain.size() > 1 ? "已收到回信" : "已寄出")) : "他的来信";
        copy.addView(Ui.boldText(a, letter.optString("title", "无标题"), 14, Ui.ink(a, a.store)));
        copy.addView(Ui.text(a, badge + " · " + letter.optString("date", "")
                + (chain.size() > 1 ? " · 共 " + chain.size() + " 页" : ""), 11, Ui.faintInk(a, a.store)));
        row.addView(copy, Ui.weighted());
        row.addView(trashButton("删除这封信及其往来回信？删除后无法恢复。", () -> deleteThread(index)));
        row.setOnClickListener(v -> openThread(index, 0));
        return row;
    }

    /* ---------- 链 ---------- */

    private void ensureIds(JSONArray letters) {
        if (letters == null) return;
        boolean changed = false;
        for (int i = 0; i < letters.length(); i++) {
            JSONObject letter = letters.optJSONObject(i);
            if (letter != null && !letter.has("id")) {
                try {
                    letter.put("id", Store.uid("lt"));
                    changed = true;
                } catch (JSONException ignored) {
                }
            }
        }
        if (changed) a.store.save();
    }

    private int indexOfId(JSONArray letters, String id) {
        if (id == null || id.isEmpty()) return -1;
        for (int i = 0; i < letters.length(); i++) {
            JSONObject letter = letters.optJSONObject(i);
            if (letter != null && id.equals(letter.optString("id"))) return i;
        }
        return -1;
    }

    private boolean isRoot(JSONArray letters, JSONObject letter) {
        String parent = letter.optString("replyTo", "");
        return parent.isEmpty() || indexOfId(letters, parent) < 0;
    }

    /** 从根信开始按回复顺序排出的链（A, B, C, D…） */
    private List<Integer> chain(JSONArray letters, int rootIndex) {
        List<Integer> chain = new ArrayList<>();
        int current = rootIndex;
        int guard = 0;
        while (current >= 0 && guard++ < 500) {
            chain.add(current);
            JSONObject node = letters.optJSONObject(current);
            String id = node != null ? node.optString("id") : "";
            int next = -1;
            for (int i = 0; i < letters.length(); i++) {
                JSONObject candidate = letters.optJSONObject(i);
                if (candidate != null && id.equals(candidate.optString("replyTo", "\u0000")) && !chain.contains(i)) {
                    next = i;
                    break;
                }
            }
            current = next;
        }
        return chain;
    }

    /* ---------- 写信 / 读信 ---------- */

    private void newLetter() {
        Dialogs.Field title = new Dialogs.Field("title", "信的标题");
        title.placeholder = "给这封信起个名字";
        Dialogs.Field body = new Dialogs.Field("content", "信的内容");
        body.textarea = true;
        body.placeholder = "把想说的话写在这里…";
        Dialogs.form(a, a.store, Icons.MAIL, "写一封信", "可以先保存成草稿，之后再寄出。", "保存草稿",
                Dialogs.fields(title, body), values -> {
                    if (values.getOrDefault("title", "").trim().isEmpty()
                            || values.getOrDefault("content", "").trim().isEmpty()) {
                        Dialogs.notice(a, a.store, "!", "内容不完整", "标题和内容都要填写。");
                        return;
                    }
                    try {
                        a.store.role().getJSONArray("letters").put(new JSONObject()
                                .put("id", Store.uid("lt"))
                                .put("mine", true)
                                .put("title", values.get("title").trim())
                                .put("content", values.get("content").trim())
                                .put("date", now())
                                .put("status", "draft"));
                        a.store.save();
                        refresh();
                        a.toast("草稿已保存");
                    } catch (JSONException ignored) {
                    }
                });
    }

    private String now() {
        return new java.text.SimpleDateFormat("yyyy/M/d HH:mm:ss", java.util.Locale.CHINA).format(new java.util.Date());
    }

    /** 往来记录弹窗：第 page 页显示链上的第 page 封信，可翻页 / 回信 / 寄出草稿 */
    private void openThread(int rootIndex, int page) {
        JSONArray letters = a.store.role().optJSONArray("letters");
        if (letters == null) return;
        List<Integer> chain = chain(letters, rootIndex);
        if (chain.isEmpty()) return;
        page = Math.max(0, Math.min(page, chain.size() - 1));
        final int shownIndex = chain.get(page);
        JSONObject letter = letters.optJSONObject(shownIndex);
        if (letter == null) return;
        boolean mine = letter.optBoolean("mine", true);
        boolean draft = mine && "draft".equals(letter.optString("status"));
        JSONObject last = letters.optJSONObject(chain.get(chain.size() - 1));
        boolean canReply = last != null && !last.optBoolean("mine", true);

        LinearLayout body = Ui.column(a);
        LinearLayout paper = Ui.column(a);
        paper.setBackground(Ui.rounded(Ui.surface(a, a.store), Ui.dp(a, 14)));
        paper.setPadding(Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 12));
        paper.addView(Ui.text(a, mine ? "我写的" : a.store.displayName() + "写的", 11, Ui.faintInk(a, a.store)));
        TextView title = Ui.boldText(a, letter.optString("title", "无标题"), 15, Ui.ink(a, a.store));
        title.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 2));
        paper.addView(title);
        paper.addView(Ui.text(a, letter.optString("date", ""), 11, Ui.faintInk(a, a.store)));
        TextView text = Ui.text(a, letter.optString("content", ""), 14, Ui.ink(a, a.store));
        text.setPadding(0, Ui.dp(a, 8), 0, 0);
        text.setLineSpacing(Ui.dp(a, 3), 1f);
        paper.addView(text);
        body.addView(paper);

        final Dialog[] holder = new Dialog[1];
        if (chain.size() > 1) {
            LinearLayout pager = Ui.row(a);
            pager.setGravity(Gravity.CENTER_VERTICAL);
            pager.setPadding(0, Ui.dp(a, 10), 0, 0);
            final int current = page;
            TextView prev = pagerButton("‹", page > 0, () -> {
                holder[0].dismiss();
                openThread(rootIndex, current - 1);
            });
            TextView label = Ui.text(a, (page + 1) + " / " + chain.size(), 13, Ui.mutedInk(a, a.store));
            label.setGravity(Gravity.CENTER);
            TextView next = pagerButton("›", page < chain.size() - 1, () -> {
                holder[0].dismiss();
                openThread(rootIndex, current + 1);
            });
            pager.addView(prev);
            pager.addView(label, Ui.weighted());
            pager.addView(next);
            body.addView(pager);
        }

        String confirmText = draft ? "寄出这封信" : (canReply ? "回信" : null);
        Runnable onConfirm = draft ? () -> sendLetter(shownIndex)
                : (canReply ? () -> replyLetter(last.optString("id")) : null);
        holder[0] = Dialogs.custom(a, a.store, Icons.MAIL, "往来记录",
                "第 " + (page + 1) + " / " + chain.size() + " 页", body, "关闭", confirmText, onConfirm);
    }

    private TextView pagerButton(String label, boolean enabled, Runnable onClick) {
        TextView button = Ui.boldText(a, label, 20, enabled ? Ui.ink(a, a.store) : Ui.line(a, a.store));
        button.setGravity(Gravity.CENTER);
        button.setBackground(Ui.roundedStroke(Ui.surface(a, a.store), Ui.dp(a, 10), Ui.line(a, a.store), Ui.dp(a, 1)));
        button.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 36)));
        button.setEnabled(enabled);
        if (enabled) button.setOnClickListener(v -> onClick.run());
        return button;
    }

    private void replyLetter(String replyToId) {
        Dialogs.Field body = new Dialogs.Field("content", "回信内容");
        body.textarea = true;
        body.placeholder = "写下你想回的话…";
        Dialogs.form(a, a.store, Icons.MAIL, "回信", "寄出后他会再回你一封。", "寄出",
                Dialogs.fields(body), values -> {
                    String text = values.getOrDefault("content", "").trim();
                    if (text.isEmpty()) return;
                    try {
                        a.store.role().getJSONArray("letters").put(new JSONObject()
                                .put("id", Store.uid("lt"))
                                .put("mine", true)
                                .put("replyTo", replyToId)
                                .put("title", "回信")
                                .put("content", text)
                                .put("date", now())
                                .put("status", "sent"));
                        a.store.save();
                        refresh();
                        a.toast("回信已寄出");
                        scheduleHisReply();
                    } catch (JSONException ignored) {
                    }
                });
    }

    private void sendLetter(int index) {
        try {
            JSONArray letters = a.store.role().getJSONArray("letters");
            JSONObject letter = letters.getJSONObject(index);
            letter.put("status", "sent");
            a.store.save();
            refresh();
            a.toast("信已寄出，等他回信吧");
            scheduleHisReply();
        } catch (JSONException ignored) {
        }
    }

    private void scheduleHisReply() {
        JSONArray letters = a.store.role().optJSONArray("letters");
        JSONObject last = letters != null && letters.length() > 0 ? letters.optJSONObject(letters.length() - 1) : null;
        final String replyTo = last != null ? last.optString("id") : "";
        a.logic.handler().postDelayed(() -> hisReplyLetter(replyTo), a.store.rand(60, 180) * 1000L);
    }

    private void hisReplyLetter(String replyTo) {
        try {
            JSONArray letters = a.store.role().getJSONArray("letters");
            int parent = indexOfId(letters, replyTo);
            if (parent < 0) return;
            List<String> pool = a.store.allCards();
            StringBuilder body = new StringBuilder("你的信我认真读完了。\n");
            for (int i = 0; i < Math.min(3, pool.size()); i++) {
                body.append(pool.get(a.store.rand(0, pool.size() - 1))).append('\n');
            }
            body.append("等你回信。");
            letters.put(new JSONObject()
                    .put("id", Store.uid("lt"))
                    .put("mine", false)
                    .put("replyTo", replyTo)
                    .put("title", "给" + a.store.role().optString("myName", "你") + "的回信")
                    .put("content", body.toString())
                    .put("date", now())
                    .put("status", "recv"));
            a.store.save();
            if ("pageLetter".equals(a.currentPage)) refresh();
            a.toast("收到一封他的回信");
        } catch (JSONException ignored) {
        }
    }

    private void deleteThread(int rootIndex) {
        JSONArray letters = a.store.role().optJSONArray("letters");
        if (letters == null) return;
        List<Integer> chain = chain(letters, rootIndex);
        java.util.Collections.sort(chain);
        for (int i = chain.size() - 1; i >= 0; i--) letters.remove(chain.get(i));
        a.store.save();
        refresh();
    }
}

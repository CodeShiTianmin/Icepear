package com.icepear.app;

import android.graphics.Color;
import android.graphics.Paint;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 字卡设置页：回复字卡分组（折叠 / 编辑 / 隐藏 / 导入导出 / 清空 / 批量添加）、
 * Emoji 库与图片表情包、拍一拍格式与文案、状态池、位置池、红包美化、他的日常抽取池。
 */
public class CardsPage extends Page {

    private LinearLayout content;
    private String tab = "cards"; // cards | sticker | poke
    /** 各折叠区的展开状态，刷新后保持 */
    private final Set<String> openSections = new HashSet<>();
    private boolean sectionsInit;

    public CardsPage(MainActivity activity) {
        super(activity);
    }

    @Override
    protected View create() {
        content = Ui.column(a);
        return pageWithBar("字卡", content);
    }

    @Override
    public void refresh() {
        if (content == null) return;
        content.removeAllViews();
        if (!sectionsInit) {
            sectionsInit = true;
            JSONObject cards = cardsObject();
            Iterator<String> it = cards != null ? cards.keys() : null;
            if (it != null && it.hasNext()) openSections.add("g:" + it.next());
            openSections.add("emoji");
            openSections.add("sticker");
            openSections.add("poke");
        }

        LinearLayout tabs = Ui.row(a);
        tabs.addView(tabButton("回复字卡", "cards"));
        tabs.addView(tabButton("表情", "sticker"));
        tabs.addView(tabButton("氛围感", "poke"));
        content.addView(tabs);

        switch (tab) {
            case "sticker": renderStickerTab(); break;
            case "poke": renderPokeTab(); break;
            default: renderCardsTab();
        }
    }

    private TextView tabButton(String label, String id) {
        boolean on = tab.equals(id);
        TextView button = Ui.boldText(a, label, 13, on ? Color.WHITE : Ui.ink(a, a.store));
        button.setBackground(on
                ? Ui.rounded(Ui.plum(a, a.store), Ui.dp(a, 12))
                : Ui.roundedStroke(0x00000000, Ui.dp(a, 12), Ui.line(a, a.store), Ui.dp(a, 1)));
        button.setPadding(Ui.dp(a, 14), Ui.dp(a, 8), Ui.dp(a, 14), Ui.dp(a, 8));
        LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(a, 8);
        button.setLayoutParams(lp);
        button.setOnClickListener(v -> {
            tab = id;
            refresh();
        });
        return button;
    }

    /** 记住展开状态的折叠区 */
    private LinearLayout foldSection(String key, String title, String iconSvg) {
        return section(title, iconSvg, openSections.contains(key), open -> {
            if (open) openSections.add(key);
            else openSections.remove(key);
        });
    }

    private View iconButton(String svg, int color, String desc, Runnable onClick) {
        View icon = SvgIcon.view(a, svg, color, 20);
        icon.setPadding(Ui.dp(a, 7), Ui.dp(a, 7), Ui.dp(a, 7), Ui.dp(a, 7));
        icon.setContentDescription(desc);
        icon.setOnClickListener(v -> onClick.run());
        return icon;
    }

    private TextView linkButton(String label, int color, Runnable onClick) {
        TextView button = Ui.boldText(a, label, 12, color);
        button.setPadding(0, Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 4));
        button.setOnClickListener(v -> onClick.run());
        return button;
    }

    private JSONObject cardsObject() {
        JSONObject role = a.store.role();
        if (role == null) return null;
        JSONObject cards = role.optJSONObject("cards");
        if (cards == null) {
            cards = new JSONObject();
            try {
                role.put("cards", cards);
            } catch (JSONException ignored) {
            }
        }
        return cards;
    }

    private List<String> groupNames(JSONObject cards) {
        List<String> keys = new ArrayList<>();
        Iterator<String> it = cards.keys();
        while (it.hasNext()) keys.add(it.next());
        return keys;
    }

    /* ---------- 回复字卡 ---------- */

    private void renderCardsTab() {
        JSONObject cards = cardsObject();
        if (cards == null) return;
        List<String> groups = groupNames(cards);

        int total = 0, hiddenCount = a.store.hiddenCards().length();
        for (String g : groups) total += cards.optJSONArray(g) != null ? cards.optJSONArray(g).length() : 0;
        LinearLayout head = card(null);
        LinearLayout headRow = Ui.row(a);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        headRow.addView(Ui.boldText(a, groups.size() + " 个分组 · " + total + " 句"
                + (hiddenCount > 0 ? " · 隐藏 " + hiddenCount : ""), 14, Ui.ink(a, a.store)), Ui.weighted());
        headRow.addView(iconButton(Icons.DOWNLOAD, Ui.plum(a, a.store), "导入", this::importCards));
        headRow.addView(iconButton(Icons.SAVE, Ui.plum(a, a.store), "导出", this::exportCards));
        headRow.addView(iconButton(Icons.PLUS, Ui.plum(a, a.store), "新建分组", this::addGroup));
        head.addView(headRow);
        head.addView(hint("隐藏的字卡会保留在分组里，但他不会再拿来回复；输入框里回车即添加，一次粘贴多行会按行拆开。"));
        content.addView(head);

        for (String group : groups) {
            content.addView(groupSection(cards, group, groups.size()));
        }
    }

    private LinearLayout groupSection(JSONObject cards, String group, int groupCount) {
        JSONArray list = cards.optJSONArray(group);
        if (list == null) {
            list = new JSONArray();
            try {
                cards.put(group, list);
            } catch (JSONException ignored) {
            }
        }
        final JSONArray items = list;
        int hidden = 0;
        for (int i = 0; i < items.length(); i++) if (a.store.isCardHidden(items.optString(i))) hidden++;
        String title = group + "（" + items.length() + (hidden > 0 ? "，隐藏 " + hidden : "") + "）";
        LinearLayout box = foldSection("g:" + group, title, null);
        LinearLayout body = sectionBody(box);

        for (int i = 0; i < items.length(); i++) {
            final int index = i;
            final String text = items.optString(i);
            final boolean isHidden = a.store.isCardHidden(text);
            LinearLayout row = Ui.row(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(a, 2), 0, Ui.dp(a, 2));
            TextView label = Ui.text(a, text, 13, isHidden ? Ui.faintInk(a, a.store) : Ui.ink(a, a.store));
            if (isHidden) label.setPaintFlags(label.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            row.addView(label, Ui.weighted());
            row.addView(iconButton(isHidden ? Icons.EYE_OFF : Icons.EYE,
                    isHidden ? Ui.faintInk(a, a.store) : Ui.mutedInk(a, a.store),
                    isHidden ? "取消隐藏" : "隐藏", () -> {
                        a.store.setCardHidden(text, !isHidden);
                        a.store.save();
                        refresh();
                    }));
            row.addView(iconButton(Icons.EDIT, Ui.mutedInk(a, a.store), "编辑", () ->
                    Dialogs.prompt(a, a.store, Icons.EDIT, "编辑字卡", "字卡内容", null, text, value -> {
                        a.store.renameCard(group, index, value);
                        a.store.save();
                        refresh();
                    })));
            row.addView(trashButton("删除这句字卡？", () -> {
                items.remove(index);
                a.store.setCardHidden(text, false);
                a.store.save();
                refresh();
            }));
            body.addView(row);
        }
        if (items.length() == 0) body.addView(hint("暂无字卡"));

        body.addView(quickInput(items, "输入一句，回车添加…"));

        LinearLayout tools = Ui.row(a);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.addView(linkButton("≡ 批量添加", Ui.plum(a, a.store), () -> batchImport(items)));
        tools.addView(linkButton("重命名", Ui.mutedInk(a, a.store), () -> renameGroup(cards, group)));
        View spacer = new View(a);
        tools.addView(spacer, Ui.weighted());
        tools.addView(linkButton("清空分组", a.getColor(R.color.danger), () ->
                Dialogs.confirm(a, a.store, Icons.TRASH, "清空“" + group + "”？",
                        "分组会保留，里面 " + items.length() + " 句字卡会被删除", "清空", true, () -> {
                            for (int i = items.length() - 1; i >= 0; i--) {
                                a.store.setCardHidden(items.optString(i), false);
                                items.remove(i);
                            }
                            a.store.save();
                            refresh();
                        })));
        if (groupCount > 1) {
            tools.addView(trashButton("删除分组“" + group + "”及其中所有字卡？", () -> {
                for (int i = 0; i < items.length(); i++) a.store.setCardHidden(items.optString(i), false);
                cards.remove(group);
                openSections.remove("g:" + group);
                a.store.save();
                refresh();
            }));
        }
        body.addView(tools);
        return box;
    }

    /** 单行输入框：回车 / 完成即添加；粘贴多行文本按行拆分 */
    private EditText quickInput(JSONArray target, String placeholder) {
        EditText input = Dialogs.makeInput(a, a.store, false);
        input.setHint(placeholder);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setSingleLine(false);
        input.setMaxLines(4);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(a, 8);
        input.setLayoutParams(lp);
        Runnable commit = () -> {
            int added = addLines(target, input.getText().toString());
            input.setText("");
            if (added > 0) {
                a.store.save();
                refresh();
            }
        };
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEND
                    || actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN)) {
                commit.run();
                return true;
            }
            return false;
        });
        input.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                commit.run();
                return true;
            }
            return false;
        });
        return input;
    }

    /** 按行加入，去重（全局）；返回新增数量 */
    private int addLines(JSONArray target, String raw) {
        if (target == null || raw == null) return 0;
        Set<String> known = new HashSet<>(a.store.allCards());
        JSONArray hidden = a.store.hiddenCards();
        for (int i = 0; i < hidden.length(); i++) known.add(hidden.optString(i));
        int added = 0;
        for (String line : raw.split("\n")) {
            String item = line.trim();
            if (item.isEmpty() || known.contains(item)) continue;
            known.add(item);
            target.put(item);
            added++;
        }
        return added;
    }

    private void addGroup() {
        Dialogs.prompt(a, a.store, Icons.PLUS, "新建字卡分组", "分组名称", "例如：晚安、安慰、撒娇", "", value -> {
            JSONObject cards = cardsObject();
            if (cards == null) return;
            if (cards.has(value)) {
                Dialogs.notice(a, a.store, "!", "分组已存在", "换一个名称再试试。");
                return;
            }
            try {
                cards.put(value, new JSONArray());
                openSections.add("g:" + value);
                a.store.save();
                refresh();
            } catch (JSONException ignored) {
            }
        });
    }

    private void renameGroup(JSONObject cards, String group) {
        Dialogs.prompt(a, a.store, Icons.EDIT, "重命名分组", "分组名称", null, group, value -> {
            if (value.equals(group)) return;
            if (cards.has(value)) {
                Dialogs.notice(a, a.store, "!", "分组已存在", "换一个名称再试试。");
                return;
            }
            try {
                JSONObject rebuilt = new JSONObject();
                for (String key : groupNames(cards)) {
                    rebuilt.put(key.equals(group) ? value : key, cards.opt(key));
                }
                a.store.role().put("cards", rebuilt);
                if (openSections.remove("g:" + group)) openSections.add("g:" + value);
                a.store.save();
                refresh();
            } catch (JSONException ignored) {
            }
        });
    }

    private void batchImport(JSONArray target) {
        Dialogs.Field field = new Dialogs.Field("content", "字卡内容");
        field.textarea = true;
        field.placeholder = "一句一行…";
        Dialogs.form(a, a.store, "≡", "批量添加字卡", "每行一句，会自动跳过重复内容。", "添加",
                Dialogs.fields(field), values -> {
                    int added = addLines(target, values.getOrDefault("content", ""));
                    a.store.save();
                    refresh();
                    a.toast("已添加 " + added + " 句");
                });
    }

    /* ---------- 导入 / 导出 ---------- */

    private void exportCards() {
        JSONObject cards = cardsObject();
        if (cards == null) return;
        JSONObject payload = new JSONObject();
        try {
            payload.put("app", "icepear-cards").put("version", 1)
                    .put("cards", cards).put("hidden", a.store.hiddenCards())
                    .put("pokes", a.store.role().optJSONArray("pokes"))
                    .put("pokeFormat", a.store.pokeFormat());
        } catch (JSONException ignored) {
        }
        final String json = payload.toString();
        a.saveFile("application/json", "icepear-cards-" + System.currentTimeMillis() + ".json", uri -> {
            try (OutputStream out = a.getContentResolver().openOutputStream(uri)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
                a.toast("字卡已导出");
            } catch (Exception e) {
                a.toast("导出失败");
            }
        });
    }

    /** 支持两种文件：本页导出的 JSON（合并分组）或纯文本（每行一句，进入“导入”分组） */
    private void importCards() {
        a.pickFile("*/*", (bytes, mime, name) -> {
            String text = new String(bytes, StandardCharsets.UTF_8).trim();
            JSONObject cards = cardsObject();
            if (cards == null || text.isEmpty()) {
                a.toast("文件是空的");
                return;
            }
            int added = 0;
            try {
                if (text.startsWith("{")) {
                    JSONObject payload = new JSONObject(text);
                    JSONObject incoming = payload.optJSONObject("cards");
                    if (incoming == null) incoming = payload;
                    Iterator<String> it = incoming.keys();
                    while (it.hasNext()) {
                        String group = it.next();
                        JSONArray list = incoming.optJSONArray(group);
                        if (list == null) continue;
                        JSONArray target = cards.optJSONArray(group);
                        if (target == null) {
                            target = new JSONArray();
                            cards.put(group, target);
                        }
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < list.length(); i++) sb.append(list.optString(i)).append('\n');
                        added += addLines(target, sb.toString());
                    }
                    JSONArray hidden = payload.optJSONArray("hidden");
                    for (int i = 0; hidden != null && i < hidden.length(); i++) {
                        a.store.setCardHidden(hidden.optString(i), true);
                    }
                    JSONArray pokes = payload.optJSONArray("pokes");
                    if (pokes != null) {
                        JSONArray mine = a.store.role().optJSONArray("pokes");
                        if (mine == null) {
                            mine = new JSONArray();
                            a.store.role().put("pokes", mine);
                        }
                        Set<String> known = new LinkedHashSet<>();
                        for (int i = 0; i < mine.length(); i++) known.add(mine.optString(i));
                        for (int i = 0; i < pokes.length(); i++) {
                            String p = pokes.optString(i);
                            if (!p.isEmpty() && known.add(p)) mine.put(p);
                        }
                    }
                    if (payload.has("pokeFormat")) a.store.setPokeFormat(payload.optString("pokeFormat"));
                } else if (text.startsWith("[")) {
                    JSONArray list = new JSONArray(text);
                    JSONArray target = importTarget(cards);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < list.length(); i++) sb.append(list.optString(i)).append('\n');
                    added = addLines(target, sb.toString());
                } else {
                    added = addLines(importTarget(cards), text);
                }
            } catch (JSONException e) {
                added = addLines(importTarget(cards), text);
            }
            a.store.save();
            refresh();
            a.toast("已导入 " + added + " 句");
        });
    }

    private JSONArray importTarget(JSONObject cards) {
        JSONArray target = cards.optJSONArray("导入");
        if (target == null) {
            target = new JSONArray();
            try {
                cards.put("导入", target);
            } catch (JSONException ignored) {
            }
        }
        openSections.add("g:导入");
        return target;
    }

    /* ---------- 表情：Emoji 库 + 图片表情包 ---------- */

    private void renderStickerTab() {
        JSONArray emoji = a.store.data.optJSONArray("emoji");
        LinearLayout emojiBox = foldSection("emoji", "Emoji 库（" + (emoji != null ? emoji.length() : 0) + "）", Icons.SMILE);
        LinearLayout emojiBody = sectionBody(emojiBox);
        GridLayout grid = new GridLayout(a);
        grid.setColumnCount(6);
        for (int i = 0; emoji != null && i < emoji.length(); i++) {
            final int index = i;
            TextView cell = Ui.text(a, emoji.optString(i), 20, Ui.ink(a, a.store));
            cell.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));
            cell.setOnLongClickListener(v -> {
                Dialogs.confirm(a, a.store, Icons.TRASH, "删除这个 Emoji？", "聊天记录中的内容不会受影响",
                        "删除", true, () -> {
                            emoji.remove(index);
                            a.store.save();
                            refresh();
                        });
                return true;
            });
            grid.addView(cell);
        }
        emojiBody.addView(grid);
        emojiBody.addView(hint("长按可删除"));
        emojiBody.addView(button("＋ 添加 Emoji", false, () ->
                Dialogs.prompt(a, a.store, Icons.PLUS, "添加 Emoji", "内容", "例如：🥰", "", value -> {
                    a.store.data.optJSONArray("emoji").put(value);
                    a.store.save();
                    refresh();
                })));
        emojiBody.addView(button("≡ 批量添加 Emoji（一行一个）", false, () -> {
            Dialogs.Field field = new Dialogs.Field("content", "内容");
            field.textarea = true;
            field.placeholder = "🥰\n(｡・ω・｡)\n❤️";
            Dialogs.form(a, a.store, "≡", "批量添加", null, "添加", Dialogs.fields(field), values -> {
                for (String line : values.getOrDefault("content", "").split("\n")) {
                    if (!line.trim().isEmpty()) a.store.data.optJSONArray("emoji").put(line.trim());
                }
                a.store.save();
                refresh();
            });
        }));
        content.addView(emojiBox);

        JSONArray stickers = a.store.data.optJSONArray("stickers");
        LinearLayout stickerBox = foldSection("sticker", "图片表情包（" + (stickers != null ? stickers.length() : 0) + "）", Icons.IMAGE);
        LinearLayout stickerBody = sectionBody(stickerBox);
        GridLayout stickerGrid = new GridLayout(a);
        stickerGrid.setColumnCount(4);
        for (int i = 0; stickers != null && i < stickers.length(); i++) {
            final int index = i;
            android.graphics.Bitmap bitmap = Ui.decodeDataUrl(a.store.resolveMedia(stickers.optString(i)));
            if (bitmap == null) continue;
            ImageView image = new ImageView(a);
            image.setImageBitmap(bitmap);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = Ui.dp(a, 62);
            lp.height = Ui.dp(a, 62);
            lp.setMargins(Ui.dp(a, 5), Ui.dp(a, 5), Ui.dp(a, 5), Ui.dp(a, 5));
            image.setLayoutParams(lp);
            image.setOnLongClickListener(v -> {
                Dialogs.confirm(a, a.store, Icons.TRASH, "删除这个表情？", "删除后无法恢复", "删除", true, () -> {
                    a.store.deleteMedia(stickers.optString(index));
                    stickers.remove(index);
                    a.store.save();
                    refresh();
                });
                return true;
            });
            stickerGrid.addView(image);
        }
        stickerBody.addView(stickerGrid);
        if (stickers == null || stickers.length() == 0) stickerBody.addView(hint("暂无表情包"));
        stickerBody.addView(hint("长按可删除"));
        stickerBody.addView(button("＋ 从相册添加表情", true, () ->
                a.pickFile("image/*", (bytes, mime, name) -> {
                    String ref = a.store.importImage(bytes, mime);
                    if (ref.isEmpty()) {
                        a.toast("图片导入失败");
                        return;
                    }
                    a.store.data.optJSONArray("stickers").put(ref);
                    a.store.save();
                    refresh();
                })));
        content.addView(stickerBox);
    }

    /* ---------- 氛围感：拍一拍 / 状态池 / 位置池 / 感谢语 / 红包美化 / 他的日常 ---------- */

    private void renderPokeTab() {
        JSONObject role = a.store.role();
        if (role == null) return;
        content.addView(pokeSection(role));
        content.addView(poolSection("status", "他的状态池", null, role.optJSONArray("statuses"), "例如：想你"));
        content.addView(poolSection("locs", "他的位置池", null, a.store.data.optJSONArray("hisLocs"), "例如：家里"));
        content.addView(poolSection("bless", "收到红包的感谢语", null, a.store.data.optJSONArray("bless"), "例如：辛苦啦"));
        content.addView(txTitleSection("red", "红包标题", "例如：恭喜发财，大吉大利"));
        content.addView(txTitleSection("zhuan", "转账标题", "例如：拿去花，别客气"));
        content.addView(txTitleSection("gift", "礼物标题", "例如：特意为你挑的"));
        content.addView(txIconSection());
        content.addView(dailySection());
    }

    private LinearLayout pokeSection(JSONObject role) {
        JSONArray pokes = role.optJSONArray("pokes");
        if (pokes == null) {
            pokes = new JSONArray();
            try {
                role.put("pokes", pokes);
            } catch (JSONException ignored) {
            }
        }
        LinearLayout box = foldSection("poke", "拍一拍", Icons.POKE);
        LinearLayout body = sectionBody(box);

        body.addView(Ui.text(a, "显示格式", 12, Ui.mutedInk(a, a.store)));
        EditText format = Dialogs.makeInput(a, a.store, false);
        format.setText(a.store.pokeFormat());
        format.setHint(Store.DEFAULT_POKE_FORMAT);
        body.addView(format);
        TextView preview = hint("");
        Runnable updatePreview = () -> {
            String saved = a.store.pokeFormat();
            a.store.setPokeFormat(format.getText().toString());
            String sample = pokesSample(role);
            preview.setText("预览：" + a.logic.pokeText("me", sample) + "　/　" + a.logic.pokeText("other", sample));
            a.store.setPokeFormat(saved);
        };
        updatePreview.run();
        format.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) { updatePreview.run(); }
        });
        body.addView(preview);
        body.addView(hint("占位符：{我} 发起方（你/他的名字）、{他} 被拍的一方、{文案} 下方文案池抽到的一句。空格会原样保留。"));
        LinearLayout formatTools = Ui.row(a);
        formatTools.addView(linkButton("保存格式", Ui.plum(a, a.store), () -> {
            a.store.setPokeFormat(format.getText().toString());
            a.store.save();
            a.toast("已保存");
            refresh();
        }));
        formatTools.addView(linkButton("恢复默认", Ui.mutedInk(a, a.store), () -> {
            a.store.setPokeFormat(null);
            a.store.save();
            refresh();
        }));
        body.addView(formatTools);

        TextView poolTitle = Ui.boldText(a, "拍一拍文案", 13, Ui.ink(a, a.store));
        poolTitle.setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, 4));
        body.addView(poolTitle);
        fillPool(body, pokes, "例如：拍了拍他的头", true);
        return box;
    }

    private String pokesSample(JSONObject role) {
        JSONArray pokes = role.optJSONArray("pokes");
        return pokes != null && pokes.length() > 0 ? pokes.optString(0) : "拍了拍他的头";
    }

    private LinearLayout poolSection(String key, String title, String iconSvg, JSONArray pool, String placeholder) {
        LinearLayout box = foldSection(key, title + "（" + (pool != null ? pool.length() : 0) + "）", iconSvg);
        fillPool(sectionBody(box), pool, placeholder, true);
        return box;
    }

    /** 文本池：每行可编辑/删除；底部回车即添加的输入框 */
    private void fillPool(LinearLayout body, JSONArray pool, String placeholder, boolean editable) {
        for (int i = 0; pool != null && i < pool.length(); i++) {
            final int index = i;
            final String text = pool.optString(i);
            LinearLayout row = Ui.row(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(a, 2), 0, Ui.dp(a, 2));
            row.addView(Ui.text(a, text, 13, Ui.ink(a, a.store)), Ui.weighted());
            if (editable) {
                row.addView(iconButton(Icons.EDIT, Ui.mutedInk(a, a.store), "编辑", () ->
                        Dialogs.prompt(a, a.store, Icons.EDIT, "编辑", "内容", placeholder, text, value -> {
                            try {
                                pool.put(index, value);
                            } catch (JSONException ignored) {
                            }
                            a.store.save();
                            refresh();
                        })));
            }
            row.addView(trashButton("删除“" + text + "”？", () -> {
                pool.remove(index);
                a.store.save();
                refresh();
            }));
            body.addView(row);
        }
        if (pool == null || pool.length() == 0) body.addView(hint("暂无内容"));
        if (pool != null) body.addView(poolInput(pool, placeholder));
    }

    private EditText poolInput(JSONArray pool, String placeholder) {
        EditText input = Dialogs.makeInput(a, a.store, false);
        input.setHint(placeholder + "，回车添加");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setSingleLine(false);
        input.setMaxLines(4);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(a, 8);
        input.setLayoutParams(lp);
        Runnable commit = () -> {
            int added = 0;
            Set<String> known = new HashSet<>();
            for (int i = 0; i < pool.length(); i++) known.add(pool.optString(i));
            for (String line : input.getText().toString().split("\n")) {
                String item = line.trim();
                if (item.isEmpty() || !known.add(item)) continue;
                pool.put(item);
                added++;
            }
            input.setText("");
            if (added > 0) {
                a.store.save();
                refresh();
            }
        };
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEND
                    || actionId == EditorInfo.IME_ACTION_GO) {
                commit.run();
                return true;
            }
            return false;
        });
        input.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                commit.run();
                return true;
            }
            return false;
        });
        return input;
    }

    /* ---------- 红包美化：标题池（每种一池） + 共用 SVG 图标池 ---------- */

    private LinearLayout txTitleSection(String kind, String title, String placeholder) {
        JSONArray pool = a.store.txTitles(kind);
        LinearLayout box = foldSection("tx:" + kind, title + "（" + pool.length() + "）", null);
        LinearLayout body = sectionBody(box);
        fillPool(body, pool, placeholder, true);
        LinearLayout colorRow = Ui.row(a);
        colorRow.setGravity(Gravity.CENTER_VERTICAL);
        colorRow.setPadding(0, Ui.dp(a, 10), 0, 0);
        colorRow.addView(Ui.text(a, "卡片配色", 12, Ui.mutedInk(a, a.store)), Ui.weighted());
        JSONArray colors = a.store.data.optJSONArray("cardColors");
        JSONObject ui = a.store.cardUi(kind);
        int current = ui.optInt("color", 0);
        for (int i = 0; colors != null && i < colors.length(); i++) {
            final int index = i;
            JSONArray pair = colors.optJSONArray(i);
            if (pair == null) continue;
            View swatch = new View(a);
            android.graphics.drawable.GradientDrawable g = Ui.gradient(
                    Ui.parseColor(pair.optString(0), 0xFFFA9D3B), Ui.parseColor(pair.optString(1), 0xFFF76B1C), Ui.dp(a, 8));
            if (index == current) g.setStroke(Ui.dp(a, 2), Ui.ink(a, a.store));
            swatch.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 26), Ui.dp(a, 26));
            lp.leftMargin = Ui.dp(a, 6);
            swatch.setOnClickListener(v -> {
                try {
                    ui.put("color", index);
                    a.store.save();
                    refresh();
                } catch (JSONException ignored) {
                }
            });
            colorRow.addView(swatch, lp);
        }
        body.addView(colorRow);
        return box;
    }

    private LinearLayout txIconSection() {
        JSONArray icons = a.store.txIcons();
        LinearLayout box = foldSection("txicons", "红包/转账/礼物图标（" + icons.length() + "）", null);
        LinearLayout body = sectionBody(box);
        body.addView(hint("发出时从下方图标里随机选一个；点选删除，删空后卡片使用内置默认图标。"));
        GridLayout grid = new GridLayout(a);
        grid.setColumnCount(6);
        for (int i = 0; i < icons.length(); i++) {
            final int index = i;
            String svg = icons.optString(i);
            if (!SvgIcon.isSvg(svg)) continue;
            FrameLayout cell = new FrameLayout(a);
            cell.setBackground(Ui.rounded(Ui.surfaceStrong(a, a.store), Ui.dp(a, 12)));
            cell.addView(SvgIcon.view(a, svg, Ui.plum(a, a.store), 26), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.height = Ui.dp(a, 48);
            lp.setMargins(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
            cell.setLayoutParams(lp);
            cell.setOnClickListener(v -> Dialogs.confirm(a, a.store, Icons.TRASH, "删除这个图标？", null, "删除", true, () -> {
                icons.remove(index);
                a.store.save();
                refresh();
            }));
            grid.addView(cell);
        }
        body.addView(grid);
        LinearLayout actions = Ui.row(a);
        actions.addView(linkButton("＋ 粘贴 SVG 添加", Ui.plum(a, a.store), () ->
                Dialogs.prompt(a, a.store, Icons.PLUS, "添加图标", "SVG 代码",
                        "<svg viewBox=\"0 0 24 24\" ...>...</svg>", "", value -> {
                            if (!SvgIcon.isSvg(value)) {
                                a.toast("请粘贴 <svg …> 矢量代码");
                                return;
                            }
                            icons.put(value.trim());
                            a.store.save();
                            refresh();
                        })));
        actions.addView(linkButton("恢复内置图标", Ui.mutedInk(a, a.store), () -> {
            for (String s : Icons.TX_CURATED) {
                boolean exists = false;
                for (int i = 0; i < icons.length(); i++) if (s.equals(icons.optString(i))) exists = true;
                if (!exists) icons.put(s);
            }
            a.store.save();
            refresh();
        }));
        body.addView(actions);
        return box;
    }

    /* ---------- 他的日常：六个抽取池 ---------- */

    private LinearLayout dailySection() {
        LinearLayout box = foldSection("daily", "他的日常抽取池", Icons.PLAN);
        LinearLayout body = sectionBody(box);
        body.addView(hint("每天第一次打开“他的日常”时，会从这些池子里各抽一条。"));
        String[][] defs = {
                {"weather", "天气", "例如：晴 24°C"}, {"body", "身体", "例如：精神不错"},
                {"mood", "心情", "例如：想你"}, {"did", "做了什么", "例如：在家收拾房间"},
                {"ate", "吃了什么", "例如：番茄鸡蛋面"}, {"plan", "计划", "例如：早点睡"}};
        for (String[] def : defs) {
            JSONArray pool = a.store.dailyPool(def[0]);
            LinearLayout sub = foldSection("daily:" + def[0], def[1] + "（" + pool.length() + "）", null);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) sub.getLayoutParams();
            lp.topMargin = Ui.dp(a, 8);
            sub.setBackground(Ui.rounded(Ui.surfaceStrong(a, a.store), Ui.dp(a, 14)));
            fillPool(sectionBody(sub), pool, def[2], true);
            body.addView(sub);
        }
        return box;
    }
}

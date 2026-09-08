package com.icepear.app;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 字卡设置页：回复字卡分组管理、批量添加、Emoji 库与图片表情包管理、
 * 拍一拍文案、状态池、他的位置池。
 */
public class CardsPage extends Page {

    private LinearLayout content;
    private String currentGroup = "默认";
    private String tab = "cards"; // cards | sticker | poke

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

        /* 页签 */
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

    /* ---------- 回复字卡 ---------- */

    private void renderCardsTab() {
        JSONObject role = a.store.role();
        JSONObject cards = role != null ? role.optJSONObject("cards") : null;
        if (cards == null) return;
        if (!cards.has(currentGroup)) {
            Iterator<String> it = cards.keys();
            currentGroup = it.hasNext() ? it.next() : "默认";
        }

        LinearLayout groupCard = card("字卡分组");
        HorizontalScrollView scroll = new HorizontalScrollView(a);
        LinearLayout row = Ui.row(a);
        Iterator<String> it = cards.keys();
        while (it.hasNext()) {
            final String group = it.next();
            boolean on = group.equals(currentGroup);
            TextView chip = Ui.boldText(a, group + " (" + (cards.optJSONArray(group) != null
                    ? cards.optJSONArray(group).length() : 0) + ")", 12, on ? Color.WHITE : Ui.ink(a, a.store));
            chip.setBackground(on
                    ? Ui.rounded(Ui.plum(a, a.store), Ui.dp(a, 11))
                    : Ui.roundedStroke(Ui.surfaceStrong(a, a.store), Ui.dp(a, 11), Ui.line(a, a.store), Ui.dp(a, 1)));
            chip.setPadding(Ui.dp(a, 12), Ui.dp(a, 7), Ui.dp(a, 12), Ui.dp(a, 7));
            LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(a, 8);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                currentGroup = group;
                refresh();
            });
            chip.setOnLongClickListener(v -> {
                deleteGroup(group);
                return true;
            });
            row.addView(chip);
        }
        scroll.addView(row);
        groupCard.addView(scroll);
        LinearLayout tools = Ui.row(a);
        tools.setPadding(0, Ui.dp(a, 8), 0, 0);
        TextView add = Ui.boldText(a, "＋ 新建分组", 12, Ui.plum(a, a.store));
        add.setOnClickListener(v -> addGroup());
        tools.addView(add);
        groupCard.addView(tools);
        groupCard.addView(hint("长按分组可删除"));
        content.addView(groupCard);

        LinearLayout listCard = card("“" + currentGroup + "”的字卡");
        JSONArray list = cards.optJSONArray(currentGroup);
        for (int i = 0; list != null && i < list.length(); i++) {
            final int index = i;
            LinearLayout item = Ui.row(a);
            item.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
            TextView text = Ui.text(a, list.optString(i), 13, Ui.ink(a, a.store));
            item.addView(text, Ui.weighted());
            TextView del = Ui.boldText(a, "删除", 12, a.getColor(R.color.danger));
            del.setOnClickListener(v -> {
                list.remove(index);
                a.store.save();
                refresh();
            });
            item.addView(del);
            listCard.addView(item);
        }
        if (list == null || list.length() == 0) listCard.addView(hint("暂无字卡"));
        content.addView(listCard);

        content.addView(button("＋ 添加一句", true, () ->
                Dialogs.prompt(a, a.store, "＋", "添加字卡", "字卡内容", "他会用这些话回复你", "", value -> {
                    JSONArray target = cards.optJSONArray(currentGroup);
                    if (target != null) {
                        target.put(value);
                        a.store.save();
                        refresh();
                    }
                })));
        content.addView(button("≡ 批量添加（一行一句）", false, this::batchImport));
    }

    private void addGroup() {
        Dialogs.prompt(a, a.store, "＋", "新建字卡分组", "分组名称", "例如：晚安、安慰、撒娇", "", value -> {
            JSONObject cards = a.store.role().optJSONObject("cards");
            if (cards.has(value)) {
                Dialogs.notice(a, a.store, "!", "分组已存在", "换一个名称再试试。");
                return;
            }
            try {
                cards.put(value, new JSONArray());
                currentGroup = value;
                a.store.save();
                refresh();
            } catch (JSONException ignored) {
            }
        });
    }

    private void deleteGroup(String group) {
        JSONObject cards = a.store.role().optJSONObject("cards");
        List<String> keys = new ArrayList<>();
        Iterator<String> it = cards.keys();
        while (it.hasNext()) keys.add(it.next());
        if (keys.size() <= 1) {
            Dialogs.notice(a, a.store, "!", "无法删除", "至少需要保留一个分组。");
            return;
        }
        Dialogs.confirm(a, a.store, "⌫", "删除分组“" + group + "”？", "分组里的字卡会一起删除",
                "删除", true, () -> {
                    cards.remove(group);
                    a.store.save();
                    refresh();
                });
    }

    private void batchImport() {
        Dialogs.Field field = new Dialogs.Field("content", "字卡内容");
        field.textarea = true;
        field.placeholder = "一句一行…";
        Dialogs.form(a, a.store, "≡", "批量添加字卡", "每行一句，会自动跳过重复内容。", "添加",
                Dialogs.fields(field), values -> {
                    JSONObject cards = a.store.role().optJSONObject("cards");
                    java.util.Set<String> known = new java.util.HashSet<>(a.store.allCards());
                    JSONArray target = cards.optJSONArray(currentGroup);
                    int added = 0;
                    for (String line : values.getOrDefault("content", "").split("\n")) {
                        String item = line.trim();
                        if (item.isEmpty() || known.contains(item)) continue;
                        known.add(item);
                        target.put(item);
                        added++;
                    }
                    a.store.save();
                    refresh();
                    a.toast("已添加 " + added + " 句");
                });
    }

    /* ---------- 表情：Emoji 库 + 图片表情包 ---------- */

    private void renderStickerTab() {
        LinearLayout emojiCard = card("Emoji 库");
        GridLayout grid = new GridLayout(a);
        grid.setColumnCount(6);
        JSONArray emoji = a.store.data.optJSONArray("emoji");
        for (int i = 0; emoji != null && i < emoji.length(); i++) {
            final int index = i;
            TextView cell = Ui.text(a, emoji.optString(i), 20, Ui.ink(a, a.store));
            cell.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));
            cell.setOnLongClickListener(v -> {
                Dialogs.confirm(a, a.store, "⌫", "删除这个 Emoji？", "聊天记录中的内容不会受影响",
                        "删除", true, () -> {
                            emoji.remove(index);
                            a.store.save();
                            refresh();
                        });
                return true;
            });
            grid.addView(cell);
        }
        emojiCard.addView(grid);
        emojiCard.addView(hint("长按可删除"));
        content.addView(emojiCard);
        content.addView(button("＋ 添加 Emoji", false, () ->
                Dialogs.prompt(a, a.store, "＋", "添加 Emoji", "内容", "例如：🥰", "", value -> {
                    a.store.data.optJSONArray("emoji").put(value);
                    a.store.save();
                    refresh();
                })));
        content.addView(button("≡ 批量添加 Emoji（一行一个）", false, () -> {
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

        LinearLayout stickerCard = card("图片表情包");
        GridLayout stickerGrid = new GridLayout(a);
        stickerGrid.setColumnCount(4);
        JSONArray stickers = a.store.data.optJSONArray("stickers");
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
                Dialogs.confirm(a, a.store, "⌫", "删除这个表情？", "删除后无法恢复", "删除", true, () -> {
                    a.store.deleteMedia(stickers.optString(index));
                    stickers.remove(index);
                    a.store.save();
                    refresh();
                });
                return true;
            });
            stickerGrid.addView(image);
        }
        stickerCard.addView(stickerGrid);
        if (stickers == null || stickers.length() == 0) stickerCard.addView(hint("暂无表情包"));
        stickerCard.addView(hint("长按可删除"));
        content.addView(stickerCard);
        content.addView(button("＋ 从相册添加表情", true, () ->
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
    }

    /* ---------- 氛围感：拍一拍 / 状态池 / 位置池 / 感谢语 ---------- */

    private void renderPokeTab() {
        JSONObject role = a.store.role();
        content.addView(stringPoolCard("拍一拍文案", role.optJSONArray("pokes"), "例如：拍了拍他的头"));
        content.addView(stringPoolCard("他的状态池", role.optJSONArray("statuses"), "例如：想你"));
        content.addView(stringPoolCard("他的位置池", a.store.data.optJSONArray("hisLocs"), "例如：家里"));
        content.addView(stringPoolCard("收到红包的感谢语", a.store.data.optJSONArray("bless"), "例如：辛苦啦"));
        content.addView(txTitleCard("红包标题", "red", "例如：恭喜发财，大吉大利"));
        content.addView(txTitleCard("转账标题", "zhuan", "例如：拿去花，别客气"));
        content.addView(txTitleCard("礼物标题", "gift", "例如：特意为你挑的"));
        content.addView(txIconCard());
    }

    /* ---------- 红包美化：标题池（每种一池） + 共用 SVG 图标池 ---------- */

    private LinearLayout txTitleCard(String title, String kind, String placeholder) {
        LinearLayout poolCard = stringPoolCard(title, a.store.txTitles(kind), placeholder);
        LinearLayout colorRow = Ui.row(a);
        colorRow.setGravity(Gravity.CENTER_VERTICAL);
        colorRow.setPadding(0, Ui.dp(a, 8), 0, 0);
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
        poolCard.addView(colorRow);
        return poolCard;
    }

    private LinearLayout txIconCard() {
        LinearLayout iconCard = card("红包/转账/礼物图标（随机抽取，点选删除）");
        iconCard.addView(hint("发出时从下方图标里随机选一个；删空后卡片使用内置默认图标。"));
        JSONArray icons = a.store.txIcons();
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
            cell.setOnClickListener(v -> Dialogs.confirm(a, a.store, "⌫", "删除这个图标？", null, "删除", true, () -> {
                icons.remove(index);
                a.store.save();
                refresh();
            }));
            grid.addView(cell);
        }
        iconCard.addView(grid);
        LinearLayout actions = Ui.row(a);
        TextView add = Ui.boldText(a, "＋ 粘贴 SVG 添加", 12, Ui.plum(a, a.store));
        add.setPadding(0, Ui.dp(a, 8), Ui.dp(a, 18), 0);
        add.setOnClickListener(v -> Dialogs.prompt(a, a.store, "＋", "添加图标", "SVG 代码",
                "<svg viewBox=\"0 0 24 24\" ...>...</svg>", "", value -> {
                    if (!SvgIcon.isSvg(value)) {
                        a.toast("请粘贴 <svg …> 矢量代码");
                        return;
                    }
                    icons.put(value.trim());
                    a.store.save();
                    refresh();
                }));
        actions.addView(add);
        TextView restore = Ui.boldText(a, "恢复内置图标", 12, Ui.mutedInk(a, a.store));
        restore.setPadding(0, Ui.dp(a, 8), 0, 0);
        restore.setOnClickListener(v -> {
            for (String s : Icons.TX_CURATED) {
                boolean exists = false;
                for (int i = 0; i < icons.length(); i++) if (s.equals(icons.optString(i))) exists = true;
                if (!exists) icons.put(s);
            }
            a.store.save();
            refresh();
        });
        actions.addView(restore);
        iconCard.addView(actions);
        return iconCard;
    }

    private LinearLayout stringPoolCard(String title, JSONArray pool, String placeholder) {
        LinearLayout poolCard = card(title);
        for (int i = 0; pool != null && i < pool.length(); i++) {
            final int index = i;
            LinearLayout row = Ui.row(a);
            row.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 4));
            row.addView(Ui.text(a, pool.optString(i), 13, Ui.ink(a, a.store)), Ui.weighted());
            TextView del = Ui.boldText(a, "删除", 12, a.getColor(R.color.danger));
            del.setOnClickListener(v -> {
                pool.remove(index);
                a.store.save();
                refresh();
            });
            row.addView(del);
            poolCard.addView(row);
        }
        TextView add = Ui.boldText(a, "＋ 添加", 12, Ui.plum(a, a.store));
        add.setPadding(0, Ui.dp(a, 8), 0, 0);
        add.setOnClickListener(v -> Dialogs.prompt(a, a.store, "＋", "添加", "内容", placeholder, "", value -> {
            pool.put(value);
            a.store.save();
            refresh();
        }));
        poolCard.addView(add);
        return poolCard;
    }
}

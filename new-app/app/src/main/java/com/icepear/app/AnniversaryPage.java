package com.icepear.app;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * 纪念日页：顶栏右侧从左到右为 添加 / 切换视图 / 删除。
 * 卡片视图（可带背景图）与列表视图切换；删除进入多选模式（全选 / 删除选中 / 退出）。
 */
public class AnniversaryPage extends Page {

    private LinearLayout content;
    private boolean listView;
    private boolean selecting;
    private final Set<Integer> selected = new HashSet<>();
    private View deleteButton;

    public AnniversaryPage(MainActivity activity) {
        super(activity);
    }

    @Override
    protected View create() {
        content = Ui.column(a);
        LinearLayout actions = Ui.row(a);
        actions.addView(barIcon(Icons.PLUS, "添加纪念日", this::addMemo));
        actions.addView(barIcon(Icons.LIST, "切换视图", () -> {
            listView = !listView;
            refresh();
        }));
        deleteButton = barIcon(Icons.TRASH, "删除", () -> {
            selecting = !selecting;
            selected.clear();
            refresh();
        });
        actions.addView(deleteButton);
        return pageWithBar("纪念日", content, actions);
    }

    @Override
    public boolean handleBack() {
        if (selecting) {
            selecting = false;
            selected.clear();
            refresh();
            return true;
        }
        return false;
    }

    private JSONArray memos() {
        JSONArray memos = a.store.data.optJSONArray("memos");
        if (memos == null) {
            memos = new JSONArray();
            try {
                a.store.data.put("memos", memos);
            } catch (JSONException ignored) {
            }
        }
        return memos;
    }

    @Override
    public void refresh() {
        if (content == null) return;
        content.removeAllViews();
        if (deleteButton != null) {
            deleteButton.setBackground(selecting ? Ui.rounded(Ui.plum(a, a.store), Ui.dp(a, 12)) : null);
        }
        JSONArray memos = memos();
        if (selecting) content.addView(selectBar(memos));
        if (memos.length() == 0) {
            content.addView(hint("还没有纪念日，点右上角 + 添加一个吧。"));
            return;
        }
        if (listView) {
            for (int i = 0; i < memos.length(); i++) {
                JSONObject memo = memos.optJSONObject(i);
                if (memo != null) content.addView(listRow(memo, i));
            }
        } else {
            GridLayout grid = new GridLayout(a);
            grid.setColumnCount(2);
            for (int i = 0; i < memos.length(); i++) {
                JSONObject memo = memos.optJSONObject(i);
                if (memo == null) continue;
                View cell = gridCell(memo, i);
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                        GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
                lp.width = 0;
                lp.setMargins(Ui.dp(a, 5), Ui.dp(a, 5), Ui.dp(a, 5), Ui.dp(a, 5));
                cell.setLayoutParams(lp);
                grid.addView(cell);
            }
            content.addView(grid);
        }
    }

    private View selectBar(JSONArray memos) {
        LinearLayout bar = Ui.row(a);
        bar.setGravity(Gravity.END);
        bar.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 6));
        bar.addView(chip("全选", true, () -> {
            for (int i = 0; i < memos.length(); i++) selected.add(i);
            refresh();
        }));
        bar.addView(chip("删除选中", false, () -> {
            if (selected.isEmpty()) {
                a.toast("先选择要删除的纪念日");
                return;
            }
            Dialogs.confirm(a, a.store, Icons.TRASH, "删除选中的 " + selected.size() + " 个纪念日？",
                    "删除后无法恢复", "删除", true, () -> {
                        java.util.List<Integer> list = new java.util.ArrayList<>(selected);
                        java.util.Collections.sort(list);
                        for (int i = list.size() - 1; i >= 0; i--) memos.remove(list.get(i));
                        a.store.save();
                        selecting = false;
                        selected.clear();
                        refresh();
                    });
        }));
        bar.addView(chip("退出", false, () -> {
            selecting = false;
            selected.clear();
            refresh();
        }));
        return bar;
    }

    private TextView chip(String label, boolean primary, Runnable onClick) {
        TextView chip = Ui.boldText(a, label, 12, primary ? Color.WHITE
                : ("删除选中".equals(label) ? a.getColor(R.color.danger) : Ui.ink(a, a.store)));
        chip.setBackground(primary ? Ui.rounded(Ui.plum(a, a.store), Ui.dp(a, 12))
                : Ui.rounded(Ui.surface(a, a.store), Ui.dp(a, 12)));
        chip.setPadding(Ui.dp(a, 12), Ui.dp(a, 7), Ui.dp(a, 12), Ui.dp(a, 7));
        LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(a, 8);
        chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> onClick.run());
        return chip;
    }

    private View checkMark(int index) {
        boolean on = selected.contains(index);
        View check = SvgIcon.view(a, Icons.CHECK, on ? Color.WHITE : 0x00000000, 14);
        int size = Ui.dp(a, 22);
        check.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
        check.setBackground(on ? Ui.rounded(Ui.plum(a, a.store), size / 2f)
                : Ui.roundedStroke(0x00000000, size / 2f, Ui.faintInk(a, a.store), Ui.dp(a, 1.5f)));
        check.setLayoutParams(new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.END));
        return check;
    }

    private void toggleSelect(int index) {
        if (!selected.remove(index)) selected.add(index);
        refresh();
    }

    private View gridCell(JSONObject memo, int index) {
        FrameLayout cell = new FrameLayout(a);
        cell.setBackground(Ui.rounded(Ui.surface(a, a.store), Ui.dp(a, 16)));
        cell.setClipToOutline(true);
        String bg = a.store.resolveMedia(memo.optString("bg", ""));
        Bitmap bitmap = Ui.decodeDataUrl(bg);
        if (bitmap != null) {
            ImageView image = new ImageView(a);
            image.setImageBitmap(bitmap);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setAlpha(0.55f);
            cell.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        LinearLayout copy = Ui.column(a);
        copy.setGravity(Gravity.CENTER_HORIZONTAL);
        copy.setPadding(Ui.dp(a, 12), Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16));
        TextView name = Ui.boldText(a, memo.optString("name"), 14, Ui.ink(a, a.store));
        name.setGravity(Gravity.CENTER);
        copy.addView(name);
        TextView date = Ui.text(a, memo.optString("date"), 11, Ui.mutedInk(a, a.store));
        date.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 6));
        copy.addView(date);
        copy.addView(daysView(memo));
        cell.addView(copy, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (selecting) {
            View check = checkMark(index);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) check.getLayoutParams();
            lp.topMargin = Ui.dp(a, 8);
            lp.rightMargin = Ui.dp(a, 8);
            cell.addView(check, lp);
        }
        cell.setOnClickListener(v -> {
            if (selecting) toggleSelect(index);
            else editMemo(index);
        });
        return cell;
    }

    private View listRow(JSONObject memo, int index) {
        LinearLayout row = Ui.row(a);
        row.setBackground(Ui.rounded(Ui.surface(a, a.store), Ui.dp(a, 14)));
        row.setPadding(Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(a, 8);
        row.setLayoutParams(lp);
        View icon = SvgIcon.view(a, Icons.CALENDAR, Ui.plum(a, a.store), 18);
        icon.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));
        icon.setBackground(Ui.rounded(Ui.surfaceStrong(a, a.store), Ui.dp(a, 17)));
        row.addView(icon);
        LinearLayout copy = Ui.column(a);
        copy.setPadding(Ui.dp(a, 10), 0, 0, 0);
        copy.addView(Ui.boldText(a, memo.optString("name"), 14, Ui.ink(a, a.store)));
        copy.addView(Ui.text(a, memo.optString("date") + " · " + memoDays(memo), 12, Ui.mutedInk(a, a.store)));
        row.addView(copy, Ui.weighted());
        if (selecting) {
            View check = checkMark(index);
            check.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(a, 22), Ui.dp(a, 22)));
            row.addView(check);
        }
        row.setOnClickListener(v -> {
            if (selecting) toggleSelect(index);
            else editMemo(index);
        });
        return row;
    }

    private View daysView(JSONObject memo) {
        long days = dayDiff(memo);
        boolean countdown = "countdown".equals(memo.optString("type"));
        LinearLayout row = Ui.row(a);
        row.setGravity(Gravity.CENTER);
        String prefix;
        long shown;
        if (countdown) {
            long remain = -days;
            prefix = remain >= 0 ? "还有" : "已过";
            shown = Math.abs(remain);
        } else {
            prefix = "已";
            shown = Math.max(0, days);
        }
        row.addView(Ui.text(a, prefix, 12, Ui.mutedInk(a, a.store)));
        TextView number = Ui.boldText(a, String.valueOf(shown), 22, Ui.ink(a, a.store));
        number.setPadding(Ui.dp(a, 4), 0, Ui.dp(a, 4), 0);
        row.addView(number);
        row.addView(Ui.text(a, "天", 12, Ui.mutedInk(a, a.store)));
        return row;
    }

    private long dayDiff(JSONObject memo) {
        try {
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            long target = fmt.parse(memo.optString("date").replace('/', '-')).getTime();
            return (System.currentTimeMillis() - target) / 86400000L;
        } catch (Exception e) {
            return 0;
        }
    }

    private String memoDays(JSONObject memo) {
        long days = dayDiff(memo);
        if ("countdown".equals(memo.optString("type"))) {
            long remain = -days;
            return remain >= 0 ? "还有 " + remain + " 天" : "已过 " + days + " 天";
        }
        return "已 " + Math.max(0, days) + " 天";
    }

    /* ---------- 添加 / 编辑 ---------- */

    private void addMemo() {
        memoForm(-1);
    }

    private void editMemo(int index) {
        memoForm(index);
    }

    private void memoForm(int index) {
        JSONArray memos = memos();
        JSONObject existing = index >= 0 ? memos.optJSONObject(index) : null;
        final String[] bg = {existing != null ? existing.optString("bg", "") : ""};

        LinearLayout body = Ui.column(a);
        body.addView(fieldLabel("名称"));
        android.widget.EditText name = Dialogs.makeInput(a, a.store, false);
        name.setHint("例如：在一起");
        if (existing != null) name.setText(existing.optString("name"));
        body.addView(name);
        body.addView(fieldLabel("日期"));
        android.widget.EditText date = Dialogs.makeInput(a, a.store, false);
        date.setHint("例如：2024-05-20");
        if (existing != null) date.setText(existing.optString("date"));
        body.addView(date);
        body.addView(fieldLabel("类型"));
        android.widget.Spinner type = new android.widget.Spinner(a);
        type.setAdapter(new android.widget.ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"倒计时", "累计"}));
        type.setSelection(existing != null && "countdown".equals(existing.optString("type")) ? 0 : 1);
        body.addView(type);

        ImageView preview = new ImageView(a);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setClipToOutline(true);
        preview.setBackground(Ui.rounded(Ui.surface(a, a.store), Ui.dp(a, 12)));
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 90));
        previewLp.topMargin = Ui.dp(a, 8);
        preview.setLayoutParams(previewLp);
        Bitmap current = Ui.decodeDataUrl(a.store.resolveMedia(bg[0]));
        if (current != null) preview.setImageBitmap(current);

        TextView pick = button("添加背景", true, () -> a.pickImage((bytes, mime, fileName) -> {
            bg[0] = a.store.importImage(bytes, mime);
            Bitmap bitmap = Ui.decodeDataUrl(a.store.resolveMedia(bg[0]));
            if (bitmap != null) preview.setImageBitmap(bitmap);
        }));
        body.addView(pick);
        body.addView(preview);

        Dialogs.custom(a, a.store, Icons.CALENDAR, index >= 0 ? "编辑纪念日" : "添加纪念日", null, body,
                "取消", "保存", () -> {
                    String n = name.getText().toString().trim();
                    String d = date.getText().toString().trim();
                    if (n.isEmpty() || d.isEmpty()) {
                        Dialogs.notice(a, a.store, "!", "内容不完整", "请填写名称和日期。");
                        return;
                    }
                    try {
                        JSONObject memo = existing != null ? existing : new JSONObject();
                        memo.put("name", n).put("date", d)
                                .put("type", type.getSelectedItemPosition() == 0 ? "countdown" : "countup")
                                .put("bg", bg[0]);
                        if (existing == null) memos.put(memo);
                        a.store.save();
                        refresh();
                    } catch (JSONException ignored) {
                    }
                });
    }

    private TextView fieldLabel(String text) {
        TextView label = Ui.text(a, text, 12, Ui.mutedInk(a, a.store));
        label.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 4));
        return label;
    }
}

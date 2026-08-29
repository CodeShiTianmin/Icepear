package com.icepear.app;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 应用内弹窗，对应旧版 appNotice / appConfirm / appPrompt / appForm / toast。
 */
public final class Dialogs {

    private Dialogs() {
    }

    public interface OnConfirm {
        void run();
    }

    public interface OnText {
        void run(String value);
    }

    public interface OnValues {
        void run(Map<String, String> values);
    }

    public static class Field {
        public final String name;
        public final String label;
        public String placeholder = "";
        public String value = "";
        public boolean textarea;
        public boolean number;
        public boolean date;
        public String[] optionValues;
        public String[] optionLabels;

        public Field(String name, String label) {
            this.name = name;
            this.label = label;
        }
    }

    public static void toast(Context c, String message) {
        Toast.makeText(c, message, Toast.LENGTH_SHORT).show();
    }

    private static Dialog baseDialog(Context c, Store store, String icon, String title, String subtitle,
                                     View body, String cancelText, String confirmText, boolean danger,
                                     Runnable onConfirm) {
        return baseDialog(c, store, icon, title, subtitle, body, cancelText, confirmText, danger, onConfirm, null);
    }

    private static Dialog baseDialog(Context c, Store store, String icon, String title, String subtitle,
                                     View body, String cancelText, String confirmText, boolean danger,
                                     Runnable onConfirm, Runnable onCancel) {
        Dialog dialog = new Dialog(c);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        int pad = Ui.dp(c, 20);
        LinearLayout card = Ui.column(c);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(Ui.rounded(Ui.surfaceStrong(c, store), Ui.dp(c, 22)));

        LinearLayout head = Ui.row(c);
        TextView iconView = Ui.boldText(c, icon == null || icon.isEmpty() ? "✦" : icon, 18, Color.WHITE);
        iconView.setGravity(Gravity.CENTER);
        int iconSize = Ui.dp(c, 40);
        iconView.setBackground(Ui.rounded(Ui.plum(c, store), Ui.dp(c, 14)));
        head.addView(iconView, new LinearLayout.LayoutParams(iconSize, iconSize));
        LinearLayout titles = Ui.column(c);
        titles.setPadding(Ui.dp(c, 12), 0, 0, 0);
        titles.addView(Ui.boldText(c, title == null ? "提示" : title, 16, Ui.ink(c, store)));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = Ui.text(c, subtitle, 12, Ui.mutedInk(c, store));
            titles.addView(sub);
        }
        head.addView(titles);
        card.addView(head);

        if (body != null) {
            body.setPadding(0, Ui.dp(c, 14), 0, 0);
            card.addView(body);
        }

        LinearLayout actions = Ui.row(c);
        actions.setPadding(0, Ui.dp(c, 18), 0, 0);
        actions.setGravity(Gravity.END);
        if (cancelText != null) {
            TextView cancel = actionButton(c, store, cancelText, false, false);
            cancel.setOnClickListener(v -> {
                if (onCancel != null) onCancel.run();
                dialog.dismiss();
            });
            actions.addView(cancel);
        }
        if (confirmText != null) {
            TextView confirm = actionButton(c, store, confirmText, true, danger);
            LinearLayout.LayoutParams params = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.leftMargin = Ui.dp(c, 10);
            confirm.setLayoutParams(params);
            confirm.setOnClickListener(v -> {
                if (onConfirm != null) onConfirm.run();
                dialog.dismiss();
            });
            actions.addView(confirm);
        }
        card.addView(actions);

        ScrollView scroll = new ScrollView(c);
        scroll.addView(card);
        dialog.setContentView(scroll);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (c.getResources().getDisplayMetrics().widthPixels * 0.86),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return dialog;
    }

    private static TextView actionButton(Context c, Store store, String label, boolean primary, boolean danger) {
        TextView button = Ui.boldText(c, label, 14,
                primary ? Color.WHITE : Ui.mutedInk(c, store));
        int color = danger ? c.getColor(R.color.danger) : Ui.plum(c, store);
        button.setBackground(primary
                ? Ui.rounded(color, Ui.dp(c, 12))
                : Ui.roundedStroke(0x00000000, Ui.dp(c, 12), Ui.line(c, store), Ui.dp(c, 1)));
        button.setPadding(Ui.dp(c, 18), Ui.dp(c, 10), Ui.dp(c, 18), Ui.dp(c, 10));
        return button;
    }

    /** appNotice：只有一个“知道了”按钮 */
    public static void notice(Context c, Store store, String icon, String title, String message) {
        TextView body = Ui.text(c, message == null ? "" : message, 14, Ui.mutedInk(c, store));
        baseDialog(c, store, icon, title, null, body, null, "知道了", false, null).show();
    }

    /** appConfirm */
    public static void confirm(Context c, Store store, String icon, String title, String subtitle,
                               String confirmText, boolean danger, OnConfirm onConfirm) {
        baseDialog(c, store, icon, title, subtitle, null, "取消",
                confirmText == null ? "确定" : confirmText, danger, onConfirm::run).show();
    }

    /** 两个动作都有回调的选择弹窗，对应旧版 showCard 的双按钮形态 */
    public static void choice(Context c, Store store, String icon, String title, String subtitle,
                              String noText, String yesText, Runnable onNo, Runnable onYes) {
        baseDialog(c, store, icon, title, subtitle, null, noText, yesText, false, onYes, onNo).show();
    }

    /** appPrompt：单行输入 */
    public static void prompt(Context c, Store store, String icon, String title, String label,
                              String placeholder, String initial, OnText onConfirm) {
        LinearLayout body = Ui.column(c);
        body.addView(Ui.text(c, label == null ? "" : label, 12, Ui.mutedInk(c, store)));
        EditText input = makeInput(c, store, false);
        input.setHint(placeholder == null ? "" : placeholder);
        if (initial != null) input.setText(initial);
        body.addView(input);
        baseDialog(c, store, icon, title, null, body, "取消", "确定", false, () -> {
            String value = input.getText().toString().trim();
            if (!value.isEmpty()) onConfirm.run(value);
        }).show();
    }

    /** appForm：多字段表单 */
    public static void form(Context c, Store store, String icon, String title, String subtitle,
                            String confirmText, List<Field> fields, OnValues onConfirm) {
        LinearLayout body = Ui.column(c);
        Map<String, View> inputs = new LinkedHashMap<>();
        for (Field field : fields) {
            TextView label = Ui.text(c, field.label, 12, Ui.mutedInk(c, store));
            label.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 4));
            body.addView(label);
            if (field.optionValues != null) {
                Spinner spinner = new Spinner(c);
                ArrayAdapter<String> adapter = new ArrayAdapter<>(c,
                        android.R.layout.simple_spinner_dropdown_item, field.optionLabels);
                spinner.setAdapter(adapter);
                for (int i = 0; i < field.optionValues.length; i++) {
                    if (field.optionValues[i].equals(field.value)) spinner.setSelection(i);
                }
                body.addView(spinner);
                inputs.put(field.name, spinner);
            } else {
                EditText input = makeInput(c, store, field.textarea);
                input.setHint(field.placeholder);
                input.setText(field.value);
                if (field.number) input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                if (field.date) input.setHint("YYYY-MM-DD");
                body.addView(input);
                inputs.put(field.name, input);
            }
        }
        baseDialog(c, store, icon, title, subtitle, body, "取消",
                confirmText == null ? "确定" : confirmText, false, () -> {
            Map<String, String> values = new LinkedHashMap<>();
            for (Field field : fields) {
                View view = inputs.get(field.name);
                if (view instanceof Spinner) {
                    int index = ((Spinner) view).getSelectedItemPosition();
                    values.put(field.name, index >= 0 && index < field.optionValues.length
                            ? field.optionValues[index] : "");
                } else if (view instanceof EditText) {
                    values.put(field.name, ((EditText) view).getText().toString());
                }
            }
            onConfirm.run(values);
        }).show();
    }

    public static EditText makeInput(Context c, Store store, boolean textarea) {
        EditText input = new EditText(c);
        input.setTextColor(Ui.ink(c, store));
        input.setHintTextColor(Ui.faintInk(c, store));
        input.setTextSize(14);
        input.setBackground(Ui.roundedStroke(Ui.surface(c, store), Ui.dp(c, 12), Ui.line(c, store), Ui.dp(c, 1)));
        input.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
        if (textarea) {
            input.setSingleLine(false);
            input.setMinLines(4);
            input.setGravity(Gravity.TOP | Gravity.START);
        } else {
            input.setSingleLine(true);
        }
        return input;
    }

    public static List<Field> fields(Field... items) {
        List<Field> list = new ArrayList<>();
        java.util.Collections.addAll(list, items);
        return list;
    }
}

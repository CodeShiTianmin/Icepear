package com.icepear.app;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 页面基类。页面视图懒创建，refresh() 重绘数据，rebuild() 主题变化后重建。
 */
public abstract class Page {

    protected final MainActivity a;
    private View view;

    protected Page(MainActivity activity) {
        this.a = activity;
    }

    public View view() {
        if (view == null) view = create();
        return view;
    }

    public void rebuild() {
        view = null;
    }

    protected abstract View create();

    public void refresh() {
    }

    /** 返回 true 表示已消费返回键 */
    public boolean handleBack() {
        return false;
    }

    /* ---------- 通用页面结构：标题栏 + 滚动内容 ---------- */

    protected LinearLayout pageWithBar(String title, LinearLayout content) {
        LinearLayout page = Ui.column(a);
        page.setBackgroundColor(Ui.paper(a, a.store));
        page.addView(pageBar(title));
        ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        content.setPadding(Ui.dp(a, 14), Ui.dp(a, 10), Ui.dp(a, 14), Ui.dp(a, 24));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return page;
    }

    protected LinearLayout pageBar(String title) {
        LinearLayout bar = Ui.row(a);
        bar.setBackgroundColor(Ui.topBg(a, a.store));
        bar.setPadding(Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 14), Ui.dp(a, 10));
        TextView back = Ui.boldText(a, "‹", 26, Ui.ink(a, a.store));
        back.setPadding(Ui.dp(a, 8), 0, Ui.dp(a, 16), 0);
        back.setOnClickListener(v -> a.onBackPressed());
        bar.addView(back);
        TextView label = Ui.boldText(a, title, 17, Ui.ink(a, a.store));
        bar.addView(label);
        return bar;
    }

    /* ---------- 通用控件 ---------- */

    protected LinearLayout card(String heading) {
        LinearLayout card = Ui.column(a);
        card.setBackground(Ui.rounded(Ui.surface(a, a.store), Ui.dp(a, 18)));
        int pad = Ui.dp(a, 14);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(a, 12);
        card.setLayoutParams(lp);
        if (heading != null) {
            TextView head = Ui.boldText(a, heading, 15, Ui.ink(a, a.store));
            head.setPadding(0, 0, 0, Ui.dp(a, 8));
            card.addView(head);
        }
        return card;
    }

    protected TextView button(String label, boolean primary, Runnable onClick) {
        TextView button = Ui.boldText(a, label, 14, primary ? 0xFFFFFFFF : Ui.ink(a, a.store));
        button.setGravity(Gravity.CENTER);
        button.setBackground(primary
                ? Ui.rounded(Ui.plum(a, a.store), Ui.dp(a, 12))
                : Ui.roundedStroke(Ui.surfaceStrong(a, a.store), Ui.dp(a, 12), Ui.line(a, a.store), Ui.dp(a, 1)));
        button.setPadding(Ui.dp(a, 16), Ui.dp(a, 10), Ui.dp(a, 16), Ui.dp(a, 10));
        button.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(a, 8);
        button.setLayoutParams(lp);
        return button;
    }

    protected TextView dangerButton(String label, Runnable onClick) {
        TextView button = button(label, true, onClick);
        button.setBackground(Ui.rounded(a.getColor(R.color.danger), Ui.dp(a, 12)));
        return button;
    }

    protected TextView hint(String text) {
        TextView view = Ui.text(a, text, 12, Ui.faintInk(a, a.store));
        view.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
        return view;
    }
}

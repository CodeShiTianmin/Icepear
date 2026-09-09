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
        return pageWithBar(title, content, null);
    }

    /** 带右侧操作按钮的页面（如朋友圈的“写朋友圈”） */
    protected LinearLayout pageWithBar(String title, LinearLayout content, View action) {
        LinearLayout page = Ui.column(a);
        page.setBackgroundColor(Ui.paper(a, a.store));
        LinearLayout bar = pageBar(title);
        if (action != null) {
            View spacer = new View(a);
            bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
            bar.addView(action);
        }
        page.addView(bar);
        ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        content.setPadding(Ui.dp(a, 14), Ui.dp(a, 10), Ui.dp(a, 14), Ui.dp(a, 24));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return page;
    }

    /** 顶栏：只有一个放大的返回符号（无标题文字）；title 仅作无障碍描述 */
    protected LinearLayout pageBar(String title) {
        LinearLayout bar = Ui.row(a);
        bar.setBackgroundColor(Ui.topBg(a, a.store));
        bar.setPadding(Ui.dp(a, 8), Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 6));
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(backButton());
        if (title != null) bar.setContentDescription(title);
        return bar;
    }

    protected View backButton() {
        View back = SvgIcon.view(a, Icons.BACK, Ui.ink(a, a.store), 30);
        back.setPadding(Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 10));
        back.setContentDescription("返回");
        back.setOnClickListener(v -> a.onBackPressed());
        return back;
    }

    /** 顶栏右侧的图标按钮 */
    protected View barIcon(String svg, String desc, Runnable onClick) {
        View icon = SvgIcon.view(a, svg, Ui.ink(a, a.store), 24);
        icon.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 10));
        icon.setContentDescription(desc);
        icon.setOnClickListener(v -> onClick.run());
        return icon;
    }

    /** 可折叠分组卡片：box.getTag() 为 body；open 控制默认展开 */
    protected LinearLayout section(String title, boolean open) {
        return section(title, null, open);
    }

    protected LinearLayout section(String title, String iconSvg, boolean open) {
        return section(title, iconSvg, open, null);
    }

    protected LinearLayout section(String title, String iconSvg, boolean open,
                                   java.util.function.Consumer<Boolean> onToggle) {
        LinearLayout box = card(null);
        LinearLayout head = Ui.row(a);
        if (iconSvg != null) {
            View icon = SvgIcon.view(a, iconSvg, Ui.plum(a, a.store), 20);
            icon.setPadding(0, 0, Ui.dp(a, 10), 0);
            head.addView(icon);
        }
        TextView label = Ui.boldText(a, title, 15, Ui.ink(a, a.store));
        head.addView(label, Ui.weighted());
        View arrow = SvgIcon.view(a, Icons.CHEVRON, Ui.faintInk(a, a.store), 18);
        arrow.setRotation(open ? 180 : 0);
        head.addView(arrow);
        head.setPadding(0, Ui.dp(a, 2), 0, Ui.dp(a, 2));
        box.addView(head);
        LinearLayout body = Ui.column(a);
        body.setPadding(0, Ui.dp(a, 10), 0, 0);
        body.setVisibility(open ? View.VISIBLE : View.GONE);
        box.addView(body);
        head.setOnClickListener(v -> {
            boolean isOpen = body.getVisibility() == View.VISIBLE;
            body.setVisibility(isOpen ? View.GONE : View.VISIBLE);
            arrow.animate().rotation(isOpen ? 0 : 180).setDuration(160).start();
            if (onToggle != null) onToggle.accept(!isOpen);
        });
        box.setTag(body);
        return box;
    }

    protected LinearLayout sectionBody(LinearLayout section) {
        return (LinearLayout) section.getTag();
    }

    /** 红色垃圾桶删除按钮（矢量），点击后需确认 */
    protected View trashButton(String confirmText, Runnable onDelete) {
        View trash = SvgIcon.view(a, Icons.TRASH, a.getColor(R.color.danger), 22);
        trash.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));
        trash.setContentDescription("删除");
        trash.setOnClickListener(v -> Dialogs.confirm(a, a.store, Icons.TRASH, "确认删除",
                confirmText, "删除", true, onDelete::run));
        return trash;
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

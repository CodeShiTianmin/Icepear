package com.icepear.app;

import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 功能中心，对应浏览器版 #pageMenu：两列 group-tab 按钮（SVG 图标 + 文字，左对齐），
 * 顺序与浏览器一致：搜索聊天 / 小卖铺 / 字卡设置 / 信箱 / 设置 / 朋友圈 / 他的日常 / 词云 / 珍藏时刻 / 纪念日。
 */
public class MenuPage extends Page {

    private static final String[][] ITEMS = {
            {Icons.SEARCH, "搜索聊天", "pageSearch"},
            {Icons.SHOP, "小卖铺", "pageShop"},
            {Icons.CARDS, "字卡设置", "pageCards"},
            {Icons.MAIL, "信箱", "pageLetter"},
            {Icons.SLIDERS, "设置", "pageSet"},
            {Icons.NAV_MOMENTS, "朋友圈", "pageMoments"},
            {Icons.SUN_CLOUD, "他的日常", "pageWeather"},
            {Icons.WORD_CLOUD, "词云", "pageCloud"},
            {Icons.HEART, "珍藏时刻", "pageFav"},
            {Icons.CALENDAR, "纪念日", "pageMemo"},
    };

    public MenuPage(MainActivity activity) {
        super(activity);
    }

    @Override
    protected View create() {
        LinearLayout content = Ui.column(a);
        content.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
        GridLayout grid = new GridLayout(a);
        grid.setColumnCount(2);
        for (String[] item : ITEMS) {
            final String target = item[2];
            LinearLayout cell = Ui.row(a);
            cell.setGravity(Gravity.CENTER_VERTICAL);
            cell.setBackground(Ui.rounded(Ui.surface(a, a.store), Ui.dp(a, 14)));
            cell.setPadding(Ui.dp(a, 16), Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16));
            cell.addView(SvgIcon.view(a, item[0], Ui.plum(a, a.store), 22));
            TextView name = Ui.boldText(a, item[1], 15, Ui.ink(a, a.store));
            name.setSingleLine(true);
            name.setPadding(Ui.dp(a, 10), 0, 0, 0);
            cell.addView(name, Ui.weighted());
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.setMargins(Ui.dp(a, 5), Ui.dp(a, 5), Ui.dp(a, 5), Ui.dp(a, 5));
            cell.setLayoutParams(lp);
            cell.setOnClickListener(v -> a.goPage(target, true));
            grid.addView(cell);
        }
        content.addView(grid);
        return pageWithBar("功能中心", content);
    }
}

package com.icepear.app;

import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 功能中心：朋友圈、他的日常、互动周报、搜索聊天、词云、视频通话等入口。
 */
public class MenuPage extends Page {

    public MenuPage(MainActivity activity) {
        super(activity);
    }

    @Override
    protected View create() {
        LinearLayout content = Ui.column(a);
        GridLayout grid = new GridLayout(a);
        grid.setColumnCount(3);
        String[][] items = {
                {"🌸", "朋友圈", "pageMoments"},
                {"📔", "他的日常", "pageWeather"},
                {"📊", "互动周报", "pageWeekly"},
                {"🔍", "搜索聊天", "pageSearch"},
                {"☁️", "词云", "pageCloud"},
                {"📹", "视频通话", "@video"},
                {"📞", "他来电", "@call"},
        };
        for (String[] item : items) {
            final String target = item[2];
            LinearLayout cell = Ui.column(a);
            cell.setGravity(Gravity.CENTER);
            cell.setBackground(Ui.rounded(Ui.surface(a, a.store), Ui.dp(a, 18)));
            cell.setPadding(0, Ui.dp(a, 18), 0, Ui.dp(a, 14));
            TextView icon = Ui.text(a, item[0], 28, Ui.ink(a, a.store));
            icon.setGravity(Gravity.CENTER);
            TextView name = Ui.text(a, item[1], 12, Ui.mutedInk(a, a.store));
            name.setGravity(Gravity.CENTER);
            name.setPadding(0, Ui.dp(a, 6), 0, 0);
            cell.addView(icon);
            cell.addView(name);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.setMargins(Ui.dp(a, 5), Ui.dp(a, 5), Ui.dp(a, 5), Ui.dp(a, 5));
            cell.setLayoutParams(lp);
            cell.setOnClickListener(v -> {
                if ("@video".equals(target)) a.videoOverlay.startVideo();
                else if ("@call".equals(target)) a.videoOverlay.incomingCall();
                else a.goPage(target, true);
            });
            grid.addView(cell);
        }
        content.addView(grid);
        return pageWithBar("功能中心", content);
    }
}

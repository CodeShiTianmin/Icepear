package com.icepear.app;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 珍藏时刻（对应浏览器补丁 pageMemories）：
 * 整段对话珍藏（role.memories）、朋友圈珍藏（kind=moment）与单条收藏（msg.favorite）。
 * 支持时间正倒序、列表 / 网格切换、逐条折叠、系统消息显隐、删除、进入画布编辑与导出长图。
 */
public class FavoritesPage extends Page {

    private LinearLayout content;
    private boolean newestFirst = true;
    private boolean grid;
    private boolean showSys = true;
    private final Set<String> collapsed = new HashSet<>();
    private boolean singleOpen = true;

    public FavoritesPage(MainActivity activity) {
        super(activity);
    }

    @Override
    protected View create() {
        content = Ui.column(a);
        return pageWithBar("珍藏时刻", content);
    }

    private static String memId(JSONObject mem, int index) {
        String id = mem.optString("id", "");
        return id.isEmpty() ? "idx" + index : id;
    }

    @Override
    public void refresh() {
        if (content == null) return;
        content.removeAllViews();
        JSONArray memories = a.store.memories();

        /* 顶部：数量 + 排序 / 视图 / 系统消息 / 折叠 控制 */
        LinearLayout head = Ui.row(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), 0, Ui.dp(a, 4));
        head.addView(SvgIcon.view(a, Icons.HEART, Ui.plum(a, a.store), 18));
        TextView title = Ui.boldText(a, "珍藏时刻 · " + memories.length(), 15, Ui.ink(a, a.store));
        title.setPadding(Ui.dp(a, 6), 0, 0, 0);
        head.addView(title, Ui.weighted());
        ImageView viewToggle = SvgIcon.view(a, grid ? Icons.LIST : Icons.GRID, Ui.plum(a, a.store), 22);
        viewToggle.setContentDescription(grid ? "切换列表" : "切换网格");
        viewToggle.setPadding(Ui.dp(a, 8), Ui.dp(a, 6), Ui.dp(a, 8), Ui.dp(a, 6));
        viewToggle.setOnClickListener(v -> {
            grid = !grid;
            refresh();
        });
        head.addView(viewToggle);
        content.addView(head);

        LinearLayout controls = Ui.row(a);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(Ui.dp(a, 4), 0, Ui.dp(a, 4), Ui.dp(a, 6));
        controls.addView(chip(newestFirst ? "最新在前" : "最早在前", () -> {
            newestFirst = !newestFirst;
            refresh();
        }));
        controls.addView(chip(showSys ? "含系统消息" : "隐藏系统消息", () -> {
            showSys = !showSys;
            refresh();
        }));
        if (!grid) {
            boolean allCollapsed = memories.length() > 0 && collapsed.size() >= memories.length();
            controls.addView(chip(allCollapsed ? "全部展开" : "全部折叠", () -> {
                if (allCollapsed) {
                    collapsed.clear();
                } else {
                    for (int i = 0; i < memories.length(); i++) {
                        JSONObject mem = memories.optJSONObject(i);
                        if (mem != null) collapsed.add(memId(mem, i));
                    }
                }
                refresh();
            }));
        }
        content.addView(controls);

        if (memories.length() == 0) {
            content.addView(hint("长按聊天消息 → 多选 → 点「珍藏」，整段对话会保存在这里；朋友圈里点「珍藏」也会收进来。"));
        }

        /* 按时间排序 */
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < memories.length(); i++) if (memories.optJSONObject(i) != null) order.add(i);
        order.sort((x, y) -> {
            long tx = memories.optJSONObject(x).optLong("t");
            long ty = memories.optJSONObject(y).optLong("t");
            return newestFirst ? Long.compare(ty, tx) : Long.compare(tx, ty);
        });

        if (grid) {
            renderGrid(memories, order);
        } else {
            for (int index : order) renderListItem(memories, index);
        }
        renderSingles();
    }

    private TextView chip(String label, Runnable onClick) {
        TextView chip = Ui.boldText(a, label, 12, Ui.plum(a, a.store));
        chip.setBackground(Ui.roundedStroke(Ui.surface(a, a.store), Ui.dp(a, 12), Ui.line(a, a.store), Ui.dp(a, 1)));
        chip.setPadding(Ui.dp(a, 10), Ui.dp(a, 5), Ui.dp(a, 10), Ui.dp(a, 5));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(a, 8);
        chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> onClick.run());
        return chip;
    }

    private String memTitle(JSONObject mem, SimpleDateFormat fmt) {
        if ("moment".equals(mem.optString("kind"))) {
            return "朋友圈 · " + fmt.format(new Date(mem.optLong("momentT", mem.optLong("t"))));
        }
        JSONArray msgs = mem.optJSONArray("msgs");
        int n = 0;
        for (int k = 0; msgs != null && k < msgs.length(); k++) {
            JSONObject m = msgs.optJSONObject(k);
            if (m == null || (!showSys && "sys".equals(m.optString("type")))) continue;
            n++;
        }
        return fmt.format(new Date(mem.optLong("t"))) + " · " + n + " 条";
    }

    private void deleteMemory(JSONArray memories, JSONObject mem) {
        for (int i = 0; i < memories.length(); i++) {
            if (memories.optJSONObject(i) == mem) {
                memories.remove(i);
                break;
            }
        }
        a.store.save();
        refresh();
    }

    private void openEditor(JSONObject mem) {
        Page editor = a.page("pageMemoryEdit");
        if (editor instanceof MemoryEditPage) ((MemoryEditPage) editor).open(mem, showSys);
        a.goPage("pageMemoryEdit", true);
    }

    /* ---------- 列表模式：逐条可折叠 ---------- */

    private void renderListItem(JSONArray memories, int index) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
        final JSONObject mem = memories.optJSONObject(index);
        final String id = memId(mem, index);
        boolean moment = "moment".equals(mem.optString("kind"));
        LinearLayout box = section(memTitle(mem, fmt), moment ? Icons.NAV_MOMENTS : Icons.HEART,
                !collapsed.contains(id), open -> {
                    if (open) collapsed.remove(id);
                    else collapsed.add(id);
                });
        LinearLayout body = sectionBody(box);

        LinearLayout actions = Ui.row(a);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView edit = Ui.boldText(a, "编辑画布 / 保存长图", 12, Ui.plum(a, a.store));
        edit.setOnClickListener(v -> openEditor(mem));
        actions.addView(edit, Ui.weighted());
        actions.addView(trashButton("删除后无法恢复", () -> deleteMemory(memories, mem)));
        body.addView(actions);

        if (moment) {
            renderMomentBody(body, mem);
        } else {
            JSONArray msgs = mem.optJSONArray("msgs");
            for (int k = 0; msgs != null && k < msgs.length(); k++) {
                JSONObject m = msgs.optJSONObject(k);
                if (m == null) continue;
                if ("sys".equals(m.optString("type"))) {
                    if (!showSys) continue;
                    TextView sys = Ui.text(a, m.optString("text", ""), 11, Ui.faintInk(a, a.store));
                    sys.setGravity(Gravity.CENTER);
                    sys.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 4));
                    body.addView(sys);
                    continue;
                }
                boolean me = "me".equals(m.optString("side"));
                LinearLayout line = Ui.row(a);
                line.setGravity(me ? Gravity.END : Gravity.START);
                line.setPadding(0, Ui.dp(a, 3), 0, Ui.dp(a, 3));
                View bubble = bubbleView(m, me);
                line.addView(bubble);
                body.addView(line);
            }
        }
        content.addView(box);
    }

    private View bubbleView(JSONObject m, boolean me) {
        if ("img".equals(m.optString("type"))) {
            ImageView image = new ImageView(a);
            image.setAdjustViewBounds(true);
            image.setMaxWidth(Ui.dp(a, 180));
            image.setMaxHeight(Ui.dp(a, 220));
            if (Ui.setImage(image, a.store.resolveMedia(m.optString("src", "")))) {
                image.setBackground(Ui.rounded(Ui.surfaceStrong(a, a.store), Ui.dp(a, 12)));
                image.setClipToOutline(true);
                return image;
            }
        }
        TextView bubble = Ui.text(a, MemoryEditPage.describe(m), 13, me ? Color.WHITE : Ui.ink(a, a.store));
        bubble.setBackground(Ui.rounded(me ? Ui.plum(a, a.store) : Ui.surfaceStrong(a, a.store), Ui.dp(a, 12)));
        bubble.setPadding(Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 6));
        bubble.setMaxWidth(Ui.dp(a, 240));
        return bubble;
    }

    private void renderMomentBody(LinearLayout body, JSONObject mem) {
        body.addView(Ui.boldText(a, mem.optString("who", ""), 13, Ui.plum(a, a.store)));
        String text = mem.optString("text", "");
        if (!text.isEmpty()) {
            TextView t = Ui.text(a, text, 14, Ui.ink(a, a.store));
            t.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 4));
            body.addView(t);
        }
        String src = a.store.resolveMedia(mem.optString("image", ""));
        ImageView image = new ImageView(a);
        image.setAdjustViewBounds(true);
        image.setMaxHeight(Ui.dp(a, 260));
        if (Ui.setImage(image, src)) {
            image.setBackground(Ui.rounded(Ui.surfaceStrong(a, a.store), Ui.dp(a, 12)));
            image.setClipToOutline(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(a, 4);
            body.addView(image, lp);
        }
    }

    /* ---------- 网格模式：两列卡片，点击进入画布 ---------- */

    private void renderGrid(JSONArray memories, List<Integer> order) {
        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
        LinearLayout row = null;
        for (int n = 0; n < order.size(); n++) {
            if (n % 2 == 0) {
                row = Ui.row(a);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                row.setLayoutParams(rlp);
                content.addView(row);
            }
            final JSONObject mem = memories.optJSONObject(order.get(n));
            LinearLayout cell = card(null);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            clp.topMargin = Ui.dp(a, 10);
            if (n % 2 == 0) clp.rightMargin = Ui.dp(a, 5);
            else clp.leftMargin = Ui.dp(a, 5);
            cell.setLayoutParams(clp);
            boolean moment = "moment".equals(mem.optString("kind"));

            LinearLayout top = Ui.row(a);
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(SvgIcon.view(a, moment ? Icons.NAV_MOMENTS : Icons.HEART, Ui.plum(a, a.store), 14));
            TextView time = Ui.text(a, memTitle(mem, fmt), 10, Ui.faintInk(a, a.store));
            time.setPadding(Ui.dp(a, 4), 0, 0, 0);
            time.setSingleLine(true);
            top.addView(time, Ui.weighted());
            cell.addView(top);

            String cover = coverImage(mem);
            if (cover != null) {
                ImageView image = new ImageView(a);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                if (Ui.setImage(image, cover)) {
                    image.setBackground(Ui.rounded(Ui.surfaceStrong(a, a.store), Ui.dp(a, 10)));
                    image.setClipToOutline(true);
                    LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 96));
                    ilp.topMargin = Ui.dp(a, 6);
                    cell.addView(image, ilp);
                }
            }
            TextView preview = Ui.text(a, previewText(mem), 12, Ui.ink(a, a.store));
            preview.setMaxLines(cover != null ? 2 : 5);
            preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
            preview.setPadding(0, Ui.dp(a, 6), 0, 0);
            cell.addView(preview);

            LinearLayout foot = Ui.row(a);
            foot.setGravity(Gravity.CENTER_VERTICAL);
            TextView edit = Ui.boldText(a, "编辑", 12, Ui.plum(a, a.store));
            edit.setOnClickListener(v -> openEditor(mem));
            foot.addView(edit, Ui.weighted());
            View trash = trashButton("删除后无法恢复", () -> deleteMemory(memories, mem));
            trash.setPadding(Ui.dp(a, 6), Ui.dp(a, 4), 0, Ui.dp(a, 4));
            foot.addView(trash);
            cell.addView(foot);
            cell.setOnClickListener(v -> openEditor(mem));
            if (row != null) row.addView(cell);
        }
        if (row != null && row.getChildCount() == 1) {
            View filler = new View(a);
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0, 1, 1f);
            flp.leftMargin = Ui.dp(a, 5);
            row.addView(filler, flp);
        }
    }

    private String coverImage(JSONObject mem) {
        if ("moment".equals(mem.optString("kind"))) {
            String src = a.store.resolveMedia(mem.optString("image", ""));
            return src != null && src.startsWith("data:image") ? src : null;
        }
        JSONArray msgs = mem.optJSONArray("msgs");
        for (int k = 0; msgs != null && k < msgs.length(); k++) {
            JSONObject m = msgs.optJSONObject(k);
            if (m != null && "img".equals(m.optString("type"))) {
                String src = a.store.resolveMedia(m.optString("src", ""));
                if (src != null && src.startsWith("data:image")) return src;
            }
        }
        return null;
    }

    private String previewText(JSONObject mem) {
        if ("moment".equals(mem.optString("kind"))) {
            String text = mem.optString("text", "");
            return text.isEmpty() ? mem.optString("who", "") + " 的朋友圈" : text;
        }
        StringBuilder sb = new StringBuilder();
        JSONArray msgs = mem.optJSONArray("msgs");
        for (int k = 0; msgs != null && k < msgs.length() && sb.length() < 80; k++) {
            JSONObject m = msgs.optJSONObject(k);
            if (m == null) continue;
            if ("sys".equals(m.optString("type")) && !showSys) continue;
            String who = "sys".equals(m.optString("type")) ? "" : ("me".equals(m.optString("side")) ? "我：" : a.store.displayName() + "：");
            if (sb.length() > 0) sb.append('\n');
            sb.append(who).append(MemoryEditPage.describe(m));
        }
        return sb.length() == 0 ? "（空）" : sb.toString();
    }

    /* ---------- 单条收藏 ---------- */

    private void renderSingles() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
        JSONArray chat = a.store.chat();
        List<JSONObject> favs = new ArrayList<>();
        for (int i = 0; i < chat.length(); i++) {
            JSONObject msg = chat.optJSONObject(i);
            if (msg == null || !msg.optBoolean("favorite", false) || msg.optBoolean("recall", false)) continue;
            if ("sys".equals(msg.optString("type")) && !showSys) continue;
            favs.add(msg);
        }
        if (favs.isEmpty()) return;
        favs.sort((x, y) -> newestFirst ? Long.compare(y.optLong("t"), x.optLong("t"))
                : Long.compare(x.optLong("t"), y.optLong("t")));
        LinearLayout box = section("单条收藏 · " + favs.size(), Icons.STAR, singleOpen, open -> singleOpen = open);
        LinearLayout body = sectionBody(box);
        for (final JSONObject msg : favs) {
            LinearLayout row = Ui.column(a);
            row.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
            LinearLayout who = Ui.row(a);
            who.setGravity(Gravity.CENTER_VERTICAL);
            boolean sys = "sys".equals(msg.optString("type"));
            TextView whoText = Ui.boldText(a, sys ? "系统" : "me".equals(msg.optString("side")) ? "我" : a.store.displayName(),
                    12, Ui.plum(a, a.store));
            who.addView(whoText, Ui.weighted());
            who.addView(Ui.text(a, fmt.format(new Date(msg.optLong("t"))), 11, Ui.faintInk(a, a.store)));
            row.addView(who);
            TextView bodyText = Ui.text(a, MemoryEditPage.describe(msg), 14, Ui.ink(a, a.store));
            bodyText.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 4));
            row.addView(bodyText);
            LinearLayout actions = Ui.row(a);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            TextView jump = Ui.boldText(a, "定位到聊天", 12, Ui.plum(a, a.store));
            final String id = msg.optString("id", "");
            jump.setOnClickListener(v -> {
                a.goPage("pageChat", false);
                Page chatPage = a.page("pageChat");
                if (chatPage instanceof ChatPage && !id.isEmpty()) {
                    ((ChatPage) chatPage).jumpToMessage(id);
                }
            });
            actions.addView(jump, Ui.weighted());
            TextView unfav = Ui.boldText(a, "取消收藏", 12, a.getColor(R.color.danger));
            unfav.setOnClickListener(v -> {
                try {
                    msg.put("favorite", false);
                    a.store.save();
                    refresh();
                    a.toast("已取消收藏");
                } catch (JSONException ignored) {
                }
            });
            actions.addView(unfav);
            row.addView(actions);
            View line = new View(a);
            line.setBackgroundColor(Ui.line(a, a.store));
            row.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
            body.addView(row);
        }
        content.addView(box);
    }
}

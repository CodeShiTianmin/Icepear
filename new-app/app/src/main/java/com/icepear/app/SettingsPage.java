package com.icepear.app;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 设置页：角色管理、名称/头像、自动回复参数、聊天显示、模拟行为开关、
 * 夜间模式、气泡与字体样式、壁纸、全局美化、提示音与自定义音效、
 * 钱包、数据备份/恢复/导出、聊天记录导出、重置。
 */
public class SettingsPage extends Page {

    private LinearLayout content;
    /** 对应浏览器 #setTabs：base / style / data */
    private String tab = "base";

    public SettingsPage(MainActivity activity) {
        super(activity);
    }

    @Override
    protected View create() {
        content = Ui.column(a);
        return pageWithBar("设置", content);
    }

    @Override
    public void refresh() {
        if (content == null) return;
        content.removeAllViews();
        JSONObject role = a.store.role();
        if (role == null) return;

        LinearLayout tabs = Ui.row(a);
        tabs.setPadding(0, 0, 0, Ui.dp(a, 8));
        tabs.addView(tabButton("基础", "base"));
        tabs.addView(tabButton("样式", "style"));
        tabs.addView(tabButton("数据", "data"));
        content.addView(tabs);

        switch (tab) {
            case "style":
                renderStyleCards();
                break;
            case "data":
                renderPatchCard();
                renderSnapshotCard();
                renderDataCard();
                break;
            default:
                renderRoleCard(role);
                renderNamesCard(role);
                renderReplyCard();
                renderChatOptCard();
                renderSimCard();
                renderSoundCard();
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

    /* ---------- 折叠卡片 ---------- */

    /** 设置页所有分组默认折叠，展开状态在本页内记忆 */
    private final java.util.Set<String> opened = new java.util.HashSet<>();

    private LinearLayout section(String title) {
        return section(title, null, opened.contains(title),
                open -> {
                    if (open) opened.add(title);
                    else opened.remove(title);
                });
    }

    private LinearLayout body(LinearLayout section) {
        return (LinearLayout) section.getTag();
    }

    private LinearLayout switchRow(String label, boolean checked, OnToggle onToggle) {
        LinearLayout row = Ui.row(a);
        row.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
        row.addView(Ui.text(a, label, 13, Ui.ink(a, a.store)), Ui.weighted());
        Switch toggle = new Switch(a);
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener((v, isChecked) -> onToggle.run(isChecked));
        row.addView(toggle);
        return row;
    }

    private interface OnToggle {
        void run(boolean value);
    }

    private LinearLayout valueRow(String label, String value, Runnable onClick) {
        LinearLayout row = Ui.row(a);
        row.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
        row.addView(Ui.text(a, label, 13, Ui.ink(a, a.store)), Ui.weighted());
        row.addView(Ui.boldText(a, value + " ›", 13, Ui.plum(a, a.store)));
        row.setOnClickListener(v -> onClick.run());
        return row;
    }

    /* ---------- 角色 ---------- */

    private void renderRoleCard(JSONObject role) {
        LinearLayout section = section("陪伴对象");
        LinearLayout body = body(section);
        JSONObject roles = a.store.data.optJSONObject("roles");
        String activeId = a.store.data.optString("activeRole");
        Iterator<String> it = roles.keys();
        while (it.hasNext()) {
            final String id = it.next();
            JSONObject item = roles.optJSONObject(id);
            if (item == null) continue;
            boolean on = id.equals(activeId);
            LinearLayout row = Ui.row(a);
            row.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
            row.addView(Ui.boldText(a, (on ? "● " : "○ ") + item.optString("name"),
                    14, on ? Ui.plum(a, a.store) : Ui.ink(a, a.store)), Ui.weighted());
            if (!on) {
                TextView use = Ui.boldText(a, "切换", 12, Ui.plum(a, a.store));
                use.setOnClickListener(v -> {
                    try {
                        a.store.data.put("activeRole", id);
                        a.store.ensureV230Data();
                        a.store.save();
                        a.applyTheme();
                        a.goPage("pageSet", false);
                    } catch (JSONException ignored) {
                    }
                });
                row.addView(use);
                TextView del = Ui.boldText(a, "删除", 12, a.getColor(R.color.danger));
                del.setPadding(Ui.dp(a, 14), 0, 0, 0);
                del.setOnClickListener(v -> Dialogs.confirm(a, a.store, "⌫",
                        "删除“" + item.optString("name") + "”？", "他的聊天记录会一起删除",
                        "删除", true, () -> {
                            roles.remove(id);
                            a.store.save();
                            refresh();
                        }));
                row.addView(del);
            }
            body.addView(row);
        }
        TextView add = Ui.boldText(a, "＋ 新建陪伴对象", 13, Ui.plum(a, a.store));
        add.setPadding(0, Ui.dp(a, 10), 0, 0);
        add.setOnClickListener(v -> Dialogs.prompt(a, a.store, "＋", "新建陪伴对象", "他的名字",
                "例如：小鹿", "", value -> {
                    try {
                        String id = Store.uid("role");
                        a.store.data.optJSONObject("roles").put(id, a.store.newRole(value));
                        a.store.data.put("activeRole", id);
                        a.store.ensureV230Data();
                        a.store.save();
                        a.applyTheme();
                        refresh();
                    } catch (JSONException ignored) {
                    }
                }));
        body.addView(add);
        content.addView(section);
    }

    /* ---------- 名称与头像 ---------- */

    private void renderNamesCard(JSONObject role) {
        LinearLayout section = section("名称与头像");
        LinearLayout body = body(section);
        body.addView(valueRow("他的名字", role.optString("name"), () ->
                promptRoleField(role, "name", "他的名字")));
        body.addView(valueRow("他的备注", role.optString("nickname", "").isEmpty()
                ? "未设置" : role.optString("nickname"), () ->
                promptRoleField(role, "nickname", "他的备注")));
        body.addView(valueRow("我的名字", role.optString("myName", "未设置"), () ->
                promptRoleField(role, "myName", "我的名字")));
        body.addView(valueRow("他的头像", avatarLabel(role.optString("avatar")), () ->
                pickAvatar(role, "avatar")));
        body.addView(valueRow("我的头像", avatarLabel(role.optString("myAvatar")), () ->
                pickAvatar(role, "myAvatar")));
        body.addView(hint("头像可以从相册选择，也可以长按上面两项改成 Emoji/文字头像"));
        content.addView(section);
    }

    private String avatarLabel(String value) {
        if (value == null || value.isEmpty()) return "默认";
        if (value.startsWith("idb:") || value.startsWith("data:")) return "图片";
        return value;
    }

    private void promptRoleField(JSONObject role, String key, String label) {
        Dialogs.prompt(a, a.store, "✎", label, label, "", role.optString(key), value -> {
            try {
                role.put(key, value);
                a.store.save();
                refresh();
                a.applyTheme();
            } catch (JSONException ignored) {
            }
        });
    }

    private void pickAvatar(JSONObject role, String key) {
        Dialogs.Field mode = new Dialogs.Field("mode", "头像类型");
        mode.optionValues = new String[]{"image", "text"};
        mode.optionLabels = new String[]{"从相册选择图片", "使用 Emoji / 文字"};
        Dialogs.form(a, a.store, "◐", "设置头像", null, "继续", Dialogs.fields(mode), values -> {
            if ("image".equals(values.get("mode"))) {
                a.pickFile("image/*", (bytes, mime, name) -> {
                    String ref = a.store.importImage(bytes, mime);
                    if (ref.isEmpty()) {
                        a.toast("图片导入失败");
                        return;
                    }
                    try {
                        role.put(key, ref);
                        a.store.save();
                        refresh();
                    } catch (JSONException ignored) {
                    }
                });
            } else {
                Dialogs.prompt(a, a.store, "◐", "Emoji / 文字头像", "内容", "例如：🦌", "", value -> {
                    try {
                        role.put(key, value);
                        a.store.save();
                        refresh();
                    } catch (JSONException ignored) {
                    }
                });
            }
        });
    }

    /* ---------- 自动回复 ---------- */

    private void renderReplyCard() {
        JSONObject reply = a.store.data.optJSONObject("reply");
        LinearLayout section = section("自动回复");
        LinearLayout body = body(section);
        body.addView(valueRow("回复延迟（秒）", reply.optInt("delayMin", 10) + " ~ " + reply.optInt("delayMax", 300),
                () -> promptRange(reply, "delayMin", "delayMax", "回复延迟（秒）")));
        body.addView(valueRow("每次回复条数", reply.optInt("replyMin", 1) + " ~ " + reply.optInt("replyMax", 3),
                () -> promptRange(reply, "replyMin", "replyMax", "每次回复条数")));
        body.addView(valueRow("多条消息间隔（秒）", String.valueOf(reply.optInt("gap", 3)),
                () -> promptInt(reply, "gap", "多条消息间隔（秒）")));
        body.addView(switchRow("他会主动发消息", reply.optBoolean("active", false), value -> {
            try {
                reply.put("active", value);
                a.store.save();
                a.logic.startActiveLoop();
            } catch (JSONException ignored) {
            }
        }));
        body.addView(valueRow("主动消息间隔（秒）", reply.optInt("activeMin", 300) + " ~ " + reply.optInt("activeMax", 1800),
                () -> promptRange(reply, "activeMin", "activeMax", "主动消息间隔（秒）")));
        body.addView(valueRow("已读不回概率（%）", String.valueOf(reply.optInt("ignoreRate", 20)),
                () -> promptInt(reply, "ignoreRate", "已读不回概率（%）")));
        content.addView(section);
    }

    private void promptRange(JSONObject target, String minKey, String maxKey, String title) {
        Dialogs.Field min = new Dialogs.Field("min", "最小值");
        min.number = true;
        min.value = String.valueOf(target.optInt(minKey));
        Dialogs.Field max = new Dialogs.Field("max", "最大值");
        max.number = true;
        max.value = String.valueOf(target.optInt(maxKey));
        Dialogs.form(a, a.store, "✎", title, null, "保存", Dialogs.fields(min, max), values -> {
            try {
                int lo = Integer.parseInt(values.getOrDefault("min", "0").trim());
                int hi = Integer.parseInt(values.getOrDefault("max", "0").trim());
                if (lo < 0 || hi < lo) {
                    a.toast("数值无效");
                    return;
                }
                target.put(minKey, lo).put(maxKey, hi);
                a.store.save();
                refresh();
            } catch (Exception e) {
                a.toast("数值无效");
            }
        });
    }

    private void promptInt(JSONObject target, String key, String title) {
        Dialogs.prompt(a, a.store, "✎", title, title, "", String.valueOf(target.optInt(key)), value -> {
            try {
                int parsed = Integer.parseInt(value.trim());
                if (parsed < 0) {
                    a.toast("数值无效");
                    return;
                }
                target.put(key, parsed);
                a.store.save();
                refresh();
            } catch (Exception e) {
                a.toast("数值无效");
            }
        });
    }

    /* ---------- 聊天显示 ---------- */

    private void renderChatOptCard() {
        JSONObject chatOpt = a.store.data.optJSONObject("chatOpt");
        LinearLayout section = section("聊天显示");
        LinearLayout body = body(section);
        body.addView(valueRow("时间显示", optLabel(chatOpt.optString("timeMode", "all")), () ->
                promptMode(chatOpt, "timeMode", "时间显示")));
        body.addView(valueRow("已读显示", optLabel(chatOpt.optString("readMode", "all")), () ->
                promptMode(chatOpt, "readMode", "已读显示")));
        body.addView(switchRow("他偶尔已读不回", chatOpt.optBoolean("hisIgnore", false), value -> {
            try {
                chatOpt.put("hisIgnore", value);
                a.store.save();
            } catch (JSONException ignored) {
            }
        }));
        content.addView(section);
    }

    private String optLabel(String mode) {
        switch (mode) {
            case "me": return "只显示我的";
            case "his": return "只显示他的";
            case "none": return "都不显示";
            default: return "全部显示";
        }
    }

    private void promptMode(JSONObject target, String key, String title) {
        Dialogs.Field field = new Dialogs.Field("mode", title);
        field.optionValues = new String[]{"all", "me", "his", "none"};
        field.optionLabels = new String[]{"全部显示", "只显示我的", "只显示他的", "都不显示"};
        field.value = target.optString(key, "all");
        Dialogs.form(a, a.store, "✎", title, null, "保存", Dialogs.fields(field), values -> {
            try {
                target.put(key, values.get("mode"));
                a.store.save();
                refresh();
                a.onChatChanged(false);
            } catch (JSONException ignored) {
            }
        });
    }

    /* ---------- 模拟行为 ---------- */

    private void renderSimCard() {
        JSONObject sim = a.store.data.optJSONObject("sim");
        LinearLayout section = section("模拟行为");
        LinearLayout body = body(section);
        body.addView(simSwitch(sim, "bioClock", "生物钟状态（他的状态自动变化）", () -> a.logic.startStatusLoop()));
        body.addView(simSwitch(sim, "recallReact", "撤回后他会追问", null));
        body.addView(hint("追问文案在“氛围”页的“撤回后他会追问”文案池里维护"));
        body.addView(simSwitch(sim, "memory", "他说过的回忆（偶尔重提他说过的话）", null));
        body.addView(valueRow("主动发红包/转账/礼物概率（%）", String.valueOf(sim.optInt("txRate", 20)), () ->
                promptInt2(sim, "txRate", "主动发红包/转账/礼物概率（%）", 0, 100)));
        body.addView(simSwitch(sim, "festival", "节日自动送祝福", () -> a.logic.checkFestival()));
        body.addView(simSwitch(sim, "autoNight", "自动夜间模式（22:00-7:00）", () -> a.logic.checkAutoNight()));
        content.addView(section);
    }

    private LinearLayout simSwitch(JSONObject sim, String key, String label, Runnable after) {
        return switchRow(label, sim.optBoolean(key, false), value -> {
            try {
                sim.put(key, value);
                a.store.save();
                if (after != null) after.run();
            } catch (JSONException ignored) {
            }
        });
    }

    /* ---------- 样式 ---------- */

    private static final String[] BUBBLE_SWATCHES = {
            "#95ec69", "#6d3b58", "#f6c1cc", "#ffd8a8", "#bfe3ff", "#c9f0d6", "#e6ddff", "#fff", "#fffaf7", "#2d252a", "#111", "#ffffff"};
    private static final String[] PAGE_SWATCHES = {
            "#f8f3ef", "#fff4f4", "#f4f7fb", "#f4f8f5", "#f7f2ff", "#fffaf7", "#ffffff", "#eef2f7", "#1b171a"};
    private static final String[] ACCENT_SWATCHES = {
            "#6d3b58", "#e08578", "#7fb28f", "#5b8dd6", "#c47dbb", "#d9a441", "#3f7f7a", "#333333"};

    private void renderStyleCards() {
        renderPreviewCard();
        renderAppearanceCard();
        renderFontCard();
        renderBubbleCard();
        renderWallpaperCard();
        renderBeautyCard();
        renderBootCard();
    }

    /** 预览：把当前气泡/壁纸/美化效果合在一起看 */
    private void renderPreviewCard() {
        LinearLayout section = section("预览");
        LinearLayout body = body(section);
        body.addView(bubblePreview());
        body.addView(hint("这里实时反映气泡、字体、壁纸和全局美化的当前效果"));
        content.addView(section);
    }

    private void renderAppearanceCard() {
        LinearLayout section = section("界面外观");
        LinearLayout body = body(section);
        body.addView(switchRow("夜间模式", a.store.data.optBoolean("dark", false), value -> {
            try {
                a.store.data.put("dark", value);
                a.store.save();
                a.applyTheme();
                a.goPage("pageSet", false);
            } catch (JSONException ignored) {
            }
        }));
        body.addView(valueRow("视频通话背景", a.store.data.optString("videoBg", "").isEmpty()
                ? "默认" : "自定义图片", this::pickVideoBg));
        content.addView(section);
    }

    private void renderFontCard() {
        JSONObject font = a.store.data.optJSONObject("font");
        LinearLayout section = section("字体");
        LinearLayout body = body(section);
        body.addView(stepperRow("字号", font.optInt("size", 15), 12, 22, "sp", value -> {
            try {
                font.put("size", value);
                a.store.save();
                a.onChatChanged(false);
                refresh();
            } catch (JSONException ignored) {
            }
        }));
        content.addView(section);
    }

    private void renderBubbleCard() {
        JSONObject font = a.store.data.optJSONObject("font");
        JSONObject theme = a.store.data.optJSONObject("theme");
        LinearLayout section = section("气泡");
        LinearLayout body = body(section);
        body.addView(bubblePreview());
        body.addView(stepperRow("气泡圆角", font.optInt("radius", 10), 0, 24, "", value -> {
            try {
                font.put("radius", value);
                a.store.save();
                a.onChatChanged(false);
                refresh();
            } catch (JSONException ignored) {
            }
        }));
        body.addView(swatchRow("我的气泡", theme, "myBg", "#95ec69", BUBBLE_SWATCHES));
        body.addView(swatchRow("我的文字", theme, "myText", "#111", BUBBLE_SWATCHES));
        body.addView(swatchRow("他的气泡", theme, "hisBg", "#fff", BUBBLE_SWATCHES));
        body.addView(swatchRow("他的文字", theme, "hisText", "#111", BUBBLE_SWATCHES));
        content.addView(section);
    }

    private void renderWallpaperCard() {
        JSONObject wallpaper = a.store.data.optJSONObject("wallpaper");
        LinearLayout section = section("聊天壁纸");
        LinearLayout body = body(section);
        String current = wallpaper.optString("image", "").isEmpty() ? wallpaper.optString("preset", "默认") : "图片";
        LinearLayout row = Ui.row(a);
        row.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
        String[][] presets = {{"默认", "#F9F5F2", "#F5EFEB"}, {"粉色", "#FFF4F4", "#F8E7EC"},
                {"蓝色", "#F4F7FB", "#E8F0F4"}, {"绿色", "#F4F8F5", "#E8F1EB"}, {"星空", "#29243C", "#151323"}};
        for (String[] preset : presets) {
            boolean on = preset[0].equals(current);
            LinearLayout tile = Ui.column(a);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            View swatch = new View(a);
            android.graphics.drawable.GradientDrawable g = Ui.gradient(
                    Color.parseColor(preset[1]), Color.parseColor(preset[2]), Ui.dp(a, 10));
            if (on) g.setStroke(Ui.dp(a, 2), Ui.plum(a, a.store));
            else g.setStroke(Ui.dp(a, 1), Ui.line(a, a.store));
            swatch.setBackground(g);
            tile.addView(swatch, Ui.lp(Ui.dp(a, 44), Ui.dp(a, 64)));
            TextView label = Ui.text(a, preset[0], 11, on ? Ui.plum(a, a.store) : Ui.mutedInk(a, a.store));
            label.setPadding(0, Ui.dp(a, 4), 0, 0);
            tile.addView(label);
            tile.setOnClickListener(v -> {
                try {
                    wallpaper.put("preset", preset[0]).put("image", "");
                    a.store.save();
                    rebuildChat();
                    refresh();
                } catch (JSONException ignored) {
                }
            });
            LinearLayout.LayoutParams lp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.weight = 1;
            row.addView(tile, lp);
        }
        body.addView(row);
        if (!wallpaper.optString("image", "").isEmpty()) {
            android.widget.ImageView image = new android.widget.ImageView(a);
            image.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            image.setClipToOutline(true);
            image.setBackground(Ui.rounded(Ui.line(a, a.store), Ui.dp(a, 12)));
            Ui.setImage(image, a.store.resolveMedia(wallpaper.optString("image", "")));
            LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 120));
            lp.topMargin = Ui.dp(a, 8);
            body.addView(image, lp);
        }
        body.addView(button("从相册选择壁纸图片", false, () -> a.pickFile("image/*", (bytes, mime, name) -> {
            String ref = a.store.importImage(bytes, mime);
            if (ref.isEmpty()) {
                a.toast("图片导入失败");
                return;
            }
            try {
                a.store.deleteMedia(wallpaper.optString("image", ""));
                wallpaper.put("preset", "图片").put("image", ref);
                a.store.save();
                rebuildChat();
                refresh();
            } catch (JSONException ignored) {
            }
        })));
        if (!wallpaper.optString("image", "").isEmpty()) {
            body.addView(dangerButton("清除自定义壁纸", () -> {
                try {
                    a.store.deleteMedia(wallpaper.optString("image", ""));
                    wallpaper.put("image", "").put("preset", "默认");
                    a.store.save();
                    rebuildChat();
                    refresh();
                } catch (JSONException ignored) {
                }
            }));
        }
        content.addView(section);
    }

    private void renderBeautyCard() {
        JSONObject beauty = a.store.data.optJSONObject("beauty");
        LinearLayout section = section("全局美化");
        LinearLayout body = body(section);
        body.addView(beautyPreview(beauty));
        body.addView(swatchRow("页面底色", beauty, "pageBg", "#f8f3ef", PAGE_SWATCHES));
        body.addView(swatchRow("卡片底色", beauty, "surface", "#fffaf7", PAGE_SWATCHES));
        body.addView(swatchRow("主题色", beauty, "accent", "#6d3b58", ACCENT_SWATCHES));
        TextView reset = Ui.boldText(a, "恢复默认美化", 12, a.getColor(R.color.danger));
        reset.setPadding(0, Ui.dp(a, 8), 0, 0);
        reset.setOnClickListener(v -> {
            try {
                beauty.put("pageBg", "#f8f3ef").put("surface", "#fffaf7")
                        .put("accent", "#6d3b58").put("topBg", "#f8f3ef").put("navBg", "#fffaf7");
                a.store.save();
                a.applyTheme();
                a.goPage("pageSet", false);
            } catch (JSONException ignored) {
            }
        });
        body.addView(reset);
        content.addView(section);
    }

    private void renderBootCard() {
        JSONObject ui = icepearUi();
        LinearLayout section = section("开屏动画");
        LinearLayout body = body(section);
        String current = ui.optString("bootAnim", "hearts");
        android.widget.FrameLayout stage = a.bootStage(current, ui.optString("bootImg", ""));
        stage.setBackground(Ui.rounded(Ui.paper(a, a.store), Ui.dp(a, 14)));
        stage.setClipToOutline(true);
        LinearLayout stageWrap = Ui.column(a);
        stageWrap.setGravity(Gravity.CENTER_HORIZONTAL);
        stageWrap.addView(stage, Ui.lp(Ui.dp(a, 220), Ui.dp(a, 130)));
        body.addView(stageWrap);
        body.addView(hint("上面是实时预览：切换动画会立即播放，自定义图片叠在动画上方"));
        LinearLayout chips = Ui.row(a);
        chips.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 4));
        String[][] anims = {{"hearts", "爱心"}, {"bubbles", "气泡"}, {"stars", "星星"}, {"avatar", "头像"}, {"off", "关闭"}};
        for (String[] anim : anims) {
            boolean on = anim[0].equals(current);
            TextView chip = Ui.boldText(a, anim[1], 12, on ? Color.WHITE : Ui.ink(a, a.store));
            chip.setBackground(on ? Ui.rounded(Ui.plum(a, a.store), Ui.dp(a, 12))
                    : Ui.roundedStroke(0x00000000, Ui.dp(a, 12), Ui.line(a, a.store), Ui.dp(a, 1)));
            chip.setPadding(Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 6));
            chip.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.weight = 1;
            lp.rightMargin = Ui.dp(a, 6);
            chip.setOnClickListener(v -> {
                try {
                    ui.put("bootAnim", anim[0]);
                    a.store.save();
                    refresh();
                } catch (JSONException ignored) {
                }
            });
            chips.addView(chip, lp);
        }
        body.addView(chips);
        body.addView(button("选择自定义图片（叠在动画上方）", false, () -> a.pickFile("image/*", (bytes, mime, name) -> {
            if (bytes.length > 2 * 1024 * 1024) {
                Dialogs.notice(a, a.store, "!", "图片太大", "请选择2MB以内的图片，否则启动会变慢。");
                return;
            }
            String ref = a.store.importImage(bytes, mime);
            if (ref.isEmpty()) {
                a.toast("图片导入失败");
                return;
            }
            try {
                a.store.deleteMedia(ui.optString("bootImg", ""));
                ui.put("bootImg", ref);
                a.store.save();
                refresh();
            } catch (JSONException ignored) {
            }
        })));
        if (!ui.optString("bootImg", "").isEmpty()) {
            body.addView(dangerButton("清除自定义图片", () -> {
                try {
                    a.store.deleteMedia(ui.optString("bootImg", ""));
                    ui.put("bootImg", "");
                    a.store.save();
                    refresh();
                } catch (JSONException ignored) {
                }
            }));
        }
        content.addView(section);
    }

    /* ---------- 可视化编辑控件 ---------- */

    private interface OnInt {
        void run(int value);
    }

    /** −/+ 步进行，带当前值 */
    private LinearLayout stepperRow(String label, int value, int min, int max, String unit, OnInt onChange) {
        LinearLayout row = Ui.row(a);
        row.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
        row.addView(Ui.text(a, label, 13, Ui.ink(a, a.store)), Ui.weighted());
        row.addView(stepButton("−", () -> {
            if (value > min) onChange.run(value - 1);
        }));
        TextView current = Ui.boldText(a, value + unit, 13, Ui.plum(a, a.store));
        current.setGravity(Gravity.CENTER);
        current.setMinWidth(Ui.dp(a, 48));
        row.addView(current);
        row.addView(stepButton("+", () -> {
            if (value < max) onChange.run(value + 1);
        }));
        return row;
    }

    private TextView stepButton(String text, Runnable onClick) {
        TextView b = Ui.boldText(a, text, 16, Ui.ink(a, a.store));
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.roundedStroke(0x00000000, Ui.dp(a, 10), Ui.line(a, a.store), Ui.dp(a, 1)));
        b.setLayoutParams(Ui.lp(Ui.dp(a, 34), Ui.dp(a, 30)));
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    /** 色板行：一排颜色圆点 + 自定义；点选立即生效 */
    private LinearLayout swatchRow(String label, JSONObject target, String key, String fallback, String[] swatches) {
        LinearLayout box = Ui.column(a);
        box.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 4));
        LinearLayout head = Ui.row(a);
        head.addView(Ui.text(a, label, 13, Ui.ink(a, a.store)), Ui.weighted());
        String current = target.optString(key, fallback);
        head.addView(Ui.boldText(a, current, 12, Ui.plum(a, a.store)));
        box.addView(head);
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(a);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.row(a);
        row.setPadding(0, Ui.dp(a, 6), 0, 0);
        int curColor = Ui.parseColor(current, 0);
        for (String swatch : swatches) {
            int color = Ui.parseColor(swatch, 0);
            View dot = new View(a);
            boolean on = color == curColor;
            android.graphics.drawable.GradientDrawable g = Ui.rounded(color, Ui.dp(a, 15));
            g.setStroke(Ui.dp(a, on ? 3 : 1), on ? Ui.plum(a, a.store) : Ui.line(a, a.store));
            dot.setBackground(g);
            LinearLayout.LayoutParams lp = Ui.lp(Ui.dp(a, 30), Ui.dp(a, 30));
            lp.rightMargin = Ui.dp(a, 8);
            dot.setOnClickListener(v -> applyColor(target, key, swatch));
            row.addView(dot, lp);
        }
        TextView custom = Ui.boldText(a, "#", 13, Ui.plum(a, a.store));
        custom.setGravity(Gravity.CENTER);
        custom.setBackground(Ui.roundedStroke(0x00000000, Ui.dp(a, 15), Ui.plum(a, a.store), Ui.dp(a, 1)));
        custom.setOnClickListener(v -> promptColor(target, key, label));
        row.addView(custom, Ui.lp(Ui.dp(a, 30), Ui.dp(a, 30)));
        scroll.addView(row);
        box.addView(scroll);
        return box;
    }

    private void applyColor(JSONObject target, String key, String color) {
        try {
            target.put(key, color);
            a.store.save();
            a.applyTheme();
            a.onChatChanged(false);
            refresh();
        } catch (JSONException ignored) {
        }
    }

    /** 气泡预览：壁纸底 + 他的一条 + 我的一条 */
    private View bubblePreview() {
        JSONObject theme = a.store.data.optJSONObject("theme");
        JSONObject font = a.store.data.optJSONObject("font");
        JSONObject wallpaper = a.store.data.optJSONObject("wallpaper");
        LinearLayout box = Ui.column(a);
        box.setPadding(Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 12));
        int start, end;
        switch (wallpaper.optString("preset", "默认")) {
            case "粉色": start = 0xFFFFF4F4; end = 0xFFF8E7EC; break;
            case "蓝色": start = 0xFFF4F7FB; end = 0xFFE8F0F4; break;
            case "绿色": start = 0xFFF4F8F5; end = 0xFFE8F1EB; break;
            case "星空": start = 0xFF29243C; end = 0xFF151323; break;
            default: start = 0xFFF9F5F2; end = 0xFFF5EFEB;
        }
        box.setBackground(Ui.gradient(start, end, Ui.dp(a, 14)));
        float radius = Ui.dp(a, font.optInt("radius", 10));
        int size = font.optInt("size", 15);
        TextView his = Ui.text(a, "今天想你了", size, Ui.parseColor(theme.optString("hisText", "#111"), 0xFF111111));
        his.setBackground(Ui.rounded(Ui.parseColor(theme.optString("hisBg", "#fff"), 0xFFFFFFFF) | 0xFF000000, radius));
        his.setPadding(Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 12), Ui.dp(a, 8));
        LinearLayout.LayoutParams hisLp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hisLp.gravity = Gravity.START;
        box.addView(his, hisLp);
        TextView mine = Ui.text(a, "我也是", size, Ui.parseColor(theme.optString("myText", "#111"), 0xFF111111));
        mine.setBackground(Ui.rounded(Ui.parseColor(theme.optString("myBg", "#95ec69"), 0xFF95EC69) | 0xFF000000, radius));
        mine.setPadding(Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 12), Ui.dp(a, 8));
        LinearLayout.LayoutParams mineLp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mineLp.gravity = Gravity.END;
        mineLp.topMargin = Ui.dp(a, 8);
        box.addView(mine, mineLp);
        return box;
    }

    /** 全局美化预览：页面底 + 卡片 + 主题色按钮 */
    private View beautyPreview(JSONObject beauty) {
        int page = Ui.parseColor(beauty.optString("pageBg", "#f8f3ef"), 0xFFF8F3EF);
        int surface = Ui.parseColor(beauty.optString("surface", "#fffaf7"), 0xFFFFFAF7);
        int accent = Ui.parseColor(beauty.optString("accent", "#6d3b58"), 0xFF6D3B58);
        LinearLayout box = Ui.column(a);
        box.setBackground(Ui.rounded(page, Ui.dp(a, 14)));
        box.setPadding(Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 12));
        LinearLayout card = Ui.column(a);
        card.setBackground(Ui.rounded(surface, Ui.dp(a, 12)));
        card.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 10));
        card.addView(Ui.boldText(a, "卡片标题", 13, accent));
        card.addView(Ui.text(a, "卡片正文示例", 12, Ui.mutedInk(a, a.store)));
        TextView btn = Ui.boldText(a, "主题色按钮", 12, Color.WHITE);
        btn.setBackground(Ui.rounded(accent, Ui.dp(a, 10)));
        btn.setPadding(Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 6));
        LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(a, 8);
        card.addView(btn, lp);
        box.addView(card);
        return box;
    }

    private void promptInt2(JSONObject target, String key, String title, int min, int max) {
        Dialogs.prompt(a, a.store, "✎", title, title, "", String.valueOf(target.optInt(key)), value -> {
            try {
                int parsed = Integer.parseInt(value.trim());
                if (parsed < min || parsed > max) {
                    a.toast("范围 " + min + " ~ " + max);
                    return;
                }
                target.put(key, parsed);
                a.store.save();
                refresh();
                a.onChatChanged(false);
            } catch (Exception e) {
                a.toast("数值无效");
            }
        });
    }

    private void promptColor(JSONObject target, String key, String title) {
        Dialogs.prompt(a, a.store, "🎨", title, "颜色（#RRGGBB）", "#95ec69", target.optString(key), value -> {
            String color = value.trim();
            try {
                Color.parseColor(color.length() == 4
                        ? "#" + color.charAt(1) + color.charAt(1) + color.charAt(2)
                        + color.charAt(2) + color.charAt(3) + color.charAt(3) : color);
            } catch (Exception e) {
                a.toast("颜色格式无效");
                return;
            }
            try {
                target.put(key, color);
                a.store.save();
                a.applyTheme();
                a.goPage("pageSet", false);
            } catch (JSONException ignored) {
            }
        });
    }

    /* ---------- 开屏动画 ---------- */

    private JSONObject icepearUi() {
        JSONObject ui = a.store.data.optJSONObject("icepearUi");
        if (ui == null) {
            ui = new JSONObject();
            try {
                a.store.data.put("icepearUi", ui);
            } catch (JSONException ignored) {
            }
        }
        return ui;
    }

    /* ---------- 视频通话背景 ---------- */

    private void pickVideoBg() {
        Dialogs.Field mode = new Dialogs.Field("mode", "视频背景");
        mode.optionValues = new String[]{"@image", "@clear"};
        mode.optionLabels = new String[]{"从相册选择…", "恢复默认背景"};
        mode.value = "@image";
        Dialogs.form(a, a.store, "📹", "视频通话背景", null, "应用", Dialogs.fields(mode), values -> {
            String pick = values.get("mode");
            try {
                if ("@image".equals(pick)) {
                    a.pickFile("image/*", (bytes, mime, name) -> {
                        String ref = a.store.importImage(bytes, mime);
                        if (ref.isEmpty()) {
                            a.toast("图片导入失败");
                            return;
                        }
                        try {
                            a.store.deleteMedia(a.store.data.optString("videoBg", ""));
                            a.store.data.put("videoBg", ref);
                            a.store.save();
                            refresh();
                            a.toast("视频背景已更新");
                        } catch (JSONException ignored) {
                        }
                    });
                    return;
                }
                a.store.deleteMedia(a.store.data.optString("videoBg", ""));
                a.store.data.put("videoBg", "");
                a.store.save();
                refresh();
                a.toast("已恢复默认背景");
            } catch (JSONException ignored) {
            }
        });
    }

    private void rebuildChat() {
        Page chat = a.page("pageChat");
        if (chat != null) chat.rebuild();
        a.toast("壁纸已更新");
    }

    /* ---------- 提示音 ---------- */

    private void renderSoundCard() {
        JSONObject sound = a.store.data.optJSONObject("sound");
        LinearLayout section = section("提示音");
        LinearLayout body = body(section);
        body.addView(valueRow("提示音类型", soundLabel(sound.optString("type", "dingdong")), this::pickSound));
        body.addView(valueRow("音量（0~100）", String.valueOf(sound.optInt("volume", 50)), () ->
                promptInt2(sound, "volume", "音量（0~100）", 0, 100)));
        body.addView(switchRow("收到消息时播放", sound.optBoolean("onRecv", true), value -> {
            try {
                sound.put("onRecv", value);
                a.store.save();
            } catch (JSONException ignored) {
            }
        }));
        body.addView(switchRow("发送消息时播放", sound.optBoolean("onSend", false), value -> {
            try {
                sound.put("onSend", value);
                a.store.save();
            } catch (JSONException ignored) {
            }
        }));

        /* 自定义音效 */
        JSONArray customs = a.store.data.optJSONArray("customSounds");
        for (int i = 0; customs != null && i < customs.length(); i++) {
            final int index = i;
            JSONObject item = customs.optJSONObject(i);
            if (item == null) continue;
            LinearLayout row = Ui.row(a);
            row.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
            row.addView(Ui.text(a, "♪ " + item.optString("name", "自定义音效"), 13, Ui.ink(a, a.store)), Ui.weighted());
            TextView play = Ui.boldText(a, "试听", 12, Ui.plum(a, a.store));
            play.setOnClickListener(v -> a.sound.play("custom:" + item.optString("id")));
            row.addView(play);
            TextView use = Ui.boldText(a, "设为提示音", 12, Ui.plum(a, a.store));
            use.setPadding(Ui.dp(a, 12), 0, 0, 0);
            use.setOnClickListener(v -> {
                try {
                    sound.put("type", "custom:" + item.optString("id"));
                    a.store.save();
                    refresh();
                } catch (JSONException ignored) {
                }
            });
            row.addView(use);
            TextView del = Ui.boldText(a, "删除", 12, a.getColor(R.color.danger));
            del.setPadding(Ui.dp(a, 12), 0, 0, 0);
            del.setOnClickListener(v -> Dialogs.confirm(a, a.store, "⌫", "删除这个音效？", null,
                    "删除", true, () -> {
                        a.store.deleteMedia(item.optString("src", ""));
                        customs.remove(index);
                        a.store.save();
                        refresh();
                    }));
            row.addView(del);
            body.addView(row);
        }
        TextView add = Ui.boldText(a, "＋ 导入自定义音效（≤5MB，最多12个）", 13, Ui.plum(a, a.store));
        add.setPadding(0, Ui.dp(a, 10), 0, 0);
        add.setOnClickListener(v -> importSound());
        body.addView(add);
        content.addView(section);
    }

    private String soundLabel(String type) {
        if (type.startsWith("custom:")) return "自定义音效";
        switch (type) {
            case "bubble": return "泡泡";
            case "soft": return "轻柔";
            case "bell": return "铃铛";
            case "none": return "静音";
            default: return "叮咚";
        }
    }

    private void pickSound() {
        JSONObject sound = a.store.data.optJSONObject("sound");
        Dialogs.Field field = new Dialogs.Field("type", "提示音");
        field.optionValues = new String[]{"dingdong", "bubble", "soft", "bell", "none"};
        field.optionLabels = new String[]{"叮咚", "泡泡", "轻柔", "铃铛", "静音"};
        String current = sound.optString("type", "dingdong");
        field.value = current.startsWith("custom:") ? "dingdong" : current;
        Dialogs.form(a, a.store, "♪", "提示音类型", null, "保存", Dialogs.fields(field), values -> {
            try {
                sound.put("type", values.get("type"));
                a.store.save();
                a.sound.play(values.get("type"));
                refresh();
            } catch (JSONException ignored) {
            }
        });
    }

    private void importSound() {
        JSONArray customs = a.store.data.optJSONArray("customSounds");
        if (customs != null && customs.length() >= 12) {
            Dialogs.notice(a, a.store, "!", "音效数量已达上限", "最多保存 12 个自定义音效，请先删除一些。");
            return;
        }
        a.pickFile("audio/*", (bytes, mime, name) -> {
            if (bytes.length > 5 * 1024 * 1024) {
                Dialogs.notice(a, a.store, "!", "文件太大", "音效文件不能超过 5MB。");
                return;
            }
            String ref = a.store.importAudio(bytes, mime);
            if (ref.isEmpty()) {
                a.toast("音效导入失败");
                return;
            }
            Dialogs.prompt(a, a.store, "♪", "音效名称", "名称", "例如：猫叫", name == null ? "" : name, value -> {
                try {
                    customs.put(new JSONObject().put("id", Store.uid("sound"))
                            .put("name", value).put("src", ref));
                    a.store.save();
                    refresh();
                    a.toast("音效已导入");
                } catch (JSONException ignored) {
                }
            });
        });
    }

    /* ---------- 数据 ---------- */

    /* ---------- 永久补丁 ---------- */

    private JSONArray patches() {
        JSONArray list = a.store.data.optJSONArray("patchLog");
        if (list == null) {
            list = new JSONArray();
            try {
                a.store.data.put("patchLog", list);
            } catch (JSONException ignored) {
            }
        }
        return list;
    }

    private void renderPatchCard() {
        JSONArray list = patches();
        LinearLayout section = section("永久补丁");
        LinearLayout body = body(section);
        if (list.length() == 0) body.addView(hint("暂无永久补丁"));
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA);
        for (int i = 0; i < list.length(); i++) {
            final int index = i;
            JSONObject patch = list.optJSONObject(i);
            if (patch == null) continue;
            boolean on = patch.optBoolean("on", true);
            LinearLayout row = Ui.row(a);
            row.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
            LinearLayout info = Ui.column(a);
            info.addView(Ui.boldText(a, "#" + (i + 1) + " · " + patch.optString("name", "补丁 #" + (i + 1)),
                    13, on ? Ui.ink(a, a.store) : Ui.faintInk(a, a.store)));
            info.addView(Ui.text(a, (on ? "已启用" : "已停用") + " · " + patch.optString("cat", "其他")
                    + " · " + fmt.format(new java.util.Date(patch.optLong("t"))), 11, Ui.faintInk(a, a.store)));
            row.addView(info, Ui.weighted());
            TextView toggle = Ui.boldText(a, on ? "停用" : "启用", 12, Ui.plum(a, a.store));
            toggle.setOnClickListener(v -> {
                try {
                    patch.put("on", !on);
                    a.store.save();
                    refresh();
                } catch (JSONException ignored) {
                }
            });
            row.addView(toggle);
            TextView edit = Ui.boldText(a, "编辑", 12, Ui.plum(a, a.store));
            edit.setPadding(Ui.dp(a, 12), 0, 0, 0);
            edit.setOnClickListener(v -> editPatch(patch));
            row.addView(edit);
            TextView copy = Ui.boldText(a, "复制", 12, Ui.plum(a, a.store));
            copy.setPadding(Ui.dp(a, 12), 0, 0, 0);
            copy.setOnClickListener(v -> {
                android.content.ClipboardManager cm =
                        (android.content.ClipboardManager) a.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("patch", patch.optString("code", "")));
                a.toast("已复制");
            });
            row.addView(copy);
            row.addView(trashButton("删除补丁“" + patch.optString("name", "") + "”？", () -> {
                list.remove(index);
                a.store.save();
                refresh();
            }));
            body.addView(row);
        }
        body.addView(button("＋ 添加补丁", true, () -> editPatch(null)));
        body.addView(hint("原生版不会执行脚本，补丁只作为内容记录保存（名称 / 板块 / 内容），可停用、复制、删除"));
        content.addView(section);
    }

    private void editPatch(JSONObject existing) {
        Dialogs.Field name = new Dialogs.Field("name", "补丁名称");
        name.value = existing != null ? existing.optString("name", "") : "补丁 #" + (patches().length() + 1);
        Dialogs.Field cat = new Dialogs.Field("cat", "所属板块");
        cat.value = existing != null ? existing.optString("cat", "其他") : "其他";
        Dialogs.Field code = new Dialogs.Field("code", "补丁内容");
        code.textarea = true;
        code.value = existing != null ? existing.optString("code", "") : "";
        Dialogs.form(a, a.store, "{ }", existing != null ? "修改补丁" : "添加补丁", null, "保存",
                Dialogs.fields(name, cat, code), values -> {
                    try {
                        JSONObject target = existing != null ? existing : new JSONObject()
                                .put("t", System.currentTimeMillis()).put("on", true);
                        target.put("name", values.getOrDefault("name", "").trim())
                                .put("cat", values.getOrDefault("cat", "").trim().isEmpty()
                                        ? "其他" : values.getOrDefault("cat", "").trim())
                                .put("code", values.getOrDefault("code", ""));
                        if (existing == null) patches().put(target);
                        a.store.save();
                        refresh();
                    } catch (JSONException ignored) {
                    }
                });
    }

    /* ---------- 历史版本 ---------- */

    private void renderSnapshotCard() {
        LinearLayout section = section("历史版本");
        LinearLayout body = body(section);
        body.addView(button("🕘 保存数据快照", false, this::saveSnapshot));
        renderSnapshots(body);
        content.addView(section);
    }

    private void renderDataCard() {
        LinearLayout section = section("数据");
        LinearLayout body = body(section);
        body.addView(button("⬇ 备份全部数据（JSON）", false, this::exportBackup));
        body.addView(button("⬆ 从备份恢复", false, this::importBackup));
        body.addView(button("📄 导出聊天记录（文本）", false, this::exportChat));
        body.addView(dangerButton("清空当前角色聊天记录", this::clearChat));
        body.addView(dangerButton("重置全部数据", () ->
                Dialogs.confirm(a, a.store, "⚠", "重置全部数据？", "所有角色、聊天、设置都会被清空",
                        "全部重置", true, a::resetAllData)));
        content.addView(section);
    }

    /* ---------- 历史快照 ---------- */

    private void saveSnapshot() {
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat(
                "MM-dd HH:mm", java.util.Locale.CHINA);
        Dialogs.prompt(a, a.store, "🕘", "保存快照", "快照名称", "例如：改动前备份",
                "快照 " + fmt.format(new java.util.Date()), value -> {
                    try {
                        a.store.saveSnapshot(value);
                        refresh();
                        a.toast("快照已保存");
                    } catch (JSONException e) {
                        a.toast("快照保存失败");
                    }
                });
    }

    private void renderSnapshots(LinearLayout body) {
        JSONArray snaps = a.store.data.optJSONArray("snapshots");
        if (snaps == null || snaps.length() == 0) {
            body.addView(hint("暂无历史快照（最多保留 10 个）"));
            return;
        }
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm", java.util.Locale.CHINA);
        for (int i = snaps.length() - 1; i >= 0; i--) {
            final int index = i;
            JSONObject snap = snaps.optJSONObject(i);
            if (snap == null) continue;
            LinearLayout row = Ui.row(a);
            row.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
            LinearLayout info = Ui.column(a);
            info.addView(Ui.text(a, "🕘 " + snap.optString("name", "快照"), 13, Ui.ink(a, a.store)));
            TextView time = Ui.text(a, fmt.format(new java.util.Date(snap.optLong("t"))), 11, Ui.faintInk(a, a.store));
            info.addView(time);
            row.addView(info, Ui.weighted());
            TextView restore = Ui.boldText(a, "恢复", 12, Ui.plum(a, a.store));
            restore.setOnClickListener(v -> Dialogs.confirm(a, a.store, "🕘", "恢复到这个快照？",
                    "当前数据会被快照内容覆盖（快照列表会保留）", "恢复", false, () -> {
                        try {
                            a.store.restoreSnapshot(index);
                            a.afterDataImported();
                            a.toast("已恢复快照");
                        } catch (JSONException e) {
                            a.toast("恢复失败，快照数据损坏");
                        }
                    }));
            row.addView(restore);
            TextView del = Ui.boldText(a, "删除", 12, a.getColor(R.color.danger));
            del.setPadding(Ui.dp(a, 14), 0, 0, 0);
            del.setOnClickListener(v -> Dialogs.confirm(a, a.store, "⌫", "删除这个快照？", null,
                    "删除", true, () -> {
                        snaps.remove(index);
                        a.store.save();
                        refresh();
                    }));
            row.addView(del);
            body.addView(row);
        }
    }

    private void exportBackup() {
        a.saveFile("application/json", "icepear-backup-" + System.currentTimeMillis() + ".json", uri -> {
            try (OutputStream out = a.getContentResolver().openOutputStream(uri)) {
                out.write(a.store.exportJson().getBytes(StandardCharsets.UTF_8));
                a.toast("备份已保存");
            } catch (Exception e) {
                a.toast("备份失败");
            }
        });
    }

    private void importBackup() {
        Dialogs.confirm(a, a.store, "⚠", "从备份恢复？", "当前数据会被备份内容覆盖", "选择备份文件",
                false, () -> a.pickFile("*/*", (bytes, mime, name) -> {
                    try {
                        a.store.importJson(new String(bytes, StandardCharsets.UTF_8));
                        a.afterDataImported();
                    } catch (Exception e) {
                        Dialogs.notice(a, a.store, "!", "恢复失败", "备份文件格式不正确。");
                    }
                }));
    }

    private void exportChat() {
        JSONArray chat = a.store.chat();
        StringBuilder sb = new StringBuilder();
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA);
        for (int i = 0; i < chat.length(); i++) {
            JSONObject msg = chat.optJSONObject(i);
            if (msg == null) continue;
            String who = "sys".equals(msg.optString("type")) ? "系统"
                    : "me".equals(msg.optString("side")) ? "我" : a.store.displayName();
            String text = msg.optBoolean("recall", false) ? "（已撤回）" : describeForExport(msg);
            sb.append('[').append(fmt.format(new java.util.Date(msg.optLong("t"))))
                    .append("] ").append(who).append("：").append(text).append('\n');
        }
        final String data = sb.toString();
        a.saveFile("text/plain", "icepear-chat-" + System.currentTimeMillis() + ".txt", uri -> {
            try (OutputStream out = a.getContentResolver().openOutputStream(uri)) {
                out.write(data.getBytes(StandardCharsets.UTF_8));
                a.toast("聊天记录已导出");
            } catch (Exception e) {
                a.toast("导出失败");
            }
        });
    }

    private String describeForExport(JSONObject msg) {
        switch (msg.optString("type", "")) {
            case "img": return "[图片]";
            case "loc": return "[位置] " + msg.optString("text");
            case "red": return "[红包] ¥" + Ui.fmtMoney(msg.optDouble("amount", 0));
            case "zhuan": return "[转账] ¥" + Ui.fmtMoney(msg.optDouble("amount", 0));
            case "gift": return "[礼物] " + msg.optString("gift");
            default: return msg.optString("text", "");
        }
    }

    private void clearChat() {
        Dialogs.confirm(a, a.store, "⌫", "清空聊天记录？", "只清空当前角色，其他数据不受影响",
                "清空", true, () -> {
                    try {
                        a.store.role().put("chat", new JSONArray());
                        a.store.save();
                        a.onChatChanged(false);
                        a.toast("已清空");
                    } catch (JSONException ignored) {
                    }
                });
    }
}

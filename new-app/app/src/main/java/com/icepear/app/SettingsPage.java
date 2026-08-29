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

        renderRoleCard(role);
        renderNamesCard(role);
        renderReplyCard();
        renderChatOptCard();
        renderSimCard();
        renderStyleCard();
        renderSoundCard();
        renderWalletCard(role);
        renderDataCard();
    }

    /* ---------- 折叠卡片 ---------- */

    private LinearLayout section(String title) {
        LinearLayout box = card(null);
        LinearLayout head = Ui.row(a);
        TextView label = Ui.boldText(a, title, 15, Ui.ink(a, a.store));
        head.addView(label, Ui.weighted());
        TextView arrow = Ui.boldText(a, "▾", 14, Ui.faintInk(a, a.store));
        head.addView(arrow);
        box.addView(head);
        LinearLayout body = Ui.column(a);
        box.addView(body);
        head.setOnClickListener(v -> {
            boolean open = body.getVisibility() == View.VISIBLE;
            body.setVisibility(open ? View.GONE : View.VISIBLE);
            arrow.setText(open ? "▸" : "▾");
        });
        box.setTag(body);
        return box;
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

    private void renderStyleCard() {
        LinearLayout section = section("样式与氛围");
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
        JSONObject font = a.store.data.optJSONObject("font");
        body.addView(valueRow("字号", font.optInt("size", 15) + "sp", () ->
                promptInt2(font, "size", "字号（12~22）", 12, 22)));
        body.addView(valueRow("气泡圆角", String.valueOf(font.optInt("radius", 10)), () ->
                promptInt2(font, "radius", "气泡圆角（0~24）", 0, 24)));
        JSONObject theme = a.store.data.optJSONObject("theme");
        body.addView(valueRow("我的气泡颜色", theme.optString("myBg", "#95ec69"), () ->
                promptColor(theme, "myBg", "我的气泡颜色")));
        body.addView(valueRow("我的文字颜色", theme.optString("myText", "#111"), () ->
                promptColor(theme, "myText", "我的文字颜色")));
        body.addView(valueRow("他的气泡颜色", theme.optString("hisBg", "#fff"), () ->
                promptColor(theme, "hisBg", "他的气泡颜色")));
        body.addView(valueRow("他的文字颜色", theme.optString("hisText", "#111"), () ->
                promptColor(theme, "hisText", "他的文字颜色")));
        JSONObject wallpaper = a.store.data.optJSONObject("wallpaper");
        body.addView(valueRow("聊天壁纸", wallpaper.optString("image", "").isEmpty()
                ? wallpaper.optString("preset", "默认") : "自定义图片", this::pickWallpaper));
        body.addView(valueRow("开屏动画", bootAnimLabel(), this::pickBootAnim));
        body.addView(valueRow("视频通话背景", a.store.data.optString("videoBg", "").isEmpty()
                ? "默认" : "自定义图片", this::pickVideoBg));
        JSONObject beauty = a.store.data.optJSONObject("beauty");
        body.addView(valueRow("全局美化 · 页面底色", beauty.optString("pageBg", "#f8f3ef"), () ->
                promptColor(beauty, "pageBg", "页面底色")));
        body.addView(valueRow("全局美化 · 卡片底色", beauty.optString("surface", "#fffaf7"), () ->
                promptColor(beauty, "surface", "卡片底色")));
        body.addView(valueRow("全局美化 · 主题色", beauty.optString("accent", "#6d3b58"), () ->
                promptColor(beauty, "accent", "主题色")));
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

    private void pickWallpaper() {
        Dialogs.Field mode = new Dialogs.Field("mode", "壁纸");
        mode.optionValues = new String[]{"默认", "奶油", "雾紫", "薄荷", "夜空", "@image", "@clear"};
        mode.optionLabels = new String[]{"默认", "奶油", "雾紫", "薄荷", "夜空", "从相册选择…", "清除自定义图片"};
        JSONObject wallpaper = a.store.data.optJSONObject("wallpaper");
        mode.value = wallpaper.optString("preset", "默认");
        Dialogs.form(a, a.store, "🖼", "聊天壁纸", null, "应用", Dialogs.fields(mode), values -> {
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
                            wallpaper.put("image", ref);
                            a.store.save();
                            rebuildChat();
                        } catch (JSONException ignored) {
                        }
                    });
                    return;
                }
                if ("@clear".equals(pick)) {
                    a.store.deleteMedia(wallpaper.optString("image", ""));
                    wallpaper.put("image", "");
                } else {
                    wallpaper.put("preset", pick).put("image", "");
                }
                a.store.save();
                rebuildChat();
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

    private String bootAnimLabel() {
        switch (icepearUi().optString("bootAnim", "hearts")) {
            case "bubbles": return "气泡上升";
            case "stars": return "星星闪烁";
            case "avatar": return "头像碰碰";
            case "off": return "关闭";
            default: return "爱心飘动";
        }
    }

    private void pickBootAnim() {
        JSONObject ui = icepearUi();
        Dialogs.Field anim = new Dialogs.Field("anim", "开屏动画");
        anim.optionValues = new String[]{"hearts", "bubbles", "stars", "avatar", "off", "@image", "@clearImg"};
        anim.optionLabels = new String[]{"爱心飘动", "气泡上升", "星星闪烁", "头像碰碰", "关闭动画",
                "选择自定义图片（叠在动画上方）…", "清除自定义图片"};
        anim.value = ui.optString("bootAnim", "hearts");
        Dialogs.form(a, a.store, "✧", "开屏动画", "下次启动时生效", "保存", Dialogs.fields(anim), values -> {
            String pick = values.get("anim");
            try {
                if ("@image".equals(pick)) {
                    a.pickFile("image/*", (bytes, mime, name) -> {
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
                            a.toast("图片已设置，会叠在开屏动画上方显示");
                        } catch (JSONException ignored) {
                        }
                    });
                    return;
                }
                if ("@clearImg".equals(pick)) {
                    a.store.deleteMedia(ui.optString("bootImg", ""));
                    ui.put("bootImg", "");
                } else {
                    ui.put("bootAnim", pick);
                }
                a.store.save();
                refresh();
            } catch (JSONException ignored) {
            }
        });
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

    /* ---------- 钱包 ---------- */

    private void renderWalletCard(JSONObject role) {
        JSONObject wallet = role.optJSONObject("wallet");
        LinearLayout section = section("钱包");
        LinearLayout body = body(section);
        body.addView(valueRow("我的余额", "¥" + Ui.fmtMoney(wallet.optDouble("mine", 0)), () ->
                promptMoney(wallet, "mine", "我的余额")));
        body.addView(valueRow("他的余额", "¥" + Ui.fmtMoney(wallet.optDouble("his", 0)), () ->
                promptMoney(wallet, "his", "他的余额")));
        content.addView(section);
    }

    private void promptMoney(JSONObject wallet, String key, String title) {
        Dialogs.prompt(a, a.store, "¥", title, "金额", "0.00",
                Ui.fmtMoney(wallet.optDouble(key, 0)), value -> {
                    try {
                        double parsed = Double.parseDouble(value.trim());
                        wallet.put(key, Math.round(parsed * 100) / 100.0);
                        a.store.save();
                        refresh();
                    } catch (Exception e) {
                        a.toast("金额无效");
                    }
                });
    }

    /* ---------- 数据 ---------- */

    private void renderDataCard() {
        LinearLayout section = section("数据");
        LinearLayout body = body(section);
        body.addView(button("⬇ 备份全部数据（JSON）", false, this::exportBackup));
        body.addView(button("⬆ 从备份恢复", false, this::importBackup));
        body.addView(button("📄 导出聊天记录（文本）", false, this::exportChat));
        body.addView(button("🕘 保存数据快照", false, this::saveSnapshot));
        renderSnapshots(body);
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

package com.icepear.app;

import android.graphics.Bitmap;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 朋友圈，对应浏览器补丁 p28 renderMoments()：
 * 数据存于 role.moments（首次按字卡种子 5 条，momentsSeededV3 标记，删光后不再重生）；
 * 顶栏右侧「铅笔」发布（文字 + 图片）；卡片 = 头像 / 名字 / 三点菜单（编辑、删除）/ 正文 / 图片 /
 * 时间 + 红心 + 评论图标；评论默认折叠、点评论可编辑/删除；超过 5 条折叠为「过往朋友圈」。
 */
public class MomentsPage extends Page {

    private LinearLayout content;
    private final Set<String> expanded = new HashSet<>();
    private boolean pastOpen = false;

    public MomentsPage(MainActivity activity) {
        super(activity);
    }

    @Override
    protected View create() {
        content = Ui.column(a);
        View post = SvgIcon.view(a, Icons.EDIT, Ui.mutedInk(a, a.store), 19);
        post.setBackground(Ui.roundedStroke(Ui.surface(a, a.store), Ui.dp(a, 11), Ui.line(a, a.store), Ui.dp(a, 1)));
        post.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));
        post.setContentDescription("发布朋友圈");
        post.setOnClickListener(v -> openPostDialog());
        return pageWithBar("朋友圈", content, post);
    }

    private JSONArray moments() {
        JSONObject role = a.store.role();
        if (role == null) return new JSONArray();
        JSONArray list = role.optJSONArray("moments");
        try {
            if (list == null) {
                list = new JSONArray();
                role.put("moments", list);
            }
            JSONArray legacy = a.store.data.optJSONArray("moments");
            if (legacy != null) {
                if (list.length() == 0 && legacy.length() > 0) {
                    for (int i = legacy.length() - 1; i >= 0; i--) {
                        JSONObject m = legacy.optJSONObject(i);
                        if (m != null) list.put(m);
                    }
                    role.put("momentsSeededV3", true);
                }
                a.store.data.remove("moments");
                a.store.save();
            }
        } catch (JSONException ignored) {
        }
        return list;
    }

    private void seedIfNeeded() {
        JSONObject role = a.store.role();
        if (role == null || role.optBoolean("momentsSeededV3", false)) return;
        JSONArray list = moments();
        List<String> pool = a.store.allCards();
        if (list.length() > 0 || pool.isEmpty()) return;
        try {
            for (int h : new int[]{2, 7, 18, 31, 52}) {
                list.put(new JSONObject().put("id", Store.uid("moment"))
                        .put("text", pool.get(a.store.rand(0, pool.size() - 1)))
                        .put("t", System.currentTimeMillis() - h * 3600000L)
                        .put("likes", a.store.rand(0, 8)).put("comments", new JSONArray())
                        .put("mine", false));
            }
            role.put("momentsSeededV3", true);
            a.store.save();
        } catch (JSONException ignored) {
        }
    }

    @Override
    public void refresh() {
        if (content == null) return;
        content.removeAllViews();
        seedIfNeeded();
        JSONArray list = moments();
        if (list.length() == 0) {
            LinearLayout empty = card(null);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(Ui.dp(a, 14), Ui.dp(a, 30), Ui.dp(a, 14), Ui.dp(a, 30));
            empty.addView(SvgIcon.view(a, Icons.IMAGE, Ui.faintInk(a, a.store), 36));
            TextView t = Ui.boldText(a, "还没有朋友圈", 15, Ui.ink(a, a.store));
            t.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 4));
            empty.addView(t);
            TextView s = Ui.text(a, "先发布第一条动态，或等待" + a.store.displayName() + "发一条。", 12, Ui.mutedInk(a, a.store));
            s.setGravity(Gravity.CENTER);
            empty.addView(s);
            content.addView(empty);
            return;
        }
        LinearLayout past = null;
        for (int i = 0; i < list.length(); i++) {
            JSONObject moment = list.optJSONObject(i);
            if (moment == null) continue;
            View card = momentCard(moment, i);
            if (i < 5) {
                content.addView(card);
                continue;
            }
            if (past == null) {
                past = Ui.column(a);
                LinearLayout fold = card(null);
                LinearLayout head = Ui.row(a);
                head.setGravity(Gravity.CENTER_VERTICAL);
                head.addView(Ui.boldText(a, "过往朋友圈", 14, Ui.ink(a, a.store)));
                TextView n = Ui.text(a, "  " + (list.length() - 5) + " 条", 12, Ui.mutedInk(a, a.store));
                head.addView(n, Ui.weighted());
                View chevron = SvgIcon.view(a, Icons.CHEVRON, Ui.mutedInk(a, a.store), 18);
                chevron.setRotation(pastOpen ? 180 : 0);
                head.addView(chevron);
                head.setOnClickListener(v -> {
                    pastOpen = !pastOpen;
                    refresh();
                });
                fold.addView(head);
                content.addView(fold);
                past.setVisibility(pastOpen ? View.VISIBLE : View.GONE);
                content.addView(past);
            }
            past.addView(card);
        }
    }

    /* ---------- 卡片 ---------- */

    private static boolean isMine(JSONObject m) {
        return m.optBoolean("mine", "me".equals(m.optString("who", "")));
    }

    private static String commentName(JSONObject c, String fallback) {
        if (c.has("name")) return c.optString("name");
        return "me".equals(c.optString("who", "")) ? null : fallback;
    }

    private View momentCard(JSONObject m, int index) {
        LinearLayout box = card(null);
        LinearLayout row = Ui.row(a);
        row.addView(Ui.avatar(a, a.store, isMine(m) ? "me" : "other", 40));
        LinearLayout body = Ui.column(a);
        body.setPadding(Ui.dp(a, 10), 0, 0, 0);

        LinearLayout head = Ui.row(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.boldText(a, isMine(m) ? myName() : a.store.displayName(), 14, Ui.plum(a, a.store)), Ui.weighted());
        View more = SvgIcon.view(a, Icons.MORE, Ui.mutedInk(a, a.store), 18);
        more.setPadding(Ui.dp(a, 8), Ui.dp(a, 4), 0, Ui.dp(a, 4));
        more.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(a, v, Gravity.END);
            menu.getMenu().add("编辑");
            menu.getMenu().add("删除");
            menu.setOnMenuItemClickListener(item -> {
                if ("编辑".contentEquals(item.getTitle())) openEditDialog(m);
                else deleteMoment(m, index);
                return true;
            });
            menu.show();
        });
        head.addView(more);
        body.addView(head);

        String text = m.optString("text", "");
        if (!text.isEmpty()) {
            TextView p = Ui.text(a, text, 14, Ui.ink(a, a.store));
            p.setLineSpacing(0, 1.4f);
            p.setPadding(0, Ui.dp(a, 6), 0, 0);
            body.addView(p);
        }
        String imageRef = m.optString("image", m.optString("img", ""));
        if (!imageRef.isEmpty()) {
            Bitmap bitmap = Ui.decodeDataUrl(a.store.resolveMedia(imageRef));
            if (bitmap != null) {
                ImageView image = new ImageView(a);
                image.setImageBitmap(bitmap);
                image.setAdjustViewBounds(true);
                image.setMaxHeight(Ui.dp(a, 240));
                image.setScaleType(ImageView.ScaleType.FIT_START);
                image.setClipToOutline(true);
                image.setBackground(Ui.rounded(0x00000000, Ui.dp(a, 12)));
                LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = Ui.dp(a, 8);
                body.addView(image, lp);
            }
        }

        LinearLayout footer = Ui.row(a);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, Ui.dp(a, 8), 0, 0);
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("M-d HH:mm", java.util.Locale.CHINA);
        footer.addView(Ui.text(a, fmt.format(new java.util.Date(m.optLong("t", System.currentTimeMillis()))), 12, Ui.mutedInk(a, a.store)), Ui.weighted());
        boolean liked = m.optBoolean("likedByMe", false);
        int likes = likesCount(m);
        if (likes > 0) {
            TextView n = Ui.text(a, String.valueOf(likes), 12, liked ? 0xFFE77D73 : Ui.mutedInk(a, a.store));
            footer.addView(n);
        }
        View heart = SvgIcon.view(a, liked ? Icons.HEART_ON : Icons.HEART, liked ? 0xFFE77D73 : Ui.mutedInk(a, a.store), 18);
        heart.setPadding(Ui.dp(a, 8), Ui.dp(a, 6), Ui.dp(a, 8), Ui.dp(a, 6));
        heart.setOnClickListener(v -> likeMoment(m));
        footer.addView(heart);
        View comment = SvgIcon.view(a, Icons.COMMENT, Ui.mutedInk(a, a.store), 18);
        comment.setPadding(Ui.dp(a, 8), Ui.dp(a, 6), Ui.dp(a, 4), Ui.dp(a, 6));
        comment.setOnClickListener(v -> commentMoment(m));
        footer.addView(comment);
        body.addView(footer);

        JSONArray comments = m.optJSONArray("comments");
        int n = comments == null ? 0 : comments.length();
        if (n > 0) {
            final String id = m.optString("id");
            boolean open = expanded.contains(id);
            TextView fold = Ui.text(a, open ? "收起评论" : "评论 " + n + " 条", 12, Ui.mutedInk(a, a.store));
            fold.setBackground(Ui.rounded(Ui.surfaceStrong(a, a.store), Ui.dp(a, 10)));
            fold.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));
            LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(a, 8);
            fold.setLayoutParams(lp);
            fold.setOnClickListener(v -> {
                if (open) expanded.remove(id);
                else expanded.add(id);
                refresh();
            });
            body.addView(fold);
            if (open) {
                LinearLayout list = Ui.column(a);
                list.setBackground(Ui.rounded(Ui.surfaceStrong(a, a.store), Ui.dp(a, 10)));
                list.setPadding(Ui.dp(a, 8), Ui.dp(a, 4), Ui.dp(a, 8), Ui.dp(a, 4));
                LinearLayout.LayoutParams llp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                llp.topMargin = Ui.dp(a, 6);
                list.setLayoutParams(llp);
                for (int i = 0; i < n; i++) {
                    final int ci = i;
                    Object raw = comments.opt(i);
                    String who;
                    String txt;
                    if (raw instanceof JSONObject) {
                        JSONObject c = (JSONObject) raw;
                        String nm = commentName(c, a.store.displayName());
                        who = nm == null ? myName() : nm;
                        txt = c.optString("text", "");
                    } else {
                        who = a.store.displayName();
                        txt = String.valueOf(raw);
                    }
                    TextView line = Ui.text(a, "", 12, Ui.ink(a, a.store));
                    android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
                    sb.append(who).append("：");
                    sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, sb.length(),
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.append(txt);
                    line.setText(sb);
                    line.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 4));
                    line.setOnClickListener(v -> commentMenu(v, m, comments, ci, txt));
                    list.addView(line);
                }
                body.addView(list);
            }
        }
        row.addView(body, Ui.weighted());
        box.addView(row);
        return box;
    }

    private static int likesCount(JSONObject m) {
        Object likes = m.opt("likes");
        if (likes instanceof Number) return ((Number) likes).intValue();
        if (likes instanceof JSONArray) return ((JSONArray) likes).length();
        return 0;
    }

    private String myName() {
        JSONObject role = a.store.role();
        String name = role != null ? role.optString("myName", "") : "";
        return name.isEmpty() ? "我" : name;
    }

    /* ---------- 操作 ---------- */

    private void likeMoment(JSONObject m) {
        try {
            boolean liked = !m.optBoolean("likedByMe", false);
            m.put("likedByMe", liked);
            m.put("likes", Math.max(0, likesCount(m) + (liked ? 1 : -1)));
            a.store.save();
            refresh();
            if (liked && a.store.rand(0, 1) == 0) hisCommentLater(m, a.store.rand(6, 15));
        } catch (JSONException ignored) {
        }
    }

    private void commentMoment(JSONObject m) {
        Dialogs.prompt(a, a.store, Icons.SMILE, "写下评论", "评论内容", "说点什么…", "", value -> {
            try {
                JSONArray list = m.optJSONArray("comments");
                if (list == null) {
                    list = new JSONArray();
                    m.put("comments", list);
                }
                list.put(new JSONObject().put("name", myName()).put("text", value));
                expanded.add(m.optString("id"));
                a.store.save();
                refresh();
                hisCommentLater(m, a.store.rand(5, 12));
            } catch (JSONException ignored) {
            }
        });
    }

    private void hisCommentLater(JSONObject m, int seconds) {
        a.logic.handler().postDelayed(() -> {
            try {
                List<String> pool = a.store.allCards();
                if (pool.isEmpty()) return;
                JSONArray list = m.optJSONArray("comments");
                if (list == null) {
                    list = new JSONArray();
                    m.put("comments", list);
                }
                list.put(new JSONObject().put("name", a.store.displayName())
                        .put("text", pool.get(a.store.rand(0, pool.size() - 1))));
                a.store.save();
                if ("pageMoments".equals(a.currentPage)) refresh();
                else {
                    a.store.data.put("momentsUnread", true);
                    a.store.save();
                }
            } catch (JSONException ignored) {
            }
        }, seconds * 1000L);
    }

    private void commentMenu(View anchor, JSONObject m, JSONArray comments, int ci, String current) {
        PopupMenu menu = new PopupMenu(a, anchor);
        menu.getMenu().add("编辑");
        menu.getMenu().add("删除");
        menu.setOnMenuItemClickListener(item -> {
            if ("编辑".contentEquals(item.getTitle())) {
                Dialogs.prompt(a, a.store, Icons.EDIT, "编辑评论", "评论内容", "", current, value -> {
                    try {
                        Object raw = comments.opt(ci);
                        if (raw instanceof JSONObject) ((JSONObject) raw).put("text", value);
                        else comments.put(ci, value);
                        a.store.save();
                        refresh();
                    } catch (JSONException ignored) {
                    }
                });
            } else {
                Dialogs.confirm(a, a.store, Icons.TRASH, "删除这条评论？", null, "删除", true, () -> {
                    comments.remove(ci);
                    a.store.save();
                    refresh();
                });
            }
            return true;
        });
        menu.show();
    }

    private void deleteMoment(JSONObject m, int index) {
        String subtitle = isMine(m) ? "删除后无法恢复" : a.store.displayName() + "的朋友圈，删除后无法恢复";
        Dialogs.confirm(a, a.store, Icons.TRASH, "删除这条朋友圈？", subtitle, "删除", true, () -> {
            JSONArray list = moments();
            if (index < list.length() && list.optJSONObject(index) == m) list.remove(index);
            else {
                for (int i = list.length() - 1; i >= 0; i--) {
                    if (list.optJSONObject(i) == m) list.remove(i);
                }
            }
            a.store.save();
            refresh();
        });
    }

    /* ---------- 发布 / 编辑 ---------- */

    private String draftImage = "";

    private LinearLayout postBody(EditText input, String initialText, ImageView preview, LinearLayout previewRow) {
        LinearLayout body = Ui.column(a);
        input.setHint("这一刻的想法…");
        input.setText(initialText);
        body.addView(input);
        previewRow.setGravity(Gravity.CENTER_VERTICAL);
        previewRow.setPadding(0, Ui.dp(a, 8), 0, 0);
        preview.setAdjustViewBounds(true);
        preview.setMaxHeight(Ui.dp(a, 120));
        preview.setMaxWidth(Ui.dp(a, 120));
        preview.setClipToOutline(true);
        preview.setBackground(Ui.rounded(0x00000000, Ui.dp(a, 10)));
        previewRow.addView(preview);
        TextView remove = Ui.boldText(a, "移除图片", 12, a.getColor(R.color.danger));
        remove.setPadding(Ui.dp(a, 12), 0, 0, 0);
        remove.setOnClickListener(v -> {
            draftImage = "";
            previewRow.setVisibility(View.GONE);
        });
        previewRow.addView(remove);
        previewRow.setVisibility(View.GONE);
        body.addView(previewRow);
        TextView pick = Ui.boldText(a, "添加图片", 13, Ui.plum(a, a.store));
        pick.setPadding(0, Ui.dp(a, 10), 0, 0);
        pick.setOnClickListener(v -> a.pickImage((bytes, mime, name) -> {
            String ref = a.store.importImage(bytes, mime);
            if (ref.isEmpty()) {
                a.toast("图片导入失败");
                return;
            }
            draftImage = ref;
            Bitmap bmp = Ui.decodeDataUrl(a.store.resolveMedia(ref));
            if (bmp != null) preview.setImageBitmap(bmp);
            previewRow.setVisibility(View.VISIBLE);
            pick.setText("更换图片");
        }));
        body.addView(pick);
        return body;
    }

    private void openPostDialog() {
        draftImage = "";
        EditText input = Dialogs.makeInput(a, a.store, true);
        ImageView preview = new ImageView(a);
        LinearLayout previewRow = Ui.row(a);
        LinearLayout body = postBody(input, "", preview, previewRow);
        Dialogs.custom(a, a.store, Icons.EDIT, "写朋友圈", "文字或图片都可以", body, "取消", "发布", () -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty() && draftImage.isEmpty()) {
                Dialogs.notice(a, a.store, Icons.EDIT, "还没有内容", "写下想分享的话或添加图片。");
                return;
            }
            try {
                JSONObject moment = new JSONObject().put("id", Store.uid("moment"))
                        .put("text", text).put("image", draftImage)
                        .put("t", System.currentTimeMillis())
                        .put("likes", 0).put("comments", new JSONArray()).put("mine", true);
                JSONArray list = moments();
                JSONArray reordered = new JSONArray().put(moment);
                for (int i = 0; i < list.length(); i++) reordered.put(list.opt(i));
                a.store.role().put("moments", reordered);
                a.store.save();
                refresh();
                a.toast("已发布");
                a.logic.handler().postDelayed(() -> {
                    try {
                        moment.put("likes", likesCount(moment) + 1);
                        List<String> pool = a.store.allCards();
                        if (!pool.isEmpty()) {
                            moment.optJSONArray("comments").put(new JSONObject()
                                    .put("name", a.store.displayName())
                                    .put("text", pool.get(a.store.rand(0, pool.size() - 1))));
                        }
                        a.store.save();
                        if ("pageMoments".equals(a.currentPage)) refresh();
                        else {
                            a.store.data.put("momentsUnread", true);
                            a.store.save();
                        }
                    } catch (JSONException ignored) {
                    }
                }, a.store.rand(8, 18) * 1000L);
            } catch (JSONException ignored) {
            }
        });
    }

    private void openEditDialog(JSONObject m) {
        draftImage = m.optString("image", m.optString("img", ""));
        EditText input = Dialogs.makeInput(a, a.store, true);
        ImageView preview = new ImageView(a);
        LinearLayout previewRow = Ui.row(a);
        LinearLayout body = postBody(input, m.optString("text", ""), preview, previewRow);
        if (!draftImage.isEmpty()) {
            Bitmap bmp = Ui.decodeDataUrl(a.store.resolveMedia(draftImage));
            if (bmp != null) preview.setImageBitmap(bmp);
            previewRow.setVisibility(View.VISIBLE);
        }
        Dialogs.custom(a, a.store, Icons.EDIT, "编辑朋友圈", "内容和图片都可以改", body, "取消", "保存", () -> {
            try {
                m.put("text", input.getText().toString().trim());
                m.put("image", draftImage);
                m.remove("img");
                a.store.save();
                refresh();
                a.toast("已保存");
            } catch (JSONException ignored) {
            }
        });
    }
}

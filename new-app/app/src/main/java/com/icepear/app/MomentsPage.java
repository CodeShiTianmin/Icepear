package com.icepear.app;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 朋友圈：发布（文字/图片）、他的自动互动、点赞、评论、删除自己的动态。
 */
public class MomentsPage extends Page {

    private LinearLayout content;

    public MomentsPage(MainActivity activity) {
        super(activity);
    }

    @Override
    protected View create() {
        content = Ui.column(a);
        return pageWithBar("朋友圈", content);
    }

    @Override
    public void refresh() {
        if (content == null) return;
        content.removeAllViews();
        content.addView(button("＋ 发布动态", true, this::publish));
        content.addView(button("🖼 发布图片动态", false, this::publishImage));

        JSONArray moments = a.store.data.optJSONArray("moments");
        if (moments == null || moments.length() == 0) {
            content.addView(hint("还没有动态，发布第一条吧。"));
            return;
        }
        for (int i = moments.length() - 1; i >= 0; i--) {
            JSONObject moment = moments.optJSONObject(i);
            if (moment == null) continue;
            content.addView(momentCard(moment, i));
        }
    }

    private View momentCard(JSONObject moment, int index) {
        LinearLayout box = card(null);
        boolean mine = "me".equals(moment.optString("who", "me"));

        LinearLayout head = Ui.row(a);
        head.addView(Ui.avatar(a, a.store, mine ? "me" : "other", 36));
        LinearLayout copy = Ui.column(a);
        copy.setPadding(Ui.dp(a, 10), 0, 0, 0);
        copy.addView(Ui.boldText(a, mine ? myName() : a.store.displayName(), 14, Ui.plum(a, a.store)));
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA);
        copy.addView(Ui.text(a, fmt.format(new java.util.Date(moment.optLong("t"))), 11, Ui.faintInk(a, a.store)));
        head.addView(copy, Ui.weighted());
        if (mine) {
            TextView del = Ui.boldText(a, "删除", 12, a.getColor(R.color.danger));
            del.setOnClickListener(v -> Dialogs.confirm(a, a.store, "⌫", "删除这条动态？", null,
                    "删除", true, () -> {
                        a.store.data.optJSONArray("moments").remove(index);
                        a.store.save();
                        refresh();
                    }));
            head.addView(del);
        }
        box.addView(head);

        String text = moment.optString("text", "");
        if (!text.isEmpty()) {
            TextView body = Ui.text(a, text, 14, Ui.ink(a, a.store));
            body.setPadding(0, Ui.dp(a, 8), 0, 0);
            box.addView(body);
        }
        String imageRef = moment.optString("img", "");
        if (!imageRef.isEmpty()) {
            android.graphics.Bitmap bitmap = Ui.decodeDataUrl(a.store.resolveMedia(imageRef));
            if (bitmap != null) {
                ImageView image = new ImageView(a);
                image.setImageBitmap(bitmap);
                image.setAdjustViewBounds(true);
                image.setMaxHeight(Ui.dp(a, 220));
                image.setScaleType(ImageView.ScaleType.FIT_START);
                image.setPadding(0, Ui.dp(a, 8), 0, 0);
                box.addView(image);
            }
        }

        /* 点赞 */
        JSONArray likes = moment.optJSONArray("likes");
        StringBuilder likeText = new StringBuilder();
        for (int i = 0; likes != null && i < likes.length(); i++) {
            if (likeText.length() > 0) likeText.append("、");
            likeText.append("me".equals(likes.optString(i)) ? myName() : a.store.displayName());
        }
        if (likeText.length() > 0) {
            TextView likeView = Ui.text(a, "♡ " + likeText, 12, Ui.plum(a, a.store));
            likeView.setPadding(0, Ui.dp(a, 8), 0, 0);
            box.addView(likeView);
        }

        /* 评论 */
        JSONArray comments = moment.optJSONArray("comments");
        for (int i = 0; comments != null && i < comments.length(); i++) {
            JSONObject comment = comments.optJSONObject(i);
            if (comment == null) continue;
            String who = "me".equals(comment.optString("who")) ? myName() : a.store.displayName();
            TextView commentView = Ui.text(a, who + "：" + comment.optString("text"), 12, Ui.mutedInk(a, a.store));
            commentView.setPadding(0, Ui.dp(a, 4), 0, 0);
            box.addView(commentView);
        }

        LinearLayout actions = Ui.row(a);
        actions.setPadding(0, Ui.dp(a, 10), 0, 0);
        boolean liked = false;
        for (int i = 0; likes != null && i < likes.length(); i++) {
            if ("me".equals(likes.optString(i))) liked = true;
        }
        final boolean likedNow = liked;
        TextView like = Ui.boldText(a, liked ? "♥ 已赞" : "♡ 点赞", 12, Ui.plum(a, a.store));
        like.setOnClickListener(v -> {
            try {
                JSONArray list = moment.optJSONArray("likes");
                if (list == null) {
                    list = new JSONArray();
                    moment.put("likes", list);
                }
                if (likedNow) {
                    for (int i = list.length() - 1; i >= 0; i--) {
                        if ("me".equals(list.optString(i))) list.remove(i);
                    }
                } else {
                    list.put("me");
                }
                a.store.save();
                refresh();
            } catch (JSONException ignored) {
            }
        });
        actions.addView(like);
        TextView comment = Ui.boldText(a, "💬 评论", 12, Ui.mutedInk(a, a.store));
        comment.setPadding(Ui.dp(a, 18), 0, 0, 0);
        comment.setOnClickListener(v -> Dialogs.prompt(a, a.store, "💬", "写评论", "评论内容", "", "", value -> {
            try {
                JSONArray list = moment.optJSONArray("comments");
                if (list == null) {
                    list = new JSONArray();
                    moment.put("comments", list);
                }
                list.put(new JSONObject().put("who", "me").put("text", value));
                a.store.save();
                refresh();
                maybeHisComment(moment);
            } catch (JSONException ignored) {
            }
        }));
        actions.addView(comment);
        box.addView(actions);
        return box;
    }

    private String myName() {
        JSONObject role = a.store.role();
        String name = role != null ? role.optString("myName", "") : "";
        return name.isEmpty() ? "我" : name;
    }

    private void publish() {
        Dialogs.Field field = new Dialogs.Field("content", "这一刻的想法");
        field.textarea = true;
        Dialogs.form(a, a.store, "🌸", "发布动态", null, "发布", Dialogs.fields(field), values -> {
            String text = values.getOrDefault("content", "").trim();
            if (text.isEmpty()) return;
            postMoment(text, "");
        });
    }

    private void publishImage() {
        a.pickFile("image/*", (bytes, mime, name) -> {
            String ref = a.store.importImage(bytes, mime);
            if (ref.isEmpty()) {
                a.toast("图片导入失败");
                return;
            }
            Dialogs.prompt(a, a.store, "🌸", "配上一句话（可留空）", "内容", "", "",
                    value -> postMoment(value, ref));
        });
    }

    private void postMoment(String text, String imageRef) {
        try {
            JSONObject moment = new JSONObject()
                    .put("id", Store.uid("moment")).put("who", "me")
                    .put("text", text).put("img", imageRef)
                    .put("t", System.currentTimeMillis())
                    .put("likes", new JSONArray()).put("comments", new JSONArray());
            a.store.data.optJSONArray("moments").put(moment);
            a.store.save();
            refresh();
            scheduleHisInteraction(moment);
        } catch (JSONException ignored) {
        }
    }

    /** 他会在一会儿后点赞/评论你的动态 */
    private void scheduleHisInteraction(JSONObject moment) {
        a.logic.handler().postDelayed(() -> {
            try {
                moment.optJSONArray("likes").put("his");
                if (a.store.rand(0, 1) == 0) {
                    java.util.List<String> pool = a.store.allCards();
                    if (!pool.isEmpty()) {
                        moment.optJSONArray("comments").put(new JSONObject()
                                .put("who", "his")
                                .put("text", pool.get(a.store.rand(0, pool.size() - 1))));
                    }
                }
                a.store.save();
                if ("pageMoments".equals(a.currentPage)) refresh();
            } catch (JSONException ignored) {
            }
        }, a.store.rand(4, 30) * 1000L);
    }

    private void maybeHisComment(JSONObject moment) {
        if (a.store.rand(0, 1) != 0) return;
        a.logic.handler().postDelayed(() -> {
            try {
                java.util.List<String> pool = a.store.allCards();
                if (pool.isEmpty()) return;
                moment.optJSONArray("comments").put(new JSONObject()
                        .put("who", "his").put("text", pool.get(a.store.rand(0, pool.size() - 1))));
                a.store.save();
                if ("pageMoments".equals(a.currentPage)) refresh();
            } catch (JSONException ignored) {
            }
        }, a.store.rand(5, 25) * 1000L);
    }
}

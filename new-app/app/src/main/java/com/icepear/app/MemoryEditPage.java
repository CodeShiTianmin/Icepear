package com.icepear.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 珍藏画布编辑器：把一条珍藏（整段对话 / 朋友圈）渲染成竖版长图，
 * 可叠加 emoji / 文字 / 图片贴纸（单指拖动、双指缩放旋转、长按编辑或删除），
 * 贴纸信息写回 memory.decor 持久化；「保存到相册」按画布原始像素导出 PNG 无损长图。
 */
public class MemoryEditPage extends Page {

    private static final String[] EMOJIS = {
            "❤️", "💕", "🥰", "😘", "🤍", "✨", "🌸", "🎀", "🍓", "🧸", "🐻", "🐰",
            "🍰", "🌙", "⭐", "🎈", "🎁", "💌", "☁️", "🌈", "🍀", "🫶", "😆", "🥺"};

    private JSONObject memory;
    private boolean showSys = true;
    private FrameLayout canvas;
    private LinearLayout base;
    private ScrollView scroll;
    private TextView titleView;

    public MemoryEditPage(MainActivity activity) {
        super(activity);
    }

    public void open(JSONObject mem, boolean includeSys) {
        memory = mem;
        showSys = includeSys;
    }

    @Override
    protected View create() {
        LinearLayout page = Ui.column(a);
        page.setBackgroundColor(Ui.paper(a, a.store));
        LinearLayout bar = pageBar("编辑珍藏画布");
        titleView = Ui.boldText(a, "珍藏画布", 15, Ui.ink(a, a.store));
        titleView.setSingleLine(true);
        bar.addView(titleView, Ui.weighted());
        bar.addView(barIcon(Icons.SMILE, "添加 emoji", this::addEmoji));
        bar.addView(barIcon(Icons.EDIT, "添加文字", this::addText));
        bar.addView(barIcon(Icons.IMAGE, "添加图片", this::addImage));
        bar.addView(barIcon(Icons.DOWNLOAD, "保存到相册", this::saveToGallery));
        page.addView(bar);

        scroll = new ScrollView(a);
        scroll.setFillViewport(false);
        LinearLayout wrap = Ui.column(a);
        wrap.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 24));
        canvas = new FrameLayout(a);
        canvas.setClipChildren(true);
        canvas.setClipToPadding(true);
        base = Ui.column(a);
        canvas.addView(base, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.addView(canvas, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.addView(hint("单指拖动贴纸，双指缩放 / 旋转，长按贴纸可编辑或删除；导出为 PNG 原图长图，不压缩。"));
        scroll.addView(wrap);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return page;
    }

    @Override
    public void refresh() {
        if (canvas == null) return;
        canvas.removeAllViews();
        canvas.addView(base, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        base.removeAllViews();
        if (memory == null) {
            base.addView(hint("没有可编辑的珍藏"));
            return;
        }
        boolean dark = Ui.dark(a.store);
        canvas.setBackground(Ui.gradient(dark ? 0xFF241B2E : 0xFFFFF4EE, dark ? 0xFF191322 : 0xFFF6E6F1, Ui.dp(a, 18)));
        base.setPadding(Ui.dp(a, 16), Ui.dp(a, 18), Ui.dp(a, 16), Ui.dp(a, 18));
        renderBase();
        JSONArray decor = memory.optJSONArray("decor");
        for (int i = 0; decor != null && i < decor.length(); i++) {
            JSONObject d = decor.optJSONObject(i);
            if (d != null) attachSticker(d);
        }
    }

    /* ---------- 底图：对话 / 朋友圈 ---------- */

    private void renderBase() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA);
        boolean moment = "moment".equals(memory.optString("kind"));
        long when = moment ? memory.optLong("momentT", memory.optLong("t")) : memory.optLong("t");
        titleView.setText(moment ? "朋友圈珍藏" : "珍藏对话");
        LinearLayout head = Ui.row(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(SvgIcon.view(a, Icons.HEART, Ui.plum(a, a.store), 18));
        TextView title = Ui.boldText(a, moment ? "朋友圈珍藏" : "珍藏时刻", 16, Ui.ink(a, a.store));
        title.setPadding(Ui.dp(a, 6), 0, 0, 0);
        head.addView(title, Ui.weighted());
        head.addView(Ui.text(a, fmt.format(new Date(when)), 11, Ui.faintInk(a, a.store)));
        base.addView(head);
        View line = new View(a);
        line.setBackgroundColor(Ui.line(a, a.store));
        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lineLp.topMargin = Ui.dp(a, 10);
        lineLp.bottomMargin = Ui.dp(a, 10);
        base.addView(line, lineLp);

        if (moment) {
            renderMoment();
        } else {
            JSONArray msgs = memory.optJSONArray("msgs");
            for (int k = 0; msgs != null && k < msgs.length(); k++) {
                JSONObject m = msgs.optJSONObject(k);
                if (m == null) continue;
                if ("sys".equals(m.optString("type")) && !showSys) continue;
                base.addView(bubble(m));
            }
        }
        TextView foot = Ui.text(a, "Icepear · " + a.store.displayName() + " & " + myName(), 11, Ui.faintInk(a, a.store));
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, Ui.dp(a, 18), 0, 0);
        base.addView(foot);
    }

    private String myName() {
        JSONObject role = a.store.role();
        String name = role != null ? role.optString("myName", "") : "";
        return name.isEmpty() ? "我" : name;
    }

    private void renderMoment() {
        LinearLayout who = Ui.row(a);
        who.setGravity(Gravity.CENTER_VERTICAL);
        who.addView(Ui.boldText(a, memory.optString("who", ""), 14, Ui.plum(a, a.store)));
        base.addView(who);
        String text = memory.optString("text", "");
        if (!text.isEmpty()) {
            TextView body = Ui.text(a, text, 15, Ui.ink(a, a.store));
            body.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
            body.setLineSpacing(0, 1.2f);
            base.addView(body);
        }
        String src = a.store.resolveMedia(memory.optString("image", ""));
        if (src != null && !src.isEmpty()) {
            ImageView image = new ImageView(a);
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (Ui.setImage(image, src)) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = Ui.dp(a, 6);
                image.setLayoutParams(lp);
                base.addView(image);
            }
        }
    }

    private View bubble(JSONObject m) {
        if ("sys".equals(m.optString("type"))) {
            TextView sys = Ui.text(a, m.optString("text", ""), 11, Ui.faintInk(a, a.store));
            sys.setGravity(Gravity.CENTER);
            sys.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
            return sys;
        }
        boolean me = "me".equals(m.optString("side"));
        LinearLayout line = Ui.row(a);
        line.setGravity(me ? Gravity.END : Gravity.START);
        line.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 4));
        if ("img".equals(m.optString("type"))) {
            String src = a.store.resolveMedia(m.optString("src", ""));
            ImageView image = new ImageView(a);
            image.setAdjustViewBounds(true);
            image.setMaxWidth(Ui.dp(a, 200));
            image.setMaxHeight(Ui.dp(a, 260));
            if (Ui.setImage(image, src)) {
                image.setBackground(Ui.rounded(Ui.surfaceStrong(a, a.store), Ui.dp(a, 12)));
                image.setClipToOutline(true);
                line.addView(image);
                return line;
            }
        }
        TextView bubble = Ui.text(a, describe(m), 13, me ? Color.WHITE : Ui.ink(a, a.store));
        bubble.setBackground(Ui.rounded(me ? Ui.plum(a, a.store) : Ui.surfaceStrong(a, a.store), Ui.dp(a, 12)));
        bubble.setPadding(Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 6));
        bubble.setMaxWidth(Ui.dp(a, 250));
        line.addView(bubble);
        return line;
    }

    static String describe(JSONObject msg) {
        switch (msg.optString("type", "")) {
            case "img": return "[图片]";
            case "video": return "[视频]";
            case "voice": return "[语音]";
            case "loc": return "[位置] " + msg.optString("text");
            case "red": return "[红包] ¥" + Ui.fmtMoney(ChatLogic.txAmount(msg));
            case "zhuan": return "[转账] ¥" + Ui.fmtMoney(ChatLogic.txAmount(msg));
            case "gift": return "[礼物] " + msg.optString("gift");
            case "sys": return "[系统] " + msg.optString("text");
            default: return msg.optString("text", "");
        }
    }

    /* ---------- 贴纸 ---------- */

    private JSONArray decorList() {
        JSONArray decor = memory.optJSONArray("decor");
        if (decor == null) {
            decor = new JSONArray();
            try {
                memory.put("decor", decor);
            } catch (JSONException ignored) {
            }
        }
        return decor;
    }

    private void addEmoji() {
        if (memory == null) return;
        LinearLayout grid = Ui.column(a);
        LinearLayout row = null;
        for (int i = 0; i < EMOJIS.length; i++) {
            if (i % 6 == 0) {
                row = Ui.row(a);
                grid.addView(row);
            }
            final String emoji = EMOJIS[i];
            TextView cell = Ui.text(a, emoji, 26, Ui.ink(a, a.store));
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
            cell.setTag(emoji);
            row.addView(cell, Ui.weighted());
        }
        final android.app.Dialog[] holder = new android.app.Dialog[1];
        for (int r = 0; r < grid.getChildCount(); r++) {
            LinearLayout line = (LinearLayout) grid.getChildAt(r);
            for (int c = 0; c < line.getChildCount(); c++) {
                View cell = line.getChildAt(c);
                cell.setOnClickListener(v -> {
                    newSticker("emoji", (String) v.getTag(), "");
                    if (holder[0] != null) holder[0].dismiss();
                });
            }
        }
        LinearLayout body = Ui.column(a);
        body.addView(grid);
        body.addView(hint("也可以输入任意 emoji 或短语："));
        android.widget.EditText input = Dialogs.makeInput(a, a.store, false);
        input.setHint("例如 🎂 或 小可爱");
        body.addView(input);
        holder[0] = Dialogs.custom(a, a.store, Icons.SMILE, "添加 emoji", "点选一个，或输入后点确定", body,
                "取消", "确定", () -> {
                    String s = input.getText().toString().trim();
                    if (!s.isEmpty()) newSticker("emoji", s, "");
                });
    }

    private void addText() {
        if (memory == null) return;
        Dialogs.prompt(a, a.store, Icons.EDIT, "添加文字", "写一句话贴在画布上", "例如：最好的我们", "",
                text -> newSticker("text", text, ""));
    }

    private void addImage() {
        if (memory == null) return;
        a.pickImage((bytes, mime, name) -> {
            String ref = a.store.importImage(bytes, mime);
            if (ref == null || ref.isEmpty()) {
                a.toast("图片导入失败");
                return;
            }
            newSticker("image", "", ref);
        });
    }

    private void newSticker(String kind, String text, String src) {
        try {
            JSONObject d = new JSONObject()
                    .put("kind", kind)
                    .put("text", text)
                    .put("src", src)
                    .put("x", 0.5)
                    .put("y", Math.min(0.9, Math.max(0.05, visibleCenterFraction())))
                    .put("rot", 0)
                    .put("scale", 1);
            decorList().put(d);
            a.store.save();
            attachSticker(d);
        } catch (JSONException ignored) {
        }
    }

    /** 当前滚动到的位置在画布中的比例，用于把新贴纸放到可见区域中央 */
    private double visibleCenterFraction() {
        if (canvas.getHeight() == 0) return 0.2;
        double center = scroll.getScrollY() + scroll.getHeight() / 2.0 - canvas.getTop() - Ui.dp(a, 10);
        return center / canvas.getHeight();
    }

    private void attachSticker(JSONObject d) {
        View v;
        String kind = d.optString("kind");
        if ("image".equals(kind)) {
            ImageView image = new ImageView(a);
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (!Ui.setImage(image, a.store.resolveMedia(d.optString("src", "")))) return;
            image.setLayoutParams(new FrameLayout.LayoutParams(Ui.dp(a, 140), ViewGroup.LayoutParams.WRAP_CONTENT));
            v = image;
        } else {
            boolean emoji = "emoji".equals(kind);
            TextView t = Ui.boldText(a, d.optString("text", ""), emoji ? 40 : 18,
                    emoji ? Ui.ink(a, a.store) : Ui.plum(a, a.store));
            t.setPadding(Ui.dp(a, 6), Ui.dp(a, 4), Ui.dp(a, 6), Ui.dp(a, 4));
            if (!emoji) {
                t.setShadowLayer(Ui.dp(a, 3), 0, Ui.dp(a, 1), 0x66000000);
                t.setBackground(Ui.rounded(Ui.dark(a.store) ? 0x33000000 : 0x66FFFFFF, Ui.dp(a, 8)));
            }
            t.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            v = t;
        }
        v.setTag(d);
        canvas.addView(v);
        final View sticker = v;
        v.post(() -> applyTransform(sticker, d));
        v.setOnTouchListener(new StickerTouch(v, d));
    }

    private void applyTransform(View v, JSONObject d) {
        int w = canvas.getWidth(), h = canvas.getHeight();
        if (w == 0 || h == 0) return;
        float scale = (float) d.optDouble("scale", 1);
        v.setScaleX(scale);
        v.setScaleY(scale);
        v.setRotation((float) d.optDouble("rot", 0));
        v.setX((float) (d.optDouble("x", 0.5) * w) - v.getWidth() / 2f);
        v.setY((float) (d.optDouble("y", 0.2) * h) - v.getHeight() / 2f);
    }

    private void storeTransform(View v, JSONObject d) {
        int w = canvas.getWidth(), h = canvas.getHeight();
        if (w == 0 || h == 0) return;
        try {
            d.put("x", (v.getX() + v.getWidth() / 2f) / w)
                    .put("y", (v.getY() + v.getHeight() / 2f) / h)
                    .put("rot", v.getRotation())
                    .put("scale", v.getScaleX());
        } catch (JSONException ignored) {
        }
        a.store.save();
    }

    private void removeSticker(View v, JSONObject d) {
        JSONArray decor = decorList();
        for (int i = 0; i < decor.length(); i++) {
            if (decor.optJSONObject(i) == d) {
                decor.remove(i);
                break;
            }
        }
        canvas.removeView(v);
        a.store.save();
    }

    private void stickerMenu(View v, JSONObject d) {
        String kind = d.optString("kind");
        if ("image".equals(kind)) {
            Dialogs.choice(a, a.store, Icons.IMAGE, "图片贴纸", "要怎么处理这张贴纸？", "删除", "置于顶层",
                    () -> removeSticker(v, d), () -> v.bringToFront());
            return;
        }
        Dialogs.choice(a, a.store, Icons.EDIT, "编辑贴纸", d.optString("text", ""), "删除", "改文字",
                () -> removeSticker(v, d),
                () -> Dialogs.prompt(a, a.store, Icons.EDIT, "改文字", "新的内容", "", d.optString("text", ""), text -> {
                    try {
                        d.put("text", text);
                    } catch (JSONException ignored) {
                    }
                    ((TextView) v).setText(text);
                    a.store.save();
                }));
    }

    /** 单指拖动、双指缩放旋转、长按菜单 */
    private class StickerTouch implements View.OnTouchListener {
        private final View v;
        private final JSONObject d;
        private float downX, downY, startX, startY;
        private double startDist, startAngle;
        private float startScale, startRot;
        private boolean multi, moved, longPressed;
        private final Runnable longPress;

        StickerTouch(View v, JSONObject d) {
            this.v = v;
            this.d = d;
            longPress = () -> {
                longPressed = true;
                stickerMenu(v, d);
            };
        }

        @Override
        public boolean onTouch(View view, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    scroll.requestDisallowInterceptTouchEvent(true);
                    downX = e.getRawX();
                    downY = e.getRawY();
                    startX = v.getX();
                    startY = v.getY();
                    multi = false;
                    moved = false;
                    longPressed = false;
                    v.bringToFront();
                    v.postDelayed(longPress, 520);
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (e.getPointerCount() == 2) {
                        v.removeCallbacks(longPress);
                        multi = true;
                        startDist = dist(e);
                        startAngle = angle(e);
                        startScale = v.getScaleX();
                        startRot = v.getRotation();
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (longPressed) return true;
                    if (multi && e.getPointerCount() >= 2) {
                        double dist = dist(e);
                        double ang = angle(e);
                        float scale = (float) Math.max(0.3, Math.min(6, startScale * (dist / Math.max(1, startDist))));
                        v.setScaleX(scale);
                        v.setScaleY(scale);
                        v.setRotation((float) (startRot + Math.toDegrees(ang - startAngle)));
                        return true;
                    }
                    float dx = e.getRawX() - downX;
                    float dy = e.getRawY() - downY;
                    if (!moved && (Math.abs(dx) > 6 || Math.abs(dy) > 6)) {
                        moved = true;
                        v.removeCallbacks(longPress);
                    }
                    if (moved) {
                        v.setX(startX + dx);
                        v.setY(startY + dy);
                    }
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    if (e.getPointerCount() <= 2) {
                        multi = false;
                        int remain = e.getActionIndex() == 0 ? 1 : 0;
                        downX = e.getRawX() + (e.getX(remain) - e.getX(e.getActionIndex()));
                        downY = e.getRawY() + (e.getY(remain) - e.getY(e.getActionIndex()));
                        startX = v.getX();
                        startY = v.getY();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.removeCallbacks(longPress);
                    scroll.requestDisallowInterceptTouchEvent(false);
                    if (!longPressed) storeTransform(v, d);
                    return true;
            }
            return false;
        }

        private double dist(MotionEvent e) {
            return Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1));
        }

        private double angle(MotionEvent e) {
            return Math.atan2(e.getY(1) - e.getY(0), e.getX(1) - e.getX(0));
        }
    }

    /* ---------- 导出 ---------- */

    private void saveToGallery() {
        if (canvas == null || canvas.getWidth() == 0 || canvas.getHeight() == 0) {
            a.toast("画布还没渲染好");
            return;
        }
        int w = canvas.getWidth(), h = canvas.getHeight();
        Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError err) {
            a.toast("长图太大，内存不足");
            return;
        }
        Canvas c = new Canvas(bitmap);
        canvas.draw(c);
        a.saveImageToGallery(bitmap, "icepear-memory-" + memory.optString("id", String.valueOf(System.currentTimeMillis())) + ".png");
    }
}

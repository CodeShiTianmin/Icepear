package com.icepear.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * UI 工具：主题色 token（含夜间模式与全局美化覆盖）、圆角背景、头像等。
 */
public final class Ui {

    private Ui() {
    }

    public static int dp(Context c, float v) {
        return Math.round(c.getResources().getDisplayMetrics().density * v);
    }

    public static int parseColor(String value, int fallback) {
        try {
            String s = value == null ? "" : value.trim();
            if (s.isEmpty()) return fallback;
            if (s.startsWith("#") && s.length() == 4) {
                s = "#" + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2) + s.charAt(3) + s.charAt(3);
            }
            return Color.parseColor(s);
        } catch (Exception e) {
            return fallback;
        }
    }

    /* ---------- 主题 token ---------- */

    public static boolean dark(Store store) {
        return store.data.optBoolean("dark", false);
    }

    public static int paper(Context c, Store s) {
        if (dark(s)) return c.getColor(R.color.dark_paper);
        return parseColor(beauty(s).optString("pageBg", ""), c.getColor(R.color.paper));
    }

    public static int surface(Context c, Store s) {
        if (dark(s)) return c.getColor(R.color.dark_surface);
        return parseColor(beauty(s).optString("surface", ""), c.getColor(R.color.surface));
    }

    public static int surfaceStrong(Context c, Store s) {
        return dark(s) ? c.getColor(R.color.dark_surface_strong) : c.getColor(R.color.surface_strong);
    }

    public static int ink(Context c, Store s) {
        return dark(s) ? c.getColor(R.color.dark_ink) : c.getColor(R.color.ink);
    }

    public static int mutedInk(Context c, Store s) {
        return dark(s) ? c.getColor(R.color.dark_muted_ink) : c.getColor(R.color.muted_ink);
    }

    public static int faintInk(Context c, Store s) {
        return dark(s) ? c.getColor(R.color.dark_faint_ink) : c.getColor(R.color.faint_ink);
    }

    public static int plum(Context c, Store s) {
        return parseColor(beauty(s).optString("accent", ""), c.getColor(R.color.plum));
    }

    public static int line(Context c, Store s) {
        return dark(s) ? c.getColor(R.color.dark_line) : c.getColor(R.color.line);
    }

    public static int topBg(Context c, Store s) {
        if (dark(s)) return c.getColor(R.color.dark_paper);
        return parseColor(beauty(s).optString("topBg", ""), c.getColor(R.color.paper));
    }

    public static int navBg(Context c, Store s) {
        if (dark(s)) return c.getColor(R.color.dark_surface);
        return parseColor(beauty(s).optString("navBg", ""), c.getColor(R.color.surface));
    }

    private static JSONObject beauty(Store s) {
        JSONObject beauty = s.data.optJSONObject("beauty");
        return beauty != null ? beauty : new JSONObject();
    }

    /* ---------- 绘制辅助 ---------- */

    public static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    public static GradientDrawable roundedStroke(int color, float radiusPx, int strokeColor, int strokeWidthPx) {
        GradientDrawable drawable = rounded(color, radiusPx);
        drawable.setStroke(strokeWidthPx, strokeColor);
        return drawable;
    }

    public static GradientDrawable gradient(int start, int end, float radiusPx) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    public static RippleDrawable ripple(GradientDrawable base) {
        return new RippleDrawable(ColorStateList.valueOf(0x22000000), base, null);
    }

    public static TextView text(Context c, String value, float sizeSp, int color) {
        TextView view = new TextView(c);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        return view;
    }

    public static TextView boldText(Context c, String value, float sizeSp, int color) {
        TextView view = text(c, value, sizeSp, color);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    public static LinearLayout column(Context c) {
        LinearLayout layout = new LinearLayout(c);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public static LinearLayout row(Context c) {
        LinearLayout layout = new LinearLayout(c);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    public static LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    public static LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    /** data:image base64 -> Bitmap */
    public static Bitmap decodeDataUrl(String dataUrl) {
        try {
            if (dataUrl == null) return null;
            int comma = dataUrl.indexOf(',');
            if (!dataUrl.startsWith("data:image") || comma < 0) return null;
            byte[] bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 头像视图：图片引用则显示圆形图片，否则显示 emoji/文字圆形底。
     * 等价于旧版 headHTML()。
     */
    public static View avatar(Context c, Store store, String side, int sizeDp) {
        JSONObject role = store.role();
        String value = "";
        if (role != null) {
            value = "me".equals(side) ? role.optString("myAvatar", "我") : role.optString("avatar", "🧑");
        }
        return avatarFromValue(c, store, value, sizeDp);
    }

    public static View avatarFromValue(Context c, Store store, String value, int sizeDp) {
        int size = dp(c, sizeDp);
        String resolved = store.resolveMedia(value == null ? "" : value);
        FrameLayout box = new FrameLayout(c);
        box.setLayoutParams(new ViewGroup.LayoutParams(size, size));
        if (resolved.startsWith("data:image")) {
            Bitmap bitmap = decodeDataUrl(resolved);
            if (bitmap != null) {
                ImageView image = new ImageView(c);
                image.setImageBitmap(bitmap);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setClipToOutline(true);
                image.setBackground(rounded(0x00000000, size / 2f));
                image.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, android.graphics.Outline outline) {
                        outline.setOval(0, 0, view.getWidth(), view.getHeight());
                    }
                });
                box.addView(image, new FrameLayout.LayoutParams(size, size));
                return box;
            }
        }
        TextView label = new TextView(c);
        label.setText(value == null || value.isEmpty() ? "🧑" : value);
        label.setGravity(Gravity.CENTER);
        label.setTextSize(sizeDp * 0.42f);
        label.setTextColor(ink(c, store));
        label.setBackground(rounded(dark(store) ? 0xFF3A3037 : 0xFFEFE7E9, size / 2f));
        box.addView(label, new FrameLayout.LayoutParams(size, size));
        return box;
    }

    public static String fmtMoney(double value) {
        String s = String.format(java.util.Locale.US, "%.2f", value);
        if (s.endsWith(".00")) s = s.substring(0, s.length() - 3);
        return s;
    }

    public static String pad2(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }

    public static String fmtDur(int sec) {
        if (sec < 0) sec = 0;
        return pad2(sec / 3600) + ":" + pad2(sec % 3600 / 60) + ":" + pad2(sec % 60);
    }
}

package com.icepear.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 运行时 SVG 图标渲染：解析浏览器版同款的 24x24 SVG 片段（path / circle / rect / line），
 * 以描边方式绘制。这样原生版与 WebView 版共用同一套矢量图标。
 */
public final class SvgIcon extends Drawable {

    private final Path path = new Path();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float viewBox;
    private final float strokeWidth;
    private final boolean fill;
    private int sizePx;

    private SvgIcon(String svg, int color, int sizePx) {
        this.sizePx = sizePx;
        viewBox = parseViewBox(svg);
        String sw = attr(svg, "stroke-width");
        strokeWidth = sw.isEmpty() ? 1.7f : Float.parseFloat(sw);
        String fillAttr = attr(svg, "fill");
        fill = !fillAttr.isEmpty() && !"none".equals(fillAttr);
        buildPath(svg);
        paint.setColor(color);
        paint.setStyle(fill ? Paint.Style.FILL : Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    public static SvgIcon of(Context c, String svg, int color, float sizeDp) {
        return new SvgIcon(svg, color, Ui.dp(c, sizeDp));
    }

    public static ImageView view(Context c, String svg, int color, float sizeDp) {
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(of(c, svg, color, sizeDp));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, sizeDp), Ui.dp(c, sizeDp));
        lp.gravity = Gravity.CENTER;
        iv.setLayoutParams(lp);
        return iv;
    }

    public static boolean isSvg(String s) {
        return s != null && s.contains("<svg");
    }

    public void setColor(int color) {
        paint.setColor(color);
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        RectF b = new RectF(getBounds());
        float size = Math.min(b.width(), b.height());
        if (size <= 0) return;
        float scale = size / viewBox;
        canvas.save();
        canvas.translate(b.left + (b.width() - size) / 2f, b.top + (b.height() - size) / 2f);
        Matrix m = new Matrix();
        m.setScale(scale, scale);
        Path scaled = new Path();
        path.transform(m, scaled);
        paint.setStrokeWidth(strokeWidth * scale);
        canvas.drawPath(scaled, paint);
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return sizePx;
    }

    @Override
    public int getIntrinsicHeight() {
        return sizePx;
    }

    /* ---------- 解析 ---------- */

    private static final Pattern TAG = Pattern.compile("<(path|circle|rect|line|polyline|polygon)\\b([^>]*)/?>");
    private static final Pattern ATTR = Pattern.compile("([a-zA-Z-]+)=\"([^\"]*)\"");

    private static float parseViewBox(String svg) {
        String vb = attr(svg, "viewBox");
        if (vb.isEmpty()) return 24f;
        String[] parts = vb.trim().split("[\\s,]+");
        try {
            return parts.length >= 4 ? Float.parseFloat(parts[2]) : 24f;
        } catch (NumberFormatException e) {
            return 24f;
        }
    }

    private static String attr(String s, String name) {
        int head = s.indexOf('>');
        String open = head > 0 ? s.substring(0, head) : s;
        Matcher m = Pattern.compile("\\b" + name + "=\"([^\"]*)\"").matcher(open);
        return m.find() ? m.group(1) : "";
    }

    private void buildPath(String svg) {
        Matcher m = TAG.matcher(svg);
        while (m.find()) {
            String tag = m.group(1);
            java.util.Map<String, String> a = new java.util.HashMap<>();
            Matcher am = ATTR.matcher(m.group(2));
            while (am.find()) a.put(am.group(1), am.group(2));
            switch (tag) {
                case "path":
                    appendPathData(path, a.get("d"));
                    break;
                case "circle": {
                    float cx = num(a.get("cx")), cy = num(a.get("cy")), r = num(a.get("r"));
                    path.addCircle(cx, cy, r, Path.Direction.CW);
                    break;
                }
                case "rect": {
                    float x = num(a.get("x")), y = num(a.get("y"));
                    float w = num(a.get("width")), h = num(a.get("height"));
                    float rx = num(a.get("rx"));
                    path.addRoundRect(new RectF(x, y, x + w, y + h), rx, rx, Path.Direction.CW);
                    break;
                }
                case "line":
                    path.moveTo(num(a.get("x1")), num(a.get("y1")));
                    path.lineTo(num(a.get("x2")), num(a.get("y2")));
                    break;
                case "polyline":
                case "polygon": {
                    List<Float> pts = numbers(a.get("points"));
                    for (int i = 0; i + 1 < pts.size(); i += 2) {
                        if (i == 0) path.moveTo(pts.get(i), pts.get(i + 1));
                        else path.lineTo(pts.get(i), pts.get(i + 1));
                    }
                    if ("polygon".equals(tag)) path.close();
                    break;
                }
            }
        }
    }

    private static float num(String s) {
        if (s == null || s.isEmpty()) return 0f;
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    private static final Pattern NUM = Pattern.compile("-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?");

    private static List<Float> numbers(String s) {
        List<Float> out = new ArrayList<>();
        if (s == null) return out;
        Matcher m = NUM.matcher(s);
        while (m.find()) out.add(Float.parseFloat(m.group()));
        return out;
    }

    /** 最小 SVG path data 解析：M L H V C S Q T A Z 及相对命令 */
    static void appendPathData(Path p, String d) {
        if (d == null) return;
        float cx = 0, cy = 0, sx = 0, sy = 0, lcx = 0, lcy = 0;
        char last = 0;
        int i = 0, n = d.length();
        while (i < n) {
            char ch = d.charAt(i);
            if (Character.isWhitespace(ch) || ch == ',') {
                i++;
                continue;
            }
            char cmd;
            if (Character.isLetter(ch)) {
                cmd = ch;
                i++;
            } else {
                cmd = last == 'M' ? 'L' : last == 'm' ? 'l' : last;
                if (cmd == 0) break;
            }
            int end = i;
            StringBuilder args = new StringBuilder();
            while (end < n && (!Character.isLetter(d.charAt(end)) || d.charAt(end) == 'e' || d.charAt(end) == 'E')) {
                args.append(d.charAt(end));
                end++;
            }
            i = end;
            List<Float> v = numbers(args.toString());
            boolean rel = Character.isLowerCase(cmd);
            char up = Character.toUpperCase(cmd);
            int k = 0;
            switch (up) {
                case 'Z':
                    p.close();
                    cx = sx;
                    cy = sy;
                    break;
                case 'M':
                    while (k + 1 < v.size()) {
                        float x = v.get(k) + (rel ? cx : 0), y = v.get(k + 1) + (rel ? cy : 0);
                        if (k == 0) {
                            p.moveTo(x, y);
                            sx = x;
                            sy = y;
                        } else p.lineTo(x, y);
                        cx = x;
                        cy = y;
                        k += 2;
                    }
                    break;
                case 'L':
                    while (k + 1 < v.size()) {
                        cx = v.get(k) + (rel ? cx : 0);
                        cy = v.get(k + 1) + (rel ? cy : 0);
                        p.lineTo(cx, cy);
                        k += 2;
                    }
                    break;
                case 'H':
                    while (k < v.size()) {
                        cx = v.get(k) + (rel ? cx : 0);
                        p.lineTo(cx, cy);
                        k++;
                    }
                    break;
                case 'V':
                    while (k < v.size()) {
                        cy = v.get(k) + (rel ? cy : 0);
                        p.lineTo(cx, cy);
                        k++;
                    }
                    break;
                case 'C':
                    while (k + 5 < v.size()) {
                        float x1 = v.get(k) + (rel ? cx : 0), y1 = v.get(k + 1) + (rel ? cy : 0);
                        float x2 = v.get(k + 2) + (rel ? cx : 0), y2 = v.get(k + 3) + (rel ? cy : 0);
                        float x = v.get(k + 4) + (rel ? cx : 0), y = v.get(k + 5) + (rel ? cy : 0);
                        p.cubicTo(x1, y1, x2, y2, x, y);
                        lcx = x2;
                        lcy = y2;
                        cx = x;
                        cy = y;
                        k += 6;
                    }
                    break;
                case 'S':
                    while (k + 3 < v.size()) {
                        float x1 = (last == 'C' || last == 'c' || last == 'S' || last == 's') ? 2 * cx - lcx : cx;
                        float y1 = (last == 'C' || last == 'c' || last == 'S' || last == 's') ? 2 * cy - lcy : cy;
                        float x2 = v.get(k) + (rel ? cx : 0), y2 = v.get(k + 1) + (rel ? cy : 0);
                        float x = v.get(k + 2) + (rel ? cx : 0), y = v.get(k + 3) + (rel ? cy : 0);
                        p.cubicTo(x1, y1, x2, y2, x, y);
                        lcx = x2;
                        lcy = y2;
                        cx = x;
                        cy = y;
                        last = 'S';
                        k += 4;
                    }
                    break;
                case 'Q':
                    while (k + 3 < v.size()) {
                        float x1 = v.get(k) + (rel ? cx : 0), y1 = v.get(k + 1) + (rel ? cy : 0);
                        float x = v.get(k + 2) + (rel ? cx : 0), y = v.get(k + 3) + (rel ? cy : 0);
                        p.quadTo(x1, y1, x, y);
                        lcx = x1;
                        lcy = y1;
                        cx = x;
                        cy = y;
                        k += 4;
                    }
                    break;
                case 'T':
                    while (k + 1 < v.size()) {
                        float x1 = (last == 'Q' || last == 'q' || last == 'T' || last == 't') ? 2 * cx - lcx : cx;
                        float y1 = (last == 'Q' || last == 'q' || last == 'T' || last == 't') ? 2 * cy - lcy : cy;
                        float x = v.get(k) + (rel ? cx : 0), y = v.get(k + 1) + (rel ? cy : 0);
                        p.quadTo(x1, y1, x, y);
                        lcx = x1;
                        lcy = y1;
                        cx = x;
                        cy = y;
                        last = 'T';
                        k += 2;
                    }
                    break;
                case 'A':
                    while (k + 6 < v.size()) {
                        float rx = v.get(k), ry = v.get(k + 1), rot = v.get(k + 2);
                        boolean large = v.get(k + 3) != 0, sweep = v.get(k + 4) != 0;
                        float x = v.get(k + 5) + (rel ? cx : 0), y = v.get(k + 6) + (rel ? cy : 0);
                        arcTo(p, cx, cy, rx, ry, rot, large, sweep, x, y);
                        cx = x;
                        cy = y;
                        k += 7;
                    }
                    break;
            }
            last = cmd;
        }
    }

    /** SVG 弧线 → 圆心参数化，再交给 Path.arcTo */
    private static void arcTo(Path p, float x0, float y0, float rx, float ry, float rotDeg,
                              boolean large, boolean sweep, float x, float y) {
        if (rx == 0 || ry == 0) {
            p.lineTo(x, y);
            return;
        }
        rx = Math.abs(rx);
        ry = Math.abs(ry);
        double phi = Math.toRadians(rotDeg);
        double cosP = Math.cos(phi), sinP = Math.sin(phi);
        double dx = (x0 - x) / 2.0, dy = (y0 - y) / 2.0;
        double x1 = cosP * dx + sinP * dy;
        double y1 = -sinP * dx + cosP * dy;
        double lambda = (x1 * x1) / (rx * rx) + (y1 * y1) / (ry * ry);
        if (lambda > 1) {
            double s = Math.sqrt(lambda);
            rx *= s;
            ry *= s;
        }
        double sign = (large == sweep) ? -1 : 1;
        double num = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1;
        double den = rx * rx * y1 * y1 + ry * ry * x1 * x1;
        double coef = den == 0 ? 0 : sign * Math.sqrt(Math.max(0, num / den));
        double cx1 = coef * (rx * y1 / ry);
        double cy1 = coef * -(ry * x1 / rx);
        double cx = cosP * cx1 - sinP * cy1 + (x0 + x) / 2.0;
        double cy = sinP * cx1 + cosP * cy1 + (y0 + y) / 2.0;
        double ux = (x1 - cx1) / rx, uy = (y1 - cy1) / ry;
        double vx = (-x1 - cx1) / rx, vy = (-y1 - cy1) / ry;
        double start = Math.toDegrees(Math.atan2(uy, ux));
        double dot = ux * vx + uy * vy;
        double len = Math.sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy));
        double sweepAngle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, len == 0 ? 1 : dot / len))));
        if (ux * vy - uy * vx < 0) sweepAngle = -sweepAngle;
        if (sweep && sweepAngle < 0) sweepAngle += 360;
        if (!sweep && sweepAngle > 0) sweepAngle -= 360;
        RectF oval = new RectF((float) (cx - rx), (float) (cy - ry), (float) (cx + rx), (float) (cy + ry));
        if (rotDeg == 0) {
            p.arcTo(oval, (float) start, (float) sweepAngle, false);
        } else {
            Path arc = new Path();
            arc.arcTo(oval, (float) start, (float) sweepAngle, true);
            Matrix m = new Matrix();
            m.setRotate(rotDeg, (float) cx, (float) cy);
            arc.transform(m);
            p.addPath(arc);
        }
    }
}

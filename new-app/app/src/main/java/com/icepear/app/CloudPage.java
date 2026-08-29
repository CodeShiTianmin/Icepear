package com.icepear.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 聊天词云：提取聊天关键词按频次布局，Canvas 绘制渐变背景与词块，
 * 螺旋散点布局并做碰撞检测。
 */
public class CloudPage extends Page {

    private CloudView cloudView;

    public CloudPage(MainActivity activity) {
        super(activity);
    }

    @Override
    protected View create() {
        LinearLayout content = Ui.column(a);
        cloudView = new CloudView(a);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 420));
        lp.topMargin = Ui.dp(a, 10);
        cloudView.setLayoutParams(lp);
        content.addView(cloudView);
        content.addView(button("↻ 重新生成", false, () -> {
            cloudView.regenerate();
            cloudView.invalidate();
        }));
        return pageWithBar("聊天词云", content);
    }

    @Override
    public void refresh() {
        if (cloudView != null) {
            cloudView.regenerate();
            cloudView.invalidate();
        }
    }

    /* ---------- 关键词统计（与旧版词云一致的停用词逻辑） ---------- */

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "的", "了", "是", "我", "你", "他", "她", "在", "吗", "吧", "啊", "呀", "哦",
            "嗯", "和", "也", "都", "就", "不", "有", "没", "这", "那", "什么", "一个",
            "怎么", "还", "要", "会", "去", "来", "说", "好", "很", "呢", "哈", "哈哈"));

    public static Map<String, Integer> keywordFrequency(String text) {
        Map<String, Integer> freq = new HashMap<>();
        StringBuilder token = new StringBuilder();
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isLetterOrDigit(ch) || (ch >= 0x4E00 && ch <= 0x9FFF)) {
                token.append(ch);
            } else if (token.length() > 0) {
                tokens.add(token.toString());
                token.setLength(0);
            }
        }
        if (token.length() > 0) tokens.add(token.toString());
        for (String word : tokens) {
            /* 中文串再按二字滑窗拆分，等价旧版分词效果 */
            if (word.length() >= 2 && word.charAt(0) >= 0x4E00) {
                for (int i = 0; i + 2 <= word.length(); i++) {
                    String pair = word.substring(i, i + 2);
                    if (!STOP_WORDS.contains(pair) && !STOP_WORDS.contains(pair.substring(0, 1))
                            && !STOP_WORDS.contains(pair.substring(1))) {
                        freq.merge(pair, 1, Integer::sum);
                    }
                }
            } else if (word.length() >= 2 && !STOP_WORDS.contains(word)) {
                freq.merge(word, 1, Integer::sum);
            }
        }
        return freq;
    }

    /* ---------- Canvas 词云 ---------- */

    private class CloudView extends View {

        private final List<Object[]> placed = new ArrayList<>(); // {word, size, x, y, color}
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] palette = {0xFF6D3B58, 0xFF9A6B87, 0xFFE77D73, 0xFFB98A5E, 0xFF5B7A6E};

        CloudView(Context context) {
            super(context);
        }

        void regenerate() {
            placed.clear();
            JSONArray chat = a.store.chat();
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < chat.length(); i++) {
                JSONObject msg = chat.optJSONObject(i);
                if (msg == null || msg.optBoolean("recall", false)) continue;
                if (!"".equals(msg.optString("type", ""))) continue;
                text.append(msg.optString("text", "")).append(' ');
            }
            Map<String, Integer> freq = keywordFrequency(text.toString());
            List<Map.Entry<String, Integer>> top = new ArrayList<>(freq.entrySet());
            top.sort((x, y) -> y.getValue() - x.getValue());
            int max = top.isEmpty() ? 1 : top.get(0).getValue();
            int width = getWidth() > 0 ? getWidth() : Ui.dp(a, 340);
            int height = getHeight() > 0 ? getHeight() : Ui.dp(a, 420);
            List<RectF> boxes = new ArrayList<>();
            int count = Math.min(40, top.size());
            for (int i = 0; i < count; i++) {
                String word = top.get(i).getKey();
                float size = Ui.dp(a, 13) + (Ui.dp(a, 26) * top.get(i).getValue() / (float) max);
                paint.setTextSize(size);
                float w = paint.measureText(word);
                float h = size * 1.2f;
                /* 螺旋布局 + 碰撞检测 */
                float cx = width / 2f, cy = height / 2f;
                boolean ok = false;
                for (double t = 0; t < 120; t += 0.6) {
                    float x = (float) (cx + t * 3.4 * Math.cos(t)) - w / 2;
                    float y = (float) (cy + t * 2.6 * Math.sin(t));
                    RectF rect = new RectF(x - 6, y - h, x + w + 6, y + 6);
                    if (rect.left < 8 || rect.top < Ui.dp(a, 60)
                            || rect.right > width - 8 || rect.bottom > height - 12) continue;
                    boolean hit = false;
                    for (RectF other : boxes) {
                        if (RectF.intersects(rect, other)) {
                            hit = true;
                            break;
                        }
                    }
                    if (!hit) {
                        boxes.add(rect);
                        placed.add(new Object[]{word, size, x, y, palette[i % palette.length]});
                        ok = true;
                        break;
                    }
                }
                if (!ok && placed.size() > 24) break;
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            regenerate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            paint.setShader(new LinearGradient(0, 0, 0, getHeight(),
                    Ui.dark(a.store) ? 0xFF241B2E : 0xFFFDF3EC,
                    Ui.dark(a.store) ? 0xFF191322 : 0xFFF3E4EF, Shader.TileMode.CLAMP));
            RectF bg = new RectF(0, 0, getWidth(), getHeight());
            canvas.drawRoundRect(bg, Ui.dp(a, 20), Ui.dp(a, 20), paint);
            paint.setShader(null);

            paint.setTextSize(Ui.dp(a, 17));
            paint.setFakeBoldText(true);
            paint.setColor(Ui.plum(a, a.store));
            canvas.drawText("我们的聊天词云", Ui.dp(a, 18), Ui.dp(a, 34), paint);
            paint.setFakeBoldText(false);

            if (placed.isEmpty()) {
                paint.setTextSize(Ui.dp(a, 13));
                paint.setColor(Ui.mutedInk(a, a.store));
                canvas.drawText("聊天内容还不够，多聊聊再来看看吧", Ui.dp(a, 18), Ui.dp(a, 70), paint);
                return;
            }
            for (Object[] item : placed) {
                paint.setTextSize((float) item[1]);
                paint.setColor((int) item[4]);
                canvas.drawText((String) item[0], (float) item[2], (float) item[3], paint);
            }
        }
    }
}

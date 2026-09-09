package com.icepear.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 聊天词云：提取聊天关键词按频次布局，Canvas 绘制渐变背景与词块，
 * 螺旋散点布局并做碰撞检测。
 */
public class CloudPage extends Page {

    private CloudView cloudView;
    private TextView statsView;

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
        statsView = hint("");
        content.addView(statsView);
        LinearLayout actions = Ui.row(a);
        TextView regen = button("↻ 重新生成", false, () -> {
            cloudView.regenerate(true);
            cloudView.invalidate();
            updateStats();
            a.toast("已按当前聊天重新统计并排版");
        });
        actions.addView(regen, Ui.weighted());
        TextView save = button("保存到相册", true, this::saveCloud);
        LinearLayout.LayoutParams slp = Ui.weighted();
        slp.leftMargin = Ui.dp(a, 8);
        actions.addView(save, slp);
        content.addView(actions);
        return pageWithBar("聊天词云", content);
    }

    private void updateStats() {
        if (statsView == null || cloudView == null) return;
        statsView.setText(cloudView.summary());
    }

    private void saveCloud() {
        if (cloudView == null || cloudView.getWidth() == 0 || cloudView.getHeight() == 0) {
            a.toast("词云还没渲染好，稍后再试");
            return;
        }
        Bitmap bitmap = Bitmap.createBitmap(cloudView.getWidth(), cloudView.getHeight(), Bitmap.Config.ARGB_8888);
        cloudView.draw(new Canvas(bitmap));
        a.saveImageToGallery(bitmap, "icepear-cloud-" + System.currentTimeMillis() + ".png");
    }

    @Override
    public void refresh() {
        if (cloudView != null) {
            cloudView.regenerate(false);
            cloudView.invalidate();
            updateStats();
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
        private long seed = 20240101L;
        private int messageCount;
        private int wordCount;
        private String topWords = "";

        CloudView(Context context) {
            super(context);
        }

        String summary() {
            if (messageCount == 0) return "还没有文字消息";
            return "统计 " + messageCount + " 条文字消息 · " + wordCount + " 个关键词"
                    + (topWords.isEmpty() ? "" : " · 高频：" + topWords);
        }

        /** reseed 为 true 时换一套随机起点与配色，得到不同排版；词频始终按当前聊天重算 */
        void regenerate(boolean reseed) {
            if (reseed) seed = System.nanoTime();
            Random rnd = new Random(seed);
            placed.clear();
            JSONArray chat = a.store.chat();
            StringBuilder text = new StringBuilder();
            messageCount = 0;
            for (int i = 0; i < chat.length(); i++) {
                JSONObject msg = chat.optJSONObject(i);
                if (msg == null || msg.optBoolean("recall", false)) continue;
                if (!"".equals(msg.optString("type", ""))) continue;
                String t = msg.optString("text", "");
                if (t.isEmpty()) continue;
                messageCount++;
                text.append(t).append(' ');
            }
            Map<String, Integer> freq = keywordFrequency(text.toString());
            List<Map.Entry<String, Integer>> top = new ArrayList<>(freq.entrySet());
            top.sort((x, y) -> y.getValue() - x.getValue());
            wordCount = top.size();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(5, top.size()); i++) {
                if (i > 0) sb.append(" / ");
                sb.append(top.get(i).getKey()).append('×').append(top.get(i).getValue());
            }
            topWords = sb.toString();
            int max = top.isEmpty() ? 1 : top.get(0).getValue();
            int width = getWidth() > 0 ? getWidth() : Ui.dp(a, 340);
            int height = getHeight() > 0 ? getHeight() : Ui.dp(a, 420);
            List<RectF> boxes = new ArrayList<>();
            int count = Math.min(40, top.size());
            int colorShift = rnd.nextInt(palette.length);
            for (int i = 0; i < count; i++) {
                String word = top.get(i).getKey();
                float size = Ui.dp(a, 13) + (Ui.dp(a, 26) * top.get(i).getValue() / (float) max);
                paint.setTextSize(size);
                float w = paint.measureText(word);
                float h = size * 1.2f;
                /* 螺旋布局 + 碰撞检测；起始角与旋向由种子决定 */
                float cx = width / 2f, cy = height / 2f + Ui.dp(a, 20);
                double phase = rnd.nextDouble() * Math.PI * 2;
                int dir = rnd.nextBoolean() ? 1 : -1;
                boolean ok = false;
                for (double t = 0; t < 120; t += 0.6) {
                    float x = (float) (cx + t * 3.4 * Math.cos(dir * t + phase)) - w / 2;
                    float y = (float) (cy + t * 2.6 * Math.sin(dir * t + phase));
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
                        placed.add(new Object[]{word, size, x, y, palette[(i + colorShift) % palette.length]});
                        ok = true;
                        break;
                    }
                }
                if (!ok && placed.size() > 24) break;
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            regenerate(false);
            updateStats();
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

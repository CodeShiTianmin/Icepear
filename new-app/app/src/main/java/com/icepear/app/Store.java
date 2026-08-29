package com.icepear.app;

import android.content.Context;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 本地数据仓库。数据结构与旧版 milkData JSON 完全一致（roles/chat/theme/font/...），
 * 因此旧版导出的备份文件可以直接导入。
 */
public final class Store {

    private static Store instance;
    private final File dataFile;
    private final File mediaDir;
    public JSONObject data;
    private final Random random = new Random();

    public static Store get(Context context) {
        if (instance == null) instance = new Store(context.getApplicationContext());
        return instance;
    }

    private Store(Context context) {
        dataFile = new File(context.getFilesDir(), "milkData.json");
        mediaDir = new File(context.getFilesDir(), "media");
        if (!mediaDir.exists()) mediaDir.mkdirs();
        load();
    }

    private void load() {
        data = new JSONObject();
        if (dataFile.exists()) {
            try (FileInputStream in = new FileInputStream(dataFile)) {
                byte[] buf = new byte[(int) dataFile.length()];
                int off = 0;
                while (off < buf.length) {
                    int n = in.read(buf, off, buf.length - off);
                    if (n < 0) break;
                    off += n;
                }
                String s = new String(buf, StandardCharsets.UTF_8);
                if (s.startsWith("\uFEFF")) s = s.substring(1);
                data = new JSONObject(s);
            } catch (Exception ignored) {
                data = new JSONObject();
            }
        }
        ensureDefaults();
    }

    public void save() {
        try (FileOutputStream out = new FileOutputStream(dataFile)) {
            out.write(data.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    /* ---------- 默认数据，与 index.html 初始化逻辑一致 ---------- */

    public void ensureDefaults() {
        try {
            if (!data.has("roles")) {
                JSONObject roles = new JSONObject();
                JSONObject first = newRole("他");
                first.put("shop", starterShop());
                first.getJSONArray("chat").put(new JSONObject()
                        .put("id", uid("msg")).put("side", "other")
                        .put("text", "嗨，我在这里。今天想聊点什么？")
                        .put("t", System.currentTimeMillis()).put("read", true));
                roles.put("r1", first);
                data.put("roles", roles);
                data.put("activeRole", "r1");
                data.put("reply", new JSONObject()
                        .put("delayMin", 2).put("delayMax", 7)
                        .put("replyMin", 1).put("replyMax", 2).put("gap", 2)
                        .put("active", false).put("activeMin", 300).put("activeMax", 1800)
                        .put("statusGap", 600).put("ignoreRate", 20).put("enterSend", true));
                data.put("theme", new JSONObject()
                        .put("myBg", "#6d3b58").put("myText", "#ffffff")
                        .put("hisBg", "#fffaf7").put("hisText", "#2d252a"));
                data.put("font", new JSONObject()
                        .put("name", "sans-serif").put("size", 16).put("radius", 18).put("topSkin", false));
            }
            if (!data.has("stickers")) data.put("stickers", new JSONArray());
            if (!data.has("memos")) data.put("memos", new JSONArray());
            if (!data.has("freq")) data.put("freq", new JSONObject());
            if (!data.has("stFreq")) data.put("stFreq", new JSONObject());
            if (!data.has("patchLog")) data.put("patchLog", new JSONArray());
            if (!data.has("snapshots")) data.put("snapshots", new JSONArray());
            if (!data.has("reply")) data.put("reply", new JSONObject()
                    .put("delayMin", 10).put("delayMax", 300)
                    .put("replyMin", 1).put("replyMax", 3).put("gap", 3)
                    .put("active", false).put("activeMin", 300).put("activeMax", 1800)
                    .put("statusGap", 600).put("ignoreRate", 20).put("enterSend", true));
            if (!data.has("chatOpt")) data.put("chatOpt", new JSONObject()
                    .put("timeMode", "all").put("readMode", "all").put("hisIgnore", false));
            if (!data.has("sim")) data.put("sim", new JSONObject()
                    .put("bioClock", false).put("recall", false).put("festival", false)
                    .put("autoNight", false).put("recallReact", false).put("weekly", false));
            if (!data.has("theme")) data.put("theme", new JSONObject()
                    .put("myBg", "#95ec69").put("myText", "#111")
                    .put("hisBg", "#fff").put("hisText", "#111"));
            if (!data.has("font")) data.put("font", new JSONObject()
                    .put("name", "sans-serif").put("size", 15).put("radius", 10).put("topSkin", false));
            if (!data.has("sound")) data.put("sound", new JSONObject()
                    .put("type", "dingdong").put("volume", 50).put("onRecv", true).put("onSend", false));
            if (!data.has("wallpaper")) data.put("wallpaper", new JSONObject()
                    .put("preset", "默认").put("image", ""));
            if (!data.has("videoBg")) data.put("videoBg", "");
            if (!data.has("weatherPool")) data.put("weatherPool", jsonArray("晴", "多云", "阴", "小雨", "雷阵雨", "小雪", "雾", "大风"));
            if (!data.has("hisLocs")) data.put("hisLocs", jsonArray("家里", "公司", "学校", "咖啡店"));
            if (!data.has("recv")) {
                JSONObject recv = new JSONObject();
                for (String k : new String[]{"red", "gift", "zhuan", "wm"}) {
                    recv.put(k, new JSONObject().put("his", new JSONArray()).put("mine", new JSONArray()));
                }
                data.put("recv", recv);
            }
            if (!data.has("cardUi")) data.put("cardUi", new JSONObject()
                    .put("red", new JSONObject().put("t", "恭喜发财").put("icon", "🧧").put("color", 0))
                    .put("zhuan", new JSONObject().put("t", "转账").put("icon", "💸").put("color", 1))
                    .put("gift", new JSONObject().put("t", "礼物").put("icon", "🎁").put("color", 2)));
            if (!data.has("cardColors")) {
                JSONArray colors = new JSONArray();
                colors.put(jsonArray("#fa9d3b", "#f76b1c"));
                colors.put(jsonArray("#e05a5a", "#ff7a59"));
                colors.put(jsonArray("#07c160", "#3bd389"));
                colors.put(jsonArray("#7b5cff", "#a78bfa"));
                data.put("cardColors", colors);
            }
            if (!data.has("bless")) data.put("bless", jsonArray("辛苦啦,买点好吃的", "想你了,收下吧", "今天也要开开心心"));
            if (!data.has("weekly")) data.put("weekly", new JSONObject().put("key", "").put("date", "").put("stats", JSONObject.NULL));
            if (!data.has("gameCount")) data.put("gameCount", 0);
            if (!data.has("bill")) data.put("bill", new JSONArray());
            if (!data.has("moments")) data.put("moments", new JSONArray());
            if (!data.has("emoji")) data.put("emoji", jsonArray(
                    "😊", "😘", "🥰", "😭", "😤", "🤧", "😴", "🥺", "❤️", "💔", "✨", "🌙",
                    "☀️", "🌸", "🍓", "🐰", "(。・ω・。)", "(*´▽`*)", "(๑•̀ㅂ•́)و✧"));
            if (!data.has("daily")) data.put("daily", new JSONObject()
                    .put("weather", jsonArray("晴,阳光很好", "多云,风有点大", "小雨,记得带伞"))
                    .put("body", jsonArray("精神很好", "有点累", "睡得很好"))
                    .put("mood", jsonArray("想你了", "心情不错", "有点小情绪"))
                    .put("did", jsonArray("去上班/上学了", "和朋友吃了顿饭", "在家看剧"))
                    .put("ate", jsonArray("吃了米饭和青菜", "点了外卖", "喝了杯咖啡"))
                    .put("plan", jsonArray("早点休息", "晚上给你打电话", "明天想见你")));
            if (!data.has("nextHisLetter")) data.put("nextHisLetter", 0);
            if (!data.has("customSounds")) data.put("customSounds", new JSONArray());
            if (!data.has("beauty")) data.put("beauty", new JSONObject()
                    .put("pageBg", "#f8f3ef").put("surface", "#fffaf7").put("accent", "#6d3b58")
                    .put("topBg", "#f8f3ef").put("navBg", "#fffaf7").put("css", ""));
            if (!data.has("messageReminders")) data.put("messageReminders", new JSONArray());
            if (!data.has("icepearUi")) data.put("icepearUi", new JSONObject());
            if (!data.has("dark")) data.put("dark", false);
            ensureV230Data();
        } catch (JSONException ignored) {
        }
    }

    public JSONObject newRole(String name) throws JSONException {
        JSONObject role = new JSONObject();
        role.put("name", name).put("nickname", "").put("avatar", "🧑")
                .put("myName", "我").put("myAvatar", "我")
                .put("wallet", new JSONObject().put("his", 100).put("mine", 100))
                .put("cards", starterCards())
                .put("statuses", jsonArray("想你")).put("statusNow", "想你")
                .put("pokes", jsonArray("拍了拍他的头"))
                .put("shop", new JSONArray()).put("letters", new JSONArray())
                .put("chat", new JSONArray()).put("bill", new JSONArray())
                .put("moments", new JSONArray())
                .put("weekly", new JSONObject().put("key", "").put("date", "").put("stats", JSONObject.NULL));
        return role;
    }

    /** 初始字卡，等价于旧版 v2StarterCards() */
    public static JSONObject starterCards() throws JSONException {
        JSONObject cards = new JSONObject();
        cards.put("日常", jsonArray(
                "我在，慢慢说。", "今天过得怎么样？", "先喝口水，再继续忙。", "刚刚突然想到你。",
                "你愿意讲的话，我会认真听。", "别急，我们一件一件来。", "有我陪你呢。", "今天也辛苦啦。"));
        cards.put("关心", jsonArray(
                "不舒服就休息一下，好吗？", "你已经做得很好了。", "不用一直坚强。", "我更在意你的感受。",
                "先照顾好自己，其他事情可以晚一点。", "抱抱你。", "别把所有情绪都藏起来。", "我没有走开。"));
        cards.put("晚安", jsonArray(
                "晚安，祝你做一个轻轻的梦。", "把今天放下吧，明天再继续。", "盖好被子，别着凉。", "睡醒记得来找我。",
                "今天的你也值得被好好珍惜。", "去休息吧，我在这里。"));
        cards.put("小情绪", jsonArray(
                "哼，那你要哄哄我。", "好吧，只原谅你一点点。", "想和你多待一会儿。", "你是不是忘了想我？",
                "不许偷偷难过。", "再靠近一点。"));
        return cards;
    }

    /** 初始商品，等价于旧版 fresh 数据的默认小卖铺 */
    private static JSONArray starterShop() throws JSONException {
        JSONArray shop = new JSONArray();
        shop.put(new JSONObject().put("name", "草莓奶油蛋糕").put("price", 28).put("cat", "food").put("wm", true));
        shop.put(new JSONObject().put("name", "热可可").put("price", 16).put("cat", "drink").put("wm", true));
        shop.put(new JSONObject().put("name", "香薰蜡烛").put("price", 39).put("cat", "daily").put("wm", false));
        return shop;
    }

    /** 保证 2.3.0 数据字段齐全：商品分组、余额兜底文案、消息/动态 id */
    public void ensureV230Data() {
        try {
            JSONObject roles = data.optJSONObject("roles");
            if (roles == null) return;
            Iterator<String> it = roles.keys();
            while (it.hasNext()) {
                String key = it.next();
                JSONObject role = roles.optJSONObject(key);
                if (role == null) continue;
                if (!role.has("shop")) role.put("shop", new JSONArray());
                JSONArray cats = role.optJSONArray("shopCategories");
                if (cats == null || cats.length() == 0) {
                    cats = new JSONArray();
                    cats.put(new JSONObject().put("id", "food").put("name", "食物"));
                    cats.put(new JSONObject().put("id", "drink").put("name", "饮品"));
                    cats.put(new JSONObject().put("id", "daily").put("name", "日用"));
                    role.put("shopCategories", cats);
                }
                JSONArray shop = role.optJSONArray("shop");
                for (int i = 0; i < shop.length(); i++) {
                    JSONObject product = shop.optJSONObject(i);
                    if (product == null) continue;
                    if (!product.has("id")) product.put("id", uid("product"));
                    if (!product.has("cat")) product.put("cat", cats.getJSONObject(0).getString("id"));
                }
                JSONObject fallback = role.optJSONObject("balanceFallback");
                if (fallback == null) {
                    fallback = new JSONObject();
                    role.put("balanceFallback", fallback);
                }
                String legacy = "今天先送你一个抱抱，别的下次补上。";
                if (!fallback.has("red")) fallback.put("red", legacy);
                if (!fallback.has("zhuan")) fallback.put("zhuan", legacy);
                if (!role.has("moments")) role.put("moments", new JSONArray());
                JSONArray moments = role.optJSONArray("moments");
                for (int i = 0; i < moments.length(); i++) {
                    JSONObject moment = moments.optJSONObject(i);
                    if (moment == null) continue;
                    if (!moment.has("id")) moment.put("id", uid("moment"));
                    if (!moment.has("comments")) moment.put("comments", new JSONArray());
                    if (!moment.has("likes")) moment.put("likes", new JSONArray());
                }
                if (!role.has("chat")) role.put("chat", new JSONArray());
                JSONArray chat = role.optJSONArray("chat");
                for (int i = 0; i < chat.length(); i++) {
                    JSONObject message = chat.optJSONObject(i);
                    if (message != null && !message.has("id")) message.put("id", uid("msg"));
                }
                if (!role.has("bill")) role.put("bill", new JSONArray());
                if (!role.has("letters")) role.put("letters", new JSONArray());
                if (!role.has("statuses")) role.put("statuses", jsonArray("想你"));
                if (!role.has("pokes")) role.put("pokes", jsonArray("拍了拍他的头"));
                if (!role.has("wallet")) role.put("wallet", new JSONObject().put("his", 100).put("mine", 100));
                if (!role.has("cards")) role.put("cards", new JSONObject().put("默认", new JSONArray()));
            }
        } catch (JSONException ignored) {
        }
    }

    /* ---------- 常用访问器 ---------- */

    /** 当前角色，等价于旧版 R() */
    public JSONObject role() {
        JSONObject roles = data.optJSONObject("roles");
        String active = data.optString("activeRole", "r1");
        JSONObject role = roles != null ? roles.optJSONObject(active) : null;
        if (role == null && roles != null && roles.keys().hasNext()) {
            String first = roles.keys().next();
            try {
                data.put("activeRole", first);
            } catch (JSONException ignored) {
            }
            role = roles.optJSONObject(first);
        }
        return role;
    }

    /** 显示名：备注优先，等价于旧版 displayName() */
    public String displayName() {
        JSONObject role = role();
        if (role == null) return "他";
        String nickname = role.optString("nickname", "");
        if (!nickname.isEmpty()) return nickname;
        String name = role.optString("name", "他");
        return name.isEmpty() ? "他" : name;
    }

    public JSONArray chat() {
        JSONObject role = role();
        return role != null ? role.optJSONArray("chat") : new JSONArray();
    }

    /** 全部字卡拼成一个池，等价于旧版 allCards() */
    public List<String> allCards() {
        List<String> pool = new ArrayList<>();
        JSONObject role = role();
        if (role == null) return pool;
        JSONObject cards = role.optJSONObject("cards");
        if (cards == null) return pool;
        Iterator<String> it = cards.keys();
        while (it.hasNext()) {
            JSONArray list = cards.optJSONArray(it.next());
            if (list == null) continue;
            for (int i = 0; i < list.length(); i++) {
                String s = list.optString(i, "");
                if (!s.isEmpty()) pool.add(s);
            }
        }
        return pool;
    }

    public int rand(int a, int b) {
        if (b < a) return a;
        return a + random.nextInt(b - a + 1);
    }

    public static String uid(String prefix) {
        return (prefix == null ? "id" : prefix) + "-" + Long.toString(System.currentTimeMillis(), 36)
                + "-" + Long.toString(Math.abs(new Random().nextLong() % 2176782336L), 36);
    }

    public static JSONArray jsonArray(String... items) {
        JSONArray array = new JSONArray();
        for (String item : items) array.put(item);
        return array;
    }

    /* ---------- 备份导出/导入 ---------- */

    public String exportJson() {
        try {
            return data.toString(2);
        } catch (JSONException e) {
            return data.toString();
        }
    }

    public void importJson(String json) throws JSONException {
        String s = json == null ? "" : json.trim();
        if (s.startsWith("\uFEFF")) s = s.substring(1);
        data = new JSONObject(s);
        ensureDefaults();
        save();
    }

    /* ---------- 历史快照 ---------- */

    public void saveSnapshot(String name) throws JSONException {
        JSONArray snaps = data.optJSONArray("snapshots");
        if (snaps == null) {
            snaps = new JSONArray();
            data.put("snapshots", snaps);
        }
        JSONObject copy = new JSONObject(data.toString());
        copy.remove("snapshots");
        if (snaps.length() >= 10) snaps.remove(0);
        snaps.put(new JSONObject()
                .put("t", System.currentTimeMillis())
                .put("name", name == null || name.trim().isEmpty() ? "快照" : name.trim())
                .put("data", copy.toString()));
        save();
    }

    public void restoreSnapshot(int index) throws JSONException {
        JSONArray snaps = data.optJSONArray("snapshots");
        if (snaps == null || index < 0 || index >= snaps.length()) return;
        JSONObject restored = new JSONObject(snaps.getJSONObject(index).getString("data"));
        restored.put("snapshots", snaps);
        data = restored;
        ensureDefaults();
        ensureV230Data();
        save();
    }

    /* ---------- 媒体仓库：大图/音频以文件形式保存，引用为 idb:key ---------- */

    public String putMedia(byte[] bytes, String key) {
        try (FileOutputStream out = new FileOutputStream(new File(mediaDir, key))) {
            out.write(bytes);
            return "idb:" + key;
        } catch (IOException e) {
            return "";
        }
    }

    public String putMediaDataUrl(String dataUrl, String key) {
        try (FileOutputStream out = new FileOutputStream(new File(mediaDir, key))) {
            out.write(dataUrl.getBytes(StandardCharsets.UTF_8));
            return "idb:" + key;
        } catch (IOException e) {
            return "";
        }
    }

    /** 解析媒体引用，等价于旧版 RS()：idb:key -> data url 内容 */
    public String resolveMedia(String ref) {
        String source = ref == null ? "" : ref;
        if (!source.startsWith("idb:")) return source;
        File file = new File(mediaDir, source.substring(4));
        if (!file.exists()) return "";
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n < 0) break;
                off += n;
            }
            return new String(buf, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    public void clearAllMedia() {
        File[] files = mediaDir.listFiles();
        for (int i = 0; files != null && i < files.length; i++) {
            //noinspection ResultOfMethodCallIgnored
            files[i].delete();
        }
    }

    public void deleteMedia(String ref) {
        if (ref != null && ref.startsWith("idb:")) {
            //noinspection ResultOfMethodCallIgnored
            new File(mediaDir, ref.substring(4)).delete();
        }
    }

    /** 图片字节转 data url 引用并入库 */
    public String importImage(byte[] bytes, String mime) {
        String dataUrl = "data:" + (mime == null || mime.isEmpty() ? "image/png" : mime) + ";base64,"
                + Base64.encodeToString(bytes, Base64.NO_WRAP);
        return putMediaDataUrl(dataUrl, uid("img"));
    }

    public String importAudio(byte[] bytes, String mime) {
        String dataUrl = "data:" + (mime == null || mime.isEmpty() ? "audio/mpeg" : mime) + ";base64,"
                + Base64.encodeToString(bytes, Base64.NO_WRAP);
        return putMediaDataUrl(dataUrl, uid("sound"));
    }
}

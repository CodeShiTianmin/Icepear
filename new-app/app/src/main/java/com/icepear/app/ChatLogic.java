package com.icepear.app;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.List;

/**
 * 聊天核心逻辑：发消息、自动回复、主动消息、已读、红包/转账/礼物处理、
 * 节日祝福、生物钟状态、自动夜间模式、消息提醒。与旧版 JS 行为一致。
 */
public final class ChatLogic {

    public interface Host {
        void onChatChanged(boolean scrollToBottom);

        void onWalletChanged();

        void onTyping(boolean typing);

        void toast(String message);

        void onThemeMaybeChanged();
    }

    private final Store store;
    private final SoundPlayer sound;
    private final Host host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable replyRunnable;
    private Runnable activeRunnable;
    private Runnable statusRunnable;

    public ChatLogic(Store store, SoundPlayer sound, Host host) {
        this.store = store;
        this.sound = sound;
        this.host = host;
    }

    /* ---------- 基础 ---------- */

    public JSONObject addText(String side, String text) {
        JSONObject msg = new JSONObject();
        try {
            msg.put("side", side).put("text", text);
        } catch (JSONException ignored) {
        }
        return addMsg(side, msg);
    }

    public JSONObject addSys(String text) {
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", "sys").put("text", text);
        } catch (JSONException ignored) {
        }
        return addMsg("sys", msg);
    }

    public JSONObject addMsg(String side, JSONObject msg) {
        try {
            msg.put("side", side);
            msg.put("t", System.currentTimeMillis());
            if (!msg.has("id")) msg.put("id", Store.uid("msg"));
            store.chat().put(msg);
            store.save();
            host.onChatChanged("me".equals(side));
            JSONObject soundOpt = store.data.optJSONObject("sound");
            if ("other".equals(side) && soundOpt != null && soundOpt.optBoolean("onRecv", true)) {
                sound.play(soundOpt.optString("type", "dingdong"));
            }
            if ("me".equals(side) && !msg.optBoolean("recall", false)) {
                markReadLater(msg);
            }
        } catch (JSONException ignored) {
        }
        return msg;
    }

    /** 已读延迟：1.5~6 秒后标记我发的消息为已读 */
    private void markReadLater(JSONObject msg) {
        String readMode = optChatOpt().optString("readMode", "all");
        if ("none".equals(readMode)) return;
        handler.postDelayed(() -> {
            try {
                msg.put("read", true);
                store.save();
                host.onChatChanged(false);
            } catch (JSONException ignored) {
            }
        }, store.rand(1500, 6000));
    }

    private JSONObject optChatOpt() {
        JSONObject opt = store.data.optJSONObject("chatOpt");
        return opt != null ? opt : new JSONObject();
    }

    private JSONObject optReply() {
        JSONObject reply = store.data.optJSONObject("reply");
        return reply != null ? reply : new JSONObject();
    }

    /* ---------- 自动回复 ---------- */

    public void scheduleReply() {
        if (replyRunnable != null) handler.removeCallbacks(replyRunnable);
        JSONObject reply = optReply();
        JSONObject chatOpt = optChatOpt();
        if (chatOpt.optBoolean("hisIgnore", false)
                && store.rand(0, 99) < reply.optInt("ignoreRate", 20)) {
            handler.postDelayed(this::markAllRead, 1500);
            return;
        }
        host.onTyping(true);
        int wait = store.rand(reply.optInt("delayMin", 10), reply.optInt("delayMax", 300)) * 1000;
        replyRunnable = () -> {
            host.onTyping(false);
            List<String> pool = store.allCards();
            if (pool.isEmpty()) return;
            int count = store.rand(reply.optInt("replyMin", 1), reply.optInt("replyMax", 3));
            int gap = reply.optInt("gap", 3) * 1000;
            for (int i = 0; i < count; i++) {
                String pick = pool.get(store.rand(0, pool.size() - 1));
                handler.postDelayed(() -> addText("other", pick), (long) i * gap);
            }
        };
        handler.postDelayed(replyRunnable, wait);
    }

    public void markAllRead() {
        JSONArray chat = store.chat();
        boolean changed = false;
        for (int i = 0; i < chat.length(); i++) {
            JSONObject msg = chat.optJSONObject(i);
            if (msg != null && "me".equals(msg.optString("side")) && !msg.optBoolean("read", false)) {
                try {
                    msg.put("read", true);
                    changed = true;
                } catch (JSONException ignored) {
                }
            }
        }
        if (changed) {
            store.save();
            host.onChatChanged(false);
        }
    }

    /* ---------- 他主动发消息（2.3 只发已配置内容） ---------- */

    public void startActiveLoop() {
        stopActiveLoop();
        JSONObject reply = optReply();
        if (!reply.optBoolean("active", false)) return;
        int wait = store.rand(reply.optInt("activeMin", 300), reply.optInt("activeMax", 1800)) * 1000;
        activeRunnable = () -> {
            heSendRandom();
            startActiveLoop();
        };
        handler.postDelayed(activeRunnable, wait);
    }

    public void stopActiveLoop() {
        if (activeRunnable != null) handler.removeCallbacks(activeRunnable);
        activeRunnable = null;
    }

    /** 等价于 heSendConfiguredOnly：字卡/emoji/表情包/定位/红包/转账/礼物 */
    public void heSendRandom() {
        try {
            JSONObject role = store.role();
            if (role == null) return;
            int kind = store.rand(0, 6);
            if (kind == 0) {
                cardFallback();
                return;
            }
            if (kind == 1) {
                JSONArray emoji = store.data.optJSONArray("emoji");
                if (emoji != null && emoji.length() > 0) {
                    addText("other", emoji.optString(store.rand(0, emoji.length() - 1)));
                } else cardFallback();
                return;
            }
            if (kind == 2) {
                JSONArray stickers = store.data.optJSONArray("stickers");
                if (stickers != null && stickers.length() > 0) {
                    JSONObject msg = new JSONObject().put("type", "img")
                            .put("src", stickers.optString(store.rand(0, stickers.length() - 1)));
                    addMsg("other", msg);
                } else cardFallback();
                return;
            }
            if (kind == 6) {
                JSONArray locs = store.data.optJSONArray("hisLocs");
                if (locs != null && locs.length() > 0) {
                    JSONObject msg = new JSONObject().put("type", "loc")
                            .put("text", locs.optString(store.rand(0, locs.length() - 1)));
                    addMsg("other", msg);
                } else cardFallback();
                return;
            }
            String type = kind == 3 ? "red" : kind == 5 ? "zhuan" : "gift";
            JSONObject wallet = role.getJSONObject("wallet");
            double his = wallet.optDouble("his", 0);
            if (his <= 0) {
                if ("red".equals(type) || "zhuan".equals(type)) {
                    JSONObject fallback = role.optJSONObject("balanceFallback");
                    String text = fallback != null ? fallback.optString(type, "").trim() : "";
                    if (!text.isEmpty()) addText("other", text);
                } else cardFallback();
                return;
            }
            double amount;
            JSONObject msg = new JSONObject().put("type", type).put("read", true)
                    .put("txVersion", 2).put("txStatus", "");
            if ("gift".equals(type)) {
                JSONArray shop = role.optJSONArray("shop");
                JSONObject pick = null;
                java.util.List<JSONObject> affordable = new java.util.ArrayList<>();
                for (int i = 0; shop != null && i < shop.length(); i++) {
                    JSONObject product = shop.optJSONObject(i);
                    if (product != null && product.optDouble("price", 0) > 0
                            && product.optDouble("price", 0) <= his) {
                        affordable.add(product);
                    }
                }
                if (affordable.isEmpty()) {
                    cardFallback();
                    return;
                }
                pick = affordable.get(store.rand(0, affordable.size() - 1));
                amount = pick.optDouble("price", 0);
                msg.put("gift", pick.optString("name")).put("price", amount);
            } else {
                amount = Math.max(0.01, Math.round(store.rand(0, (int) Math.min(9999999, his)) * 100) / 100.0);
                amount = Math.min(his, amount);
            }
            msg.put("amount", amount);
            wallet.put("his", his - amount);
            addMsg("other", msg);
            host.onWalletChanged();
            store.save();
        } catch (JSONException ignored) {
        }
    }

    private void cardFallback() {
        List<String> pool = store.allCards();
        if (!pool.isEmpty()) addText("other", pool.get(store.rand(0, pool.size() - 1)));
    }

    /* ---------- 生物钟状态轮换 ---------- */

    public void startStatusLoop() {
        stopStatusLoop();
        JSONObject sim = store.data.optJSONObject("sim");
        if (sim == null || !sim.optBoolean("bioClock", false)) return;
        int gap = optReply().optInt("statusGap", 600) * 1000;
        statusRunnable = () -> {
            try {
                JSONObject role = store.role();
                if (role != null) {
                    JSONArray statuses = role.optJSONArray("statuses");
                    if (statuses != null && statuses.length() > 0) {
                        role.put("statusNow", statuses.optString(store.rand(0, statuses.length() - 1)));
                        store.save();
                        host.onChatChanged(false);
                    }
                }
            } catch (JSONException ignored) {
            }
            startStatusLoop();
        };
        handler.postDelayed(statusRunnable, gap);
    }

    public void stopStatusLoop() {
        if (statusRunnable != null) handler.removeCallbacks(statusRunnable);
        statusRunnable = null;
    }

    /* ---------- 撤回 ---------- */

    public void recall(int index) {
        try {
            JSONObject msg = store.chat().optJSONObject(index);
            if (msg == null) return;
            msg.put("recall", true);
            store.save();
            host.onChatChanged(false);
            JSONObject sim = store.data.optJSONObject("sim");
            if (sim != null && sim.optBoolean("recallReact", false) && "me".equals(msg.optString("side"))) {
                handler.postDelayed(() -> addText("other",
                        new String[]{"刚刚撤回了什么呀？", "让我看看你撤回了什么", "撤回也来不及啦，我看到了"}
                                [store.rand(0, 2)]), store.rand(2000, 6000));
            }
        } catch (JSONException ignored) {
        }
    }

    /* ---------- 红包/转账被他领取 ---------- */

    public void hisAutoAccept(JSONObject msg) {
        handler.postDelayed(() -> {
            try {
                if (msg.optBoolean("recall", false)) return;
                msg.put("txStatus", "accepted");
                JSONObject role = store.role();
                if (role != null) {
                    JSONObject wallet = role.getJSONObject("wallet");
                    double amount = msg.optDouble("amount", msg.optDouble("price", 0));
                    wallet.put("his", wallet.optDouble("his", 0) + amount);
                }
                store.save();
                host.onChatChanged(false);
                host.onWalletChanged();
                JSONArray bless = store.data.optJSONArray("bless");
                if (bless != null && bless.length() > 0 && store.rand(0, 1) == 0) {
                    handler.postDelayed(() -> addText("other",
                            bless.optString(store.rand(0, bless.length() - 1))), store.rand(1500, 5000));
                }
            } catch (JSONException ignored) {
            }
        }, store.rand(2000, 12000));
    }

    /* ---------- 节日祝福 / 自动夜间模式 ---------- */

    public void checkFestival() {
        JSONObject sim = store.data.optJSONObject("sim");
        if (sim == null || !sim.optBoolean("festival", false)) return;
        Calendar now = Calendar.getInstance();
        String key = (now.get(Calendar.MONTH) + 1) + "-" + now.get(Calendar.DAY_OF_MONTH);
        String greeting = null;
        switch (key) {
            case "1-1": greeting = "新年快乐！新的一年也要一直在一起。"; break;
            case "2-14": greeting = "情人节快乐，我的心意只给你。"; break;
            case "5-20": greeting = "520，我爱你。"; break;
            case "6-1": greeting = "儿童节快乐，永远保持童心。"; break;
            case "10-1": greeting = "国庆快乐，假期要好好休息呀。"; break;
            case "12-24": greeting = "平安夜快乐，愿你平安喜乐。"; break;
            case "12-25": greeting = "圣诞快乐！有没有想要的礼物？"; break;
        }
        if (greeting == null) return;
        String flagKey = "festival-" + now.get(Calendar.YEAR) + "-" + key;
        if (flagKey.equals(store.data.optString("lastFestival", ""))) return;
        try {
            store.data.put("lastFestival", flagKey);
        } catch (JSONException ignored) {
        }
        final String text = greeting;
        handler.postDelayed(() -> addText("other", text), 3000);
    }

    /** 自动夜间模式：22:00-7:00 深色 */
    public void checkAutoNight() {
        JSONObject sim = store.data.optJSONObject("sim");
        if (sim == null || !sim.optBoolean("autoNight", false)) return;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean night = hour >= 22 || hour < 7;
        if (store.data.optBoolean("dark", false) != night) {
            try {
                store.data.put("dark", night);
                store.save();
                host.onThemeMaybeChanged();
            } catch (JSONException ignored) {
            }
        }
    }

    /* ---------- 消息提醒 ---------- */

    public void scheduleStoredReminders() {
        JSONArray reminders = store.data.optJSONArray("messageReminders");
        for (int i = 0; reminders != null && i < reminders.length(); i++) {
            JSONObject reminder = reminders.optJSONObject(i);
            if (reminder == null || reminder.optBoolean("done", false)) continue;
            scheduleReminder(reminder);
        }
    }

    public void scheduleReminder(JSONObject reminder) {
        long wait = Math.max(0, reminder.optLong("at") - System.currentTimeMillis());
        if (wait > 2147483000L) return;
        handler.postDelayed(() -> {
            try {
                if (reminder.optBoolean("done", false)) return;
                JSONObject roles = store.data.optJSONObject("roles");
                JSONObject role = roles != null ? roles.optJSONObject(reminder.optString("roleId")) : null;
                if (role == null) return;
                reminder.put("done", true);
                JSONObject msg = new JSONObject().put("type", "sys")
                        .put("text", "提醒：" + reminder.optString("text"))
                        .put("t", System.currentTimeMillis()).put("id", Store.uid("msg"));
                role.getJSONArray("chat").put(msg);
                store.save();
                if (store.data.optString("activeRole").equals(reminder.optString("roleId"))) {
                    host.onChatChanged(true);
                    host.toast("消息提醒已到时间");
                }
            } catch (JSONException ignored) {
            }
        }, wait);
    }

    /* ---------- 账单 ---------- */

    public void billAdd(String from, String to, String type, double amount, String status) {
        try {
            JSONObject role = store.role();
            if (role == null) return;
            JSONArray bill = role.optJSONArray("bill");
            if (bill == null) {
                bill = new JSONArray();
                role.put("bill", bill);
            }
            bill.put(new JSONObject().put("from", from).put("to", to).put("type", type)
                    .put("amount", amount).put("status", status).put("t", System.currentTimeMillis()));
            store.save();
        } catch (JSONException ignored) {
        }
    }

    public Handler handler() {
        return handler;
    }
}

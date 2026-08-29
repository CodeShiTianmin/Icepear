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
            if ("me".equals(side) && soundOpt != null && soundOpt.optBoolean("onSend", false)) {
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
        }, store.rand(4, 9) * 1000);
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
            return;
        }
        host.onTyping(true);
        long wait = store.rand(reply.optInt("delayMin", 10), reply.optInt("delayMax", 300)) * 1000L;
        JSONObject sim = store.data.optJSONObject("sim");
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (sim != null && sim.optBoolean("bioClock", false) && (hour >= 23 || hour < 7)) {
            wait = (long) (wait * 2.5);
        }
        replyRunnable = () -> {
            host.onTyping(false);
            JSONObject simNow = store.data.optJSONObject("sim");
            boolean festivalOn = simNow != null && simNow.optBoolean("festival", false);
            String festival = festivalToday();
            if (festivalOn && festival != null && Math.random() < 0.4) {
                addText("other", "今天是" + festival + "，"
                        + new String[]{"想你", "陪你过节", "节日快乐"}[store.rand(0, 2)]);
                return;
            }
            String memo = memoToday();
            if (festivalOn && memo != null && Math.random() < 0.4) {
                addText("other", "今天是「" + memo + "」，"
                        + new String[]{"一直在心里", "记得呢", "陪你一起"}[store.rand(0, 2)]);
                return;
            }
            List<String> pool = store.allCards();
            if (pool.isEmpty()) return;
            java.util.Collections.shuffle(pool);
            int count = Math.min(pool.size(),
                    store.rand(reply.optInt("replyMin", 1), reply.optInt("replyMax", 3)));
            int gap = reply.optInt("gap", 3) * 1000;
            String quoted = lastReadMeText();
            for (int i = 0; i < count; i++) {
                final String pick = pool.get(i);
                final boolean withQuote = i == 0 && quoted != null && Math.random() < 0.4;
                final String quoteText = quoted;
                handler.postDelayed(() -> {
                    JSONObject msg = new JSONObject();
                    try {
                        msg.put("text", pick);
                        if (withQuote) msg.put("quote", quoteText);
                    } catch (JSONException ignored) {
                    }
                    addMsg("other", msg);
                }, (long) i * gap);
            }
        };
        handler.postDelayed(replyRunnable, wait);
    }

    /** 最后一条已读的我方文字消息，等价于旧版 v2LastReadMessage() */
    private String lastReadMeText() {
        JSONArray chat = store.chat();
        for (int i = chat.length() - 1; i >= 0; i--) {
            JSONObject msg = chat.optJSONObject(i);
            if (msg == null) continue;
            if ("me".equals(msg.optString("side")) && !msg.optBoolean("recall", false)
                    && msg.optString("type", "").isEmpty() && !msg.optString("text", "").isEmpty()
                    && msg.optBoolean("read", false)) {
                return msg.optString("text");
            }
        }
        return null;
    }

    private static final String[][] FESTIVALS = {
            {"1-1", "元旦"}, {"2-14", "情人节"}, {"5-20", "520"}, {"6-1", "儿童节"},
            {"10-1", "国庆节"}, {"12-24", "平安夜"}, {"12-25", "圣诞节"}, {"12-31", "跨年"},
    };

    /** 今天的节日名，等价于旧版 festivalToday() */
    public String festivalToday() {
        Calendar now = Calendar.getInstance();
        String key = (now.get(Calendar.MONTH) + 1) + "-" + now.get(Calendar.DAY_OF_MONTH);
        for (String[] festival : FESTIVALS) {
            if (festival[0].equals(key)) return festival[1];
        }
        return null;
    }

    /** 今天的纪念日名，等价于旧版 memoToday() */
    public String memoToday() {
        Calendar now = Calendar.getInstance();
        JSONArray memos = store.data.optJSONArray("memos");
        for (int i = 0; memos != null && i < memos.length(); i++) {
            JSONObject memo = memos.optJSONObject(i);
            if (memo == null) continue;
            String[] parts = memo.optString("date", "").split("-");
            if (parts.length < 3) continue;
            try {
                if (Integer.parseInt(parts[1]) == now.get(Calendar.MONTH) + 1
                        && Integer.parseInt(parts[2]) == now.get(Calendar.DAY_OF_MONTH)) {
                    return memo.optString("name");
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
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
                int max = (int) Math.min(9999999, Math.max(0, Math.floor(his)));
                amount = Math.min(his, store.rand(0, max));
                amount = Math.max(0.01, Math.round(amount * 100) / 100.0);
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

    /* ---------- 交易结算，等价于旧版 v2FinishTransaction / v2ScheduleHisDecision ---------- */

    public static double txAmount(JSONObject msg) {
        return msg.has("gift") ? msg.optDouble("price", 0) : msg.optDouble("amount", 0);
    }

    public static String txKindLabel(JSONObject msg) {
        if ("zhuan".equals(msg.optString("type"))) return "转账";
        return msg.has("gift") ? "礼物" : "红包";
    }

    /** 结算一笔交易：更新钱包、状态、账单 */
    public void finishTransaction(JSONObject msg, String status) {
        try {
            JSONObject role = store.role();
            if (role == null || msg == null || !msg.optString("txStatus", "").isEmpty()) return;
            double amount = txAmount(msg);
            boolean legacy = msg.optInt("txVersion", 0) != 2;
            JSONObject wallet = role.getJSONObject("wallet");
            boolean accepted = "accepted".equals(status);
            if ("me".equals(msg.optString("side"))) {
                if (accepted) {
                    if (!legacy) wallet.put("his", wallet.optDouble("his", 0) + amount);
                } else if (legacy) {
                    wallet.put("his", Math.max(0, wallet.optDouble("his", 0) - amount));
                    wallet.put("mine", wallet.optDouble("mine", 0) + amount);
                } else {
                    wallet.put("mine", wallet.optDouble("mine", 0) + amount);
                }
            } else if (accepted) {
                wallet.put("mine", wallet.optDouble("mine", 0) + amount);
            } else if (!legacy) {
                wallet.put("his", wallet.optDouble("his", 0) + amount);
            }
            msg.put("txStatus", status);
            msg.put("handled", true);
            msg.put("read", true);
            String type = "zhuan".equals(msg.optString("type")) ? "转账"
                    : msg.has("gift") ? "礼物：" + msg.optString("gift") : "红包";
            billAdd(msg.optString("side"), "me".equals(msg.optString("side")) ? "his" : "me",
                    type, amount, accepted ? "已接收" : "已退还");
            store.save();
            host.onWalletChanged();
            host.onChatChanged(false);
        } catch (JSONException ignored) {
        }
    }

    /** 他对我发的红包/转账/礼物做决定：70% 接收，30% 退还 */
    public void scheduleHisDecision(JSONObject msg) {
        handler.postDelayed(() -> {
            try {
                if (msg == null || !msg.optString("txStatus", "").isEmpty()
                        || msg.optBoolean("handled", false) || msg.optBoolean("recall", false)) return;
                boolean accepted = Math.random() < 0.7;
                finishTransaction(msg, accepted ? "accepted" : "returned");
                String kind = txKindLabel(msg);
                addSys(store.displayName() + (accepted ? "已接收" : "已退还") + kind);
                if (accepted) {
                    String key = "zhuan".equals(msg.optString("type")) ? "zhuan"
                            : msg.has("gift") ? "gift" : "red";
                    JSONObject recv = store.data.optJSONObject("recv");
                    JSONObject entry = recv != null ? recv.optJSONObject(key) : null;
                    JSONArray pool = entry != null ? entry.optJSONArray("mine") : null;
                    if (pool != null && pool.length() > 0) {
                        final String text = pool.optString(store.rand(0, pool.length() - 1));
                        handler.postDelayed(() -> addText("other", text), store.rand(2, 4) * 1000L);
                    }
                }
            } catch (Exception ignored) {
            }
        }, store.rand(3, 6) * 1000L);
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

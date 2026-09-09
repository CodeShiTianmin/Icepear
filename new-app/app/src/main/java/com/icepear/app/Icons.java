package com.icepear.app;

/**
 * 全局 SVG 图标（与浏览器版 index.html / app-fixes.js / 内置补丁中的图标一致）。
 */
public final class Icons {

    private Icons() {
    }

    private static final String O = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.7\" stroke-linecap=\"round\" stroke-linejoin=\"round\">";
    private static final String O2 = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\">";
    private static final String OF = "<svg viewBox=\"0 0 24 24\" fill=\"currentColor\">";
    private static final String E = "</svg>";

    /* 底部导航 */
    public static final String NAV_CHAT = O + "<path d=\"M5 17.5 3.5 21l4.2-1.6A9 9 0 1 0 5 17.5Z\"/><path d=\"M8 11.5h8M8 8.5h5\"/>" + E;
    public static final String NAV_MOMENTS = O + "<circle cx=\"12\" cy=\"12\" r=\"4\"/><path d=\"M12 2.5v2.5M12 19v2.5M2.5 12H5M19 12h2.5M5.3 5.3l1.8 1.8M16.9 16.9l1.8 1.8M5.3 18.7l1.8-1.8M16.9 7.1l1.8-1.8\"/>" + E;
    public static final String NAV_MENU = O + "<path d=\"M12 3 9.5 9.5 3 12l6.5 2.5L12 21l2.5-6.5L21 12l-6.5-2.5Z\"/>" + E;
    public static final String NAV_LETTER = O + "<path d=\"M4 5h16v14H4z\"/><path d=\"m4 7 8 6 8-6\"/>" + E;
    public static final String NAV_SET = O + "<circle cx=\"12\" cy=\"8\" r=\"4\"/><path d=\"M4.5 21a7.5 7.5 0 0 1 15 0\"/>" + E;

    /* 顶栏 */
    public static final String SUN = O2 + "<circle cx=\"12\" cy=\"12\" r=\"4\"/><line x1=\"12\" y1=\"2\" x2=\"12\" y2=\"4\"/><line x1=\"12\" y1=\"20\" x2=\"12\" y2=\"22\"/><line x1=\"4\" y1=\"12\" x2=\"2\" y2=\"12\"/><line x1=\"22\" y1=\"12\" x2=\"20\" y2=\"12\"/><line x1=\"5\" y1=\"5\" x2=\"6.5\" y2=\"6.5\"/><line x1=\"17.5\" y1=\"17.5\" x2=\"19\" y2=\"19\"/>" + E;
    public static final String MOON = O2 + "<path d=\"M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5Z\"/>" + E;
    public static final String MORE = OF + "<circle cx=\"5\" cy=\"12\" r=\"2\"/><circle cx=\"12\" cy=\"12\" r=\"2\"/><circle cx=\"19\" cy=\"12\" r=\"2\"/>" + E;
    public static final String BACK = O2 + "<path d=\"M15 5l-7 7 7 7\"/>" + E;
    public static final String CHEVRON = O + "<path d=\"m6 9 6 6 6-6\"/>" + E;
    public static final String CLOSE = O2 + "<path d=\"M6 6l12 12M18 6 6 18\"/>" + E;
    public static final String CHECK = O2 + "<path d=\"m5 12 5 5L20 7\"/>" + E;

    /* 朋友圈 */
    public static final String HEART_ON = "<svg viewBox=\"0 0 24 24\" fill=\"currentColor\" stroke=\"currentColor\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M12 21s-7-4.6-9.5-9A5.5 5.5 0 0 1 12 6.5 5.5 5.5 0 0 1 21.5 12c-2.5 4.4-9.5 9-9.5 9Z\"/></svg>";
    public static final String COMMENT = O + "<path d=\"M21 12a8 8 0 0 1-8 8H4l3-3a8 8 0 1 1 14-5Z\"/><path d=\"M8.5 11h.01M12 11h.01M15.5 11h.01\"/>" + E;
    public static final String IMAGE = O + "<rect x=\"3\" y=\"4\" width=\"18\" height=\"16\" rx=\"3\"/><circle cx=\"8.5\" cy=\"9\" r=\"1.5\"/><path d=\"m4 18 5-5 3 3 3-3 5 5\"/>" + E;
    public static final String PLUS = O2 + "<path d=\"M12 5v14M5 12h14\"/>" + E;
    public static final String TRASH = O + "<path d=\"M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13\"/><path d=\"M10 11v6M14 11v6\"/>" + E;
    public static final String EDIT = O + "<path d=\"M4 20h4l10-10-4-4L4 16z\"/><path d=\"m13 7 4 4\"/>" + E;
    public static final String DICE = O + "<rect x=\"4\" y=\"4\" width=\"16\" height=\"16\" rx=\"3\"/><path d=\"M9 9h.01M15 9h.01M9 15h.01M15 15h.01\"/>" + E;

    /* 输入栏 */
    public static final String SMILE = O + "<circle cx=\"12\" cy=\"12\" r=\"9\"/><path d=\"M8.5 14.5a4.5 4.5 0 0 0 7 0\"/><path d=\"M9 10h.01M15 10h.01\"/>" + E;
    public static final String PLUS_CIRCLE = O + "<circle cx=\"12\" cy=\"12\" r=\"9\"/><path d=\"M12 8v8M8 12h8\"/>" + E;
    public static final String SEND = O + "<path d=\"m4 4 17 8-17 8 3-8Z\"/><path d=\"M7 12h14\"/>" + E;
    public static final String KEYBOARD = O + "<rect x=\"3\" y=\"6\" width=\"18\" height=\"12\" rx=\"2\"/><path d=\"M7 10h.01M11 10h.01M15 10h.01M7 14h10\"/>" + E;

    /* 加号面板（index.html #plusPanel 原样） */
    public static final String ALBUM = O + "<rect x=\"3\" y=\"3\" width=\"18\" height=\"18\" rx=\"3\"/><circle cx=\"8.5\" cy=\"8.5\" r=\"1.5\"/><path d=\"M21 15l-5-5-8 8\"/>" + E;
    public static final String RED = O + "<rect x=\"3\" y=\"8\" width=\"18\" height=\"12\" rx=\"2\"/><path d=\"M3 13h18M12 8v12M12 8s-3-4-5-4 0 4 5 4ZM12 8s3-4 5-4 0 4-5 4Z\"/>" + E;
    public static final String ZHUAN = O + "<circle cx=\"12\" cy=\"12\" r=\"9\"/><path d=\"M8 12h8\"/><path d=\"M12 8v8\"/>" + E;
    public static final String LOC = O + "<path d=\"M12 21s7-5.5 7-11a7 7 0 1 0-14 0c0 5.5 7 11 7 11z\"/><circle cx=\"12\" cy=\"10\" r=\"2.5\"/>" + E;
    public static final String SHOP = O + "<path d=\"M4 4h16l2 6H2z\"/><path d=\"M2 10h20v4a3 3 0 0 1-3 3H5a3 3 0 0 1-3-3z\"/><path d=\"M8 17v3h8v-3\"/>" + E;
    public static final String VIDEO = O + "<rect x=\"2\" y=\"6\" width=\"13\" height=\"12\" rx=\"2\"/><path d=\"M15 10l7-4v12l-7-4\"/>" + E;
    public static final String CLOUD = O + "<path d=\"M17.5 17h-11A3.5 3.5 0 0 1 6.2 10a5.5 5.5 0 0 1 10.6-.4A3.2 3.2 0 0 1 17.5 17Z\"/><circle cx=\"6.8\" cy=\"8.2\" r=\"2.6\"/><path d=\"M6.8 2.9v1M2.9 6.8h1M3.6 4.3l.8.8\"/>" + E;
    public static final String WEEKLY = O + "<path d=\"M4 19V9\"/><path d=\"M10 19V5\"/><path d=\"M16 19v-7\"/><path d=\"M22 19H2\"/>" + E;
    public static final String GIFT = O + "<path d=\"M12 8v13M12 8s-3-4-5-4 0 4 5 4ZM12 8s3-4 5-4 0 4-5 4Z\"/><path d=\"M4 12h16\"/>" + E;
    public static final String POKE = O + "<path d=\"M8 13V5.5a1.5 1.5 0 0 1 3 0V12\"/><path d=\"M11 11.5v-2a1.5 1.5 0 0 1 3 0V12\"/><path d=\"M14 12v-1a1.5 1.5 0 0 1 3 0v2\"/><path d=\"M17 13a1.5 1.5 0 0 1 3 0v3a6 6 0 0 1-6 6h-2a6 6 0 0 1-5-2.7L4.6 15A1.6 1.6 0 0 1 7 13l1 1\"/>" + E;

    /* 功能中心（app-fixes.js menuIcons + p14/p18/p25） */
    public static final String SEARCH = O + "<circle cx=\"11\" cy=\"11\" r=\"7\"/><path d=\"m20 20-4-4\"/>" + E;
    public static final String BAG = O + "<path d=\"M4 8h16l-1 12H5Z\"/><path d=\"M8 8a4 4 0 0 1 8 0\"/>" + E;
    public static final String CARDS = O + "<rect x=\"5\" y=\"4\" width=\"14\" height=\"16\" rx=\"3\"/><path d=\"M9 8h6M9 12h6M9 16h4\"/>" + E;
    public static final String MAIL = O + "<rect x=\"3\" y=\"5\" width=\"18\" height=\"14\" rx=\"3\"/><path d=\"m4 7 8 6 8-6\"/>" + E;
    public static final String SLIDERS = O + "<path d=\"M4 7h10M18 7h2M4 17h2M10 17h10\"/><circle cx=\"16\" cy=\"7\" r=\"2\"/><circle cx=\"8\" cy=\"17\" r=\"2\"/>" + E;
    public static final String SUN_CLOUD = O + "<circle cx=\"8\" cy=\"8\" r=\"3\"/><path d=\"M8 2v2M8 12v2M2 8h2M12 8h2\"/><path d=\"M8 19h10a3 3 0 0 0 .4-6A5 5 0 0 0 9 15\"/>" + E;
    public static final String WORD_CLOUD = O + "<path d=\"M7 18h10a4 4 0 0 0 .6-8A6 6 0 0 0 6.3 12 3 3 0 0 0 7 18Z\"/>" + E;
    public static final String HEART = O + "<path d=\"M12 21s-7-4.6-9.5-9A5.5 5.5 0 0 1 12 6.5 5.5 5.5 0 0 1 21.5 12c-2.5 4.4-9.5 9-9.5 9Z\"/>" + E;
    public static final String CALENDAR = O + "<rect x=\"3\" y=\"5\" width=\"18\" height=\"16\" rx=\"2\"/><path d=\"M8 3v4M16 3v4M3 10h18\"/>" + E;
    public static final String STAR = O + "<path d=\"m12 3 2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9Z\"/>" + E;
    public static final String CART = O + "<circle cx=\"9\" cy=\"20\" r=\"1.6\"/><circle cx=\"17\" cy=\"20\" r=\"1.6\"/><path d=\"M3 4h2l2.4 11.2a2 2 0 0 0 2 1.6h7.9a2 2 0 0 0 2-1.5L21 8H6\"/>" + E;
    public static final String CAMERA = O + "<path d=\"M4 7h3l2-2h6l2 2h3v12H4Z\"/><circle cx=\"12\" cy=\"13\" r=\"3\"/>" + E;
    public static final String WALLET = O + "<rect x=\"3\" y=\"6\" width=\"18\" height=\"13\" rx=\"2\"/><path d=\"M3 10h18\"/><circle cx=\"16\" cy=\"14.5\" r=\"1.2\"/>" + E;
    public static final String BELL = O + "<path d=\"M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15Z\"/><path d=\"M10 20a2 2 0 0 0 4 0\"/>" + E;
    public static final String QUOTE = O + "<path d=\"M7 7h4v4H7zM13 7h4v4h-4z\"/><path d=\"M7 11c0 3-1 4-3 5M13 11c0 3-1 4-3 5\"/>" + E;
    public static final String COPY = O + "<rect x=\"8\" y=\"8\" width=\"12\" height=\"12\" rx=\"2\"/><path d=\"M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2\"/>" + E;
    public static final String UNDO = O + "<path d=\"M9 14 4 9l5-5\"/><path d=\"M4 9h10a6 6 0 0 1 0 12h-3\"/>" + E;
    public static final String SELECT = O + "<rect x=\"3\" y=\"3\" width=\"18\" height=\"18\" rx=\"4\"/><path d=\"m8 12 3 3 5-6\"/>" + E;
    public static final String CLOCK = O + "<circle cx=\"12\" cy=\"12\" r=\"9\"/><path d=\"M12 7v5l3 2\"/>" + E;
    public static final String PHONE_OFF = O + "<path d=\"M5 4c1 6 6 11 12 12l3-3-3-3-3 2a10 10 0 0 1-5-5l2-3-3-3Z\"/>" + E;
    public static final String MINIMIZE = O + "<path d=\"M4 14h6v6\"/><path d=\"m10 14-6 6\"/><path d=\"M20 10h-6V4\"/><path d=\"m14 10 6-6\"/>" + E;
    public static final String SORT = O + "<path d=\"M8 4v16M8 20l-3-3M8 20l3-3\"/><path d=\"M16 20V4M16 4l-3 3M16 4l3 3\"/>" + E;
    public static final String GRID = O + "<rect x=\"4\" y=\"4\" width=\"7\" height=\"7\" rx=\"1.5\"/><rect x=\"13\" y=\"4\" width=\"7\" height=\"7\" rx=\"1.5\"/><rect x=\"4\" y=\"13\" width=\"7\" height=\"7\" rx=\"1.5\"/><rect x=\"13\" y=\"13\" width=\"7\" height=\"7\" rx=\"1.5\"/>" + E;
    public static final String LIST = O2 + "<path d=\"M4 7h16M4 12h16M4 17h10\"/>" + E;
    public static final String DOWNLOAD = O + "<path d=\"M12 4v11\"/><path d=\"m7 10 5 5 5-5\"/><path d=\"M4 19h16\"/>" + E;
    public static final String SAVE = O + "<path d=\"M5 4h11l3 3v13H5Z\"/><path d=\"M8 4v5h7V4\"/><rect x=\"8\" y=\"13\" width=\"8\" height=\"5\"/>" + E;
    public static final String REFRESH = O + "<path d=\"M20 12a8 8 0 1 1-2.3-5.7\"/><path d=\"M20 4v5h-5\"/>" + E;
    public static final String EYE_OFF = O + "<path d=\"M3 3l18 18\"/><path d=\"M10.6 10.6a2 2 0 0 0 2.8 2.8\"/><path d=\"M9.4 5.6A10 10 0 0 1 12 5c5 0 9 4 10 7a11 11 0 0 1-2.6 3.7M6.6 6.6A11 11 0 0 0 2 12c1 3 5 7 10 7 1.5 0 2.9-.3 4.1-.9\"/>" + E;
    public static final String EYE = O + "<path d=\"M2 12c1-3 5-7 10-7s9 4 10 7c-1 3-5 7-10 7S3 15 2 12Z\"/><circle cx=\"12\" cy=\"12\" r=\"3\"/>" + E;
    public static final String IMAGE_BG = O + "<rect x=\"3\" y=\"3\" width=\"18\" height=\"18\" rx=\"3\"/><circle cx=\"8.5\" cy=\"8.5\" r=\"1.5\"/><path d=\"m4 17 5-5 3 3 3-3 5 5\"/>" + E;

    /* 他的日常（p11） */
    public static final String WX = SUN_CLOUD;
    public static final String BODY = O + "<circle cx=\"12\" cy=\"6\" r=\"3\"/><path d=\"M5 21v-2a7 7 0 0 1 14 0v2\"/>" + E;
    public static final String MOOD = SMILE;
    public static final String DID = O + "<path d=\"M9 11l3 3L22 4\"/><path d=\"M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11\"/>" + E;
    public static final String ATE = O + "<path d=\"M4 11h16a8 8 0 0 1-16 0Z\"/><path d=\"M8 11V4M12 11V3M16 11V4\"/>" + E;
    public static final String PLAN = O + "<rect x=\"4\" y=\"5\" width=\"16\" height=\"15\" rx=\"2\"/><path d=\"M8 3v4M16 3v4M4 10h16M9 15h3\"/>" + E;

    /* 红包/转账/礼物图标池（p05 CURATED，12 个，三种共用） */
    private static final String O18 = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\">";
    public static final String[] TX_CURATED = {
            O18 + "<rect x=\"3\" y=\"8\" width=\"18\" height=\"12\" rx=\"2\"/><path d=\"M3 13h18M12 8v12M12 8s-3-4-5-4 0 4 5 4ZM12 8s3-4 5-4 0 4-5 4Z\"/>" + E,
            O18 + "<rect x=\"4\" y=\"6\" width=\"16\" height=\"14\" rx=\"2\"/><path d=\"M4 10h16M9 6c0-1.6-1-2.6-2.5-2.6S4 4.4 4 6M15 6c0-1.6 1-2.6 2.5-2.6S20 4.4 20 6\"/>" + E,
            O18 + "<rect x=\"3\" y=\"8\" width=\"18\" height=\"12\" rx=\"2\"/><path d=\"M3 13h18\"/><path d=\"M12 17.5c-2.6-1.8-4.2-3.1-4.2-4.6 0-1 .8-1.8 1.9-1.8.8 0 1.5.4 2.3 1.2.8-.8 1.5-1.2 2.3-1.2 1.1 0 1.9.8 1.9 1.8 0 1.5-1.6 2.8-4.2 4.6Z\"/>" + E,
            O18 + "<rect x=\"3\" y=\"8\" width=\"18\" height=\"12\" rx=\"2\"/><path d=\"M3 13h18\"/><circle cx=\"12\" cy=\"17\" r=\"2\"/>" + E,
            O18 + "<path d=\"M9 5c2 5 4 5 6 0M6 14h12M12 5v14\"/>" + E,
            O18 + "<rect x=\"5\" y=\"3\" width=\"14\" height=\"18\" rx=\"2\"/><path d=\"M9 8h6M9 12h6M9 16h4\"/>" + E,
            O18 + "<circle cx=\"12\" cy=\"12\" r=\"9\"/><path d=\"M8.5 7.5l3.5 6 3.5-6M12 13.5v5M7.5 15h9M7.5 17.5h9\"/>" + E,
            O18 + "<rect x=\"4\" y=\"4\" width=\"16\" height=\"16\" rx=\"3\"/><path d=\"M8.5 7.5l3.5 6 3.5-6M12 13.5v5M7.5 15h9M7.5 17.5h9\"/>" + E,
            O18 + "<path d=\"M8.5 7.5l3.5 6 3.5-6M12 13.5v5M7.5 15h9M7.5 17.5h9\"/><path d=\"M4 6V4h2M20 18v2h-2M4 18v2h2M20 6V4h-2\"/>" + E,
            O18 + "<path d=\"M12 8v13M12 8s-3-4-5-4 0 4 5 4ZM12 8s3-4 5-4 0 4-5 4Z\"/><path d=\"M4 12h16\"/>" + E,
            O18 + "<rect x=\"3\" y=\"8\" width=\"18\" height=\"12\" rx=\"2\"/><path d=\"M3 13h18M12 8v12M12 8s-3-4-5-4 0 4 5 4ZM12 8s3-4 5-4 0 4-5 4Z\"/>" + E,
            O18 + "<rect x=\"4\" y=\"9\" width=\"16\" height=\"10\" rx=\"2\"/><path d=\"M12 9v10M4 13h16\"/><path d=\"M10 13.5l4 4M14 13.5l-4 4\"/>" + E,
    };

    /** 浏览器版默认标题池（p05 TITLES） */
    public static final String[] TITLES_RED = {"恭喜发财，大吉大利", "新年快乐，万事如意", "生日快乐，天天开心", "辛苦啦，收下我的心意", "今天也要开开心心", "想你了，一点点心意", "请收下这份祝福", "平安喜乐，顺遂无忧", "爱你，比心", "愿你一切顺利"};
    public static final String[] TITLES_ZHUAN = {"转账给你", "生活费记得收", "还你上次的", "拿去花，别客气", "照顾好自己，买点好吃的", "最近辛苦了", "按时吃饭", "一点点心意", "加油，我支持你", "爱你哟"};
    public static final String[] TITLES_GIFT = {"礼物请收下", "特意为你挑的", "希望你喜欢", "生日快乐", "纪念日快乐", "看到它就想到你", "节日快乐", "送你的小心意", "好好照顾自己", "愿你每天都开心"};
}

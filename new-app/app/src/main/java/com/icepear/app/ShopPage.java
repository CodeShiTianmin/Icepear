package com.icepear.app;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 小卖铺：钱包余额卡片、商品分组（新建/改名/删除）、商品增删与“送给他”、账单。
 */
public class ShopPage extends Page {

    private LinearLayout content;

    public ShopPage(MainActivity activity) {
        super(activity);
    }

    @Override
    protected View create() {
        content = Ui.column(a);
        return pageWithBar("小卖铺", content);
    }

    @Override
    public void refresh() {
        if (content == null) return;
        content.removeAllViews();
        JSONObject role = a.store.role();
        if (role == null) return;
        a.store.ensureV230Data();

        /* 余额卡片 */
        LinearLayout walletCard = card(null);
        walletCard.setBackground(Ui.gradient(Ui.plum(a, a.store), 0xFFE77D73, Ui.dp(a, 20)));
        JSONObject wallet = role.optJSONObject("wallet");
        LinearLayout walletRow = Ui.row(a);
        LinearLayout mineCol = Ui.column(a);
        mineCol.addView(Ui.text(a, "我的余额（点击修改）", 12, 0xCCFFFFFF));
        mineCol.addView(Ui.boldText(a, "¥" + Ui.fmtMoney(wallet != null ? wallet.optDouble("mine", 0) : 0), 22, Color.WHITE));
        mineCol.setOnClickListener(v -> editBalance("mine", "我的余额"));
        LinearLayout hisCol = Ui.column(a);
        hisCol.addView(Ui.text(a, "他的余额（点击修改）", 12, 0xCCFFFFFF));
        hisCol.addView(Ui.boldText(a, "¥" + Ui.fmtMoney(wallet != null ? wallet.optDouble("his", 0) : 0), 22, Color.WHITE));
        hisCol.setOnClickListener(v -> editBalance("his", "他的余额"));
        walletRow.addView(mineCol, Ui.weighted());
        walletRow.addView(hisCol, Ui.weighted());
        walletCard.addView(walletRow);
        LinearLayout walletTools = Ui.row(a);
        walletTools.setGravity(Gravity.CENTER_VERTICAL);
        walletTools.setPadding(0, Ui.dp(a, 10), 0, 0);
        TextView billButton = Ui.boldText(a, "查看账单 ›", 12, Color.WHITE);
        billButton.setOnClickListener(v -> showBill());
        walletTools.addView(billButton, Ui.weighted());
        LinearLayout cartButton = Ui.row(a);
        cartButton.setGravity(Gravity.CENTER_VERTICAL);
        cartButton.setBackground(Ui.rounded(0x33FFFFFF, Ui.dp(a, 12)));
        cartButton.setPadding(Ui.dp(a, 10), Ui.dp(a, 5), Ui.dp(a, 10), Ui.dp(a, 5));
        cartButton.addView(SvgIcon.view(a, Icons.CART, Color.WHITE, 16));
        int cartCount = cartIds().size();
        TextView cartText = Ui.boldText(a, "购物车" + (cartCount > 0 ? " · " + cartCount : ""), 12, Color.WHITE);
        cartText.setPadding(Ui.dp(a, 4), 0, 0, 0);
        cartButton.addView(cartText);
        cartButton.setOnClickListener(v -> openCartSheet());
        walletTools.addView(cartButton);
        walletCard.addView(walletTools);
        content.addView(walletCard);

        /* 分组 */
        LinearLayout groupCard = card("商品分组");
        JSONArray cats = role.optJSONArray("shopCategories");
        JSONArray shop = role.optJSONArray("shop");
        String activeCat = activeCategory(role);
        GridLayout grid = new GridLayout(a);
        grid.setColumnCount(3);
        for (int i = 0; cats != null && i < cats.length(); i++) {
            JSONObject cat = cats.optJSONObject(i);
            if (cat == null) continue;
            final String id = cat.optString("id");
            int count = 0;
            for (int j = 0; shop != null && j < shop.length(); j++) {
                JSONObject product = shop.optJSONObject(j);
                if (product != null && id.equals(product.optString("cat"))) count++;
            }
            boolean on = id.equals(activeCat);
            LinearLayout cell = Ui.column(a);
            cell.setGravity(Gravity.CENTER);
            cell.setBackground(on
                    ? Ui.rounded(Ui.plum(a, a.store), Ui.dp(a, 14))
                    : Ui.roundedStroke(Ui.surfaceStrong(a, a.store), Ui.dp(a, 14), Ui.line(a, a.store), Ui.dp(a, 1)));
            cell.setPadding(Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 10));
            cell.addView(Ui.boldText(a, cat.optString("name"), 13, on ? Color.WHITE : Ui.ink(a, a.store)));
            cell.addView(Ui.text(a, count + " 件商品", 10, on ? 0xCCFFFFFF : Ui.faintInk(a, a.store)));
            cell.setOnClickListener(v -> {
                setActiveCategory(id);
                refresh();
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.setMargins(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
            cell.setLayoutParams(lp);
            grid.addView(cell);
        }
        groupCard.addView(grid);
        LinearLayout tools = Ui.row(a);
        tools.addView(toolButton("＋ 新建分组", this::addCategory));
        tools.addView(toolButton("改名", this::renameCategory));
        tools.addView(toolButton("删除", this::deleteCategory));
        tools.setPadding(0, Ui.dp(a, 8), 0, 0);
        groupCard.addView(tools);
        content.addView(groupCard);

        /* 商品列表 */
        LinearLayout listCard = card(categoryName(role, activeCat));
        boolean any = false;
        for (int i = 0; shop != null && i < shop.length(); i++) {
            JSONObject product = shop.optJSONObject(i);
            if (product == null || !activeCat.equals(product.optString("cat"))) continue;
            any = true;
            final String productId = product.optString("id");
            LinearLayout row = Ui.row(a);
            row.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
            row.setGravity(Gravity.CENTER_VERTICAL);
            String productIcon = product.optString("icon", "");
            row.addView(SvgIcon.view(a, SvgIcon.isSvg(productIcon) ? productIcon : Icons.GIFT, Ui.plum(a, a.store), 22));
            LinearLayout copy = Ui.column(a);
            copy.setPadding(Ui.dp(a, 10), 0, Ui.dp(a, 6), 0);
            copy.addView(Ui.boldText(a, product.optString("name"), 14, Ui.ink(a, a.store)));
            copy.addView(Ui.text(a, "¥" + Ui.fmtMoney(product.optDouble("price", 0)), 12, Ui.mutedInk(a, a.store)));
            row.addView(copy, Ui.weighted());
            int inCart = countInCart(productId);
            LinearLayout cartAdd = Ui.row(a);
            cartAdd.setGravity(Gravity.CENTER_VERTICAL);
            cartAdd.addView(SvgIcon.view(a, Icons.CART, inCart > 0 ? Color.WHITE : Ui.plum(a, a.store), 18));
            if (inCart > 0) {
                TextView n = Ui.boldText(a, "×" + inCart, 12, Color.WHITE);
                n.setPadding(Ui.dp(a, 3), 0, 0, 0);
                cartAdd.addView(n);
            }
            cartAdd.setBackground(inCart > 0
                    ? Ui.rounded(0xFF7FA98C, Ui.dp(a, 10))
                    : Ui.roundedStroke(0x00000000, Ui.dp(a, 10), Ui.line(a, a.store), Ui.dp(a, 1)));
            cartAdd.setPadding(Ui.dp(a, 8), Ui.dp(a, 7), Ui.dp(a, 8), Ui.dp(a, 7));
            cartAdd.setContentDescription("加入购物车");
            cartAdd.setOnClickListener(v -> addToCart(productId));
            LinearLayout.LayoutParams cartLp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cartLp.rightMargin = Ui.dp(a, 8);
            row.addView(cartAdd, cartLp);
            TextView send = Ui.boldText(a, "送给他", 12, Color.WHITE);
            send.setBackground(Ui.rounded(Ui.plum(a, a.store), Ui.dp(a, 10)));
            send.setPadding(Ui.dp(a, 12), Ui.dp(a, 7), Ui.dp(a, 12), Ui.dp(a, 7));
            send.setOnClickListener(v -> sendProduct(productId));
            row.addView(send);
            row.addView(trashButton("删除商品“" + product.optString("name") + "”？", () -> deleteProduct(productId)));
            listCard.addView(row);
        }
        if (!any) listCard.addView(hint("这个分组还没有商品，点击下方“添加商品”放入第一件商品。"));
        content.addView(listCard);

        content.addView(button("＋ 添加商品", true, this::addProduct));
    }

    private TextView toolButton(String label, Runnable onClick) {
        TextView button = Ui.boldText(a, label, 12, Ui.plum(a, a.store));
        button.setPadding(Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 6));
        button.setBackground(Ui.roundedStroke(0x00000000, Ui.dp(a, 10), Ui.line(a, a.store), Ui.dp(a, 1)));
        button.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(a, 8);
        button.setLayoutParams(lp);
        return button;
    }

    /* ---------- 分组管理 ---------- */

    private String activeCategory(JSONObject role) {
        try {
            JSONObject prefs = a.store.data.optJSONObject("icepearUi");
            if (prefs == null) {
                prefs = new JSONObject();
                a.store.data.put("icepearUi", prefs);
            }
            JSONObject shopActive = prefs.optJSONObject("shopActive");
            if (shopActive == null) {
                shopActive = new JSONObject();
                prefs.put("shopActive", shopActive);
            }
            String roleKey = a.store.data.optString("activeRole");
            String id = shopActive.optString(roleKey, "");
            JSONArray cats = role.optJSONArray("shopCategories");
            for (int i = 0; cats != null && i < cats.length(); i++) {
                JSONObject cat = cats.optJSONObject(i);
                if (cat != null && id.equals(cat.optString("id"))) return id;
            }
            return cats != null && cats.length() > 0 ? cats.getJSONObject(0).optString("id") : "food";
        } catch (JSONException e) {
            return "food";
        }
    }

    private void setActiveCategory(String id) {
        try {
            JSONObject prefs = a.store.data.optJSONObject("icepearUi");
            JSONObject shopActive = prefs.optJSONObject("shopActive");
            if (shopActive == null) {
                shopActive = new JSONObject();
                prefs.put("shopActive", shopActive);
            }
            shopActive.put(a.store.data.optString("activeRole"), id);
            a.store.save();
        } catch (JSONException ignored) {
        }
    }

    private String categoryName(JSONObject role, String id) {
        JSONArray cats = role.optJSONArray("shopCategories");
        for (int i = 0; cats != null && i < cats.length(); i++) {
            JSONObject cat = cats.optJSONObject(i);
            if (cat != null && id.equals(cat.optString("id"))) return cat.optString("name");
        }
        return "商品";
    }

    private void addCategory() {
        Dialogs.prompt(a, a.store, "＋", "新建商品分组", "分组名称", "例如：文具、零食、纪念品", "", value -> {
            try {
                JSONObject role = a.store.role();
                JSONArray cats = role.optJSONArray("shopCategories");
                for (int i = 0; i < cats.length(); i++) {
                    if (value.equals(cats.getJSONObject(i).optString("name"))) {
                        Dialogs.notice(a, a.store, "!", "分组名称重复", "换一个名称再试试。");
                        return;
                    }
                }
                String id = Store.uid("shopcat");
                cats.put(new JSONObject().put("id", id).put("name", value));
                setActiveCategory(id);
                a.store.save();
                refresh();
            } catch (JSONException ignored) {
            }
        });
    }

    private void renameCategory() {
        JSONObject role = a.store.role();
        String activeCat = activeCategory(role);
        Dialogs.prompt(a, a.store, "✎", "修改分组名称", "新的名称", "", categoryName(role, activeCat), value -> {
            try {
                JSONArray cats = role.optJSONArray("shopCategories");
                for (int i = 0; i < cats.length(); i++) {
                    JSONObject cat = cats.getJSONObject(i);
                    if (!activeCat.equals(cat.optString("id")) && value.equals(cat.optString("name"))) {
                        Dialogs.notice(a, a.store, "!", "分组名称重复", "换一个名称再试试。");
                        return;
                    }
                }
                for (int i = 0; i < cats.length(); i++) {
                    JSONObject cat = cats.getJSONObject(i);
                    if (activeCat.equals(cat.optString("id"))) cat.put("name", value);
                }
                a.store.save();
                refresh();
            } catch (JSONException ignored) {
            }
        });
    }

    private void deleteCategory() {
        JSONObject role = a.store.role();
        JSONArray cats = role.optJSONArray("shopCategories");
        if (cats == null || cats.length() <= 1) {
            Dialogs.notice(a, a.store, "!", "至少保留一个分组", "可以先新建分组，再删除当前分组。");
            return;
        }
        String activeCat = activeCategory(role);
        JSONArray shop = role.optJSONArray("shop");
        int count = 0;
        for (int i = 0; shop != null && i < shop.length(); i++) {
            JSONObject product = shop.optJSONObject(i);
            if (product != null && activeCat.equals(product.optString("cat"))) count++;
        }
        Dialogs.confirm(a, a.store, "⌫", "删除“" + categoryName(role, activeCat) + "”？",
                count > 0 ? "分组内的 " + count + " 件商品也会一起删除" : "删除后无法恢复",
                "删除分组", true, () -> {
                    try {
                        JSONArray newCats = new JSONArray();
                        for (int i = 0; i < cats.length(); i++) {
                            JSONObject cat = cats.getJSONObject(i);
                            if (!activeCat.equals(cat.optString("id"))) newCats.put(cat);
                        }
                        role.put("shopCategories", newCats);
                        JSONArray newShop = new JSONArray();
                        for (int i = 0; shop != null && i < shop.length(); i++) {
                            JSONObject product = shop.getJSONObject(i);
                            if (!activeCat.equals(product.optString("cat"))) newShop.put(product);
                        }
                        role.put("shop", newShop);
                        setActiveCategory(newCats.getJSONObject(0).optString("id"));
                        a.store.save();
                        refresh();
                    } catch (JSONException ignored) {
                    }
                });
    }

    /* ---------- 商品 ---------- */

    private void addProduct() {
        JSONObject role = a.store.role();
        JSONArray cats = role.optJSONArray("shopCategories");
        String[] values = new String[cats.length()];
        String[] labels = new String[cats.length()];
        for (int i = 0; i < cats.length(); i++) {
            JSONObject cat = cats.optJSONObject(i);
            values[i] = cat.optString("id");
            labels[i] = cat.optString("name");
        }
        Dialogs.Field name = new Dialogs.Field("name", "商品名称");
        name.placeholder = "例如：草莓奶油蛋糕";
        Dialogs.Field price = new Dialogs.Field("price", "价格");
        price.number = true;
        price.placeholder = "0.00";
        Dialogs.Field category = new Dialogs.Field("category", "所属分组");
        category.optionValues = values;
        category.optionLabels = labels;
        category.value = activeCategory(role);
        Dialogs.form(a, a.store, "🛍", "添加商品", "商品会放进你选择的分组", "添加商品",
                Dialogs.fields(name, price, category), result -> {
                    String productName = result.getOrDefault("name", "").trim();
                    double value;
                    try {
                        value = Double.parseDouble(result.getOrDefault("price", ""));
                    } catch (Exception e) {
                        Dialogs.notice(a, a.store, "!", "价格无效", "价格应为不小于 0 的数字");
                        return;
                    }
                    if (productName.isEmpty() || value < 0) {
                        Dialogs.notice(a, a.store, "!", "内容不完整", "请填写商品名称和价格。");
                        return;
                    }
                    try {
                        role.getJSONArray("shop").put(new JSONObject()
                                .put("id", Store.uid("product"))
                                .put("name", productName)
                                .put("price", Math.round(value * 100) / 100.0)
                                .put("cat", result.get("category")));
                        setActiveCategory(result.get("category"));
                        a.store.save();
                        refresh();
                        a.toast("商品已添加");
                    } catch (JSONException ignored) {
                    }
                });
    }

    private JSONObject findProduct(String id) {
        JSONArray shop = a.store.role().optJSONArray("shop");
        for (int i = 0; shop != null && i < shop.length(); i++) {
            JSONObject product = shop.optJSONObject(i);
            if (product != null && id.equals(product.optString("id"))) return product;
        }
        return null;
    }

    private void editBalance(String key, String label) {
        JSONObject role = a.store.role();
        JSONObject wallet = role != null ? role.optJSONObject("wallet") : null;
        if (wallet == null) return;
        Dialogs.prompt(a, a.store, "¥", "修改" + label, "金额", "0.00",
                Ui.fmtMoney(wallet.optDouble(key, 0)), value -> {
                    try {
                        double amount = Double.parseDouble(value.trim());
                        if (amount < 0) throw new NumberFormatException();
                        wallet.put(key, Math.round(amount * 100) / 100.0);
                        a.store.save();
                        refresh();
                    } catch (NumberFormatException e) {
                        Dialogs.notice(a, a.store, "!", "金额无效", "请输入不小于 0 的数字");
                    } catch (JSONException ignored) {
                    }
                });
    }

    private void sendProduct(String id) {
        JSONObject product = findProduct(id);
        if (product == null) return;
        Dialogs.prompt(a, a.store, "🎁", "送给他：" + product.optString("name"),
                "数量", "1", "1", value -> {
                    int qty;
                    try {
                        qty = Integer.parseInt(value.trim());
                    } catch (NumberFormatException e) {
                        qty = 0;
                    }
                    if (qty <= 0) {
                        Dialogs.notice(a, a.store, "!", "数量无效", "请输入大于 0 的整数");
                        return;
                    }
                    double total = product.optDouble("price", 0) * qty;
                    JSONObject wallet = a.store.role().optJSONObject("wallet");
                    if (wallet != null && total > wallet.optDouble("mine", 0)) {
                        a.toast("余额不足");
                        return;
                    }
                    commitGift(product, qty);
                });
    }

    private void commitGift(JSONObject product, int qty) {
        try {
            JSONObject role = a.store.role();
            JSONObject wallet = role.getJSONObject("wallet");
            double price = Math.round(product.optDouble("price", 0) * qty * 100) / 100.0;
            wallet.put("mine", wallet.optDouble("mine", 0) - price);
            JSONObject msg = new JSONObject().put("type", "gift")
                    .put("gift", product.optString("name") + (qty > 1 ? " ×" + qty : "")).put("price", price)
                    .put("amount", price).put("read", false)
                    .put("txVersion", 2).put("txStatus", "");
            a.logic.decorateTx(msg, "gift");
            JSONObject sent = a.logic.addMsg("me", msg);
            a.store.save();
            refresh();
            a.toast("礼物已送出");
            a.logic.scheduleHisDecision(sent);
        } catch (JSONException ignored) {
        }
    }

    private void deleteProduct(String id) {
        JSONObject product = findProduct(id);
        if (product == null) return;
        Dialogs.confirm(a, a.store, "⌫", "删除“" + product.optString("name") + "”？", null,
                "删除", true, () -> {
                    try {
                        JSONObject role = a.store.role();
                        JSONArray shop = role.getJSONArray("shop");
                        JSONArray next = new JSONArray();
                        for (int i = 0; i < shop.length(); i++) {
                            JSONObject item = shop.getJSONObject(i);
                            if (!id.equals(item.optString("id"))) next.put(item);
                        }
                        role.put("shop", next);
                        a.store.save();
                        refresh();
                    } catch (JSONException ignored) {
                    }
                });
    }

    /* ---------- 购物车（对应浏览器 mp4Ball / openCartSheet / checkoutCart） ---------- */

    private List<String> cartIds() {
        JSONArray cart = a.store.cart();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < cart.length(); i++) {
            String id = cart.optString(i, "");
            if (!id.isEmpty()) ids.add(id);
        }
        return ids;
    }

    private int countInCart(String id) {
        int n = 0;
        for (String cartId : cartIds()) if (cartId.equals(id)) n++;
        return n;
    }

    /** 购物车按商品合并，返回 [商品, 数量] */
    private List<Object[]> cartLines() {
        List<Object[]> lines = new ArrayList<>();
        for (String id : cartIds()) {
            boolean merged = false;
            for (Object[] line : lines) {
                if (((JSONObject) line[0]).optString("id").equals(id)) {
                    line[1] = (Integer) line[1] + 1;
                    merged = true;
                    break;
                }
            }
            if (merged) continue;
            JSONObject product = findProduct(id);
            if (product != null) lines.add(new Object[]{product, 1});
        }
        return lines;
    }

    private List<JSONObject> cartItems() {
        List<JSONObject> items = new ArrayList<>();
        for (String id : cartIds()) {
            JSONObject product = findProduct(id);
            if (product != null) items.add(product);
        }
        return items;
    }

    private void saveCart(List<String> ids) {
        JSONArray cart = a.store.cart();
        while (cart.length() > 0) cart.remove(cart.length() - 1);
        for (String id : ids) cart.put(id);
        a.store.save();
    }

    private void addToCart(String id) {
        List<String> ids = cartIds();
        ids.add(id);
        saveCart(ids);
        a.toast("已加入购物车 ×" + countInCart(id));
        refresh();
    }

    private android.app.Dialog cartDialog;

    private void openCartSheet() {
        if (cartDialog != null) cartDialog.dismiss();
        List<JSONObject> items = cartItems();
        LinearLayout body = Ui.column(a);
        double total = 0;
        if (items.isEmpty()) {
            body.addView(hint("购物车是空的"));
        }
        for (Object[] line : cartLines()) {
            JSONObject product = (JSONObject) line[0];
            int qty = (Integer) line[1];
            final String id = product.optString("id");
            total += product.optDouble("price", 0) * qty;
            LinearLayout row = Ui.row(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
            String productIcon = product.optString("icon", "");
            row.addView(SvgIcon.view(a, SvgIcon.isSvg(productIcon) ? productIcon : Icons.GIFT, Ui.plum(a, a.store), 18));
            LinearLayout copy = Ui.column(a);
            copy.setPadding(Ui.dp(a, 8), 0, Ui.dp(a, 8), 0);
            copy.addView(Ui.boldText(a, product.optString("name"), 14, Ui.ink(a, a.store)));
            copy.addView(Ui.text(a, "¥" + Ui.fmtMoney(product.optDouble("price", 0)) + " × " + qty, 12, Ui.mutedInk(a, a.store)));
            row.addView(copy, Ui.weighted());
            TextView minus = qtyButton("－");
            minus.setOnClickListener(v -> {
                List<String> ids = cartIds();
                ids.remove(id);
                saveCart(ids);
                refresh();
                openCartSheet();
            });
            row.addView(minus);
            TextView count = Ui.boldText(a, String.valueOf(qty), 14, Ui.ink(a, a.store));
            count.setPadding(Ui.dp(a, 8), 0, Ui.dp(a, 8), 0);
            row.addView(count);
            TextView plus = qtyButton("＋");
            plus.setOnClickListener(v -> {
                List<String> ids = cartIds();
                ids.add(id);
                saveCart(ids);
                refresh();
                openCartSheet();
            });
            row.addView(plus);
            body.addView(row);
        }
        if (!items.isEmpty()) {
            LinearLayout totalRow = Ui.row(a);
            totalRow.setPadding(0, Ui.dp(a, 10), 0, 0);
            totalRow.addView(Ui.text(a, "合计", 13, Ui.mutedInk(a, a.store)), Ui.weighted());
            totalRow.addView(Ui.boldText(a, "¥" + Ui.fmtMoney(total), 16, Ui.plum(a, a.store)));
            body.addView(totalRow);
        }
        cartDialog = Dialogs.custom(a, a.store, "🛒", "购物车", null, body, "关闭",
                items.isEmpty() ? null : "打包送出", this::checkoutCart);
    }

    private TextView qtyButton(String label) {
        TextView button = Ui.boldText(a, label, 14, Ui.plum(a, a.store));
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(Ui.dp(a, 30));
        button.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 4));
        button.setBackground(Ui.roundedStroke(0x00000000, Ui.dp(a, 8), Ui.line(a, a.store), Ui.dp(a, 1)));
        return button;
    }

    private void checkoutCart() {
        List<JSONObject> items = cartItems();
        if (items.isEmpty()) return;
        try {
            JSONObject role = a.store.role();
            JSONObject wallet = role.getJSONObject("wallet");
            double total = 0;
            JSONArray snapshot = new JSONArray();
            for (Object[] line : cartLines()) {
                JSONObject product = (JSONObject) line[0];
                int qty = (Integer) line[1];
                double price = product.optDouble("price", 0) * qty;
                total += price;
                snapshot.put(new JSONObject().put("name", product.optString("name") + (qty > 1 ? " ×" + qty : "")).put("price", price));
            }
            total = Math.round(total * 100) / 100.0;
            if (total > wallet.optDouble("mine", 0)) {
                Dialogs.notice(a, a.store, "¥", "余额不足",
                        "还需要 ¥" + Ui.fmtMoney(total - wallet.optDouble("mine", 0)) + "，点上方「我的余额」卡片补充吧。");
                return;
            }
            wallet.put("mine", wallet.optDouble("mine", 0) - total);
            String giftName = items.size() == 1 ? items.get(0).optString("name") : "礼物×" + items.size();
            JSONObject msg = new JSONObject().put("type", "gift")
                    .put("gift", giftName).put("items", snapshot).put("price", total)
                    .put("amount", total).put("read", false)
                    .put("txVersion", 2).put("txStatus", "");
            a.logic.decorateTx(msg, "gift");
            JSONObject sent = a.logic.addMsg("me", msg);
            saveCart(new ArrayList<>());
            refresh();
            a.toast("礼物已送出");
            a.logic.scheduleHisDecision(sent);
        } catch (JSONException ignored) {
        }
    }

    /* ---------- 账单 ---------- */

    /** 账单：直接从聊天里的红包/转账/礼物消息汇总，谁给谁、什么、多少、已退还/已接收/未接收 */
    private void showBill() {
        JSONObject role = a.store.role();
        if (role == null) return;
        String me = role.optString("myName", "我");
        if (me.isEmpty()) me = "我";
        String him = a.store.displayName();
        JSONArray chat = role.optJSONArray("chat");
        LinearLayout body = Ui.column(a);
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA);
        int shown = 0;
        for (int i = chat != null ? chat.length() - 1 : -1; i >= 0 && shown < 60; i--) {
            JSONObject msg = chat.optJSONObject(i);
            if (msg == null || msg.optBoolean("recall", false)) continue;
            String type = msg.optString("type", "");
            if (!"red".equals(type) && !"zhuan".equals(type) && !"gift".equals(type)) continue;
            boolean mine = "me".equals(msg.optString("side"));
            String from = mine ? me : him;
            String to = mine ? him : me;
            String kind = ChatLogic.txKindLabel(msg) + (msg.has("gift") ? "：" + msg.optString("gift") : "");
            String status = ChatPage.txStatusLabel(msg);
            int statusColor = "已接收".equals(status) ? 0xFF3F9B6A
                    : "已退还".equals(status) ? 0xFFD9822B : Ui.faintInk(a, a.store);
            LinearLayout row = Ui.row(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(a, 7), 0, Ui.dp(a, 7));
            LinearLayout copy = Ui.column(a);
            copy.addView(Ui.boldText(a, from + " → " + to + " · " + kind, 13, Ui.ink(a, a.store)));
            copy.addView(Ui.text(a, fmt.format(new java.util.Date(msg.optLong("t"))), 11, Ui.faintInk(a, a.store)));
            row.addView(copy, Ui.weighted());
            LinearLayout right = Ui.column(a);
            right.setGravity(Gravity.END);
            right.addView(Ui.boldText(a, (mine ? "-" : "+") + "¥" + Ui.fmtMoney(ChatLogic.txAmount(msg)), 14,
                    mine ? Ui.ink(a, a.store) : 0xFF3F9B6A));
            right.addView(Ui.text(a, status, 11, statusColor));
            row.addView(right);
            body.addView(row);
            shown++;
        }
        if (shown == 0) body.addView(hint("暂无账单记录"));
        Dialogs.custom(a, a.store, "¥", "钱包账单", null, body, "关闭", null, null);
    }
}

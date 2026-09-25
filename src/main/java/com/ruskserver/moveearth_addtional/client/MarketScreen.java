package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;
import com.ruskserver.moveearth_addtional.client.ui.MoveEarthTextField;
import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.network.C2S_MarketActionPacket;
import com.ruskserver.moveearth_addtional.network.S2C_MarketSnapshotPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

/** Market browser, hand-off and order entry. All transactions are revalidated server-side. */
public final class MarketScreen extends Screen implements SuppressesChatOverlay {
    private static final int W = 610, H = 348;
    private static final int VISIBLE_ROWS = 6;
    private S2C_MarketSnapshotPacket snapshot;
    private MoveEarthTextField search, tradeQuantity, quantity, price;
    private int tab, selected, scroll;

    public MarketScreen(S2C_MarketSnapshotPacket snapshot) {
        super(Component.literal("MARKET"));
        this.snapshot = snapshot;
    }

    public void update(S2C_MarketSnapshotPacket next) {
        snapshot = next;
        selected = Mth.clamp(selected, 0, Math.max(0, rowCount() - 1));
        scroll = Mth.clamp(scroll, 0, Math.max(0, rowCount() - VISIBLE_ROWS));
        syncInputs();
    }

    @Override protected void init() {
        int x = left(), y = top();
        search = new MoveEarthTextField(font, x + 15, y + 98, 251, 24, Component.literal("商品検索"));
        search.setHint(Component.literal("商品名で検索"));
        search.setMaxLength(64);
        addRenderableWidget(search);
        tradeQuantity = new MoveEarthTextField(font, x + 289, y + 210, 67, 22, Component.literal("取引数"));
        tradeQuantity.setValue("1"); tradeQuantity.setMaxLength(4); addRenderableWidget(tradeQuantity);
        quantity = new MoveEarthTextField(font, x + 289, y + 262, 86, 25, Component.literal("出品数"));
        quantity.setValue("1"); quantity.setMaxLength(4); addRenderableWidget(quantity);
        price = new MoveEarthTextField(font, x + 382, y + 262, 106, 25, Component.literal("単価"));
        price.setValue("1"); price.setMaxLength(7); addRenderableWidget(price);
        syncInputs();
    }

    private void syncInputs() {
        if (search == null || tradeQuantity == null || quantity == null || price == null) return;
        boolean orders = tab != 2;
        if (!orders) {
            search.setFocused(false);
            tradeQuantity.setFocused(false);
            quantity.setFocused(false);
            price.setFocused(false);
        }
        search.setVisible(orders);
        boolean trade = orders && selected < orders().size() && !orders().get(selected).own();
        tradeQuantity.setVisible(trade);
        if (!trade) tradeQuantity.setFocused(false);
        quantity.setVisible(orders);
        price.setVisible(orders);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float partial) { }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        syncInputs();
        drawBackground(g, width, height);
        int x = left(), y = top();
        drawPanel(g, new MoveEarthUi.Rect(x, y, W, H));
        g.drawString(font, "MARKET", x + 18, y + 13, TEXT, false);
        g.drawString(font, "商品を探す → 注文する → 現地で受け取る", x + 18, y + 27, MUTED, false);
        String balance = "残高 " + snapshot.balance() + " TC";
        g.drawString(font, balance, x + W - 48 - font.width(balance), y + 15, GOLD, false);
        tab(g, x + 18, y + 43, 90, "買う / 売り一覧", tab == 0, mx, my);
        tab(g, x + 114, y + 43, 90, "売る / 買い一覧", tab == 1, mx, my);
        tab(g, x + 210, y + 43, 90, "受け取る", tab == 2, mx, my);
        g.drawString(font, tab == 0 ? "安い順に表示" : tab == 1 ? "高い順に表示" : "受取場所へ向かう",
                x + 318, y + 50, MUTED, false);
        MoveEarthUi.Rect close = new MoveEarthUi.Rect(x + W - 37, y + 10, 20, 20);
        drawClose(g, font, close, close.contains(mx, my));
        var station = station();
        g.drawString(font, "取引拠点: " + (station == null ? "未選択" : fit(station.nation(), 115)),
                x + 18, y + 82, TEXT, false);
        button(g, x + 217, y + 76, 24, 19, "‹", ACCENT, mx, my, !snapshot.stations().isEmpty());
        button(g, x + 245, y + 76, 24, 19, "›", ACCENT, mx, my, !snapshot.stations().isEmpty());
        if (station != null) {
            g.drawString(font, fit(station.location(), 143), x + 287, y + 82, MUTED, false);
            boolean selectedDestination = isSelectedDestination(station);
            button(g, x + 438, y + 76, 92, 19,
                    selectedDestination ? "案内中" : "目的地に設定", SUCCESS, mx, my, !selectedDestination);
        }
        button(g, x + 534, y + 76, 58, 19, "案内解除", DANGER, mx, my,
                EconomyWaypointHud.waypoint().active());
        if (tab == 2) g.drawString(font, "未回収の品  " + claims().size() + "件", x + 18, y + 106, MUTED, false);
        drawCard(g, new MoveEarthUi.Rect(x + 15, y + 126, 251, H - 142), ACCENT, false, false);
        drawCard(g, new MoveEarthUi.Rect(x + 278, y + 126, W - 293, H - 142), ACCENT, false, false);
        drawRows(g, mx, my);
        drawDetail(g, mx, my);
        if (!snapshot.result().isBlank()) g.drawString(font, fit(snapshot.result(), 570), x + 18, y + H - 13,
                snapshot.result().contains("しました") || snapshot.result().contains("更新") ? SUCCESS : DANGER, false);
        super.render(g, mx, my, partial);
    }

    private void drawRows(GuiGraphics g, int mx, int my) {
        int x = left() + 20, y = top() + 148;
        g.drawString(font, tab == 2 ? "未回収の品" : "公開中の注文", x + 5, top() + 133, TEXT, false);
        g.drawString(font, rowCount() == 0 ? "0件" : (scroll + 1) + "–"
                + Math.min(rowCount(), scroll + VISIBLE_ROWS) + " / " + rowCount(),
                x + 165, top() + 133, MUTED, false);
        if (rowCount() == 0) g.drawString(font,
                tab == 2 ? "受取待ちの品はありません" : "該当する注文はありません", x + 8, y + 8, MUTED, false);
        for (int row = 0; row < VISIBLE_ROWS && row + scroll < rowCount(); row++) {
            int index = row + scroll, yy = y + row * 28;
            drawCard(g, new MoveEarthUi.Rect(x, yy, 240, 25), ACCENT, selected == index,
                    inside(mx, my, x, yy, 240, 25));
            if (tab == 2) {
                var claim = claims().get(index);
                g.drawString(font, fit(name(claim), 205), x + 9, yy + 4, TEXT, false);
                g.drawString(font, claim.quantity() + "個  " + fit(claim.nation(), 155), x + 9, yy + 15, MUTED, false);
            } else {
                var order = orders().get(index);
                g.drawString(font, fit(name(order), 205), x + 9, yy + 4, TEXT, false);
                g.drawString(font, order.remaining() + "個 × " + order.unitPrice() + " TC  "
                        + fit(order.nation(), 75), x + 9, yy + 15, GOLD, false);
            }
        }
        drawScrollbar(g, new MoveEarthUi.Rect(x + 243, y, 3, VISIBLE_ROWS * 28),
                VISIBLE_ROWS, rowCount(), scroll);
    }

    private void drawDetail(GuiGraphics g, int mx, int my) {
        int x = left() + 289, y = top();
        g.drawString(font, tab == 2 ? "受取品の詳細" : "選択中の注文", x, y + 136, TEXT, false);
        if (tab == 2) {
            if (selected < claims().size()) {
                var claim = claims().get(selected);
                g.drawString(font, fit(name(claim), 285), x, y + 152, TEXT, false);
                g.drawString(font, claim.quantity() + "個 / " + fit(claim.nation(), 140), x, y + 168, GOLD, false);
                g.drawString(font, claim.local() ? "現在地で受け取れます" : "受取先の拠点へ移動してください", x, y + 185,
                        claim.local() ? SUCCESS : MUTED, false);
                button(g, x, y + 210, 130, 24, "受け取る", SUCCESS, mx, my, claim.local());
                button(g, x + 140, y + 210, 130, 24, "受取先へ案内", ACCENT, mx, my, true);
                g.drawString(font, "受取品は現地の市場ステーションで渡されます。",
                        x, y + 252, MUTED, false);
            } else {
                g.drawString(font, "左の受取品を選択してください。", x, y + 157, MUTED, false);
            }
            return;
        }
        if (selected < orders().size()) {
            var order = orders().get(selected);
            g.drawString(font, fit(name(order), 285), x, y + 152, TEXT, false);
            g.drawString(font, "単価 " + order.unitPrice() + " TC  ·  残り " + order.remaining() + "個",
                    x, y + 167, GOLD, false);
            g.drawString(font, fit("受渡先: " + order.nation(), 260), x, y + 181, MUTED, false);
            g.drawString(font, order.local() ? "現地: 受渡できます"
                    : tab == 0 ? "遠隔購入できます / 受取は現地" : "納品には現地へ移動してください",
                    x, y + 192, order.local() ? SUCCESS : MUTED, false);
            if (!order.own()) g.drawString(font, "取引数", x, y + 201, MUTED, false);
            button(g, x + (order.own() ? 0 : 73), y + 210, order.own() ? 136 : 121, 22,
                    order.own() ? "自分の注文を取消" : tab == 0 ? "この注文から購入" : "メインハンドの品を納品",
                    order.own() ? DANGER : SUCCESS, mx, my,
                    order.own() || (parse(tradeQuantity.getValue()) > 0 && (tab == 0 || order.local())));
            button(g, x + 202, y + 210, 90, 22, "受渡先へ案内", ACCENT, mx, my, true);
        } else {
            g.drawString(font, "左の注文を選択してください。", x, y + 157, MUTED, false);
        }
        drawCard(g, new MoveEarthUi.Rect(x - 6, y + 237, 300, 89), GOLD, false, false);
        g.drawString(font, tab == 0 ? "新規売り注文  ·  メインハンドの品を預ける"
                : "新規買い注文  ·  メインハンドの品を指定", x, y + 243, GOLD, false);
        g.drawString(font, "数量", x, y + 253, MUTED, false);
        g.drawString(font, "単価 / TC", x + 93, y + 253, MUTED, false);
        boolean canCreate = canCreateOrder();
        button(g, x, y + 297, 270, 24,
                tab == 0 ? "この拠点に売り注文を出す" : "この拠点に買い注文を出す",
                ACCENT, mx, my, canCreate);
        if (!canCreate) g.drawString(font, fit(createOrderHint(), 284), x, y + 288, MUTED, false);
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        int x = left(), y = top();
        if (inside(mx, my, x + W - 37, y + 10, 20, 20)) { onClose(); return true; }
        for (int i = 0; i < 3; i++) if (inside(mx, my, x + 18 + i * 96, y + 43, 90, 22)) {
            tab = i; selected = 0; scroll = 0; syncInputs(); return true;
        }
        if (inside(mx, my, x + 217, y + 76, 52, 19) && !snapshot.stations().isEmpty()) {
            int index = 0;
            for (int i = 0; i < snapshot.stations().size(); i++)
                if (snapshot.stations().get(i).id().equals(snapshot.selectedStation())) index = i;
            int direction = mx < x + 241 ? -1 : 1;
            int next = Math.floorMod(index + direction, snapshot.stations().size());
            send("SELECT", snapshot.stations().get(next).id(), 0, 0); return true;
        }
        if (station() != null && !isSelectedDestination(station())
                && inside(mx, my, x + 438, y + 76, 92, 19)) {
            send("WAYPOINT", station().id(), 0, 0); return true;
        }
        if (EconomyWaypointHud.waypoint().active() && inside(mx, my, x + 534, y + 76, 58, 19)) {
            send("CLEAR_WAYPOINT", snapshot.selectedStation(), 0, 0); return true;
        }
        for (int row = 0; row < VISIBLE_ROWS; row++) if (row + scroll < rowCount()
                && inside(mx, my, x + 20, y + 148 + row * 28, 240, 25)) {
            selected = row + scroll; syncInputs(); return true;
        }
        int count = parse(tradeQuantity.getValue());
        if (inside(mx, my, x + (tab == 2 ? 429 : 491), y + 210, tab == 2 ? 130 : 90, 24)) {
            if (tab == 2 && selected < claims().size()) {
                send("WAYPOINT", claims().get(selected).stationId(), 0, 0); return true;
            }
            if (tab != 2 && selected < orders().size()) {
                send("WAYPOINT", orders().get(selected).stationId(), 0, 0); return true;
            }
        }
        if (tab == 2 && selected < claims().size() && claims().get(selected).local()
                && inside(mx, my, x + 289, y + 210, 130, 24)) {
            send("CLAIM", claims().get(selected).id(), 0, 0); return true;
        }
        if (tab != 2 && selected < orders().size()
                && inside(mx, my, x + (orders().get(selected).own() ? 289 : 362), y + 210,
                orders().get(selected).own() ? 136 : 121, 22)) {
            var order = orders().get(selected);
            if (!order.own() && (count < 1 || tab == 1 && !order.local())) return true;
            send(order.own() ? "CANCEL" : tab == 0 ? "PURCHASE" : "DELIVER", order.id(), count, 0);
            return true;
        }
        if (tab != 2 && canCreateOrder() && inside(mx, my, x + 289, y + 297, 270, 24)) {
            send(tab == 0 ? "SELL" : "BUY_ORDER", snapshot.selectedStation(),
                    parse(quantity.getValue()), parse(price.getValue()));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (inside(mx, my, left() + 20, top() + 148, 240, VISIBLE_ROWS * 28)) {
            scroll = Mth.clamp(scroll - (int) Math.signum(vertical), 0, Math.max(0, rowCount() - VISIBLE_ROWS));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    private List<S2C_MarketSnapshotPacket.OrderEntry> orders() {
        String query = search == null ? "" : search.getValue().toLowerCase(java.util.Locale.ROOT);
        return snapshot.orders().stream().filter(o -> o.side().equals(tab == 0 ? "SELL" : "BUY"))
                .filter(o -> name(o).toLowerCase(java.util.Locale.ROOT).contains(query))
                .sorted(tab == 0
                        ? java.util.Comparator.comparingLong(S2C_MarketSnapshotPacket.OrderEntry::unitPrice)
                        : java.util.Comparator.comparingLong(S2C_MarketSnapshotPacket.OrderEntry::unitPrice).reversed())
                .toList();
    }
    private List<S2C_MarketSnapshotPacket.ClaimEntry> claims() { return snapshot.claims(); }
    private static String name(S2C_MarketSnapshotPacket.OrderEntry order) {
        return MarketItemNames.display(order.itemName(), order.gunId());
    }
    private static String name(S2C_MarketSnapshotPacket.ClaimEntry claim) {
        return MarketItemNames.display(claim.itemName(), claim.gunId());
    }
    private int rowCount() { return tab == 2 ? claims().size() : orders().size(); }
    private S2C_MarketSnapshotPacket.StationEntry station() {
        return snapshot.stations().stream().filter(s -> s.id().equals(snapshot.selectedStation()))
                .findFirst().orElse(null);
    }
    private boolean isSelectedDestination(S2C_MarketSnapshotPacket.StationEntry station) {
        var waypoint = EconomyWaypointHud.waypoint();
        return waypoint.active() && waypoint.market()
                && waypoint.dimension().equals(station.dimension())
                && waypoint.pos().equals(station.pos());
    }
    private boolean canCreateOrder() {
        return createOrderHint().isEmpty();
    }
    private String createOrderHint() {
        var selectedStation = station();
        if (selectedStation == null) return "まず自国の取引拠点を選択してください";
        if (!selectedStation.own()) return "新規注文は自国の拠点でのみ出せます";
        if (tab == 0 && !selectedStation.local()) return "売り注文は拠点の近くで出してください";
        if (minecraft == null || minecraft.player == null || minecraft.player.getMainHandItem().isEmpty())
            return "注文する品をメインハンドに持ってください";
        if (parse(quantity.getValue()) < 1 || parse(price.getValue()) < 1)
            return "数量・単価は1以上を入力してください";
        return "";
    }
    private void send(String action, UUID target, int count, long unitPrice) {
        PacketDistributor.sendToServer(new C2S_MarketActionPacket(action, target, count, unitPrice));
    }
    private int parse(String text) {
        try { return Integer.parseInt(text); } catch (NumberFormatException ignored) { return 0; }
    }
    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
    private String fit(String value, int pixels) { return font.plainSubstrByWidth(value, pixels); }
    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
    private void tab(GuiGraphics g, int x, int y, int w, String label, boolean selected, int mx, int my) {
        MoveEarthUi.Rect bounds = new MoveEarthUi.Rect(x, y, w, 22);
        drawTab(g, font, bounds, Component.literal(label), selected, bounds.contains(mx, my));
    }
    private void button(GuiGraphics g, int x, int y, int w, int h, String label,
                        int accent, int mx, int my, boolean enabled) {
        MoveEarthUi.Rect bounds = new MoveEarthUi.Rect(x, y, w, h);
        drawButton(g, font, bounds, Component.literal(label), accent, bounds.contains(mx, my), enabled);
    }
}

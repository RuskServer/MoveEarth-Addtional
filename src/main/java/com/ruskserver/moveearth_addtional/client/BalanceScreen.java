package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.economy.BalancePaymentPolicy;
import com.ruskserver.moveearth_addtional.client.ui.MoveEarthTextField;
import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;
import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.network.C2S_BalanceActionPacket;
import com.ruskserver.moveearth_addtional.network.S2C_BalanceSnapshotPacket;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

/** Player wallet, bounded transaction history and confirmed online payments. */
public final class BalanceScreen extends Screen implements SuppressesChatOverlay {
    private static final int W = 620, H = 348;
    private static final int HISTORY_ROWS = 8, PLAYER_ROWS = 5;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM/dd HH:mm");
    private static final UUID NONE = new UUID(0L, 0L);
    private S2C_BalanceSnapshotPacket snapshot;
    private MoveEarthTextField amount;
    private UUID selectedRecipient;
    private UUID pendingRecipient;
    private long pendingAmount, pendingUntil;
    private int historyScroll, playerScroll;

    public BalanceScreen(S2C_BalanceSnapshotPacket snapshot) {
        super(Component.literal("BALANCE"));
        this.snapshot = snapshot;
    }

    public void update(S2C_BalanceSnapshotPacket next) {
        snapshot = next;
        historyScroll = Mth.clamp(historyScroll, 0, Math.max(0, next.history().size() - HISTORY_ROWS));
        playerScroll = Mth.clamp(playerScroll, 0, Math.max(0, next.recipients().size() - PLAYER_ROWS));
        if (selectedRecipient != null && next.recipients().stream().noneMatch(p -> p.id().equals(selectedRecipient)))
            selectedRecipient = null;
        if (next.success()) {
            clearConfirmation();
            if (amount != null) amount.setValue("1");
        }
    }

    @Override protected void init() {
        amount = new MoveEarthTextField(font, left() + 391, top() + 270, 198, 24,
                Component.literal("送金額"));
        amount.setValue("1");
        amount.setMaxLength(10);
        amount.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        amount.setResponder(value -> clearConfirmation());
        addRenderableWidget(amount);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float partial) { }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        if (pendingUntil < Util.getMillis()) clearConfirmation();
        int x = left(), y = top();
        drawBackground(g, width, height);
        drawPanel(g, new MoveEarthUi.Rect(x, y, W, H));
        g.drawString(font, "BALANCE", x + 18, y + 13, TEXT, false);
        g.drawString(font, "残高・入出金・プレイヤーへの送金", x + 18, y + 28, MUTED, false);
        button(g, x + W - 128, y + 12, 78, 20, "更新", ACCENT, mx, my, true);
        MoveEarthUi.Rect close = new MoveEarthUi.Rect(x + W - 37, y + 11, 20, 20);
        drawClose(g, font, close, close.contains(mx, my));

        drawCard(g, new MoveEarthUi.Rect(x + 18, y + 43, W - 36, 38), GOLD, false, false);
        g.drawString(font, "所持金", x + 31, y + 49, MUTED, false);
        g.drawString(font, money(snapshot.balance()), x + 31, y + 63, GOLD, false);

        drawCard(g, new MoveEarthUi.Rect(x + 18, y + 88, 352, 244), ACCENT, false, false);
        drawCard(g, new MoveEarthUi.Rect(x + 380, y + 88, 222, 244), SUCCESS, false, false);
        renderHistory(g, x, y);
        renderPay(g, x, y, mx, my);
        if (!snapshot.result().isBlank()) g.drawString(font,
                font.plainSubstrByWidth(snapshot.result(), W - 36), x + 18, y + H - 13,
                snapshot.success() ? SUCCESS : DANGER, false);
        super.render(g, mx, my, partial);
    }

    private void renderHistory(GuiGraphics g, int x, int y) {
        g.drawString(font, "入出金履歴", x + 29, y + 99, TEXT, false);
        g.drawString(font, "新しい順 · " + snapshot.history().size() + "件", x + 230, y + 99, MUTED, false);
        if (snapshot.history().isEmpty())
            g.drawString(font, "まだ取引履歴はありません", x + 31, y + 132, MUTED, false);
        for (int row = 0; row < HISTORY_ROWS && historyScroll + row < snapshot.history().size(); row++) {
            var entry = snapshot.history().get(historyScroll + row);
            int yy = y + 119 + row * 24;
            g.fill(x + 25, yy, x + 360, yy + 22, row % 2 == 0 ? 0x77242E3B : 0x551B222C);
            String label = font.plainSubstrByWidth(reason(entry.reason(), entry.incoming()), 209);
            g.drawString(font, label, x + 32, yy + 2, TEXT, false);
            String value = (entry.incoming() ? "+" : "−") + money(entry.amount());
            g.drawString(font, value, x + 352 - font.width(value), yy + 2,
                    entry.incoming() ? SUCCESS : DANGER, false);
            String detail = TIME.format(Instant.ofEpochMilli(entry.occurredAt()).atZone(ZoneId.systemDefault()))
                    + "  ·  " + entry.counterparty();
            g.drawString(font, font.plainSubstrByWidth(detail, 304), x + 32, yy + 12, MUTED, false);
        }
        drawScrollbar(g, new MoveEarthUi.Rect(x + 363, y + 119, 3, HISTORY_ROWS * 24),
                HISTORY_ROWS, snapshot.history().size(), historyScroll);
    }

    private void renderPay(GuiGraphics g, int x, int y, int mx, int my) {
        g.drawString(font, "プレイヤーに送金", x + 391, y + 99, TEXT, false);
        g.drawString(font, pendingRecipient == null ? "オンラインの相手を選択" : "もう一度押すと送金確定",
                x + 391, y + 113, pendingRecipient == null ? MUTED : GOLD, false);
        if (snapshot.recipients().isEmpty())
            g.drawString(font, "ほかにオンラインの人はいません", x + 391, y + 146, MUTED, false);
        for (int row = 0; row < PLAYER_ROWS && playerScroll + row < snapshot.recipients().size(); row++) {
            var recipient = snapshot.recipients().get(playerScroll + row);
            MoveEarthUi.Rect bounds = new MoveEarthUi.Rect(x + 391, y + 133 + row * 24, 198, 22);
            drawCard(g, bounds, SUCCESS, recipient.id().equals(selectedRecipient), bounds.contains(mx, my));
            g.drawString(font, font.plainSubstrByWidth(recipient.name(), 177), bounds.x() + 9,
                    bounds.y() + 7, TEXT, false);
        }
        drawScrollbar(g, new MoveEarthUi.Rect(x + 592, y + 133, 3, PLAYER_ROWS * 24),
                PLAYER_ROWS, snapshot.recipients().size(), playerScroll);
        g.drawString(font, "送金額 / TC（最大 1,000,000）", x + 391, y + 257, MUTED, false);
        button(g, x + 391, y + 300, 198, 25,
                pendingRecipient == null ? "送金内容を確認" : "確認して送金", SUCCESS, mx, my, canPay());
    }

    private boolean canPay() {
        long value = paymentAmount();
        return selectedRecipient != null && value >= 1 && value <= BalancePaymentPolicy.MAX_PAY
                && value <= snapshot.balance();
    }

    private long paymentAmount() {
        if (amount == null) return 0L;
        try { return Long.parseLong(amount.getValue()); }
        catch (NumberFormatException ignored) { return 0L; }
    }

    private void clearConfirmation() {
        pendingRecipient = null;
        pendingAmount = 0L;
        pendingUntil = 0L;
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        int x = left(), y = top();
        if (inside(mx, my, x + W - 37, y + 11, 20, 20)) { onClose(); return true; }
        if (inside(mx, my, x + W - 128, y + 12, 78, 20)) {
            PacketDistributor.sendToServer(new C2S_BalanceActionPacket("REFRESH", NONE, 0L));
            return true;
        }
        for (int row = 0; row < PLAYER_ROWS && playerScroll + row < snapshot.recipients().size(); row++) {
            if (!inside(mx, my, x + 391, y + 133 + row * 24, 198, 22)) continue;
            selectedRecipient = snapshot.recipients().get(playerScroll + row).id();
            clearConfirmation();
            return true;
        }
        if (inside(mx, my, x + 391, y + 300, 198, 25)) {
            if (!canPay()) return true;
            long value = paymentAmount();
            if (selectedRecipient.equals(pendingRecipient) && value == pendingAmount
                    && Util.getMillis() <= pendingUntil) {
                PacketDistributor.sendToServer(new C2S_BalanceActionPacket("PAY", selectedRecipient, value));
                clearConfirmation();
            } else {
                pendingRecipient = selectedRecipient;
                pendingAmount = value;
                pendingUntil = Util.getMillis() + 5_000L;
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (inside(mx, my, left() + 25, top() + 119, 340, HISTORY_ROWS * 24)) {
            historyScroll = Mth.clamp(historyScroll - (int) Math.signum(vertical), 0,
                    Math.max(0, snapshot.history().size() - HISTORY_ROWS));
            return true;
        }
        if (inside(mx, my, left() + 391, top() + 133, 204, PLAYER_ROWS * 24)) {
            playerScroll = Mth.clamp(playerScroll - (int) Math.signum(vertical), 0,
                    Math.max(0, snapshot.recipients().size() - PLAYER_ROWS));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    private static String reason(String value, boolean incoming) {
        return switch (value) {
            case "player_pay" -> "プレイヤー送金";
            case "jobs_action" -> "Jobs の報酬";
            case "vote_reward" -> "投票報酬";
            case "admin_grant" -> "管理者からの付与";
            case "market_sell_filled" -> incoming ? "市場で販売" : "市場で購入";
            case "market_buy_filled" -> "市場へ商品を納品";
            case "market_buy_escrow" -> "買い注文の預託";
            case "market_buy_refund" -> "買い注文の返金";
            case "treasury_deposit" -> "国家金庫への入金";
            case "treasury_withdrawal" -> "国家金庫からの出金";
            case "detector_upkeep" -> "検知器の維持費";
            case "detector_activation" -> "検知器の起動費";
            default -> value.replace('_', ' ');
        };
    }

    private static String money(long amount) { return String.format(Locale.US, "%,d TC", amount); }
    private void button(GuiGraphics g, int x, int y, int w, int h, String label,
                        int accent, int mx, int my, boolean enabled) {
        MoveEarthUi.Rect bounds = new MoveEarthUi.Rect(x, y, w, h);
        drawButton(g, font, bounds, Component.literal(label), accent, bounds.contains(mx, my), enabled);
    }
    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}

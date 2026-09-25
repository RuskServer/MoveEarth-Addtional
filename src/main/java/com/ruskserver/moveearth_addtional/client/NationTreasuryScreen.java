package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthTextField;
import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;
import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.Rect;
import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.network.C2S_NationTreasuryPacket;
import com.ruskserver.moveearth_addtional.network.C2S_RequestS2HubPacket;
import com.ruskserver.moveearth_addtional.network.S2C_NationTreasuryPacket;
import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import com.ruskserver.moveearth_addtional.s2.territory.UpkeepPenalty;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

public final class NationTreasuryScreen extends Screen implements SuppressesChatOverlay {
    private static final int PANEL_W = 540;
    private static final int PANEL_H = 340;

    private S2C_NationTreasuryPacket data;
    private MoveEarthTextField amountField;
    private int selectedTab = 0; // 0 = Deposit, 1 = Withdraw
    private Component toast;
    private int toastTicks;
    private int toastColor = SUCCESS;

    public NationTreasuryScreen(S2C_NationTreasuryPacket data) {
        super(Component.translatable("screen.moveearth_addtional.treasury.title"));
        update(data);
    }

    public void update(S2C_NationTreasuryPacket packet) {
        this.data = packet;
    }

    @Override
    protected void init() {
        Rect p = panel();
        Rect rc = rightCol(p);

        amountField = new MoveEarthTextField(font, rc.x() + 10, p.y() + 80, rc.width() - 20, 22,
                Component.translatable("screen.moveearth_addtional.treasury.amount_label"));
        amountField.setMaxLength(12);
        amountField.setFilter(s -> s.matches("[0-9]*"));
        amountField.setValue("1000");
        addRenderableWidget(amountField);
        setInitialFocus(amountField);
    }

    @Override
    public void tick() {
        super.tick();
        if (toastTicks > 0) {
            toastTicks--;
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(graphics, width, height);
        Rect panel = panel();
        drawPanel(graphics, panel);

        // Header
        graphics.drawString(font, title, panel.x() + 18, panel.y() + 13, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.treasury.detail"),
                panel.x() + 18, panel.y() + 27, MUTED, false);
        Rect close = close(panel);
        drawClose(graphics, font, close, close.contains(mouseX, mouseY));

        // Layout columns
        Rect lc = leftCol(panel);
        Rect rc = rightCol(panel);

        renderLeftColumn(graphics, lc, mouseX, mouseY);
        renderRightColumn(graphics, rc, panel, mouseX, mouseY);

        // Render widgets (including amountField)
        super.render(graphics, mouseX, mouseY, partialTick);

        // Toast feedback
        if (toastTicks > 0 && toast != null) {
            drawToast(graphics, font, width, height, toast, toastColor);
        }
    }

    private void renderLeftColumn(GuiGraphics graphics, Rect col, int mouseX, int mouseY) {
        // 1. Nation Treasury Card
        Rect treasuryCard = new Rect(col.x(), col.y(), col.width(), 68);
        drawCard(graphics, treasuryCard, GOLD, false, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.treasury.nation_balance"),
                treasuryCard.x() + 12, treasuryCard.y() + 10, MUTED, false);
        String natBalStr = NationTreasuryHelper.formatCurrency(data.nationBalance());
        graphics.drawString(font, natBalStr, treasuryCard.x() + 12, treasuryCard.y() + 24, GOLD, false);

        long coverageHours = NationTreasuryHelper.calculateCoverageHours(
                data.nationBalance(), data.upkeep(), data.upkeepCycleHours());
        long coverageDays = coverageHours / 24L;
        Component coverageComp = data.upkeep() <= 0L
                ? Component.translatable("screen.moveearth_addtional.treasury.upkeep_coverage_free")
                : coverageHours >= 24L && coverageHours % 24L != 0L
                ? Component.translatable("screen.moveearth_addtional.treasury.upkeep_coverage_days_hours",
                        coverageDays, coverageHours % 24L)
                : coverageHours >= 24L
                ? Component.translatable("screen.moveearth_addtional.treasury.upkeep_coverage", coverageDays)
                : coverageHours > 0L
                ? Component.translatable("screen.moveearth_addtional.treasury.upkeep_coverage_hours", coverageHours)
                : Component.translatable("screen.moveearth_addtional.treasury.upkeep_coverage_zero");
        graphics.drawString(font, coverageComp, treasuryCard.x() + 12, treasuryCard.y() + 48,
                coverageHours >= 72L ? SUCCESS : coverageHours > 0 ? GOLD : DANGER, false);

        // 2. Upkeep Status Card
        Rect upkeepCard = new Rect(col.x(), col.y() + 74, col.width(), 138);
        int upkeepAccent = data.penalty() == UpkeepPenalty.DISABLED ? DANGER
                : data.failedPayments() > 0 ? GOLD : SUCCESS;
        drawCard(graphics, upkeepCard, upkeepAccent, false, false);

        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.s2.upkeep"),
                upkeepCard.x() + 12, upkeepCard.y() + 10, MUTED, false);
        String upkeepCostStr = NationTreasuryHelper.formatCurrency(data.upkeep())
                + " / " + data.upkeepCycleHours() + "h";
        graphics.drawString(font, upkeepCostStr, upkeepCard.x() + 12, upkeepCard.y() + 24, TEXT, false);

        Component status = data.penalty() != UpkeepPenalty.CURRENT
                ? Component.translatable("screen.moveearth_addtional.treasury.penalty."
                + data.penalty().name().toLowerCase(Locale.ROOT), overdue(data.overdueSince()))
                : data.failedPayments() > 0
                ? Component.translatable("screen.moveearth_addtional.treasury.overdue", data.failedPayments())
                : Component.translatable("screen.moveearth_addtional.treasury.enabled", remaining(data.nextDueAt()));
        graphics.drawWordWrap(font, status, upkeepCard.x() + 12, upkeepCard.y() + 46, upkeepCard.width() - 24,
                upkeepAccent);

        // "Pay Now" button inside Upkeep Card
        Rect payBtn = payButton(upkeepCard);
        drawButton(graphics, font, payBtn, Component.translatable("screen.moveearth_addtional.treasury.pay_now"),
                GOLD, payBtn.contains(mouseX, mouseY), data.canManage());

        // 3. Personal Balance Card
        Rect personalCard = new Rect(col.x(), col.y() + 218, col.width(), 58);
        drawCard(graphics, personalCard, ACCENT, false, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.treasury.player_balance"),
                personalCard.x() + 12, personalCard.y() + 12, MUTED, false);
        String playerBalStr = NationTreasuryHelper.formatCurrency(data.playerBalance());
        graphics.drawString(font, playerBalStr, personalCard.x() + 12, personalCard.y() + 30, TEXT, false);
    }

    private void renderRightColumn(GuiGraphics graphics, Rect col, Rect panel, int mouseX, int mouseY) {
        // 1. Transfer Section Card
        Rect transferCard = new Rect(col.x(), col.y(), col.width(), 172);
        drawCard(graphics, transferCard, selectedTab == 0 ? SUCCESS : ACCENT, false, false);

        // Tabs: Deposit vs Withdraw
        Rect tabDep = tabDeposit(transferCard);
        Rect tabWdr = tabWithdraw(transferCard);
        drawTab(graphics, font, tabDep, Component.translatable("screen.moveearth_addtional.treasury.tab_deposit"),
                selectedTab == 0, tabDep.contains(mouseX, mouseY));
        drawTab(graphics, font, tabWdr, Component.translatable("screen.moveearth_addtional.treasury.tab_withdraw"),
                selectedTab == 1, tabWdr.contains(mouseX, mouseY));

        // Amount label
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.treasury.amount_label"),
                transferCard.x() + 12, transferCard.y() + 26, MUTED, false);

        // Preview / Validation message
        long parsed = parsedAmount();
        long maxAvailable = selectedTab == 0 ? data.playerBalance() : data.nationBalance();
        if (selectedTab == 1 && !data.canManage()) {
            graphics.drawString(font, Component.translatable("screen.moveearth_addtional.treasury.permission_required"),
                    transferCard.x() + 12, transferCard.y() + 58, DANGER, false);
        } else if (parsed > maxAvailable) {
            graphics.drawString(font, Component.translatable("screen.moveearth_addtional.treasury.insufficient_funds"),
                    transferCard.x() + 12, transferCard.y() + 58, DANGER, false);
        } else {
            long afterTransfer = maxAvailable - parsed;
            graphics.drawString(font, Component.translatable("screen.moveearth_addtional.treasury.expected_balance",
                            NationTreasuryHelper.formatNumber(afterTransfer)),
                    transferCard.x() + 12, transferCard.y() + 58, MUTED, false);
        }

        // Quick preset buttons
        Rect[] qb = quickButtons(transferCard);
        drawButton(graphics, font, qb[0], Component.literal("+1k"), ACCENT, qb[0].contains(mouseX, mouseY), true);
        drawButton(graphics, font, qb[1], Component.literal("+10k"), ACCENT, qb[1].contains(mouseX, mouseY), true);
        drawButton(graphics, font, qb[2], Component.literal("+100k"), ACCENT, qb[2].contains(mouseX, mouseY), true);
        drawButton(graphics, font, qb[3], Component.translatable("screen.moveearth_addtional.treasury.btn_upkeep_cycle"),
                GOLD, qb[3].contains(mouseX, mouseY), data.upkeep() > 0);
        drawButton(graphics, font, qb[4], Component.translatable("screen.moveearth_addtional.treasury.btn_max"),
                GOLD, qb[4].contains(mouseX, mouseY), maxAvailable > 0);
        drawButton(graphics, font, qb[5], Component.translatable("screen.moveearth_addtional.treasury.btn_clear"),
                DANGER, qb[5].contains(mouseX, mouseY), parsed > 0);

        // Main Action Button (Deposit / Withdraw)
        Rect actBtn = actionButton(transferCard);
        boolean canExecute = parsed > 0 && parsed <= maxAvailable && (selectedTab == 0 || data.canManage());
        Component actLabel = selectedTab == 0
                ? Component.translatable("screen.moveearth_addtional.treasury.btn_deposit")
                : Component.translatable("screen.moveearth_addtional.treasury.btn_withdraw");
        int actColor = selectedTab == 0 ? SUCCESS : ACCENT;
        drawButton(graphics, font, actBtn, actLabel, actColor, actBtn.contains(mouseX, mouseY), canExecute);

        // 2. Recent Transactions Card
        Rect historyCard = new Rect(col.x(), col.y() + 178, col.width(), 98);
        drawCard(graphics, historyCard, BORDER, false, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.treasury.recent_transactions"),
                historyCard.x() + 12, historyCard.y() + 8, MUTED, false);

        if (data.recentTransactions().isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.moveearth_addtional.treasury.no_transactions"),
                    historyCard.x() + 12, historyCard.y() + 26, MUTED, false);
        } else {
            int maxRows = Math.min(4, data.recentTransactions().size());
            for (int i = 0; i < maxRows; i++) {
                var tx = NationTreasuryHelper.ParsedTransaction.fromRaw(data.recentTransactions().get(i));
                int rowY = historyCard.y() + 24 + i * 16;
                String sign = tx.incoming() ? "+" : "-";
                int signColor = tx.incoming() ? SUCCESS : DANGER;
                String amountStr = sign + " " + NationTreasuryHelper.formatCurrency(tx.amount());

                graphics.drawString(font, amountStr, historyCard.x() + 12, rowY, signColor, false);
                Component reason = formatReason(tx.reason());
                graphics.drawString(font, reason, historyCard.x() + 105, rowY, TEXT, false);
            }
        }
    }

    private Component formatReason(String reasonKey) {
        if (reasonKey == null || reasonKey.isBlank()) return Component.empty();
        String langKey = "screen.moveearth_addtional.treasury.reason." + reasonKey;
        if (I18n.exists(langKey)) {
            return Component.translatable(langKey);
        }
        return Component.literal(reasonKey);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        Rect p = panel();
        if (close(p).contains(mouseX, mouseY)) {
            onClose();
            return true;
        }

        Rect lc = leftCol(p);
        Rect rc = rightCol(p);
        Rect upkeepCard = new Rect(lc.x(), lc.y() + 74, lc.width(), 138);
        Rect payBtn = payButton(upkeepCard);
        if (payBtn.contains(mouseX, mouseY)) {
            if (data.canManage()) {
                send(C2S_NationTreasuryPacket.Action.PAY_NOW, 0L);
                showToast(Component.translatable("screen.moveearth_addtional.treasury.toast_pay_now"), SUCCESS);
            } else {
                showToast(Component.translatable("screen.moveearth_addtional.treasury.permission_required"), DANGER);
            }
            return true;
        }

        Rect transferCard = new Rect(rc.x(), rc.y(), rc.width(), 172);
        Rect tabDep = tabDeposit(transferCard);
        Rect tabWdr = tabWithdraw(transferCard);
        if (tabDep.contains(mouseX, mouseY)) {
            selectedTab = 0;
            return true;
        }
        if (tabWdr.contains(mouseX, mouseY)) {
            selectedTab = 1;
            return true;
        }

        // Quick buttons
        Rect[] qb = quickButtons(transferCard);
        long maxAvailable = selectedTab == 0 ? data.playerBalance() : data.nationBalance();
        if (qb[0].contains(mouseX, mouseY)) {
            setAmount(NationTreasuryHelper.applyIncrement(parsedAmount(), 1_000L, maxAvailable));
            return true;
        }
        if (qb[1].contains(mouseX, mouseY)) {
            setAmount(NationTreasuryHelper.applyIncrement(parsedAmount(), 10_000L, maxAvailable));
            return true;
        }
        if (qb[2].contains(mouseX, mouseY)) {
            setAmount(NationTreasuryHelper.applyIncrement(parsedAmount(), 100_000L, maxAvailable));
            return true;
        }
        if (qb[3].contains(mouseX, mouseY)) {
            setAmount(Math.clamp(data.upkeep(), 0L, Math.max(0L, maxAvailable)));
            return true;
        }
        if (qb[4].contains(mouseX, mouseY)) {
            setAmount(Math.max(0L, maxAvailable));
            return true;
        }
        if (qb[5].contains(mouseX, mouseY)) {
            setAmount(0L);
            return true;
        }

        // Action button
        Rect actBtn = actionButton(transferCard);
        if (actBtn.contains(mouseX, mouseY)) {
            executeAction();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            if (executeAction()) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean executeAction() {
        long maxAvailable = selectedTab == 0 ? data.playerBalance() : data.nationBalance();
        long amt = parsedAmount();
        if (amt <= 0) return false;
        if (amt > maxAvailable) {
            showToast(Component.translatable("screen.moveearth_addtional.treasury.insufficient_funds"), DANGER);
            return false;
        }

        if (selectedTab == 0) {
            send(C2S_NationTreasuryPacket.Action.DEPOSIT, amt);
            showToast(Component.translatable("screen.moveearth_addtional.treasury.toast_deposit",
                    NationTreasuryHelper.formatNumber(amt)), SUCCESS);
            return true;
        } else {
            if (!data.canManage()) {
                showToast(Component.translatable("screen.moveearth_addtional.treasury.permission_required"), DANGER);
                return false;
            }
            send(C2S_NationTreasuryPacket.Action.WITHDRAW, amt);
            showToast(Component.translatable("screen.moveearth_addtional.treasury.toast_withdraw",
                    NationTreasuryHelper.formatNumber(amt)), ACCENT);
            return true;
        }
    }

    private void setAmount(long value) {
        if (amountField != null) {
            amountField.setValue(String.valueOf(value));
        }
    }

    private long parsedAmount() {
        if (amountField == null) return 0L;
        return NationTreasuryHelper.parseAmount(amountField.getValue());
    }

    private void showToast(Component message, int color) {
        this.toast = message;
        this.toastColor = color;
        this.toastTicks = 60; // 3 seconds at 20 ticks/sec
    }

    private static void send(C2S_NationTreasuryPacket.Action action, long amount) {
        PacketDistributor.sendToServer(new C2S_NationTreasuryPacket(action, amount));
    }

    private static String remaining(long dueAt) {
        long minutes = Math.max(0L, dueAt - System.currentTimeMillis()) / 60_000L;
        return (minutes / 60L) + "h " + (minutes % 60L) + "m";
    }

    private static String overdue(long overdueSince) {
        long minutes = Math.max(0L, System.currentTimeMillis() - overdueSince) / 60_000L;
        return (minutes / 60L) + "h " + (minutes % 60L) + "m";
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(new C2S_RequestS2HubPacket(S2HubTab.OVERVIEW));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Rect panel() {
        int w = Math.min(PANEL_W, width - 20);
        int h = Math.min(PANEL_H, height - 20);
        return new Rect((width - w) / 2, (height - h) / 2, w, h);
    }

    private static Rect close(Rect p) {
        return new Rect(p.right() - 28, p.y() + 8, 20, 20);
    }

    private static Rect leftCol(Rect p) {
        int availW = p.width() - 32;
        int leftW = (availW - 12) * 44 / 100;
        return new Rect(p.x() + 14, p.y() + 48, leftW, 276);
    }

    private static Rect rightCol(Rect p) {
        int availW = p.width() - 32;
        int leftW = (availW - 12) * 44 / 100;
        int rightW = availW - 12 - leftW;
        return new Rect(p.x() + 14 + leftW + 12, p.y() + 48, rightW, 276);
    }

    private static Rect payButton(Rect upkeepCard) {
        return new Rect(upkeepCard.x() + 10, upkeepCard.bottom() - 28, upkeepCard.width() - 20, 20);
    }

    private static Rect tabDeposit(Rect transferCard) {
        int halfW = (transferCard.width() - 24) / 2;
        return new Rect(transferCard.x() + 8, transferCard.y() + 4, halfW, 18);
    }

    private static Rect tabWithdraw(Rect transferCard) {
        int halfW = (transferCard.width() - 24) / 2;
        return new Rect(transferCard.x() + 16 + halfW, transferCard.y() + 4, halfW, 18);
    }

    private static Rect[] quickButtons(Rect transferCard) {
        int w = (transferCard.width() - 24 - 8) / 3;
        int x0 = transferCard.x() + 10;
        int y1 = transferCard.y() + 74;
        int y2 = transferCard.y() + 96;
        return new Rect[]{
                new Rect(x0, y1, w, 18),
                new Rect(x0 + w + 4, y1, w, 18),
                new Rect(x0 + (w + 4) * 2, y1, w, 18),
                new Rect(x0, y2, w, 18),
                new Rect(x0 + w + 4, y2, w, 18),
                new Rect(x0 + (w + 4) * 2, y2, w, 18),
        };
    }

    private static Rect actionButton(Rect transferCard) {
        return new Rect(transferCard.x() + 10, transferCard.bottom() - 30, transferCard.width() - 20, 22);
    }
}

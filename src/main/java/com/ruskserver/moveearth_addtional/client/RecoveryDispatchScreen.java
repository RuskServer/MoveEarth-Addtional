package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.network.c2s.nation.C2S_RequestS2HubPacket;
import com.ruskserver.moveearth_addtional.network.c2s.siege.C2S_RecoveryDispatchActionPacket;
import com.ruskserver.moveearth_addtional.network.c2s.siege.C2S_RequestRecoveryDispatchPacket;
import com.ruskserver.moveearth_addtional.network.s2c.siege.S2C_RecoveryDispatchActionResultPacket;
import com.ruskserver.moveearth_addtional.network.s2c.siege.S2C_RecoveryDispatchSnapshotPacket;
import com.ruskserver.moveearth_addtional.network.s2c.siege.S2C_RecoveryDispatchSnapshotPacket.ContractView;
import com.ruskserver.moveearth_addtional.network.s2c.siege.S2C_RecoveryDispatchSnapshotPacket.CoreOption;
import com.ruskserver.moveearth_addtional.network.s2c.siege.S2C_RecoveryDispatchSnapshotPacket.MemberOption;
import com.ruskserver.moveearth_addtional.network.s2c.siege.S2C_RecoveryDispatchSnapshotPacket.NationOption;
import com.ruskserver.moveearth_addtional.network.s2c.siege.S2C_RecoveryDispatchSnapshotPacket.RecoveryView;
import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import com.ruskserver.moveearth_addtional.s2.recovery.RecoveryObjectivePolicy;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

/**
 * Recovery after a capital falls, dispatch contracts and war history.
 *
 * <p>Laid out like the hub tab screens: it drops to a smaller GUI scale when its panel does not fit,
 * keeps the hub tab strip and returns to the hub when closed. Every clickable area is recorded while it
 * is drawn and clicks are resolved against that record, so a click lands on exactly what is drawn, rows
 * scrolled out of view cannot be clicked, and nothing under an open dialog reacts.
 */
public final class RecoveryDispatchScreen extends Screen implements SuppressesChatOverlay {
    private static final String KEY = "screen.moveearth_addtional.recovery.";
    private static final String DISPATCH = "screen.moveearth_addtional.dispatch.";
    private static final int GAP = 6;
    private static final int CONTRACT_ROW = 70;
    private static final int HISTORY_ROW = 40;
    private static final int RESPONSE_TIMEOUT_TICKS = 200;
    private static final int MAX_REVIEWS_SHOWN = 6;

    private S2C_RecoveryDispatchSnapshotPacket snapshot;
    private Tab tab = Tab.RECOVERY;
    private final EnumMap<Tab, Integer> scroll = new EnumMap<>(Tab.class);
    private final EnumMap<Tab, Integer> contentHeights = new EnumMap<>(Tab.class);
    private final EnumMap<Tab, Integer> viewportHeights = new EnumMap<>(Tab.class);
    private final List<Hit> hits = new ArrayList<>();
    private int refreshTicks;
    private int requestId;
    private int pendingRequestId = -1;
    private int pendingTicks;
    private Component toast;
    private int toastColor = ACCENT;
    private int toastTicks;
    private Modal modal = Modal.NONE;
    private int providerIndex;
    private int coreIndex;
    private int memberIndex;
    private int opponentIndex;
    private boolean defense;
    private long pricePerMinute;
    private long durationMinutes = 30L;
    private long subsidy;
    private long fundAmount = 100L;

    public RecoveryDispatchScreen(S2C_RecoveryDispatchSnapshotPacket snapshot) {
        super(Component.translatable(KEY + "title"));
        this.snapshot = snapshot;
    }

    @Override
    protected void init() {
        HubTabs.fit(this, RecoveryScreenLayout.FIT_WIDTH, RecoveryScreenLayout.FIT_HEIGHT);
    }

    public void update(S2C_RecoveryDispatchSnapshotPacket value) {
        snapshot = value;
        durationMinutes = clampDuration(durationMinutes);
        if (modal == Modal.WAIVE && !protectionActive()) modal = Modal.NONE;
    }

    public void handleResult(S2C_RecoveryDispatchActionResultPacket value) {
        if (value.requestId() == pendingRequestId) {
            pendingRequestId = -1;
            pendingTicks = 0;
        }
        String key = value.messageKey();
        showToast(I18n.exists(key) ? Component.translatable(key) : Component.translatable(KEY + "result.unknown"),
                value.success() ? SUCCESS : DANGER);
    }

    @Override
    public void tick() {
        if (toastTicks > 0) toastTicks--;
        if (pendingRequestId >= 0 && ++pendingTicks >= RESPONSE_TIMEOUT_TICKS) {
            pendingRequestId = -1;
            pendingTicks = 0;
            showToast(Component.translatable("screen.moveearth_addtional.s2.timeout"), DANGER);
            refreshTicks = 100;
        }
        if (++refreshTicks >= 100) {
            refreshTicks = 0;
            PacketDistributor.sendToServer(new C2S_RequestRecoveryDispatchPacket(false));
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        drawBackground(graphics, width, height);
        RecoveryScreenLayout.Layout layout = RecoveryScreenLayout.calculate(width, height);
        boolean dialog = modal != Modal.NONE;
        int mx = dialog ? -1 : mouseX;
        int my = dialog ? -1 : mouseY;
        Rect panel = layout.panel();
        drawPanel(graphics, panel);
        int textWidth = panel.width() - 60;
        graphics.drawString(font, title, panel.x() + 18, panel.y() + 12, TEXT, false);
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(KEY + "subtitle").getString(),
                textWidth), panel.x() + 18, panel.y() + 26, MUTED, false);
        drawClose(graphics, font, layout.close(), layout.close().contains(mx, my));
        hit(layout.close(), null, this::onClose);
        Tab[] tabs = Tab.values();
        for (Tab value : tabs) {
            Rect bounds = RecoveryScreenLayout.tab(layout.tabs(), value.ordinal(), tabs.length);
            drawTab(graphics, font, bounds, Component.translatable(value.key), tab == value, bounds.contains(mx, my));
            hit(bounds, null, () -> {
                tab = value;
            });
        }
        Rect content = layout.content();
        switch (tab) {
            case RECOVERY -> renderRecovery(graphics, content, mx, my);
            case CONTRACTS -> renderContracts(graphics, content, mx, my);
            case HISTORY -> renderHistory(graphics, content, mx, my);
        }
        HubTabs.render(graphics, font, width, HubTabs.Tab.NATION, mx, my);
        if (dialog) {
            hits.clear();
            drawModalBackdrop(graphics, width, height);
            if (modal == Modal.WAIVE) renderWaive(graphics, panel, mouseX, mouseY);
            else renderCreate(graphics, panel, mouseX, mouseY);
        }
        if (toast != null && toastTicks > 0) drawToast(graphics, font, width, height, toast, toastColor);
    }

    // ---------------------------------------------------------------- recovery

    private void renderRecovery(GuiGraphics graphics, Rect content, int mx, int my) {
        int offset = clampedScroll(Tab.RECOVERY, content.height());
        int x = content.x();
        int width = content.width() - 8;
        int y = content.y() - offset;
        graphics.enableScissor(content.x(), content.y(), content.right(), content.bottom());
        RecoveryView recovery = snapshot.recovery();
        if (recovery == null) {
            y = noRecovery(graphics, x, y, width) + GAP;
        } else {
            y = status(graphics, recovery, x, y, width) + GAP;
            y = protection(graphics, recovery, content, x, y, width, mx, my) + GAP;
            y = heading(graphics, KEY + "section.objectives", x, y);
            y = objectives(graphics, recovery, x, y, width) + GAP;
        }
        y = heading(graphics, KEY + "section.fund", x, y);
        y = fund(graphics, recovery, content, x, y, width, mx, my) + GAP;
        if (snapshot.admin() && !snapshot.fundReviews().isEmpty()) y = reviews(graphics, x, y, width) + GAP;
        graphics.disableScissor();
        int total = y + offset - content.y();
        record(Tab.RECOVERY, total, content.height());
        drawScrollbar(graphics, new Rect(content.right() - 4, content.y(), 4, content.height()),
                content.height(), total, offset);
    }

    private int noRecovery(GuiGraphics graphics, int x, int y, int width) {
        Component detail = Component.translatable(KEY + "none.detail");
        int height = 30 + wrappedHeight(font, detail, width - 28);
        Rect card = new Rect(x, y, width, height);
        drawCard(graphics, card, MUTED, false, false);
        graphics.drawString(font, Component.translatable(KEY + "none"), card.x() + 14, card.y() + 10, TEXT, false);
        drawWrapped(graphics, font, detail, card.x() + 14, card.y() + 24, width - 28, MUTED);
        return card.bottom();
    }

    private int status(GuiGraphics graphics, RecoveryView recovery, int x, int y, int width) {
        Rect card = new Rect(x, y, width, 66);
        int accent = stateColor(recovery.state());
        drawCard(graphics, card, accent, true, false);
        Component state = Component.translatable(KEY + "state." + lower(recovery.state()));
        graphics.drawString(font, state, card.x() + 14, card.y() + 10, accent, false);
        boolean live = "ACTIVE".equals(recovery.state()) || "COMPLETED".equals(recovery.state());
        Component remaining = live && recovery.remainingTicks() > 0L
                ? Component.translatable(KEY + "remaining", duration(recovery.remainingTicks()))
                : Component.translatable(KEY + "remaining.ended");
        int remainingX = card.right() - 14 - font.width(remaining);
        if (remainingX > card.x() + 24 + font.width(state)) {
            graphics.drawString(font, remaining, remainingX, card.y() + 10, TEXT, false);
        }
        Component attacker = recovery.attackerName().isBlank()
                ? Component.translatable(KEY + "attacker.individual")
                : Component.translatable(KEY + "attacker", recovery.attackerName());
        String who = attacker.getString();
        if (snapshot.settings().rivalEnabled()) {
            who += "  •  " + Component.translatable(KEY + "rival", recovery.rivalName().isBlank()
                    ? Component.translatable(KEY + "rival.none") : Component.literal(recovery.rivalName())).getString();
        }
        graphics.drawString(font, font.plainSubstrByWidth(who, width - 28), card.x() + 14, card.y() + 25, MUTED, false);
        bar(graphics, new Rect(card.x() + 14, card.y() + 42, width - 28, 13), recovery.supportPercent(),
                Component.translatable(KEY + "support", recovery.supportPercent()));
        return card.bottom();
    }

    private int protection(GuiGraphics graphics, RecoveryView recovery, Rect clip, int x, int y, int width,
                           int mx, int my) {
        boolean rival = snapshot.settings().rivalEnabled() && recovery.attackerNationId() != null;
        int buttonWidth = 168;
        boolean sideButtons = width >= 420;
        int textWidth = width - 28 - (sideButtons ? buttonWidth + 10 : 0);
        Component title = Component.translatable(KEY + (recovery.protectionActive() ? "protection.active"
                : recovery.protectionWaived() ? "protection.waived" : "protection.ended"));
        Component detail = Component.translatable(KEY + (recovery.protectionActive() ? "protection.active.detail"
                : "protection.inactive.detail"));
        Component truce = recovery.settlementTruceTicks() > 0L
                ? Component.translatable(KEY + "settlement_truce", duration(recovery.settlementTruceTicks())) : null;
        int textHeight = 24 + wrappedHeight(font, detail, textWidth)
                + (truce == null ? 0 : 4 + wrappedHeight(font, truce, textWidth));
        int buttons = 1 + (rival ? 1 : 0);
        int buttonsHeight = buttons * 22 + (buttons - 1) * 4;
        int height = sideButtons ? Math.max(textHeight + 8, buttonsHeight + 20)
                : textHeight + 14 + buttonsHeight;
        Rect card = new Rect(x, y, width, height);
        drawCard(graphics, card, recovery.protectionActive() ? GOLD : MUTED, false, false);
        graphics.drawString(font, title, card.x() + 14, card.y() + 10, recovery.protectionActive() ? GOLD : MUTED, false);
        int lineY = card.y() + 24 + drawWrapped(graphics, font, detail, card.x() + 14, card.y() + 24, textWidth, MUTED);
        if (truce != null) drawWrapped(graphics, font, truce, card.x() + 14, lineY + 4, textWidth, SUCCESS);
        int buttonX = sideButtons ? card.right() - 14 - buttonWidth : card.x() + 14;
        int buttonY = sideButtons ? card.y() + 10 : card.y() + textHeight + 6;
        int buttonW = sideButtons ? buttonWidth : Math.min(buttonWidth, width - 28);
        Rect waive = new Rect(buttonX, buttonY, buttonW, 22);
        boolean canWaive = snapshot.canManageSiege() && recovery.protectionActive() && idle();
        drawButton(graphics, font, waive, Component.translatable(recovery.protectionWaived() ? KEY + "waived"
                : KEY + "waive"), DANGER, waive.contains(mx, my), canWaive);
        if (canWaive) hit(waive, clip, () -> modal = Modal.WAIVE);
        if (rival) {
            Rect rivalButton = new Rect(buttonX, buttonY + 26, buttonW, 22);
            boolean clear = !recovery.rivalName().isBlank();
            boolean canRival = snapshot.canManageDiplomacy() && idle();
            drawButton(graphics, font, rivalButton, Component.translatable(clear ? KEY + "rival.clear"
                    : KEY + "rival.set"), GOLD, rivalButton.contains(mx, my), canRival);
            if (canRival) hit(rivalButton, clip, () -> send(clear ? C2S_RecoveryDispatchActionPacket.Action.CLEAR_RIVAL
                            : C2S_RecoveryDispatchActionPacket.Action.SET_RIVAL, recovery.id(),
                    clear ? null : recovery.attackerNationId(), null, recovery.revision(), 0L, 0L, 0, List.of()));
        }
        return card.bottom();
    }

    private int objectives(GuiGraphics graphics, RecoveryView recovery, int x, int y, int width) {
        int columns = RecoveryScreenLayout.columns(width, 3, 180, GAP);
        boolean wallsDone = recovery.wallTarget() <= 0 || recovery.healthyWalls() >= recovery.wallTarget();
        Component reseal = recovery.resealed() ? Component.translatable(KEY + "complete")
                : recovery.resealRemainingTicks() == RecoveryView.RESEAL_NOT_STARTED
                ? Component.translatable(KEY + "objective.reseal.unsealed")
                : Component.translatable(KEY + "objective.reseal.holding", duration(recovery.resealRemainingTicks()));
        Component walls = recovery.wallTarget() <= 0 ? Component.translatable(KEY + "objective.walls.none")
                : Component.translatable(KEY + (recovery.weightedWalls() ? "objective.walls.health"
                : "objective.walls.count"), recovery.healthyWalls(), recovery.wallTarget());
        Component upkeep = Component.translatable(recovery.upkeepPaid() ? KEY + "complete"
                : KEY + "objective.upkeep.pending");
        objective(graphics, RecoveryScreenLayout.cell(x, y, width, 0, columns, 50, GAP), "reseal",
                RecoveryObjectivePolicy.RESEAL_PERCENT, recovery.resealed(), reseal,
                !recovery.resealed() && recovery.resealRemainingTicks() == RecoveryView.RESEAL_NOT_STARTED);
        objective(graphics, RecoveryScreenLayout.cell(x, y, width, 1, columns, 50, GAP), "walls",
                RecoveryObjectivePolicy.WALLS_PERCENT, wallsDone, walls, false);
        objective(graphics, RecoveryScreenLayout.cell(x, y, width, 2, columns, 50, GAP), "upkeep",
                RecoveryObjectivePolicy.UPKEEP_PERCENT, recovery.upkeepPaid(), upkeep, false);
        return y + RecoveryScreenLayout.gridHeight(3, columns, 50, GAP);
    }

    private void objective(GuiGraphics graphics, Rect card, String id, int percent, boolean done,
                           Component status, boolean warning) {
        int accent = done ? SUCCESS : warning ? DANGER : GOLD;
        drawCard(graphics, card, accent, done, false);
        String points = "+" + percent + "%";
        int pointsX = card.right() - 10 - font.width(points);
        graphics.drawString(font, points, pointsX, card.y() + 8, done ? SUCCESS : MUTED, false);
        int textWidth = card.width() - 22;
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(KEY + "objective." + id).getString(),
                pointsX - card.x() - 18), card.x() + 12, card.y() + 8, done ? SUCCESS : TEXT, false);
        graphics.drawString(font, font.plainSubstrByWidth(status.getString(), textWidth),
                card.x() + 12, card.y() + 21, accent, false);
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(KEY + "objective." + id + ".hint")
                .getString(), textWidth), card.x() + 12, card.y() + 34, MUTED, false);
    }

    private int fund(GuiGraphics graphics, RecoveryView recovery, Rect clip, int x, int y, int width,
                     int mx, int my) {
        if (!snapshot.settings().fundEnabled()) {
            Rect card = new Rect(x, y, width, 28);
            drawCard(graphics, card, MUTED, false, false);
            graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(KEY + "fund.disabled").getString(),
                    width - 28), card.x() + 14, card.y() + 10, MUTED, false);
            return card.bottom();
        }
        boolean controls = snapshot.canManageTreasury() || snapshot.admin();
        int height = 26 + (recovery == null ? 0 : 13) + (controls ? 38 : 0);
        Rect card = new Rect(x, y, width, height);
        drawCard(graphics, card, ACCENT, false, false);
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(KEY + "fund",
                        NationTreasuryHelper.formatCurrency(Math.max(0L, snapshot.fundBalance() - snapshot.fundReserved())),
                        NationTreasuryHelper.formatCurrency(snapshot.fundReserved())).getString(), width - 28),
                card.x() + 14, card.y() + 10, TEXT, false);
        int lineY = card.y() + 23;
        if (recovery != null) {
            graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(KEY + "fund.own",
                            NationTreasuryHelper.formatCurrency(recovery.aidUsed()),
                            NationTreasuryHelper.formatCurrency(recovery.aidReserved())).getString(), width - 28),
                    card.x() + 14, lineY, MUTED, false);
            lineY += 13;
        }
        if (!controls) return card.bottom();
        Rect row = new Rect(card.x() + 14, lineY + 4, width - 28, 22);
        Rect minus = new Rect(row.x(), row.y(), 24, 22);
        Rect amount = new Rect(minus.right() + 4, row.y(), Math.min(120, Math.max(60, row.width() / 5)), 22);
        Rect plus = new Rect(amount.right() + 4, row.y(), 24, 22);
        Rect actions = new Rect(plus.right() + 10, row.y(), Math.max(1, row.right() - plus.right() - 10), 22);
        int count = snapshot.admin() ? 2 : 1;
        Rect donate = slot(actions, 0, count, GAP);
        drawButton(graphics, font, minus, Component.literal("−"), GOLD, minus.contains(mx, my), true);
        hit(minus, clip, () -> fundAmount = Math.max(100L, fundAmount - step(100L)));
        graphics.fill(amount.x(), amount.y(), amount.right(), amount.bottom(), BAR_BACKGROUND);
        drawBorder(graphics, amount, BORDER);
        graphics.drawCenteredString(font, NationTreasuryHelper.formatCurrency(fundAmount),
                amount.x() + amount.width() / 2, amount.y() + 7, GOLD);
        drawButton(graphics, font, plus, Component.literal("+"), GOLD, plus.contains(mx, my), true);
        hit(plus, clip, () -> fundAmount = Math.min(1_000_000L, fundAmount + step(100L)));
        boolean canDonate = snapshot.canManageTreasury() && snapshot.member() && idle();
        drawButton(graphics, font, donate, Component.translatable(KEY + "fund.donate"), SUCCESS,
                donate.contains(mx, my), canDonate);
        if (canDonate) hit(donate, clip, () -> send(C2S_RecoveryDispatchActionPacket.Action.NATION_DONATE, null, null,
                null, 0L, fundAmount, 0L, 0, List.of()));
        if (snapshot.admin()) {
            Rect mint = slot(actions, 1, count, GAP);
            drawButton(graphics, font, mint, Component.translatable(KEY + "fund.mint"), DANGER,
                    mint.contains(mx, my), idle());
            if (idle()) hit(mint, clip, () -> send(C2S_RecoveryDispatchActionPacket.Action.ADMIN_MINT, null, null,
                    null, 0L, fundAmount, 0L, 0, List.of()));
        }
        return card.bottom();
    }

    private int reviews(GuiGraphics graphics, int x, int y, int width) {
        var reviews = snapshot.fundReviews();
        int shown = Math.min(MAX_REVIEWS_SHOWN, reviews.size());
        int height = 24 + shown * 12 + (reviews.size() > shown ? 12 : 0);
        Rect card = new Rect(x, y, width, height);
        drawCard(graphics, card, DANGER, false, false);
        graphics.drawString(font, Component.translatable(KEY + "fund.review.title", reviews.size()),
                card.x() + 14, card.y() + 9, DANGER, false);
        int lineY = card.y() + 23;
        for (int i = 0; i < shown; i++) {
            var review = reviews.get(i);
            String line = Component.translatable(KEY + "fund.review.line", fundType(review.type()), review.nation(),
                    NationTreasuryHelper.formatCurrency(review.amount()), review.detail()).getString();
            graphics.drawString(font, font.plainSubstrByWidth(line, width - 28), card.x() + 14, lineY, TEXT, false);
            lineY += 12;
        }
        if (reviews.size() > shown) graphics.drawString(font, Component.translatable(KEY + "fund.review.more",
                reviews.size() - shown), card.x() + 14, lineY, MUTED, false);
        return card.bottom();
    }

    // ---------------------------------------------------------------- contracts

    private void renderContracts(GuiGraphics graphics, Rect content, int mx, int my) {
        boolean dispatch = snapshot.settings().dispatchEnabled();
        Rect create = new Rect(content.right() - 150, content.y(), 150, 22);
        boolean canCreate = dispatch && snapshot.canManageSiege() && snapshot.member() && idle();
        drawButton(graphics, font, create, Component.translatable(DISPATCH + "create"), ACCENT,
                create.contains(mx, my), canCreate);
        if (canCreate) hit(create, null, this::openCreate);
        String heading = Component.translatable(dispatch ? DISPATCH + "title" : DISPATCH + "disabled").getString();
        graphics.drawString(font, font.plainSubstrByWidth(heading, content.width() - 160), content.x(),
                content.y() + 7, dispatch ? TEXT : MUTED, false);
        Rect list = new Rect(content.x(), content.y() + 30, content.width(), Math.max(0, content.height() - 30));
        var contracts = snapshot.contracts();
        int total = contracts.size() * CONTRACT_ROW;
        record(Tab.CONTRACTS, total, list.height());
        if (contracts.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(DISPATCH + "none"),
                    list.x() + list.width() / 2, list.y() + 30, MUTED);
            return;
        }
        int offset = clampedScroll(Tab.CONTRACTS, list.height());
        graphics.enableScissor(list.x(), list.y(), list.right(), list.bottom());
        for (int i = 0; i < contracts.size(); i++) {
            Rect row = new Rect(list.x(), list.y() + i * CONTRACT_ROW - offset, list.width() - 8, CONTRACT_ROW - 4);
            if (row.bottom() <= list.y() || row.y() >= list.bottom()) continue;
            contract(graphics, contracts.get(i), row, list, mx, my);
        }
        graphics.disableScissor();
        drawScrollbar(graphics, new Rect(list.right() - 4, list.y(), 4, list.height()), list.height(), total, offset);
    }

    private void contract(GuiGraphics graphics, ContractView contract, Rect row, Rect clip, int mx, int my) {
        List<DispatchContractActions.Action> actions = DispatchContractActions.available(facts(contract));
        DispatchContractActions.Action primary = actions.stream()
                .filter(action -> action != DispatchContractActions.Action.CANCEL).findFirst().orElse(null);
        boolean cancel = actions.contains(DispatchContractActions.Action.CANCEL);
        int color = contractColor(contract.state());
        drawCard(graphics, row, color, false, row.contains(mx, my));
        boolean buttons = primary != null || cancel;
        int textRight = row.right() - (buttons ? 122 : 12);
        int textWidth = Math.max(20, textRight - row.x() - 12);
        Component state = Component.translatable(DISPATCH + "state." + lower(contract.state()));
        int stateX = textRight - font.width(state);
        graphics.drawString(font, state, stateX, row.y() + 8, color, false);
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(DISPATCH + "row.parties",
                        contract.employer(), contract.provider()).getString(), Math.max(20, stateX - row.x() - 20)),
                row.x() + 12, row.y() + 8, TEXT, false);
        Component side = Component.translatable(DISPATCH + "side." + lower(contract.side()));
        String participants = contract.participants().isEmpty() ? "-" : String.join(", ", contract.participants());
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(DISPATCH + "row.detail", side,
                        contract.target(), participants).getString(), textWidth), row.x() + 12, row.y() + 22, MUTED, false);
        int people = Math.max(1, contract.participants().size());
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(DISPATCH + "row.billing",
                        NationTreasuryHelper.formatCurrency(contract.pricePerMinute()), duration(contract.billedTicks()),
                        duration(contract.maximumTicks() * people)).getString(), textWidth),
                row.x() + 12, row.y() + 36, MUTED, false);
        graphics.drawString(font, font.plainSubstrByWidth(lastContractLine(contract).getString(), textWidth),
                row.x() + 12, row.y() + 50, DispatchContractActions.terminal(contract.state()) ? MUTED : TEXT, false);
        if (primary != null) {
            Rect bounds = new Rect(row.right() - 112, row.y() + 9, 100, 22);
            drawButton(graphics, font, bounds, Component.translatable(DISPATCH + "action." + lower(primary.name())),
                    primary == DispatchContractActions.Action.DECLINE ? GOLD : SUCCESS,
                    bounds.contains(mx, my), idle());
            if (idle()) hit(bounds, clip, () -> perform(contract, primary));
        }
        if (cancel) {
            Rect bounds = new Rect(row.right() - 112, row.y() + 37, 100, 22);
            drawButton(graphics, font, bounds, Component.translatable(DISPATCH + "action.cancel"), DANGER,
                    bounds.contains(mx, my), idle());
            if (idle()) hit(bounds, clip, () -> perform(contract, DispatchContractActions.Action.CANCEL));
        }
    }

    private Component lastContractLine(ContractView contract) {
        if (DispatchContractActions.terminal(contract.state())) {
            String reason = contract.endReason();
            String key = DISPATCH + "end." + reason.replace(':', '.');
            return Component.translatable(DISPATCH + "row.ended", reason.isBlank() ? Component.literal("-")
                    : I18n.exists(key) ? Component.translatable(key) : Component.literal(reason));
        }
        Component checks = Component.translatable(DISPATCH + "row.checks", check(contract.employerApproved()),
                check(contract.providerApproved()), contract.consentCount(), contract.participants().size());
        if (contract.requestedSubsidy() <= 0L) return checks;
        return Component.empty().append(checks).append("  •  ").append(Component.translatable(DISPATCH + "row.subsidy",
                NationTreasuryHelper.formatCurrency(contract.requestedSubsidy()), check(contract.subsidyApproved())));
    }

    private DispatchContractActions.Facts facts(ContractView contract) {
        return new DispatchContractActions.Facts(contract.state(), contract.employerApproved(),
                contract.providerApproved(), contract.subsidyApproved(), contract.requestedSubsidy(),
                contract.participants().size(), contract.consentCount(), contract.viewerParticipant(),
                contract.viewerConsented(), contract.viewerEmployer(), contract.viewerProvider(),
                snapshot.canManageSiege(), snapshot.canManageDispatch(), snapshot.canManageTreasury(),
                snapshot.admin(), snapshot.settings().subsidyNeedsAdmin());
    }

    private void perform(ContractView contract, DispatchContractActions.Action action) {
        C2S_RecoveryDispatchActionPacket.Action packet = switch (action) {
            case CONSENT -> C2S_RecoveryDispatchActionPacket.Action.CONSENT;
            case DECLINE -> C2S_RecoveryDispatchActionPacket.Action.DECLINE;
            case APPROVE -> C2S_RecoveryDispatchActionPacket.Action.APPROVE;
            case APPROVE_SUBSIDY -> C2S_RecoveryDispatchActionPacket.Action.APPROVE_SUBSIDY;
            case FUND -> C2S_RecoveryDispatchActionPacket.Action.FUND;
            case CANCEL -> C2S_RecoveryDispatchActionPacket.Action.CANCEL;
        };
        send(packet, contract.id(), null, null, contract.revision(), 0L, 0L, 0, List.of());
    }

    // ---------------------------------------------------------------- history

    private void renderHistory(GuiGraphics graphics, Rect content, int mx, int my) {
        var history = snapshot.history();
        int total = history.size() * HISTORY_ROW;
        record(Tab.HISTORY, total, content.height());
        if (history.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(KEY + "history.empty"),
                    content.x() + content.width() / 2, content.y() + 30, MUTED);
            return;
        }
        int offset = clampedScroll(Tab.HISTORY, content.height());
        graphics.enableScissor(content.x(), content.y(), content.right(), content.bottom());
        for (int i = 0; i < history.size(); i++) {
            Rect row = new Rect(content.x(), content.y() + i * HISTORY_ROW - offset, content.width() - 8,
                    HISTORY_ROW - 4);
            if (row.bottom() <= content.y() || row.y() >= content.bottom()) continue;
            var event = history.get(i);
            drawCard(graphics, row, historyColor(event.type()), false, row.contains(mx, my));
            Component ago = Component.translatable(KEY + "history.ago",
                    duration(Math.max(0L, snapshot.openTick() - event.openTick())));
            int agoX = row.right() - 12 - font.width(ago);
            graphics.drawString(font, ago, agoX, row.y() + 7, MUTED, false);
            graphics.drawString(font, font.plainSubstrByWidth(Component.translatable("screen.moveearth_addtional.history.type."
                    + lower(event.type())).getString(), Math.max(20, agoX - row.x() - 22)), row.x() + 12, row.y() + 7, TEXT, false);
            StringBuilder line = new StringBuilder(event.primary().isBlank() ? "-" : event.primary());
            if (!event.secondary().isBlank()) line.append(" / ").append(event.secondary());
            if ("RECOVERY_OBJECTIVE".equals(event.type()) && !event.details().isEmpty()) {
                String key = KEY + "history.objective." + event.details().getFirst();
                if (I18n.exists(key)) line.append("  •  ").append(Component.translatable(key).getString());
            }
            graphics.drawString(font, font.plainSubstrByWidth(line.toString(), row.width() - 24),
                    row.x() + 12, row.y() + 21, MUTED, false);
        }
        graphics.disableScissor();
        drawScrollbar(graphics, new Rect(content.right() - 4, content.y(), 4, content.height()),
                content.height(), total, offset);
    }

    // ---------------------------------------------------------------- dialogs

    private void renderWaive(GuiGraphics graphics, Rect panel, int mx, int my) {
        Component detail = Component.translatable(KEY + "waive.detail");
        int wanted = 70 + wrappedHeight(font, detail, 340 - 28);
        Rect dialog = RecoveryScreenLayout.modal(panel, 340, wanted);
        drawPanel(graphics, dialog);
        graphics.drawString(font, Component.translatable(KEY + "waive.confirm"), dialog.x() + 14, dialog.y() + 14,
                DANGER, false);
        drawWrapped(graphics, font, detail, dialog.x() + 14, dialog.y() + 30, dialog.width() - 28, TEXT);
        Rect cancel = new Rect(dialog.x() + 14, dialog.bottom() - 32, 110, 22);
        Rect confirm = new Rect(dialog.right() - 124, dialog.bottom() - 32, 110, 22);
        drawButton(graphics, font, cancel, Component.translatable("screen.moveearth_addtional.nation.cancel"), MUTED,
                cancel.contains(mx, my), true);
        hit(cancel, null, () -> modal = Modal.NONE);
        boolean canWaive = protectionActive() && idle();
        drawButton(graphics, font, confirm, Component.translatable(KEY + "waive"), DANGER, confirm.contains(mx, my),
                canWaive);
        if (canWaive) hit(confirm, null, () -> {
            RecoveryView recovery = snapshot.recovery();
            send(C2S_RecoveryDispatchActionPacket.Action.WAIVE_PROTECTION, recovery.id(), null, null,
                    recovery.revision(), 0L, 0L, 0, List.of());
            modal = Modal.NONE;
        });
    }

    private void renderCreate(GuiGraphics graphics, Rect panel, int mx, int my) {
        List<Field> fields = createFields();
        int spacing = 28;
        int wanted = 40 + fields.size() * spacing + 58;
        Rect dialog = RecoveryScreenLayout.modal(panel, 520, wanted);
        if (dialog.height() < wanted) spacing = Math.max(22, (dialog.height() - 98) / Math.max(1, fields.size()));
        int rowHeight = Math.min(24, spacing - 3);
        drawPanel(graphics, dialog);
        graphics.drawString(font, Component.translatable(DISPATCH + "create.title"), dialog.x() + 16, dialog.y() + 14,
                TEXT, false);
        Rect close = new Rect(dialog.right() - 30, dialog.y() + 8, 20, 20);
        drawClose(graphics, font, close, close.contains(mx, my));
        hit(close, null, () -> modal = Modal.NONE);

        List<NationOption> providers = providers();
        NationOption provider = pick(providers, providerIndex);
        List<MemberOption> members = provider == null ? List.of() : provider.members();
        MemberOption member = pick(members, memberIndex);
        List<CoreOption> cores = eligibleCores();
        CoreOption core = pick(cores, coreIndex);
        NationOption opponent = pick(providers, opponentIndex);
        int labelWidth = (dialog.width() - 32) * 42 / 100;
        int y = dialog.y() + 40;
        for (Field field : fields) {
            Rect row = new Rect(dialog.x() + 16, y, dialog.width() - 32, rowHeight);
            graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(DISPATCH + "field." + lower(field.name()))
                    .getString(), labelWidth - 6), row.x(), row.y() + (rowHeight - 8) / 2, MUTED, false);
            Rect control = new Rect(row.x() + labelWidth, row.y(), row.width() - labelWidth, rowHeight);
            switch (field) {
                case SIDE -> {
                    Rect offense = slot(control, 0, 2, 4);
                    Rect defenseChoice = slot(control, 1, 2, 4);
                    drawChoice(graphics, font, offense, Component.translatable(DISPATCH + "side.offense"), DANGER,
                            !defense, offense.contains(mx, my), true);
                    drawChoice(graphics, font, defenseChoice, Component.translatable(DISPATCH + "side.defense"), SUCCESS,
                            defense, defenseChoice.contains(mx, my), true);
                    hit(offense, null, () -> setDefense(false));
                    hit(defenseChoice, null, () -> setDefense(true));
                }
                case PROVIDER -> cycler(graphics, control, provider == null ? "-" : nationLabel(provider), mx, my,
                        providers.size() > 1, delta -> {
                            providerIndex = wrap(providerIndex + delta, providers.size());
                            memberIndex = 0;
                        });
                case PARTICIPANT -> cycler(graphics, control, member == null ? "-" : member.name(), mx, my,
                        members.size() > 1, delta -> memberIndex = wrap(memberIndex + delta, members.size()));
                case CORE -> cycler(graphics, control, core == null ? "-" : coreLabel(core), mx, my, cores.size() > 1,
                        delta -> coreIndex = wrap(coreIndex + delta, cores.size()));
                case OPPONENT -> cycler(graphics, control, opponent == null ? "-" : nationLabel(opponent), mx, my,
                        providers.size() > 1, delta -> opponentIndex = wrap(opponentIndex + delta, providers.size()));
                case PRICE -> cycler(graphics, control, NationTreasuryHelper.formatCurrency(pricePerMinute), mx, my, true,
                        delta -> pricePerMinute = clamp(pricePerMinute + delta * step(10L), 0L, 10_000L));
                case SUBSIDY -> cycler(graphics, control, NationTreasuryHelper.formatCurrency(subsidy), mx, my, true,
                        delta -> subsidy = clamp(subsidy + delta * step(10L), 0L, 100_000L));
                case DURATION -> cycler(graphics, control, Component.translatable(DISPATCH + "minutes", durationMinutes)
                        .getString(), mx, my, true, delta -> durationMinutes = clampDuration(durationMinutes + delta * 15L));
            }
            y += spacing;
        }
        Component note = Component.translatable(snapshot.settings().dispatchMoneyEnabled() ? DISPATCH + "create.note"
                : DISPATCH + "create.note.unpaid", snapshot.settings().dispatchMaxMinutes());
        graphics.drawString(font, font.plainSubstrByWidth(note.getString(), dialog.width() - 32), dialog.x() + 16,
                dialog.bottom() - 46, MUTED, false);
        Rect cancel = new Rect(dialog.x() + 16, dialog.bottom() - 32, 110, 22);
        Rect confirm = new Rect(cancel.right() + 8, dialog.bottom() - 32, dialog.width() - 32 - 118, 22);
        drawButton(graphics, font, cancel, Component.translatable("screen.moveearth_addtional.nation.cancel"), MUTED,
                cancel.contains(mx, my), true);
        hit(cancel, null, () -> modal = Modal.NONE);
        boolean valid = provider != null && member != null && core != null && (!defense || opponent != null) && idle();
        drawButton(graphics, font, confirm, Component.translatable(DISPATCH + "create.confirm"), SUCCESS,
                confirm.contains(mx, my), valid);
        if (valid) hit(confirm, null, () -> {
            UUID opponentId = defense ? opponent.id() : core.nationId();
            boolean money = snapshot.settings().dispatchMoneyEnabled();
            // CREATE carries the subsidy in the revision slot, the price in amount and the minutes in auxiliary.
            send(C2S_RecoveryDispatchActionPacket.Action.CREATE, core.id(), provider.id(), opponentId,
                    money && snapshot.settings().fundEnabled() ? subsidy : 0L, money ? pricePerMinute : 0L,
                    durationMinutes, defense ? 1 : 0, List.of(member.id()));
            modal = Modal.NONE;
        });
    }

    private List<Field> createFields() {
        List<Field> fields = new ArrayList<>(List.of(Field.SIDE, Field.PROVIDER, Field.PARTICIPANT, Field.CORE));
        if (defense) fields.add(Field.OPPONENT);
        if (snapshot.settings().dispatchMoneyEnabled()) {
            fields.add(Field.PRICE);
            if (snapshot.settings().fundEnabled()) fields.add(Field.SUBSIDY);
        }
        fields.add(Field.DURATION);
        return fields;
    }

    /** A value with previous and next arrows; right-clicking the value steps back too. */
    private void cycler(GuiGraphics graphics, Rect control, String value, int mx, int my, boolean enabled,
                        java.util.function.IntConsumer step) {
        Rect previous = new Rect(control.x(), control.y(), 22, control.height());
        Rect next = new Rect(control.right() - 22, control.y(), 22, control.height());
        Rect middle = new Rect(previous.right() + 4, control.y(), Math.max(1, next.x() - previous.right() - 8),
                control.height());
        drawButton(graphics, font, previous, Component.literal("◀"), ACCENT, previous.contains(mx, my), enabled);
        drawButton(graphics, font, next, Component.literal("▶"), ACCENT, next.contains(mx, my), enabled);
        drawCard(graphics, middle, ACCENT, false, enabled && middle.contains(mx, my));
        graphics.drawCenteredString(font, font.plainSubstrByWidth(value, middle.width() - 10),
                middle.x() + middle.width() / 2, middle.y() + (middle.height() - 8) / 2, enabled ? TEXT : MUTED);
        if (!enabled) return;
        hit(previous, null, () -> step.accept(-1));
        hit(next, null, () -> step.accept(1));
        hits.add(new Hit(middle, null, true, button -> step.accept(button == 1 ? -1 : 1)));
    }

    private void openCreate() {
        modal = Modal.CREATE;
        durationMinutes = clampDuration(durationMinutes);
    }

    private void setDefense(boolean value) {
        if (defense == value) return;
        defense = value;
        coreIndex = 0;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (modal == Modal.NONE && HubTabs.mouseClicked(font, width, null, mouseX, mouseY, button)) return true;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.bounds().contains(mouseX, mouseY) || hit.clip() != null && !hit.clip().contains(mouseX, mouseY)) {
                continue;
            }
            if (button != 0 && !(hit.anyButton() && button == 1)) return true;
            hit.action().click(button);
            return true;
        }
        if (modal != Modal.NONE) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && modal != Modal.NONE) {
            modal = Modal.NONE;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (modal != Modal.NONE) return true;
        int viewport = viewportHeights.getOrDefault(tab, 0);
        scroll.put(tab, scroll(scroll.getOrDefault(tab, 0), scrollY, 32, contentHeights.getOrDefault(tab, 0), viewport));
        return true;
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(new C2S_RequestS2HubPacket(S2HubTab.SIEGE));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- helpers

    private void hit(Rect bounds, Rect clip, Runnable action) {
        hits.add(new Hit(bounds, clip, false, button -> action.run()));
    }

    private void send(C2S_RecoveryDispatchActionPacket.Action action, UUID target, UUID secondary, UUID tertiary,
                      long revision, long amount, long auxiliary, int option, List<UUID> participants) {
        pendingRequestId = ++requestId;
        pendingTicks = 0;
        PacketDistributor.sendToServer(new C2S_RecoveryDispatchActionPacket(pendingRequestId, action, target,
                secondary, tertiary, revision, amount, auxiliary, option, participants));
    }

    private boolean idle() {
        return pendingRequestId < 0;
    }

    private boolean protectionActive() {
        return snapshot.recovery() != null && snapshot.recovery().protectionActive();
    }

    private void showToast(Component message, int color) {
        toast = message;
        toastColor = color;
        toastTicks = 80;
    }

    private void record(Tab value, int contentHeight, int viewportHeight) {
        contentHeights.put(value, contentHeight);
        viewportHeights.put(value, viewportHeight);
    }

    private int clampedScroll(Tab value, int viewportHeight) {
        int maximum = Math.max(0, contentHeights.getOrDefault(value, 0) - viewportHeight);
        int offset = Math.max(0, Math.min(maximum, scroll.getOrDefault(value, 0)));
        scroll.put(value, offset);
        return offset;
    }

    private int heading(GuiGraphics graphics, String key, int x, int y) {
        graphics.drawString(font, Component.translatable(key), x + 2, y + 2, ACCENT, false);
        return y + 15;
    }

    private void bar(GuiGraphics graphics, Rect bounds, int percent, Component label) {
        int clamped = Math.max(0, Math.min(100, percent));
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), BAR_BACKGROUND);
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width() * clamped / 100, bounds.bottom(),
                clamped >= 100 ? SUCCESS : GOLD);
        drawBorder(graphics, bounds, BORDER);
        graphics.drawCenteredString(font, label, bounds.x() + bounds.width() / 2, bounds.y() + (bounds.height() - 8) / 2,
                TEXT);
    }

    private Component duration(long ticks) {
        long minutes = RecoveryScreenLayout.openMinutes(ticks);
        return minutes < 60L ? Component.translatable("screen.moveearth_addtional.open_time.minutes", minutes)
                : Component.translatable("screen.moveearth_addtional.open_time.duration", minutes / 60L, minutes % 60L);
    }

    private static Component check(boolean done) {
        return Component.translatable(DISPATCH + (done ? "check.done" : "check.pending"));
    }

    private static Component fundType(String type) {
        String key = KEY + "fund.type." + lower(type);
        return I18n.exists(key) ? Component.translatable(key) : Component.literal(type);
    }

    private static String nationLabel(NationOption nation) {
        return nation.tag().isBlank() ? nation.name() : "[" + nation.tag() + "] " + nation.name();
    }

    private static String coreLabel(CoreOption core) {
        return Component.translatable(DISPATCH + "core", core.owner(),
                Component.translatable(DISPATCH + "core_type." + lower(core.type())), core.shortId()).getString();
    }

    private static int stateColor(String state) {
        return switch (state) {
            case "ACTIVE" -> GOLD;
            case "COMPLETED" -> SUCCESS;
            default -> MUTED;
        };
    }

    private static int contractColor(String state) {
        return switch (state) {
            case "ACTIVE", "FUNDED" -> SUCCESS;
            case "CANCELLED", "REVIEW_REQUIRED" -> DANGER;
            case "COMPLETED" -> MUTED;
            default -> GOLD;
        };
    }

    private static int historyColor(String type) {
        if (type.startsWith("RECOVERY")) return SUCCESS;
        if (type.startsWith("DISPATCH")) return ACCENT;
        if ("CORE_FALLEN".equals(type) || "SIEGE_STARTED".equals(type) || "VEHICLE_DESTROYED".equals(type)
                || "STORAGE_WRECKED".equals(type)) return DANGER;
        return GOLD;
    }

    private List<NationOption> providers() {
        return snapshot.nations().stream().filter(value -> !value.id().equals(snapshot.ownNationId())).toList();
    }

    private List<CoreOption> eligibleCores() {
        return snapshot.cores().stream().filter(value -> defense == value.nationId().equals(snapshot.ownNationId()))
                .toList();
    }

    private static <T> T pick(List<T> values, int index) {
        return values.isEmpty() ? null : values.get(Math.floorMod(index, values.size()));
    }

    private static int wrap(int index, int size) {
        return size <= 0 ? 0 : Math.floorMod(index, size);
    }

    private long clampDuration(long minutes) {
        long maximum = Math.max(15L, snapshot.settings().dispatchMaxMinutes());
        return clamp(minutes, 15L, maximum);
    }

    private static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** One step, or ten with Shift held. */
    private static long step(long base) {
        return hasShiftDown() ? base * 10L : base;
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private interface Click {
        void click(int button);
    }

    private record Hit(Rect bounds, Rect clip, boolean anyButton, Click action) { }

    private enum Modal { NONE, WAIVE, CREATE }

    private enum Field { SIDE, PROVIDER, PARTICIPANT, CORE, OPPONENT, PRICE, SUBSIDY, DURATION }

    private enum Tab {
        RECOVERY(KEY + "tab.recovery"),
        CONTRACTS(KEY + "tab.contracts"),
        HISTORY(KEY + "tab.history");

        private final String key;

        Tab(String key) {
            this.key = key;
        }
    }
}

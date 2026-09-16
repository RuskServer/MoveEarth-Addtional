package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.network.C2S_RecoveryDispatchActionPacket;
import com.ruskserver.moveearth_addtional.network.C2S_RequestRecoveryDispatchPacket;
import com.ruskserver.moveearth_addtional.network.S2C_RecoveryDispatchActionResultPacket;
import com.ruskserver.moveearth_addtional.network.S2C_RecoveryDispatchSnapshotPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

/** Render-driven recovery, dispatch and war-history screen. */
public final class RecoveryDispatchScreen extends Screen implements SuppressesChatOverlay {
    private static final int PANEL_WIDTH = 760;
    private static final int PANEL_HEIGHT = 430;
    private static final int ROW_HEIGHT = 58;
    private S2C_RecoveryDispatchSnapshotPacket snapshot;
    private Tab tab = Tab.RECOVERY;
    private int scroll;
    private int refreshTicks;
    private int requestId;
    private Component toast;
    private int toastTicks;
    private boolean createOpen;
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
        super(Component.translatable("screen.moveearth_addtional.recovery.title"));
        this.snapshot = snapshot;
    }

    public void update(S2C_RecoveryDispatchSnapshotPacket value) {
        snapshot = value;
        normalizeSelections();
        clampScroll();
    }

    public void handleResult(S2C_RecoveryDispatchActionResultPacket value) {
        toast = Component.translatable(value.messageKey());
        toastTicks = 80;
    }

    @Override public void tick() {
        if (toastTicks > 0) toastTicks--;
        if (++refreshTicks >= 100) {
            refreshTicks = 0;
            PacketDistributor.sendToServer(new C2S_RequestRecoveryDispatchPacket(false));
        }
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(graphics, width, height);
        Rect panel = panel();
        drawPanel(graphics, panel);
        graphics.drawString(font, title, panel.x() + 18, panel.y() + 13, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.recovery.subtitle"),
                panel.x() + 18, panel.y() + 28, MUTED, false);
        drawClose(graphics, font, close(panel), close(panel).contains(mouseX, mouseY));
        for (Tab value : Tab.values()) {
            Rect bounds = tabBounds(panel, value.ordinal());
            drawTab(graphics, font, bounds, Component.translatable(value.key), tab == value,
                    bounds.contains(mouseX, mouseY));
        }
        Rect content = content(panel);
        switch (tab) {
            case RECOVERY -> renderRecovery(graphics, content, mouseX, mouseY);
            case CONTRACTS -> renderContracts(graphics, content, mouseX, mouseY);
            case HISTORY -> renderHistory(graphics, content, mouseX, mouseY);
        }
        if (createOpen) renderCreate(graphics, panel, mouseX, mouseY);
        if (toast != null && toastTicks > 0) drawToast(graphics, font, width, height, toast, ACCENT);
    }

    private void renderRecovery(GuiGraphics graphics, Rect content, int mouseX, int mouseY) {
        var recovery = snapshot.recovery();
        if (recovery == null) {
            drawCard(graphics, content, MUTED, false, false);
            graphics.drawCenteredString(font, Component.translatable("screen.moveearth_addtional.recovery.none"),
                    content.x() + content.width() / 2, content.y() + 50, MUTED);
        } else {
            Rect summary = new Rect(content.x(), content.y(), content.width(), 78);
            drawCard(graphics, summary, recovery.supportPercent() >= 100 ? SUCCESS : GOLD, true, false);
            graphics.drawString(font, Component.translatable("screen.moveearth_addtional.recovery.state",
                    recovery.state()), summary.x() + 14, summary.y() + 11, GOLD, false);
            graphics.drawString(font, Component.translatable("screen.moveearth_addtional.recovery.remaining",
                    formatTicks(recovery.remainingTicks())), summary.x() + 14, summary.y() + 27, TEXT, false);
            int barX = summary.x() + 14, barY = summary.y() + 48, barW = summary.width() - 28;
            graphics.fill(barX, barY, barX + barW, barY + 9, BAR_BACKGROUND);
            graphics.fill(barX, barY, barX + barW * recovery.supportPercent() / 100, barY + 9, SUCCESS);
            graphics.drawCenteredString(font, recovery.supportPercent() + "%", barX + barW / 2, barY + 1, TEXT);
            String rivalText = recovery.rivalName().isBlank() ? "-" : recovery.rivalName();
            graphics.drawString(font, Component.translatable("screen.moveearth_addtional.recovery.rival", rivalText),
                    summary.x() + 14, summary.y() + 66, MUTED, false);

            int y = summary.bottom() + 44;
            drawObjective(graphics, new Rect(content.x(), y, content.width(), 36),
                    "screen.moveearth_addtional.recovery.objective.reseal", recovery.resealed(), "");
            y += 40;
            drawObjective(graphics, new Rect(content.x(), y, content.width(), 36),
                    "screen.moveearth_addtional.recovery.objective.walls",
                    recovery.wallTarget() <= 0 || recovery.healthyWalls() >= recovery.wallTarget(),
                    recovery.healthyWalls() + " / " + recovery.wallTarget());
            y += 40;
            drawObjective(graphics, new Rect(content.x(), y, content.width(), 36),
                    "screen.moveearth_addtional.recovery.objective.upkeep", recovery.upkeepPaid(), "");
            y += 40;
            Rect waive = new Rect(content.x(), y, 210, 28);
            drawButton(graphics, font, waive, Component.translatable(
                    recovery.protectionWaived() ? "screen.moveearth_addtional.recovery.waived"
                            : "screen.moveearth_addtional.recovery.waive"), DANGER,
                    waive.contains(mouseX, mouseY), snapshot.canManageSiege() && !recovery.protectionWaived());
            Rect rival = new Rect(waive.right() + 8, y, 210, 28);
            boolean canRival = snapshot.canManageDiplomacy() && recovery.attackerNationId() != null;
            drawButton(graphics, font, rival, Component.translatable(recovery.rivalName().isBlank()
                    ? "screen.moveearth_addtional.recovery.rival.set" : "screen.moveearth_addtional.recovery.rival.clear"),
                    GOLD, rival.contains(mouseX, mouseY), canRival);
        }
        Rect fund = new Rect(content.x(), content.y() + 86, 238, 28);
        drawCard(graphics, fund, ACCENT, false, false);
        graphics.drawCenteredString(font, Component.translatable("screen.moveearth_addtional.recovery.fund",
                snapshot.fundBalance() - snapshot.fundReserved(), snapshot.fundReserved()),
                fund.x() + fund.width() / 2, fund.y() + 9, TEXT);
        Rect amount = new Rect(fund.right() + 8, fund.y(), 92, 28);
        drawButton(graphics, font, amount, Component.literal("$" + fundAmount), GOLD,
                amount.contains(mouseX, mouseY), snapshot.canManageTreasury() || snapshot.admin());
        Rect donate = new Rect(amount.right() + 8, fund.y(), 118, 28);
        drawButton(graphics, font, donate, Component.translatable("screen.moveearth_addtional.recovery.fund.donate"),
                SUCCESS, donate.contains(mouseX, mouseY), snapshot.canManageTreasury());
        if (snapshot.admin()) {
            Rect mint = new Rect(donate.right() + 8, fund.y(), 100, 28);
            drawButton(graphics, font, mint, Component.translatable("screen.moveearth_addtional.recovery.fund.mint"),
                    DANGER, mint.contains(mouseX, mouseY), true);
        }
        if (snapshot.admin() && !snapshot.fundReviews().isEmpty()) {
            var review = snapshot.fundReviews().getFirst();
            graphics.drawString(font, Component.translatable("screen.moveearth_addtional.recovery.fund.review",
                            snapshot.fundReviews().size(), review.type(), review.nation(), review.amount(), review.detail()),
                    content.x() + 4, content.bottom() - 12, DANGER, false);
        }
    }

    private void drawObjective(GuiGraphics graphics, Rect bounds, String key, boolean complete, String detail) {
        drawCard(graphics, bounds, complete ? SUCCESS : MUTED, complete, false);
        graphics.drawString(font, Component.translatable(key), bounds.x() + 12, bounds.y() + 9,
                complete ? SUCCESS : TEXT, false);
        graphics.drawString(font, detail.isBlank() ? Component.translatable(complete
                        ? "screen.moveearth_addtional.recovery.complete" : "screen.moveearth_addtional.recovery.pending")
                        : Component.literal(detail), bounds.x() + 12, bounds.y() + 25, MUTED, false);
    }

    private void renderContracts(GuiGraphics graphics, Rect content, int mouseX, int mouseY) {
        Rect create = new Rect(content.right() - 150, content.y(), 150, 27);
        drawButton(graphics, font, create, Component.translatable("screen.moveearth_addtional.dispatch.create"),
                ACCENT, create.contains(mouseX, mouseY), snapshot.canManageSiege() && snapshot.member());
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.dispatch.title"),
                content.x() + 4, content.y() + 9, TEXT, false);
        Rect list = new Rect(content.x(), content.y() + 36, content.width(), content.height() - 36);
        graphics.enableScissor(list.x(), list.y(), list.right(), list.bottom());
        int y = list.y() - scroll;
        if (snapshot.contracts().isEmpty()) graphics.drawString(font,
                Component.translatable("screen.moveearth_addtional.dispatch.none"), list.x() + 4, y + 8, MUTED, false);
        for (var contract : snapshot.contracts()) {
            Rect row = new Rect(list.x(), y, list.width() - 7, ROW_HEIGHT - 4);
            if (row.bottom() > list.y() && row.y() < list.bottom()) {
                int color = "ACTIVE".equals(contract.state()) ? SUCCESS
                        : contract.state().contains("CANCEL") || contract.state().contains("REVIEW") ? DANGER : GOLD;
                drawCard(graphics, row, color, false, row.contains(mouseX, mouseY));
                graphics.drawString(font, Component.literal(contract.employer() + " → " + contract.provider()),
                        row.x() + 10, row.y() + 7, TEXT, false);
                graphics.drawString(font, Component.literal(contract.side() + " • " + contract.state()
                                + " • " + contract.consentCount() + "/" + contract.participants().size()),
                        row.x() + 10, row.y() + 22, color, false);
                graphics.drawString(font, Component.translatable("screen.moveearth_addtional.dispatch.billing",
                                contract.pricePerMinute(), formatTicks(contract.billedTicks()),
                                formatTicks(contract.maximumTicks() * Math.max(1, contract.participants().size()))),
                        row.x() + 10, row.y() + 37, MUTED, false);
                Rect action = contractAction(row);
                Component label = contractActionLabel(contract);
                boolean enabled = label != null;
                if (enabled) drawButton(graphics, font, action, label, ACCENT,
                        action.contains(mouseX, mouseY), true);
            }
            y += ROW_HEIGHT;
        }
        graphics.disableScissor();
        drawScrollbar(graphics, new Rect(list.right() - 4, list.y(), 4, list.height()),
                list.height(), snapshot.contracts().size() * ROW_HEIGHT, scroll);
    }

    private Component contractActionLabel(S2C_RecoveryDispatchSnapshotPacket.ContractView contract) {
        if (contract.viewerParticipant() && !contract.viewerConsented()
                && ("CONSENT_PENDING".equals(contract.state()) || "APPROVAL_PENDING".equals(contract.state())))
            return Component.translatable("screen.moveearth_addtional.dispatch.consent");
        if (snapshot.admin() && contract.requestedSubsidy() > 0L && !contract.subsidyApproved()
                && ("APPROVAL_PENDING".equals(contract.state()) || "CONSENT_PENDING".equals(contract.state())))
            return Component.translatable("screen.moveearth_addtional.dispatch.approve_subsidy");
        if ((snapshot.canManageSiege() && !contract.employerApproved()
                || snapshot.canManageDispatch() && !contract.providerApproved())
                && ("APPROVAL_PENDING".equals(contract.state()) || "CONSENT_PENDING".equals(contract.state())))
            return Component.translatable("screen.moveearth_addtional.dispatch.approve");
        if (snapshot.canManageTreasury() && "CONSENT_PENDING".equals(contract.state())
                && contract.employerApproved() && contract.providerApproved()
                && contract.consentCount() == contract.participants().size())
            return Component.translatable("screen.moveearth_addtional.dispatch.fund_action");
        if ((snapshot.canManageSiege() || snapshot.canManageDispatch()) && !isTerminal(contract.state()))
            return Component.translatable("screen.moveearth_addtional.dispatch.cancel");
        return null;
    }

    private void renderHistory(GuiGraphics graphics, Rect content, int mouseX, int mouseY) {
        graphics.enableScissor(content.x(), content.y(), content.right(), content.bottom());
        int y = content.y() - scroll;
        for (var event : snapshot.history()) {
            Rect row = new Rect(content.x(), y, content.width() - 7, 46);
            drawCard(graphics, row, ACCENT, false, row.contains(mouseX, mouseY));
            graphics.drawString(font, Component.translatable("screen.moveearth_addtional.history.type." +
                    event.type().toLowerCase(java.util.Locale.ROOT)), row.x() + 10, row.y() + 8, TEXT, false);
            graphics.drawString(font, Component.literal(event.primary() + (event.secondary().isBlank()
                    ? "" : " / " + event.secondary())), row.x() + 10, row.y() + 24, MUTED, false);
            y += 50;
        }
        graphics.disableScissor();
        drawScrollbar(graphics, new Rect(content.right() - 4, content.y(), 4, content.height()),
                content.height(), snapshot.history().size() * 50, scroll);
    }

    private void renderCreate(GuiGraphics graphics, Rect panel, int mouseX, int mouseY) {
        drawModalBackdrop(graphics, width, height);
        Rect modal = createModal(panel);
        drawPanel(graphics, modal);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.dispatch.create.title"),
                modal.x() + 16, modal.y() + 14, TEXT, false);
        drawClose(graphics, font, modalClose(modal), modalClose(modal).contains(mouseX, mouseY));
        List<S2C_RecoveryDispatchSnapshotPacket.NationOption> providers = providers();
        List<S2C_RecoveryDispatchSnapshotPacket.CoreOption> cores = eligibleCores();
        var provider = providers.isEmpty() ? null : providers.get(Math.floorMod(providerIndex, providers.size()));
        var core = cores.isEmpty() ? null : cores.get(Math.floorMod(coreIndex, cores.size()));
        var members = provider == null ? List.<S2C_RecoveryDispatchSnapshotPacket.MemberOption>of() : provider.members();
        var member = members.isEmpty() ? null : members.get(Math.floorMod(memberIndex, members.size()));
        var opponents = providers();
        var opponent = opponents.isEmpty() ? null : opponents.get(Math.floorMod(opponentIndex, opponents.size()));
        int y = modal.y() + 42;
        drawSelector(graphics, field(modal, y), Component.translatable("screen.moveearth_addtional.dispatch.field.side"),
                defense ? "DEFENSE" : "OFFENSE", mouseX, mouseY); y += 31;
        drawSelector(graphics, field(modal, y), Component.translatable("screen.moveearth_addtional.dispatch.field.provider"),
                provider == null ? "-" : provider.name(), mouseX, mouseY); y += 31;
        drawSelector(graphics, field(modal, y), Component.translatable("screen.moveearth_addtional.dispatch.field.participant"),
                member == null ? "-" : member.name(), mouseX, mouseY); y += 31;
        drawSelector(graphics, field(modal, y), Component.translatable("screen.moveearth_addtional.dispatch.field.core"),
                core == null ? "-" : core.owner() + " " + core.x() + "," + core.z(), mouseX, mouseY); y += 31;
        if (defense) { drawSelector(graphics, field(modal, y), Component.translatable("screen.moveearth_addtional.dispatch.field.opponent"),
                opponent == null ? "-" : opponent.name(), mouseX, mouseY); y += 31; }
        drawSelector(graphics, field(modal, y), Component.translatable("screen.moveearth_addtional.dispatch.field.price"),
                Long.toString(pricePerMinute), mouseX, mouseY); y += 31;
        drawSelector(graphics, field(modal, y), Component.translatable("screen.moveearth_addtional.dispatch.field.subsidy"),
                Long.toString(subsidy), mouseX, mouseY); y += 31;
        drawSelector(graphics, field(modal, y), Component.translatable("screen.moveearth_addtional.dispatch.field.duration"),
                durationMinutes + " min", mouseX, mouseY);
        Rect confirm = new Rect(modal.x() + 16, modal.bottom() - 38, modal.width() - 32, 27);
        boolean valid = provider != null && core != null && member != null && (!defense || opponent != null);
        drawButton(graphics, font, confirm, Component.translatable("screen.moveearth_addtional.dispatch.create.confirm"),
                SUCCESS, confirm.contains(mouseX, mouseY), valid);
    }

    private void drawSelector(GuiGraphics graphics, Rect bounds, Component label, String value, int mouseX, int mouseY) {
        drawCard(graphics, bounds, ACCENT, false, bounds.contains(mouseX, mouseY));
        graphics.drawString(font, label, bounds.x() + 10, bounds.y() + 9, MUTED, false);
        graphics.drawString(font, font.plainSubstrByWidth(value, bounds.width() / 2 - 20),
                bounds.x() + bounds.width() / 2, bounds.y() + 9, TEXT, false);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Rect panel = panel();
        if (createOpen) return createClicked(panel, mouseX, mouseY, button);
        if (close(panel).contains(mouseX, mouseY)) { onClose(); return true; }
        for (Tab value : Tab.values()) if (tabBounds(panel, value.ordinal()).contains(mouseX, mouseY)) {
            tab = value; scroll = 0; return true;
        }
        Rect content = content(panel);
        if (tab == Tab.RECOVERY && snapshot.recovery() != null) {
            int y = content.y() + 78 + 44 + 40 * 3;
            Rect waive = new Rect(content.x(), y, 210, 28);
            Rect rival = new Rect(waive.right() + 8, y, 210, 28);
            if (waive.contains(mouseX, mouseY) && snapshot.canManageSiege()
                    && !snapshot.recovery().protectionWaived()) {
                action(C2S_RecoveryDispatchActionPacket.Action.WAIVE_PROTECTION, snapshot.recovery().id(), null, null,
                        snapshot.recovery().revision(), 0, 0, 0, List.of()); return true;
            }
            if (rival.contains(mouseX, mouseY) && snapshot.canManageDiplomacy()
                    && snapshot.recovery().attackerNationId() != null) {
                boolean clear = !snapshot.recovery().rivalName().isBlank();
                action(clear ? C2S_RecoveryDispatchActionPacket.Action.CLEAR_RIVAL
                                : C2S_RecoveryDispatchActionPacket.Action.SET_RIVAL,
                        snapshot.recovery().id(), clear ? null : snapshot.recovery().attackerNationId(), null,
                        snapshot.recovery().revision(), 0, 0, 0, List.of()); return true;
            }
        }
        if (tab == Tab.RECOVERY) {
            Rect fund = new Rect(content.x(), content.y() + 86, 238, 28);
            Rect amount = new Rect(fund.right() + 8, fund.y(), 92, 28);
            Rect donate = new Rect(amount.right() + 8, fund.y(), 118, 28);
            Rect mint = new Rect(donate.right() + 8, fund.y(), 100, 28);
            if (amount.contains(mouseX, mouseY) && (snapshot.canManageTreasury() || snapshot.admin())) {
                fundAmount = cycle(fundAmount, button, 100, 100000); return true;
            }
            if (donate.contains(mouseX, mouseY) && snapshot.canManageTreasury()) {
                action(C2S_RecoveryDispatchActionPacket.Action.NATION_DONATE, null, null, null,
                        0, fundAmount, 0, 0, List.of()); return true;
            }
            if (mint.contains(mouseX, mouseY) && snapshot.admin()) {
                action(C2S_RecoveryDispatchActionPacket.Action.ADMIN_MINT, null, null, null,
                        0, fundAmount, 0, 0, List.of()); return true;
            }
        }
        if (tab == Tab.CONTRACTS) {
            Rect create = new Rect(content.right() - 150, content.y(), 150, 27);
            if (create.contains(mouseX, mouseY) && snapshot.canManageSiege()) { createOpen = true; normalizeSelections(); return true; }
            Rect list = new Rect(content.x(), content.y() + 36, content.width(), content.height() - 36);
            int y = list.y() - scroll;
            for (var contract : snapshot.contracts()) {
                Rect row = new Rect(list.x(), y, list.width() - 7, ROW_HEIGHT - 4);
                if (contractAction(row).contains(mouseX, mouseY)) { performContractAction(contract); return true; }
                y += ROW_HEIGHT;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean createClicked(Rect panel, double mouseX, double mouseY, int button) {
        Rect modal = createModal(panel);
        if (modalClose(modal).contains(mouseX, mouseY)) { createOpen = false; return true; }
        int y = modal.y() + 42;
        if (field(modal, y).contains(mouseX, mouseY)) { defense = !defense; coreIndex = 0; return true; } y += 31;
        if (field(modal, y).contains(mouseX, mouseY)) { providerIndex++; memberIndex = 0; return true; } y += 31;
        if (field(modal, y).contains(mouseX, mouseY)) { memberIndex++; return true; } y += 31;
        if (field(modal, y).contains(mouseX, mouseY)) { coreIndex++; return true; } y += 31;
        if (defense) { if (field(modal, y).contains(mouseX, mouseY)) { opponentIndex++; return true; } y += 31; }
        if (field(modal, y).contains(mouseX, mouseY)) { pricePerMinute = cycle(pricePerMinute, button, 10, 10000); return true; } y += 31;
        if (field(modal, y).contains(mouseX, mouseY)) { subsidy = cycle(subsidy, button, 10, 100000); return true; } y += 31;
        if (field(modal, y).contains(mouseX, mouseY)) { durationMinutes = cycle(durationMinutes, button, 15, 240); return true; }
        Rect confirm = new Rect(modal.x() + 16, modal.bottom() - 38, modal.width() - 32, 27);
        if (confirm.contains(mouseX, mouseY)) {
            var providers = providers(); var cores = eligibleCores();
            if (providers.isEmpty() || cores.isEmpty()) return true;
            var provider = providers.get(Math.floorMod(providerIndex, providers.size()));
            if (provider.members().isEmpty()) return true;
            var member = provider.members().get(Math.floorMod(memberIndex, provider.members().size()));
            var core = cores.get(Math.floorMod(coreIndex, cores.size()));
            UUID opponent = defense && !providers.isEmpty()
                    ? providers.get(Math.floorMod(opponentIndex, providers.size())).id() : core.nationId();
            action(C2S_RecoveryDispatchActionPacket.Action.CREATE, core.id(), provider.id(), opponent,
                    subsidy, pricePerMinute, durationMinutes, defense ? 1 : 0, List.of(member.id()));
            createOpen = false; return true;
        }
        return true;
    }

    private void performContractAction(S2C_RecoveryDispatchSnapshotPacket.ContractView contract) {
        Component label = contractActionLabel(contract); if (label == null) return;
        C2S_RecoveryDispatchActionPacket.Action action;
        if (contract.viewerParticipant() && !contract.viewerConsented()
                && ("CONSENT_PENDING".equals(contract.state()) || "APPROVAL_PENDING".equals(contract.state())))
            action = C2S_RecoveryDispatchActionPacket.Action.CONSENT;
        else if (snapshot.admin() && contract.requestedSubsidy() > 0L && !contract.subsidyApproved())
            action = C2S_RecoveryDispatchActionPacket.Action.APPROVE_SUBSIDY;
        else if ((snapshot.canManageSiege() && !contract.employerApproved()
                || snapshot.canManageDispatch() && !contract.providerApproved())
                && ("APPROVAL_PENDING".equals(contract.state()) || "CONSENT_PENDING".equals(contract.state())))
            action = C2S_RecoveryDispatchActionPacket.Action.APPROVE;
        else if (snapshot.canManageTreasury() && "CONSENT_PENDING".equals(contract.state()))
            action = C2S_RecoveryDispatchActionPacket.Action.FUND;
        else action = C2S_RecoveryDispatchActionPacket.Action.CANCEL;
        action(action, contract.id(), null, null, contract.revision(), 0, 0, 0, List.of());
    }

    private void action(C2S_RecoveryDispatchActionPacket.Action action, UUID target, UUID secondary,
                        UUID tertiary, long revision, long amount, long auxiliary, int option,
                        List<UUID> participants) {
        PacketDistributor.sendToServer(new C2S_RecoveryDispatchActionPacket(++requestId, action, target,
                secondary, tertiary, revision, amount, auxiliary, option, participants));
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int content = tab == Tab.CONTRACTS ? snapshot.contracts().size() * ROW_HEIGHT
                : tab == Tab.HISTORY ? snapshot.history().size() * 50 : 0;
        scroll = scroll(scroll, scrollY, 32, content, content(panel()).height()); return true;
    }
    @Override public boolean isPauseScreen() { return false; }

    private List<S2C_RecoveryDispatchSnapshotPacket.NationOption> providers() {
        return snapshot.nations().stream().filter(value -> !value.id().equals(snapshot.ownNationId())).toList();
    }
    private List<S2C_RecoveryDispatchSnapshotPacket.CoreOption> eligibleCores() {
        return snapshot.cores().stream().filter(value -> defense
                ? value.nationId().equals(snapshot.ownNationId()) : !value.nationId().equals(snapshot.ownNationId())).toList();
    }
    private void normalizeSelections() { providerIndex = nonNegative(providerIndex); coreIndex = nonNegative(coreIndex);
        memberIndex = nonNegative(memberIndex); opponentIndex = nonNegative(opponentIndex); }
    private static int nonNegative(int value) { return Math.max(0, value); }
    private void clampScroll() { int content = tab == Tab.CONTRACTS ? snapshot.contracts().size() * ROW_HEIGHT
            : tab == Tab.HISTORY ? snapshot.history().size() * 50 : 0; scroll = Math.min(scroll, Math.max(0, content - content(panel()).height())); }
    private static boolean isTerminal(String state) { return "COMPLETED".equals(state) || "CANCELLED".equals(state) || "REVIEW_REQUIRED".equals(state); }
    private static long cycle(long current, int button, long step, long maximum) {
        if (button == 1) return Math.max(0L, current - step); return current + step > maximum ? 0L : current + step;
    }
    private Rect panel() { int w = Math.min(PANEL_WIDTH, width - 20), h = Math.min(PANEL_HEIGHT, height - 20); return new Rect((width - w) / 2, (height - h) / 2, w, h); }
    private static Rect close(Rect panel) { return new Rect(panel.right() - 30, panel.y() + 8, 20, 20); }
    private static Rect tabBounds(Rect panel, int index) { return new Rect(panel.x() + 18 + index * 142, panel.y() + 48, 134, 28); }
    private static Rect content(Rect panel) { return new Rect(panel.x() + 18, panel.y() + 86, panel.width() - 36, panel.height() - 104); }
    private static Rect contractAction(Rect row) { return new Rect(row.right() - 112, row.y() + 13, 100, 28); }
    private static Rect modalClose(Rect modal) { return new Rect(modal.right() - 30, modal.y() + 8, 20, 20); }
    private static Rect createModal(Rect panel) { return new Rect(panel.x() + 105, panel.y() + 28, panel.width() - 210, panel.height() - 56); }
    private static Rect field(Rect modal, int y) { return new Rect(modal.x() + 16, y, modal.width() - 32, 26); }
    private static String formatTicks(long ticks) { long seconds = Math.max(0L, ticks / 20L); return String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L); }

    private enum Tab {
        RECOVERY("screen.moveearth_addtional.recovery.tab.recovery"),
        CONTRACTS("screen.moveearth_addtional.recovery.tab.contracts"),
        HISTORY("screen.moveearth_addtional.recovery.tab.history");
        private final String key; Tab(String key) { this.key = key; }
    }
}

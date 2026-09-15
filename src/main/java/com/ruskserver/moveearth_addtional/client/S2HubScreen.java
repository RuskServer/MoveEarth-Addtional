package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;
import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.network.C2S_NationMembershipPacket;
import com.ruskserver.moveearth_addtional.network.C2S_NationApplicationActionPacket;
import com.ruskserver.moveearth_addtional.network.C2S_NationDiplomacyPacket;
import com.ruskserver.moveearth_addtional.network.C2S_NationTreasuryPacket;
import com.ruskserver.moveearth_addtional.network.C2S_S2HubActionPacket;
import com.ruskserver.moveearth_addtional.network.C2S_SiegeActionPacket;
import com.ruskserver.moveearth_addtional.network.C2S_RequestTechnologyPacket;
import com.ruskserver.moveearth_addtional.network.S2C_S2ActionResultPacket;
import com.ruskserver.moveearth_addtional.network.S2C_S2HubSnapshotPacket;
import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import com.ruskserver.moveearth_addtional.s2.S2NationSnapshot;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

public final class S2HubScreen extends Screen implements SuppressesChatOverlay {
    private static final int PANEL_WIDTH = 620;
    private static final int PANEL_HEIGHT = 356;
    private static final int ROW_HEIGHT = 43;
    private static final int SIEGE_ROW_HEIGHT = 64;
    private static final DateTimeFormatter LAST_SEEN_FORMAT =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
    private static S2HubTab lastTab = S2HubTab.OVERVIEW;
    private static int nextRequestId;

    private S2HubTab tab;
    private S2NationSnapshot snapshot;
    private int scrollOffset;
    private int pendingRequestId = -1;
    private Component toast;
    private int toastColor = SUCCESS;
    private int toastTicks;
    private UUID kickTargetId;
    private String kickTargetName = "";
    private UUID surrenderTargetId;
    private String surrenderTargetName = "";
    private boolean vaultChangeConfirmation;

    public S2HubScreen(S2C_S2HubSnapshotPacket packet) {
        super(Component.translatable("screen.moveearth_addtional.s2.title"));
        this.snapshot = packet.snapshot();
        this.tab = packet.tab() == null ? lastTab : packet.tab();
        lastTab = this.tab;
    }

    public void update(S2C_S2HubSnapshotPacket packet) {
        this.snapshot = packet.snapshot();
        this.tab = packet.tab();
        lastTab = tab;
        clampScroll();
    }

    public void handleResult(S2C_S2ActionResultPacket packet) {
        if (pendingRequestId >= 0 && packet.requestId() != pendingRequestId) return;
        if (pendingRequestId < 0 && !packet.success()) return;
        pendingRequestId = -1;
        Component body = Component.translatable(packet.messageKey());
        toast = packet.success() ? MoveEarthMessage.success(body) : MoveEarthMessage.error(body);
        toastColor = packet.success() ? SUCCESS : DANGER;
        toastTicks = 60;
    }

    @Override
    public void tick() {
        if (toastTicks > 0) toastTicks--;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(graphics, width, height);
        Rect panel = panelBounds();
        drawPanel(graphics, panel);
        graphics.drawString(font, title, panel.x() + 18, panel.y() + 14, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.s2.subtitle"),
                panel.x() + 18, panel.y() + 28, MUTED, false);

        Rect close = closeBounds(panel);
        drawClose(graphics, font, close, close.contains(mouseX, mouseY));
        Rect refresh = refreshBounds(panel);
        boolean refreshEnabled = pendingRequestId < 0;
        drawButton(graphics, font, refresh,
                Component.translatable("screen.moveearth_addtional.s2.refresh"), ACCENT,
                refreshEnabled && refresh.contains(mouseX, mouseY), refreshEnabled);

        int tabWidth = Math.max(72, (panel.width() - 36) / S2HubTab.values().length);
        for (S2HubTab candidate : S2HubTab.values()) {
            Rect bounds = tabBounds(panel, candidate, tabWidth);
            drawTab(graphics, font, bounds, tabLabel(candidate), candidate == tab,
                    bounds.contains(mouseX, mouseY));
        }

        Rect content = contentBounds(panel);
        if (!snapshot.member() && tab == S2HubTab.SIEGE && !snapshot.sieges().isEmpty()) {
            drawSieges(graphics, content, mouseX, mouseY);
        } else if (!snapshot.member()) drawUnaffiliated(graphics, content, mouseX, mouseY);
        else if (tab == S2HubTab.OVERVIEW) drawOverview(graphics, content, mouseX, mouseY);
        else if (tab == S2HubTab.MEMBERS) drawMembers(graphics, content, mouseX, mouseY);
        else if (tab == S2HubTab.ROLES) drawRoles(graphics, content, mouseX, mouseY);
        else if (tab == S2HubTab.DIPLOMACY) drawDiplomacy(graphics, content, mouseX, mouseY);
        else drawSieges(graphics, content, mouseX, mouseY);

        if (kickTargetId != null) drawKickConfirmation(graphics, mouseX, mouseY);
        if (surrenderTargetId != null) drawSurrenderConfirmation(graphics, mouseX, mouseY);
        if (vaultChangeConfirmation) drawVaultConfirmation(graphics, mouseX, mouseY);
        if (toastTicks > 0 && toast != null) drawToast(graphics, font, width, height, toast, toastColor);
    }

    private void drawUnaffiliated(GuiGraphics graphics, Rect content, int mouseX, int mouseY) {
        Rect card = new Rect(content.x(), content.y(), content.width(), Math.min(118, content.height()));
        drawCard(graphics, card, ACCENT, false, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.s2.no_nation"),
                card.x() + 16, card.y() + 15, ACCENT, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.s2.no_nation.detail"),
                card.x() + 16, card.y() + 34, MUTED, false);
        Rect create = new Rect(card.x() + 16, card.bottom() - 34, 138, 22);
        drawButton(graphics, font, create,
                Component.translatable("screen.moveearth_addtional.s2.create"), ACCENT,
                create.contains(mouseX, mouseY), true);
        Rect preview = new Rect(create.right() + 8, create.y(), 148, 22);
        drawButton(graphics, font, preview,
                Component.translatable("screen.moveearth_addtional.territory.preview"), SUCCESS,
                preview.contains(mouseX, mouseY), true);
        if (!snapshot.invitations().isEmpty()) {
            S2NationSnapshot.InvitationView invitation = snapshot.invitations().getFirst();
            Rect invitationCard = invitationBounds(content);
            drawCard(graphics, invitationCard, GOLD, false, invitationCard.contains(mouseX, mouseY));
            String nation = invitation.nationTag().isBlank() ? invitation.nationName()
                    : "[" + invitation.nationTag() + "] " + invitation.nationName();
            graphics.drawString(font, Component.translatable(
                            "screen.moveearth_addtional.nation.invitation", nation),
                    invitationCard.x() + 13, invitationCard.y() + 10, GOLD, false);
            Rect accept = invitationAcceptBounds(invitationCard);
            Rect decline = invitationDeclineBounds(invitationCard);
            boolean enabled = pendingRequestId < 0;
            drawButton(graphics, font, decline,
                    Component.translatable("screen.moveearth_addtional.nation.decline"), DANGER,
                    enabled && decline.contains(mouseX, mouseY), enabled);
            drawButton(graphics, font, accept,
                    Component.translatable("screen.moveearth_addtional.nation.accept"), SUCCESS,
                    enabled && accept.contains(mouseX, mouseY), enabled);
        }
        if (snapshot.serverAdmin()) {
            int adminY = snapshot.invitations().isEmpty() ? card.bottom() + 13 : invitationBounds(content).bottom() + 8;
            graphics.drawString(font, Component.translatable("screen.moveearth_addtional.s2.admin_view"),
                    content.x(), adminY, GOLD, false);
        }
    }

    private void drawOverview(GuiGraphics graphics, Rect content, int mouseX, int mouseY) {
        String nationTitle = snapshot.nationTag().isBlank()
                ? snapshot.nationName() : "[" + snapshot.nationTag() + "] " + snapshot.nationName();
        graphics.drawString(font, nationTitle, content.x(), content.y(), ACCENT, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.s2.role", snapshot.roleName()),
                content.x(), content.y() + 15, MUTED, false);
        Component ownerText = Component.translatable(
                "screen.moveearth_addtional.s2.owner", snapshot.ownerName());
        graphics.drawString(font, ownerText,
                content.right() - font.width(ownerText), content.y() + 15, GOLD, false);

        int gap = 8;
        int cardWidth = (content.width() - gap) / 2;
        drawMetric(graphics, new Rect(content.x(), content.y() + 38, cardWidth, 58),
                "screen.moveearth_addtional.s2.members", snapshot.onlineMembers() + " / " + snapshot.totalMembers());
        drawMetric(graphics, new Rect(content.x() + cardWidth + gap, content.y() + 38, cardWidth, 58),
                "screen.moveearth_addtional.s2.territory", Integer.toString(snapshot.territoryChunks()));
        drawMetric(graphics, new Rect(content.x(), content.y() + 104, cardWidth, 58),
                "screen.moveearth_addtional.s2.cores", Integer.toString(snapshot.activeCores()));
        drawMetric(graphics, new Rect(content.x() + cardWidth + gap, content.y() + 104, cardWidth, 58),
                "screen.moveearth_addtional.s2.upkeep", Component.translatable(
                        "screen.moveearth_addtional.s2.upkeep_value", snapshot.upkeep()).getString());
        Rect siege = new Rect(content.x(), content.y() + 170, content.width(), 47);
        drawCard(graphics, siege, DANGER, false, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.s2.siege"),
                siege.x() + 13, siege.y() + 9, MUTED, false);
        graphics.drawString(font, snapshot.siegeStatus(), siege.x() + 13, siege.y() + 25, TEXT, false);
        Rect preview = memberPreviewBounds(content);
        drawButton(graphics, font, preview,
                Component.translatable("screen.moveearth_addtional.territory.preview"), SUCCESS,
                preview.contains(mouseX, mouseY), true);
        Rect treasury = treasuryBounds(content);
        drawButton(graphics, font, treasury,
                Component.translatable("screen.moveearth_addtional.treasury.open"), GOLD,
                treasury.contains(mouseX, mouseY), true);
        Rect vault = vaultBounds(content);
        Component vaultLabel = snapshot.vaultConfigured()
                ? snapshot.vaultChangeCooldownTicks() > 0L
                ? Component.translatable("screen.moveearth_addtional.vault.cooldown",
                formatTicks(snapshot.vaultChangeCooldownTicks()))
                : Component.translatable("screen.moveearth_addtional.vault.current",
                snapshot.vaultChunkX(), snapshot.vaultChunkZ())
                : Component.translatable("screen.moveearth_addtional.vault.set");
        boolean vaultEnabled = canManageTerritory() && pendingRequestId < 0
                && snapshot.vaultChangeCooldownTicks() <= 0L;
        drawButton(graphics, font, vault, vaultLabel, ACCENT,
                vaultEnabled && vault.contains(mouseX, mouseY), vaultEnabled);
        if (isOwner()) {
            Rect settings = memberLeaveBounds(content);
            drawButton(graphics, font, settings,
                    Component.translatable("screen.moveearth_addtional.nation.settings.open"), ACCENT,
                    settings.contains(mouseX, mouseY), true);
        } else {
            Rect leave = memberLeaveBounds(content);
            boolean enabled = pendingRequestId < 0;
            drawButton(graphics, font, leave,
                    Component.translatable("screen.moveearth_addtional.nation.leave"), DANGER,
                    enabled && leave.contains(mouseX, mouseY), enabled);
        }
    }

    private void drawMetric(GuiGraphics graphics, Rect bounds, String labelKey, String value) {
        drawCard(graphics, bounds, ACCENT, false, false);
        graphics.drawString(font, Component.translatable(labelKey), bounds.x() + 13, bounds.y() + 10, MUTED, false);
        graphics.drawString(font, value, bounds.x() + 13, bounds.y() + 29, TEXT, false);
    }

    private void drawMembers(GuiGraphics graphics, Rect content, int mouseX, int mouseY) {
        if (canManageMembers()) {
            Rect applications = memberApplicationsBounds(content);
            drawButton(graphics, font, applications,
                    Component.translatable("screen.moveearth_addtional.onboarding.applications.open"), GOLD,
                    applications.contains(mouseX, mouseY), true);
            Rect invite = memberInviteBounds(content);
            drawButton(graphics, font, invite,
                    Component.translatable("screen.moveearth_addtional.nation.invite_member"), SUCCESS,
                    invite.contains(mouseX, mouseY), true);
        }
        drawRows(graphics, memberListBounds(content), snapshot.members(), mouseX, mouseY, true);
    }

    private void drawRoles(GuiGraphics graphics, Rect content, int mouseX, int mouseY) {
        if (canManageRoles()) {
            Rect create = roleCreateBounds(content);
            drawButton(graphics, font, create,
                    Component.translatable("screen.moveearth_addtional.nation.role.create"), SUCCESS,
                    create.contains(mouseX, mouseY), true);
        }
        drawRows(graphics, roleListBounds(content), snapshot.roles(), mouseX, mouseY, false);
    }

    private void drawDiplomacy(GuiGraphics graphics, Rect content, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.diplomacy.detail"),
                content.x(), content.y() + 4, MUTED, false);
        Rect list = diplomacyListBounds(content);
        if (snapshot.diplomacy().isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.moveearth_addtional.diplomacy.empty"),
                    list.x() + list.width() / 2, list.y() + 42, MUTED);
            return;
        }
        graphics.enableScissor(list.x(), list.y(), list.right(), list.bottom());
        for (int index = 0; index < snapshot.diplomacy().size(); index++) {
            S2NationSnapshot.DiplomacyView relation = snapshot.diplomacy().get(index);
            Rect card = diplomacyCard(list, index);
            if (card.bottom() <= list.y() || card.y() >= list.bottom()) continue;
            int color = relation.state() == S2NationSnapshot.DiplomacyState.ALLIED ? SUCCESS
                    : relation.state() == S2NationSnapshot.DiplomacyState.HOSTILE ? DANGER : ACCENT;
            drawCard(graphics, card, color, false, card.contains(mouseX, mouseY));
            String nation = relation.nationTag().isBlank() ? relation.nationName()
                    : "[" + relation.nationTag() + "] " + relation.nationName();
            graphics.drawString(font, nation, card.x() + 13, card.y() + 8, TEXT, false);
            graphics.drawString(font, Component.translatable(diplomacyStateKey(relation.state())),
                    card.x() + 13, card.y() + 23, color, false);
            drawDiplomacyActions(graphics, card, relation, mouseX, mouseY);
        }
        graphics.disableScissor();
        drawScrollbar(graphics, new Rect(list.right() - 4, list.y(), 4, list.height()),
                list.height(), snapshot.diplomacy().size() * ROW_HEIGHT, scrollOffset);
    }

    private void drawDiplomacyActions(GuiGraphics graphics, Rect card,
                                      S2NationSnapshot.DiplomacyView relation, int mouseX, int mouseY) {
        if (!canManageDiplomacy()) return;
        Rect primary = diplomacyPrimaryBounds(card);
        Rect secondary = diplomacySecondaryBounds(card);
        boolean enabled = pendingRequestId < 0;
        switch (relation.state()) {
            case NEUTRAL -> {
                drawButton(graphics, font, secondary,
                        Component.translatable("screen.moveearth_addtional.diplomacy.hostile"), DANGER,
                        enabled && secondary.contains(mouseX, mouseY), enabled);
                drawButton(graphics, font, primary,
                        Component.translatable("screen.moveearth_addtional.diplomacy.request"), SUCCESS,
                        enabled && primary.contains(mouseX, mouseY), enabled);
            }
            case INCOMING_REQUEST -> {
                drawButton(graphics, font, secondary,
                        Component.translatable("screen.moveearth_addtional.diplomacy.decline"), DANGER,
                        enabled && secondary.contains(mouseX, mouseY), enabled);
                drawButton(graphics, font, primary,
                        Component.translatable("screen.moveearth_addtional.diplomacy.accept"), SUCCESS,
                        enabled && primary.contains(mouseX, mouseY), enabled);
            }
            case OUTGOING_REQUEST -> drawButton(graphics, font, primary,
                    Component.translatable("screen.moveearth_addtional.diplomacy.hostile"), DANGER,
                    enabled && primary.contains(mouseX, mouseY), enabled);
            case ALLIED -> drawButton(graphics, font, primary,
                    Component.translatable("screen.moveearth_addtional.diplomacy.end_alliance"), DANGER,
                    enabled && primary.contains(mouseX, mouseY), enabled);
            case HOSTILE -> drawButton(graphics, font, primary,
                    Component.translatable(relation.hostileByViewer()
                            ? "screen.moveearth_addtional.diplomacy.neutral"
                            : "screen.moveearth_addtional.diplomacy.hostile_incoming"), ACCENT,
                    enabled && relation.hostileByViewer() && primary.contains(mouseX, mouseY),
                    enabled && relation.hostileByViewer());
        }
    }

    private void drawSieges(GuiGraphics graphics, Rect content, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.siege.detail"),
                content.x(), content.y() + 4, MUTED, false);
        Rect list = diplomacyListBounds(content);
        if (snapshot.sieges().isEmpty() && snapshot.peaceProposals().isEmpty()
                && snapshot.truces().isEmpty() && snapshot.prisoners().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.moveearth_addtional.siege.empty"),
                    list.x() + list.width() / 2, list.y() + 42, MUTED);
            return;
        }
        graphics.enableScissor(list.x(), list.y(), list.right(), list.bottom());
        int row = 0;
        for (S2NationSnapshot.PeaceView peace : snapshot.peaceProposals()) {
            Rect card = siegeCard(list, row++);
            if (card.bottom() <= list.y() || card.y() >= list.bottom()) continue;
            drawCard(graphics, card, peace.incoming() ? GOLD : ACCENT, false, card.contains(mouseX, mouseY));
            String opponent = peace.opponentTag().isBlank() ? peace.opponentName()
                    : "[" + peace.opponentTag() + "] " + peace.opponentName();
            graphics.drawString(font, Component.translatable(peace.incoming()
                            ? "screen.moveearth_addtional.peace.incoming"
                            : "screen.moveearth_addtional.peace.outgoing", opponent),
                    card.x() + 13, card.y() + 8, TEXT, false);
            graphics.drawString(font, Component.translatable("screen.moveearth_addtional.peace.terms",
                            peace.goldCompensation(), formatTicks(peace.remainingTicks())),
                    card.x() + 13, card.y() + 25, GOLD, false);
            if (canManageDiplomacy()) {
                boolean enabled = pendingRequestId < 0;
                if (peace.incoming()) {
                    drawButton(graphics, font, peaceRejectBounds(card),
                            Component.translatable("screen.moveearth_addtional.peace.reject"), DANGER,
                            enabled && peaceRejectBounds(card).contains(mouseX, mouseY), enabled);
                    drawButton(graphics, font, peaceAcceptBounds(card),
                            Component.translatable("screen.moveearth_addtional.peace.accept"), SUCCESS,
                            enabled && peaceAcceptBounds(card).contains(mouseX, mouseY), enabled);
                } else {
                    drawButton(graphics, font, peaceAcceptBounds(card),
                            Component.translatable("screen.moveearth_addtional.peace.cancel"), DANGER,
                            enabled && peaceAcceptBounds(card).contains(mouseX, mouseY), enabled);
                }
            }
        }
        for (S2NationSnapshot.TruceView truce : snapshot.truces()) {
            Rect card = siegeCard(list, row++);
            if (card.bottom() <= list.y() || card.y() >= list.bottom()) continue;
            drawCard(graphics, card, SUCCESS, false, card.contains(mouseX, mouseY));
            String opponent = truce.opponentTag().isBlank() ? truce.opponentName()
                    : "[" + truce.opponentTag() + "] " + truce.opponentName();
            graphics.drawString(font, Component.translatable(
                            "screen.moveearth_addtional.peace.truce", opponent),
                    card.x() + 13, card.y() + 12, TEXT, false);
            graphics.drawString(font, Component.translatable(
                            "screen.moveearth_addtional.peace.truce_remaining",
                            formatTicks(truce.remainingTicks())),
                    card.x() + 13, card.y() + 31, SUCCESS, false);
        }
        for (S2NationSnapshot.PrisonerView prisoner : snapshot.prisoners()) {
            Rect card = siegeCard(list, row++);
            if (card.bottom() <= list.y() || card.y() >= list.bottom()) continue;
            int color = prisoner.heldByViewer() ? GOLD : DANGER;
            drawCard(graphics, card, color, false, card.contains(mouseX, mouseY));
            String opponent = prisoner.opponentTag().isBlank() ? prisoner.opponentName()
                    : "[" + prisoner.opponentTag() + "] " + prisoner.opponentName();
            graphics.drawString(font, Component.translatable(prisoner.heldByViewer()
                            ? "screen.moveearth_addtional.prisoner.held_by_us"
                            : "screen.moveearth_addtional.prisoner.held_by_them", prisoner.playerName()),
                    card.x() + 13, card.y() + 12, TEXT, false);
            graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(
                            "screen.moveearth_addtional.prisoner.opponent", opponent).getString()
                            + " • " + formatTicks(prisoner.remainingTicks()),
                            card.width() - 226),
                    card.x() + 13, card.y() + 31, color, false);
            if (canManageDiplomacy()) {
                Rect peace = siegePeaceBounds(card);
                boolean enabled = pendingRequestId < 0;
                drawButton(graphics, font, peace,
                        Component.translatable("screen.moveearth_addtional.peace.propose"), SUCCESS,
                        enabled && peace.contains(mouseX, mouseY), enabled);
            }
        }
        for (S2NationSnapshot.SiegeView siege : snapshot.sieges()) {
            Rect card = siegeCard(list, row++);
            if (card.bottom() <= list.y() || card.y() >= list.bottom()) continue;
            int color = siege.phase() == S2NationSnapshot.SiegePhase.INITIAL_LOCK ? GOLD : DANGER;
            drawCard(graphics, card, color, false, card.contains(mouseX, mouseY));
            String opponent = siege.opponentTag().isBlank() ? siege.opponentName()
                    : "[" + siege.opponentTag() + "] " + siege.opponentName();
            graphics.drawString(font, Component.translatable(siege.attacker()
                            ? "screen.moveearth_addtional.siege.attacking"
                            : "screen.moveearth_addtional.siege.defending", opponent),
                    card.x() + 13, card.y() + 7, TEXT, false);
            String phaseKey = siege.phase() == S2NationSnapshot.SiegePhase.ROLLING
                    ? "screen.moveearth_addtional.siege.phase.rolling"
                    : siege.phase() == S2NationSnapshot.SiegePhase.FALLEN
                    ? "screen.moveearth_addtional.siege.phase.fallen"
                    : "screen.moveearth_addtional.siege.phase.initial";
            Component phase = Component.translatable(phaseKey, formatTicks(siege.remainingTicks()),
                    siege.fallStage());
            if (siege.offlineDefenseActive()) {
                phase = phase.copy().append(Component.translatable(
                        "screen.moveearth_addtional.siege.offline_defense"));
            }
            graphics.drawString(font, phase, card.x() + 13, card.y() + 22, color, false);
            String core = siege.coreX() + ", " + siege.coreY() + ", " + siege.coreZ();
            graphics.drawString(font, core, card.right() - 210, card.y() + 7, MUTED, false);
            int barX = card.x() + 13;
            int barY = card.y() + 42;
            boolean canWithdraw = canWithdraw(siege);
            int barWidth = canWithdraw || canManageDiplomacy() ? 286 : card.width() - 26;
            graphics.fill(barX, barY, barX + barWidth, barY + 7, BAR_BACKGROUND);
            boolean fallen = siege.phase() == S2NationSnapshot.SiegePhase.FALLEN;
            int filled = fallen && siege.counterRequiredTicks() > 0L
                    ? (int) ((long) barWidth * siege.counterCaptureTicks() / siege.counterRequiredTicks())
                    : (int) ((long) barWidth * siege.coreHealth() / siege.coreMaximumHealth());
            graphics.fill(barX, barY, barX + filled, barY + 7, color);
            Component barLabel = fallen
                    ? Component.translatable("screen.moveearth_addtional.siege.counter_progress",
                    Math.min(100L, siege.counterCaptureTicks() * 100L
                            / Math.max(1L, siege.counterRequiredTicks())))
                    : Component.literal(siege.coreHealth() + " / " + siege.coreMaximumHealth());
            graphics.drawCenteredString(font, barLabel, barX + barWidth / 2, barY - 1, TEXT);
            if (canWithdraw || canManageDiplomacy()) {
                boolean enabled = pendingRequestId < 0;
                Rect peace = siegePeaceBounds(card);
                Rect surrender = siegeSurrenderBounds(card);
                drawButton(graphics, font, peace,
                        Component.translatable("screen.moveearth_addtional.peace.propose"), SUCCESS,
                        enabled && !siege.individualAttacker() && canManageDiplomacy()
                                && peace.contains(mouseX, mouseY),
                        enabled && !siege.individualAttacker() && canManageDiplomacy());
                drawButton(graphics, font, surrender,
                        Component.translatable(siege.attacker()
                                ? "screen.moveearth_addtional.siege.withdraw"
                                : "screen.moveearth_addtional.siege.surrender"), DANGER,
                        enabled && canWithdraw && surrender.contains(mouseX, mouseY),
                        enabled && canWithdraw);
            }
        }
        graphics.disableScissor();
        drawScrollbar(graphics, new Rect(list.right() - 4, list.y(), 4, list.height()),
                list.height(), siegeRowCount() * SIEGE_ROW_HEIGHT, scrollOffset);
    }

    private void drawRows(GuiGraphics graphics, Rect content, List<?> rows,
                          int mouseX, int mouseY, boolean members) {
        if (rows.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable(members ? "screen.moveearth_addtional.s2.members.empty"
                            : "screen.moveearth_addtional.s2.roles.empty"),
                    content.x() + content.width() / 2, content.y() + 42, MUTED);
            return;
        }
        graphics.enableScissor(content.x(), content.y(), content.right(), content.bottom());
        for (int index = 0; index < rows.size(); index++) {
            int y = content.y() + index * ROW_HEIGHT - scrollOffset;
            Rect card = new Rect(content.x(), y, content.width() - 8, ROW_HEIGHT - 5);
            if (card.bottom() <= content.y() || card.y() >= content.bottom()) continue;
            drawCard(graphics, card, members ? SUCCESS : GOLD, false, card.contains(mouseX, mouseY));
            if (members) {
                S2NationSnapshot.MemberView member = (S2NationSnapshot.MemberView) rows.get(index);
                graphics.drawString(font, member.name(), card.x() + 13, card.y() + 9,
                        member.online() ? SUCCESS : TEXT, false);
                graphics.drawString(font, member.roleName(), card.x() + 13, card.y() + 23, MUTED, false);
                Component presence = member.online()
                        ? Component.translatable("screen.moveearth_addtional.s2.online")
                        : lastSeenText(member.lastSeenAt());
                int presenceRight = card.right() - (canAssignRole(member) ? 142 : canKick(member) ? 72 : 12);
                graphics.drawString(font, presence,
                        presenceRight - font.width(presence), card.y() + 15,
                        member.online() ? SUCCESS : MUTED, false);
                if (canAssignRole(member)) {
                    Rect assign = roleAssignBounds(card);
                    drawButton(graphics, font, assign,
                            Component.translatable("screen.moveearth_addtional.nation.role.change"), ACCENT,
                            pendingRequestId < 0 && assign.contains(mouseX, mouseY), pendingRequestId < 0);
                }
                if (canKick(member)) {
                    Rect kick = kickBounds(card);
                    drawButton(graphics, font, kick,
                            Component.translatable("screen.moveearth_addtional.nation.kick"), DANGER,
                            pendingRequestId < 0 && kick.contains(mouseX, mouseY), pendingRequestId < 0);
                }
            } else {
                S2NationSnapshot.RoleView role = (S2NationSnapshot.RoleView) rows.get(index);
                graphics.drawString(font, role.displayName(), card.x() + 13, card.y() + 9, TEXT, false);
                graphics.drawString(font, Component.translatable("screen.moveearth_addtional.s2.role_summary",
                                role.memberCount(), role.permissionCount()),
                        card.x() + 13, card.y() + 23, MUTED, false);
                if (canEditRole(role)) {
                    Rect edit = roleEditBounds(card);
                    drawButton(graphics, font, edit,
                            Component.translatable("screen.moveearth_addtional.nation.role.edit"), ACCENT,
                            edit.contains(mouseX, mouseY), true);
                }
            }
        }
        graphics.disableScissor();
        drawScrollbar(graphics, new Rect(content.right() - 4, content.y(), 4, content.height()),
                content.height(), rows.size() * ROW_HEIGHT, scrollOffset);
    }

    private void drawKickConfirmation(GuiGraphics graphics, int mouseX, int mouseY) {
        drawModalBackdrop(graphics, width, height);
        Rect modal = kickModalBounds();
        drawPanel(graphics, modal);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.nation.kick_title"),
                modal.x() + 16, modal.y() + 15, DANGER, false);
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(
                        "screen.moveearth_addtional.nation.kick_detail", kickTargetName).getString(),
                modal.width() - 32), modal.x() + 16, modal.y() + 38, TEXT, false);
        Rect cancel = modalCancelBounds(modal);
        Rect confirm = modalConfirmBounds(modal);
        drawButton(graphics, font, cancel,
                Component.translatable("screen.moveearth_addtional.nation.cancel"), MUTED,
                cancel.contains(mouseX, mouseY), true);
        drawButton(graphics, font, confirm,
                Component.translatable("screen.moveearth_addtional.nation.kick_confirm"), DANGER,
                confirm.contains(mouseX, mouseY), true);
    }

    private void drawSurrenderConfirmation(GuiGraphics graphics, int mouseX, int mouseY) {
        drawModalBackdrop(graphics, width, height);
        Rect modal = kickModalBounds();
        drawPanel(graphics, modal);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.siege.surrender_title"),
                modal.x() + 16, modal.y() + 15, DANGER, false);
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(
                        "screen.moveearth_addtional.siege.surrender_detail", surrenderTargetName).getString(),
                modal.width() - 32), modal.x() + 16, modal.y() + 38, TEXT, false);
        Rect cancel = modalCancelBounds(modal);
        Rect confirm = modalConfirmBounds(modal);
        drawButton(graphics, font, cancel,
                Component.translatable("screen.moveearth_addtional.nation.cancel"), MUTED,
                cancel.contains(mouseX, mouseY), true);
        drawButton(graphics, font, confirm,
                Component.translatable("screen.moveearth_addtional.siege.surrender_confirm"), DANGER,
                confirm.contains(mouseX, mouseY), true);
    }

    private void drawVaultConfirmation(GuiGraphics graphics, int mouseX, int mouseY) {
        drawModalBackdrop(graphics, width, height);
        Rect modal = kickModalBounds();
        drawPanel(graphics, modal);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.vault.change_title"),
                modal.x() + 16, modal.y() + 15, GOLD, false);
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(
                        "screen.moveearth_addtional.vault.change_detail").getString(), modal.width() - 32),
                modal.x() + 16, modal.y() + 38, TEXT, false);
        Rect cancel = modalCancelBounds(modal);
        Rect confirm = modalConfirmBounds(modal);
        drawButton(graphics, font, cancel,
                Component.translatable("screen.moveearth_addtional.nation.cancel"), MUTED,
                cancel.contains(mouseX, mouseY), true);
        drawButton(graphics, font, confirm,
                Component.translatable("screen.moveearth_addtional.vault.change_confirm"), GOLD,
                confirm.contains(mouseX, mouseY), true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (vaultChangeConfirmation) {
            Rect modal = kickModalBounds();
            if (modalCancelBounds(modal).contains(mouseX, mouseY)) {
                vaultChangeConfirmation = false;
            } else if (modalConfirmBounds(modal).contains(mouseX, mouseY) && pendingRequestId < 0) {
                sendSiegeAction(C2S_SiegeActionPacket.Action.SET_VAULT, new UUID(0L, 0L), 0L);
                vaultChangeConfirmation = false;
            }
            return true;
        }
        if (surrenderTargetId != null) {
            Rect modal = kickModalBounds();
            if (modalCancelBounds(modal).contains(mouseX, mouseY)) {
                surrenderTargetId = null;
                surrenderTargetName = "";
            } else if (modalConfirmBounds(modal).contains(mouseX, mouseY) && pendingRequestId < 0) {
                sendSiegeAction(C2S_SiegeActionPacket.Action.SURRENDER, surrenderTargetId, 0L);
                surrenderTargetId = null;
                surrenderTargetName = "";
            }
            return true;
        }
        if (kickTargetId != null) {
            Rect modal = kickModalBounds();
            if (modalCancelBounds(modal).contains(mouseX, mouseY)) {
                kickTargetId = null;
                kickTargetName = "";
            } else if (modalConfirmBounds(modal).contains(mouseX, mouseY) && pendingRequestId < 0) {
                sendMembershipAction(C2S_NationMembershipPacket.Action.KICK, kickTargetId);
                kickTargetId = null;
                kickTargetName = "";
            }
            return true;
        }
        Rect panel = panelBounds();
        if (closeBounds(panel).contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (pendingRequestId < 0 && refreshBounds(panel).contains(mouseX, mouseY)) {
            int requestId = ++nextRequestId;
            pendingRequestId = requestId;
            PacketDistributor.sendToServer(new C2S_S2HubActionPacket(
                    requestId, snapshot.revision(), tab, C2S_S2HubActionPacket.Action.REFRESH));
            return true;
        }
        int tabWidth = Math.max(72, (panel.width() - 36) / S2HubTab.values().length);
        for (S2HubTab candidate : S2HubTab.values()) {
            if (tabBounds(panel, candidate, tabWidth).contains(mouseX, mouseY)) {
                if (candidate == S2HubTab.TECHNOLOGY) {
                    PacketDistributor.sendToServer(new C2S_RequestTechnologyPacket());
                    return true;
                }
                tab = candidate;
                lastTab = candidate;
                scrollOffset = 0;
                return true;
            }
        }
        if ((!snapshot.member() && tab != S2HubTab.SIEGE) || tab == S2HubTab.OVERVIEW) {
            Rect content = contentBounds(panel);
            if (!snapshot.member()) {
                Rect create = new Rect(content.x() + 16,
                        content.y() + Math.min(118, content.height()) - 34, 138, 22);
                if (create.contains(mouseX, mouseY)) {
                    minecraft.setScreen(new NationCreateScreen(snapshot.revision()));
                    return true;
                }
                if (!snapshot.invitations().isEmpty() && pendingRequestId < 0) {
                    S2NationSnapshot.InvitationView invitation = snapshot.invitations().getFirst();
                    Rect invitationCard = invitationBounds(content);
                    if (invitationAcceptBounds(invitationCard).contains(mouseX, mouseY)) {
                        sendMembershipAction(C2S_NationMembershipPacket.Action.ACCEPT, invitation.nationId());
                        return true;
                    }
                    if (invitationDeclineBounds(invitationCard).contains(mouseX, mouseY)) {
                        sendMembershipAction(C2S_NationMembershipPacket.Action.DECLINE, invitation.nationId());
                        return true;
                    }
                }
            } else if (!isOwner() && pendingRequestId < 0
                    && memberLeaveBounds(content).contains(mouseX, mouseY)) {
                sendMembershipAction(C2S_NationMembershipPacket.Action.LEAVE, new UUID(0L, 0L));
                return true;
            }
            Rect preview = snapshot.member()
                    ? memberPreviewBounds(content)
                    : new Rect(content.x() + 162,
                    content.y() + Math.min(118, content.height()) - 34, 148, 22);
            if (preview.contains(mouseX, mouseY)) {
                minecraft.setScreen(new TerritoryCoreWizardScreen());
                return true;
            }
            if (snapshot.member() && treasuryBounds(content).contains(mouseX, mouseY)) {
                PacketDistributor.sendToServer(new C2S_NationTreasuryPacket(
                        C2S_NationTreasuryPacket.Action.OPEN, null));
                return true;
            }
            if (snapshot.member() && isOwner() && memberLeaveBounds(content).contains(mouseX, mouseY)) {
                minecraft.setScreen(new NationSettingsScreen(snapshot));
                return true;
            }
            if (snapshot.member() && canManageTerritory() && pendingRequestId < 0
                    && snapshot.vaultChangeCooldownTicks() <= 0L
                    && vaultBounds(content).contains(mouseX, mouseY)) {
                if (snapshot.vaultConfigured()) vaultChangeConfirmation = true;
                else sendSiegeAction(C2S_SiegeActionPacket.Action.SET_VAULT, new UUID(0L, 0L), 0L);
                return true;
            }
        }
        if (snapshot.member() && tab == S2HubTab.MEMBERS) {
            Rect content = contentBounds(panel);
            if (canManageMembers() && memberInviteBounds(content).contains(mouseX, mouseY)) {
                minecraft.setScreen(new NationInviteScreen(snapshot.revision(), snapshot.inviteCandidates()));
                return true;
            }
            if (canManageMembers() && memberApplicationsBounds(content).contains(mouseX, mouseY)) {
                PacketDistributor.sendToServer(new C2S_NationApplicationActionPacket(
                        snapshot.revision(), C2S_NationApplicationActionPacket.Action.OPEN, null));
                return true;
            }
            Rect list = memberListBounds(content);
            if (pendingRequestId < 0 && list.contains(mouseX, mouseY)) {
                for (int index = 0; index < snapshot.members().size(); index++) {
                    S2NationSnapshot.MemberView member = snapshot.members().get(index);
                    Rect card = new Rect(list.x(), list.y() + index * ROW_HEIGHT - scrollOffset,
                            list.width() - 8, ROW_HEIGHT - 5);
                    if (canAssignRole(member) && roleAssignBounds(card).contains(mouseX, mouseY)) {
                        minecraft.setScreen(new NationRoleAssignScreen(
                                snapshot.revision(), member, snapshot.roles()));
                        return true;
                    }
                    if (canKick(member) && kickBounds(card).contains(mouseX, mouseY)) {
                        kickTargetId = member.id();
                        kickTargetName = member.name();
                        return true;
                    }
                }
            }
        }
        if (snapshot.member() && tab == S2HubTab.ROLES) {
            Rect content = contentBounds(panel);
            if (canManageRoles() && roleCreateBounds(content).contains(mouseX, mouseY)) {
                minecraft.setScreen(new NationRoleEditorScreen(snapshot.revision(), null));
                return true;
            }
            Rect list = roleListBounds(content);
            if (canManageRoles() && list.contains(mouseX, mouseY)) {
                for (int index = 0; index < snapshot.roles().size(); index++) {
                    S2NationSnapshot.RoleView role = snapshot.roles().get(index);
                    Rect card = new Rect(list.x(), list.y() + index * ROW_HEIGHT - scrollOffset,
                            list.width() - 8, ROW_HEIGHT - 5);
                    if (canEditRole(role) && roleEditBounds(card).contains(mouseX, mouseY)) {
                        minecraft.setScreen(new NationRoleEditorScreen(snapshot.revision(), role));
                        return true;
                    }
                }
            }
        }
        if (snapshot.member() && tab == S2HubTab.DIPLOMACY && canManageDiplomacy()
                && pendingRequestId < 0) {
            Rect list = diplomacyListBounds(contentBounds(panel));
            if (list.contains(mouseX, mouseY)) {
                for (int index = 0; index < snapshot.diplomacy().size(); index++) {
                    S2NationSnapshot.DiplomacyView relation = snapshot.diplomacy().get(index);
                    Rect card = diplomacyCard(list, index);
                    C2S_NationDiplomacyPacket.Action action = diplomacyActionAt(
                            relation, card, mouseX, mouseY);
                    if (action != null) {
                        sendDiplomacyAction(action, relation.nationId());
                        return true;
                    }
                }
            }
        }
        if (tab == S2HubTab.SIEGE
                && (snapshot.member() || !snapshot.sieges().isEmpty())
                && pendingRequestId < 0) {
            Rect list = diplomacyListBounds(contentBounds(panel));
            if (list.contains(mouseX, mouseY)) {
                int row = 0;
                for (S2NationSnapshot.PeaceView peace : snapshot.peaceProposals()) {
                    Rect card = siegeCard(list, row++);
                    if (canManageDiplomacy() && peace.incoming()
                            && peaceRejectBounds(card).contains(mouseX, mouseY)) {
                        sendSiegeAction(C2S_SiegeActionPacket.Action.REJECT_PEACE, peace.id(), 0L);
                        return true;
                    }
                    if (canManageDiplomacy() && peaceAcceptBounds(card).contains(mouseX, mouseY)) {
                        sendSiegeAction(peace.incoming() ? C2S_SiegeActionPacket.Action.ACCEPT_PEACE
                                : C2S_SiegeActionPacket.Action.CANCEL_PEACE, peace.id(), 0L);
                        return true;
                    }
                }
                row += snapshot.truces().size();
                for (S2NationSnapshot.PrisonerView prisoner : snapshot.prisoners()) {
                    Rect card = siegeCard(list, row++);
                    if (canManageDiplomacy() && siegePeaceBounds(card).contains(mouseX, mouseY)) {
                        minecraft.setScreen(new PeaceProposalScreen(snapshot.revision(),
                                prisoner.opponentNationId(), prisoner.opponentName()));
                        return true;
                    }
                }
                for (S2NationSnapshot.SiegeView siege : snapshot.sieges()) {
                    Rect card = siegeCard(list, row++);
                    if (!siege.individualAttacker() && canManageDiplomacy()
                            && siegePeaceBounds(card).contains(mouseX, mouseY)) {
                        minecraft.setScreen(new PeaceProposalScreen(snapshot.revision(),
                                siege.opponentNationId(), siege.opponentName()));
                        return true;
                    }
                    if (canWithdraw(siege) && siegeSurrenderBounds(card).contains(mouseX, mouseY)) {
                        surrenderTargetId = siege.id();
                        surrenderTargetName = siege.opponentName();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Rect content = contentBounds(panelBounds());
        if (content.contains(mouseX, mouseY)
                && (snapshot.member() || tab == S2HubTab.SIEGE) && tab != S2HubTab.OVERVIEW) {
            int rows = rowCount();
            Rect viewport = listBounds(content);
            int rowHeight = tab == S2HubTab.SIEGE ? SIEGE_ROW_HEIGHT : ROW_HEIGHT;
            scrollOffset = MoveEarthUi.scroll(scrollOffset, scrollY, rowHeight,
                    rows * rowHeight, viewport.height());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void clampScroll() {
        int rows = rowCount();
        Rect content = contentBounds(panelBounds());
        int viewportHeight = listBounds(content).height();
        int rowHeight = tab == S2HubTab.SIEGE ? SIEGE_ROW_HEIGHT : ROW_HEIGHT;
        scrollOffset = Math.min(scrollOffset, Math.max(0, rows * rowHeight - viewportHeight));
    }

    private void sendMembershipAction(C2S_NationMembershipPacket.Action action, UUID targetId) {
        pendingRequestId = ++nextRequestId;
        PacketDistributor.sendToServer(new C2S_NationMembershipPacket(
                pendingRequestId, snapshot.revision(), action, targetId));
    }

    private boolean canManageMembers() {
        return hasNationPermission(S2Permission.MANAGE_MEMBERS);
    }

    private boolean isOwner() {
        return (snapshot.ownPermissionMask() & S2Permission.OWNER.mask()) != 0L;
    }

    private boolean canKick(S2NationSnapshot.MemberView member) {
        return canManageMembers() && minecraft != null && minecraft.player != null
                && !member.id().equals(minecraft.player.getUUID()) && !"owner".equals(member.roleId());
    }

    private boolean canManageRoles() {
        return hasNationPermission(S2Permission.MANAGE_ROLES);
    }

    private boolean canManageDiplomacy() {
        return hasNationPermission(S2Permission.MANAGE_DIPLOMACY);
    }

    private boolean canManageSiege() { return hasNationPermission(S2Permission.MANAGE_SIEGE); }
    private boolean canWithdraw(S2NationSnapshot.SiegeView siege) {
        return canManageSiege() || siege.individualAttacker() && siege.attacker();
    }
    private boolean canManageTerritory() { return hasNationPermission(S2Permission.MANAGE_TERRITORY); }

    private boolean hasNationPermission(S2Permission permission) {
        return snapshot.can(permission);
    }

    private boolean canAssignRole(S2NationSnapshot.MemberView member) {
        return canManageRoles() && !"owner".equals(member.roleId());
    }

    private boolean canEditRole(S2NationSnapshot.RoleView role) {
        return canManageRoles() && !"owner".equals(role.id()) && !"member".equals(role.id());
    }

    private Rect panelBounds() {
        int panelWidth = Math.min(PANEL_WIDTH, Math.max(260, width - 20));
        int panelHeight = Math.min(PANEL_HEIGHT, Math.max(220, height - 20));
        return new Rect((width - panelWidth) / 2, (height - panelHeight) / 2, panelWidth, panelHeight);
    }

    private static Rect closeBounds(Rect panel) {
        return new Rect(panel.right() - 28, panel.y() + 8, 20, 20);
    }

    private static Rect refreshBounds(Rect panel) {
        return new Rect(panel.right() - 122, panel.y() + 10, 84, 20);
    }

    private static Rect tabBounds(Rect panel, S2HubTab tab, int tabWidth) {
        return new Rect(panel.x() + 12 + tab.ordinal() * tabWidth, panel.y() + 48, tabWidth, 27);
    }

    private static Rect contentBounds(Rect panel) {
        return new Rect(panel.x() + 18, panel.y() + 88, panel.width() - 36, panel.height() - 104);
    }

    private static Rect memberPreviewBounds(Rect content) {
        return new Rect(content.right() - 148, content.bottom() - 23, 148, 22);
    }

    private static Rect treasuryBounds(Rect content) {
        return new Rect(content.right() - 306, content.bottom() - 23, 122, 22);
    }

    private static Rect vaultBounds(Rect content) {
        return new Rect(content.right() - 436, content.bottom() - 23, 122, 22);
    }

    private static Rect memberLeaveBounds(Rect content) {
        return new Rect(content.x(), content.bottom() - 23, 112, 22);
    }

    private static Rect invitationBounds(Rect content) {
        return new Rect(content.x(), content.y() + 128, content.width(), 58);
    }

    private static Rect invitationDeclineBounds(Rect card) {
        return new Rect(card.right() - 174, card.y() + 28, 76, 22);
    }

    private static Rect invitationAcceptBounds(Rect card) {
        return new Rect(card.right() - 90, card.y() + 28, 78, 22);
    }

    private static Rect memberInviteBounds(Rect content) {
        return new Rect(content.right() - 132, content.y(), 132, 22);
    }

    private static Rect memberApplicationsBounds(Rect content) {
        return new Rect(content.right() - 272, content.y(), 132, 22);
    }

    private static Rect memberListBounds(Rect content) {
        return new Rect(content.x(), content.y() + 30, content.width(), Math.max(0, content.height() - 30));
    }

    private static Rect kickBounds(Rect card) {
        return new Rect(card.right() - 62, card.y() + 8, 50, 22);
    }

    private static Rect roleAssignBounds(Rect card) {
        return new Rect(card.right() - 132, card.y() + 8, 62, 22);
    }

    private static Rect roleCreateBounds(Rect content) {
        return new Rect(content.right() - 120, content.y(), 120, 22);
    }

    private static Rect roleListBounds(Rect content) {
        return new Rect(content.x(), content.y() + 30, content.width(), Math.max(0, content.height() - 30));
    }

    private static Rect diplomacyListBounds(Rect content) {
        return new Rect(content.x(), content.y() + 24, content.width(), Math.max(0, content.height() - 24));
    }

    private Rect diplomacyCard(Rect list, int index) {
        return new Rect(list.x(), list.y() + index * ROW_HEIGHT - scrollOffset,
                list.width() - 8, ROW_HEIGHT - 5);
    }

    private Rect siegeCard(Rect list, int index) {
        return new Rect(list.x(), list.y() + index * SIEGE_ROW_HEIGHT - scrollOffset,
                list.width() - 8, SIEGE_ROW_HEIGHT - 5);
    }

    private static Rect siegePeaceBounds(Rect card) {
        return new Rect(card.right() - 190, card.y() + 34, 94, 20);
    }

    private static Rect siegeSurrenderBounds(Rect card) {
        return new Rect(card.right() - 88, card.y() + 34, 76, 20);
    }

    private static Rect peaceRejectBounds(Rect card) {
        return new Rect(card.right() - 178, card.y() + 32, 78, 20);
    }

    private static Rect peaceAcceptBounds(Rect card) {
        return new Rect(card.right() - 92, card.y() + 32, 80, 20);
    }

    private static Rect diplomacyPrimaryBounds(Rect card) {
        return new Rect(card.right() - 94, card.y() + 8, 82, 22);
    }

    private static Rect diplomacySecondaryBounds(Rect card) {
        return new Rect(card.right() - 184, card.y() + 8, 82, 22);
    }

    private static Rect roleEditBounds(Rect card) {
        return new Rect(card.right() - 70, card.y() + 8, 58, 22);
    }

    private Rect kickModalBounds() {
        return new Rect((width - 330) / 2, (height - 112) / 2, 330, 112);
    }

    private static Rect modalCancelBounds(Rect modal) {
        return new Rect(modal.right() - 184, modal.bottom() - 34, 82, 22);
    }

    private static Rect modalConfirmBounds(Rect modal) {
        return new Rect(modal.right() - 94, modal.bottom() - 34, 82, 22);
    }

    private static Component tabLabel(S2HubTab tab) {
        return Component.translatable(switch (tab) {
            case OVERVIEW -> "screen.moveearth_addtional.s2.tab.overview";
            case MEMBERS -> "screen.moveearth_addtional.s2.tab.members";
            case ROLES -> "screen.moveearth_addtional.s2.tab.roles";
            case DIPLOMACY -> "screen.moveearth_addtional.s2.tab.diplomacy";
            case SIEGE -> "screen.moveearth_addtional.s2.tab.siege";
            case TECHNOLOGY -> "screen.moveearth_addtional.s2.tab.technology";
        });
    }

    private int rowCount() {
        return switch (tab) {
            case MEMBERS -> snapshot.members().size();
            case ROLES -> snapshot.roles().size();
            case DIPLOMACY -> snapshot.diplomacy().size();
            case SIEGE -> siegeRowCount();
            case TECHNOLOGY -> 0;
            default -> 0;
        };
    }

    private int siegeRowCount() {
        return snapshot.sieges().size() + snapshot.peaceProposals().size()
                + snapshot.truces().size() + snapshot.prisoners().size();
    }

    private Rect listBounds(Rect content) {
        return switch (tab) {
            case MEMBERS -> memberListBounds(content);
            case ROLES -> roleListBounds(content);
            case DIPLOMACY -> diplomacyListBounds(content);
            case SIEGE -> diplomacyListBounds(content);
            default -> content;
        };
    }

    private void sendDiplomacyAction(C2S_NationDiplomacyPacket.Action action, UUID targetNationId) {
        pendingRequestId = ++nextRequestId;
        PacketDistributor.sendToServer(new C2S_NationDiplomacyPacket(
                pendingRequestId, snapshot.revision(), action, targetNationId));
    }

    private void sendSiegeAction(C2S_SiegeActionPacket.Action action, UUID targetId, long gold) {
        pendingRequestId = ++nextRequestId;
        PacketDistributor.sendToServer(new C2S_SiegeActionPacket(
                pendingRequestId, snapshot.revision(), action, targetId, gold));
    }

    private static C2S_NationDiplomacyPacket.Action diplomacyActionAt(
            S2NationSnapshot.DiplomacyView relation, Rect card, double mouseX, double mouseY) {
        boolean primary = diplomacyPrimaryBounds(card).contains(mouseX, mouseY);
        boolean secondary = diplomacySecondaryBounds(card).contains(mouseX, mouseY);
        return switch (relation.state()) {
            case NEUTRAL -> primary ? C2S_NationDiplomacyPacket.Action.REQUEST_ALLIANCE
                    : secondary ? C2S_NationDiplomacyPacket.Action.DECLARE_HOSTILE : null;
            case INCOMING_REQUEST -> primary ? C2S_NationDiplomacyPacket.Action.ACCEPT_ALLIANCE
                    : secondary ? C2S_NationDiplomacyPacket.Action.DECLINE_ALLIANCE : null;
            case OUTGOING_REQUEST -> primary ? C2S_NationDiplomacyPacket.Action.DECLARE_HOSTILE : null;
            case ALLIED -> primary ? C2S_NationDiplomacyPacket.Action.END_ALLIANCE : null;
            case HOSTILE -> primary && relation.hostileByViewer()
                    ? C2S_NationDiplomacyPacket.Action.SET_NEUTRAL : null;
        };
    }

    private static String diplomacyStateKey(S2NationSnapshot.DiplomacyState state) {
        return "screen.moveearth_addtional.diplomacy.state." + state.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String formatTicks(long ticks) {
        long seconds = Math.max(0L, (ticks + 19L) / 20L);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L);
    }

    private static Component lastSeenText(long lastSeenAt) {
        if (lastSeenAt <= 0L) {
            return Component.translatable("screen.moveearth_addtional.s2.last_seen_unknown");
        }
        String formatted = LAST_SEEN_FORMAT.format(
                Instant.ofEpochMilli(lastSeenAt).atZone(ZoneId.systemDefault()));
        return Component.translatable("screen.moveearth_addtional.s2.last_seen", formatted);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

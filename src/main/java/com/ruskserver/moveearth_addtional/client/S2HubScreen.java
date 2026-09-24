package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;
import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.network.C2S_NationMembershipPacket;
import com.ruskserver.moveearth_addtional.network.C2S_NationApplicationActionPacket;
import com.ruskserver.moveearth_addtional.network.C2S_NationDiplomacyPacket;
import com.ruskserver.moveearth_addtional.network.C2S_NationTreasuryPacket;
import com.ruskserver.moveearth_addtional.network.C2S_S2HubActionPacket;
import com.ruskserver.moveearth_addtional.network.C2S_SiegeActionPacket;
import com.ruskserver.moveearth_addtional.network.C2S_RequestRegionViewPacket;
import com.ruskserver.moveearth_addtional.network.C2S_RequestPrisonerScreenPacket;
import com.ruskserver.moveearth_addtional.network.C2S_RequestRecoveryDispatchPacket;
import com.ruskserver.moveearth_addtional.network.S2C_S2ActionResultPacket;
import com.ruskserver.moveearth_addtional.network.S2C_S2HubSnapshotPacket;
import com.ruskserver.moveearth_addtional.region.RegionMaterialNames;
import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import com.ruskserver.moveearth_addtional.s2.S2NationSnapshot;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

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

    /**
     * The regions around the player, fetched when the tab is chosen.
     *
     * <p>Empty until the reply lands, which is why the tab says it is loading
     * rather than that there are no regions. Those say opposite things, and the
     * first frame after a click would always have shown the wrong one.
     */
    private com.ruskserver.moveearth_addtional.region.RegionSnapshot regionView =
            com.ruskserver.moveearth_addtional.region.RegionSnapshot.none();

    /** False until the first reply, so "loading" and "nowhere" stay distinct. */
    private boolean regionViewReceived;

    private S2HubTab tab;
    private final S2HubNavigation navigation;
    private S2NationSnapshot snapshot;
    private int scrollOffset;
    private int pendingRequestId = -1;
    private int pendingTicks;
    private int invitationIndex;
    private Component toast;
    private int toastColor = SUCCESS;
    private int toastTicks;
    private UUID kickTargetId;
    private String kickTargetName = "";
    private UUID surrenderTargetId;
    private String surrenderTargetName = "";
    private boolean vaultChangeConfirmation;
    private boolean leaveConfirmation;
    private boolean onlineMembersOnly;
    private EditBox memberSearch;

    public S2HubScreen(S2C_S2HubSnapshotPacket packet) {
        super(Component.translatable("screen.moveearth_addtional.s2.title"));
        this.snapshot = packet.snapshot();
        this.tab = packet.tab() == null ? lastTab : packet.tab();
        this.navigation = new S2HubNavigation(this.tab);
        lastTab = this.tab;
    }

    public void update(S2C_S2HubSnapshotPacket packet) {
        this.snapshot = packet.snapshot();
        this.tab = navigation.networkTab();
        lastTab = tab;
        clampScroll();
        invitationIndex = Math.min(invitationIndex, Math.max(0, snapshot.invitations().size() - 1));
    }

    public void handleResult(S2C_S2ActionResultPacket packet) {
        if (pendingRequestId >= 0 && packet.requestId() != pendingRequestId) return;
        if (pendingRequestId < 0 && !packet.success()) return;
        pendingRequestId = -1;
        pendingTicks = 0;
        Component body = Component.translatable(packet.messageKey());
        toast = packet.success() ? MoveEarthMessage.success(body) : MoveEarthMessage.error(body);
        toastColor = packet.success() ? SUCCESS : DANGER;
        toastTicks = 60;
    }

    @Override
    public void tick() {
        if (toastTicks > 0) toastTicks--;
        if (pendingRequestId >= 0 && ++pendingTicks >= 200) {
            pendingRequestId = -1;
            pendingTicks = 0;
            toast = MoveEarthMessage.error(Component.translatable("screen.moveearth_addtional.s2.timeout"));
            toastColor = DANGER;
            toastTicks = 100;
        }
    }

    @Override
    protected void init() {
        Rect bounds = memberSearchBounds(hubLayout().content());
        memberSearch = new EditBox(font, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                Component.translatable("screen.moveearth_addtional.s2.members.search"));
        memberSearch.setHint(Component.translatable("screen.moveearth_addtional.s2.members.search"));
        memberSearch.setMaxLength(48);
        memberSearch.setResponder(ignored -> {
            scrollOffset = 0;
            navigation.saveScrollOffset(0);
        });
        memberSearch.setVisible(false);
        addRenderableWidget(memberSearch);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(graphics, width, height);
        S2HubLayout.Layout layout = hubLayout();
        Rect panel = layout.panel();
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
        if (snapshot.member()) {
            Rect settings = headerSettingsBounds(panel);
            drawButton(graphics, font, settings,
                    Component.translatable(isOwner()
                            ? "screen.moveearth_addtional.nation.settings.open"
                            : "screen.moveearth_addtional.nation.leave"), isOwner() ? GOLD : DANGER,
                    pendingRequestId < 0 && settings.contains(mouseX, mouseY), pendingRequestId < 0);
        }

        for (S2HubNavigation.Section candidate : S2HubNavigation.Section.values()) {
            Rect bounds = sectionBounds(layout, candidate);
            drawTab(graphics, font, bounds, sectionLabel(candidate), candidate == navigation.section(),
                    bounds.contains(mouseX, mouseY));
        }
        List<S2HubNavigation.Page> pages = navigation.pages();
        if (pages.size() > 1) for (int index = 0; index < pages.size(); index++) {
            S2HubNavigation.Page candidate = pages.get(index);
            Rect bounds = S2HubLayout.item(layout.subpages(), index, pages.size());
            drawTab(graphics, font, bounds, pageLabel(candidate), candidate == navigation.page(),
                    bounds.contains(mouseX, mouseY));
        }

        Rect content = layout.content();
        // Before the membership check, not after it. A region describes the
        // ground rather than a nation, and the player who most needs to know
        // what is under their feet is the one still deciding where to settle.
        if (navigation.page() == S2HubNavigation.Page.REGION) {
            drawRegions(graphics, content, mouseX, mouseY);
        } else if (!snapshot.member() && navigation.section() != S2HubNavigation.Section.WAR) {
            drawUnaffiliated(graphics, content, mouseX, mouseY);
        } else switch (navigation.page()) {
            case HOME -> drawOverview(graphics, content, mouseX, mouseY);
            case TERRITORY -> drawTerritory(graphics, content, mouseX, mouseY);
            case FINANCE -> drawFinance(graphics, content, mouseX, mouseY);
            case MEMBERS -> drawMembers(graphics, content, mouseX, mouseY);
            case ROLES -> drawRoles(graphics, content, mouseX, mouseY);
            case RELATIONS -> drawDiplomacy(graphics, content, mouseX, mouseY);
            case PEACE -> drawConflictRows(graphics, content, mouseX, mouseY, true, true, false, false);
            case SIEGES -> drawConflictRows(graphics, content, mouseX, mouseY, false, false, false, true);
            case PRISONERS -> drawConflictRows(graphics, content, mouseX, mouseY, false, false, true, false);
            case RECOVERY -> drawRecoveryEntry(graphics, content, mouseX, mouseY);
            case REGION -> drawRegions(graphics, content, mouseX, mouseY);
        }

        if (memberSearch != null) {
            Rect search = memberSearchBounds(content);
            memberSearch.setX(search.x());
            memberSearch.setY(search.y());
            memberSearch.setWidth(search.width());
            memberSearch.setVisible(snapshot.member()
                    && navigation.page() == S2HubNavigation.Page.MEMBERS
                    && content.width() >= 520
                    && kickTargetId == null && surrenderTargetId == null
                    && !vaultChangeConfirmation && !leaveConfirmation);
        }
        super.render(graphics, mouseX, mouseY, partialTick);

        if (kickTargetId != null) drawKickConfirmation(graphics, mouseX, mouseY);
        if (surrenderTargetId != null) drawSurrenderConfirmation(graphics, mouseX, mouseY);
        if (vaultChangeConfirmation) drawVaultConfirmation(graphics, mouseX, mouseY);
        if (leaveConfirmation) drawLeaveConfirmation(graphics, mouseX, mouseY);
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
            S2NationSnapshot.InvitationView invitation = snapshot.invitations().get(invitationIndex);
            Rect invitationCard = invitationBounds(content);
            drawCard(graphics, invitationCard, GOLD, false, invitationCard.contains(mouseX, mouseY));
            String nation = invitation.nationTag().isBlank() ? invitation.nationName()
                    : "[" + invitation.nationTag() + "] " + invitation.nationName();
            graphics.drawString(font, Component.translatable(
                            "screen.moveearth_addtional.nation.invitation", nation),
                    invitationCard.x() + 13, invitationCard.y() + 10, GOLD, false);
            if (snapshot.invitations().size() > 1) {
                Component count = Component.literal((invitationIndex + 1) + " / " + snapshot.invitations().size());
                graphics.drawString(font, count, invitationCard.right() - 79, invitationCard.y() + 9, MUTED, false);
                drawButton(graphics, font, invitationPreviousBounds(invitationCard), Component.literal("‹"), ACCENT,
                        invitationPreviousBounds(invitationCard).contains(mouseX, mouseY), true);
                drawButton(graphics, font, invitationNextBounds(invitationCard), Component.literal("›"), ACCENT,
                        invitationNextBounds(invitationCard).contains(mouseX, mouseY), true);
            }
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
        if (!snapshot.member()) {
            drawUnaffiliated(graphics, content, mouseX, mouseY);
            return;
        }
        String nationTitle = snapshot.nationTag().isBlank()
                ? snapshot.nationName() : "[" + snapshot.nationTag() + "] " + snapshot.nationName();
        graphics.drawString(font, font.plainSubstrByWidth(nationTitle, Math.max(40, content.width() - 120)),
                content.x(), content.y(), ACCENT, false);
        Component roleText = Component.translatable("screen.moveearth_addtional.s2.role", snapshot.roleName());
        graphics.drawString(font, font.plainSubstrByWidth(roleText.getString(), Math.max(40, content.width() / 2)),
                content.x(), content.y() + 15, MUTED, false);
        Component ownerText = Component.translatable(
                "screen.moveearth_addtional.s2.owner", snapshot.ownerName());
        String owner = font.plainSubstrByWidth(ownerText.getString(), Math.max(40, content.width() / 2));
        graphics.drawString(font, owner,
                content.right() - font.width(owner), content.y() + 15, GOLD, false);

        if (content.height() >= 210) {
            int gap = 6;
            int cardWidth = (content.width() - gap) / 2;
            drawMetric(graphics, new Rect(content.x(), content.y() + 34, cardWidth, 44),
                    "screen.moveearth_addtional.s2.members", snapshot.onlineMembers() + " / " + snapshot.totalMembers());
            drawMetric(graphics, new Rect(content.x() + cardWidth + gap, content.y() + 34, cardWidth, 44),
                    "screen.moveearth_addtional.s2.territory", Integer.toString(snapshot.territoryChunks()));
            drawMetric(graphics, new Rect(content.x(), content.y() + 84, cardWidth, 44),
                    "screen.moveearth_addtional.s2.cores", Integer.toString(snapshot.activeCores()));
            drawMetric(graphics, new Rect(content.x() + cardWidth + gap, content.y() + 84, cardWidth, 44),
                    "screen.moveearth_addtional.s2.upkeep", Component.translatable(
                            "screen.moveearth_addtional.s2.upkeep_value", snapshot.upkeep()).getString());
        }
        if (content.height() >= 100) {
            Rect attention = homeAttentionBounds(content);
            int attentionColor = !snapshot.peaceProposals().isEmpty() || !snapshot.sieges().isEmpty()
                    ? DANGER : snapshot.invitations().isEmpty() ? SUCCESS : GOLD;
            drawCard(graphics, attention, attentionColor, false, attention.contains(mouseX, mouseY));
            graphics.drawString(font, Component.translatable("screen.moveearth_addtional.s2.attention"),
                    attention.x() + 13, attention.y() + 7, MUTED, false);
            Component attentionText = !snapshot.peaceProposals().isEmpty()
                    ? Component.translatable("screen.moveearth_addtional.s2.attention.peace", snapshot.peaceProposals().size())
                    : !snapshot.sieges().isEmpty()
                    ? Component.translatable("screen.moveearth_addtional.s2.attention.siege", snapshot.sieges().size())
                    : Component.translatable("screen.moveearth_addtional.s2.attention.none");
            graphics.drawString(font, attentionText, attention.x() + 13, attention.y() + 21, TEXT, false);
        }
        Rect territory = homeQuickBounds(content, 0);
        Rect finance = homeQuickBounds(content, 1);
        Rect members = homeQuickBounds(content, 2);
        drawButton(graphics, font, territory, pageLabel(S2HubNavigation.Page.TERRITORY), SUCCESS,
                territory.contains(mouseX, mouseY), true);
        drawButton(graphics, font, finance, pageLabel(S2HubNavigation.Page.FINANCE), GOLD,
                finance.contains(mouseX, mouseY), true);
        drawButton(graphics, font, members, pageLabel(S2HubNavigation.Page.MEMBERS), ACCENT,
                members.contains(mouseX, mouseY), true);
    }

    private void drawMetric(GuiGraphics graphics, Rect bounds, String labelKey, String value) {
        drawCard(graphics, bounds, ACCENT, false, false);
        graphics.drawString(font, Component.translatable(labelKey), bounds.x() + 13, bounds.y() + 10, MUTED, false);
        graphics.drawString(font, value, bounds.x() + 13, bounds.y() + 25, TEXT, false);
    }

    private void drawTerritory(GuiGraphics graphics, Rect content, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.s2.domestic.territory.detail"),
                content.x(), content.y() + 2, MUTED, false);
        if (content.height() >= 100) {
            int gap = 8;
            int cardWidth = (content.width() - gap) / 2;
            drawMetric(graphics, new Rect(content.x(), content.y() + 24, cardWidth, 58),
                    "screen.moveearth_addtional.s2.territory", Integer.toString(snapshot.territoryChunks()));
            drawMetric(graphics, new Rect(content.x() + cardWidth + gap, content.y() + 24, cardWidth, 58),
                    "screen.moveearth_addtional.s2.cores", Integer.toString(snapshot.activeCores()));
        }
        Rect preview = territoryPreviewBounds(content);
        drawButton(graphics, font, preview,
                Component.translatable("screen.moveearth_addtional.territory.preview"), SUCCESS,
                preview.contains(mouseX, mouseY), true);
    }

    private void drawFinance(GuiGraphics graphics, Rect content, int mouseX, int mouseY) {
        if (content.height() >= 100) {
            drawMetric(graphics, new Rect(content.x(), content.y(), content.width(), 58),
                    "screen.moveearth_addtional.s2.upkeep", Component.translatable(
                            "screen.moveearth_addtional.s2.upkeep_value", snapshot.upkeep()).getString());
        }
        Rect treasury = financeTreasuryBounds(content);
        drawButton(graphics, font, treasury,
                Component.translatable("screen.moveearth_addtional.treasury.open"), GOLD,
                treasury.contains(mouseX, mouseY), true);
        Rect vault = financeVaultBounds(content);
        Component vaultLabel = snapshot.vaultConfigured()
                ? snapshot.vaultChangeCooldownTicks() > 0L
                ? Component.translatable("screen.moveearth_addtional.vault.cooldown",
                formatTicks(snapshot.vaultChangeCooldownTicks()))
                : Component.translatable("screen.moveearth_addtional.vault.current",
                snapshot.vaultChunkX(), snapshot.vaultChunkZ())
                : Component.translatable("screen.moveearth_addtional.vault.set");
        boolean enabled = canManageTerritory() && pendingRequestId < 0
                && snapshot.vaultChangeCooldownTicks() <= 0L;
        drawButton(graphics, font, vault, vaultLabel, ACCENT,
                enabled && vault.contains(mouseX, mouseY), enabled);
    }

    private void drawRecoveryEntry(GuiGraphics graphics, Rect content, int mouseX, int mouseY) {
        Rect card = new Rect(content.x(), content.y() + 12, content.width(), Math.min(112, content.height()));
        drawCard(graphics, card, SUCCESS, false, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.recovery.title"),
                card.x() + 14, card.y() + 14, SUCCESS, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.recovery.subtitle"),
                card.x() + 14, card.y() + 34, MUTED, false);
        Rect open = recoveryEntryBounds(content);
        drawButton(graphics, font, open,
                Component.translatable("screen.moveearth_addtional.recovery.open"), SUCCESS,
                open.contains(mouseX, mouseY), true);
    }

    private void drawMembers(GuiGraphics graphics, Rect content, int mouseX, int mouseY) {
        Rect filter = memberFilterBounds(content);
        drawButton(graphics, font, filter,
                Component.translatable(onlineMembersOnly
                        ? "screen.moveearth_addtional.s2.members.filter.online"
                        : "screen.moveearth_addtional.s2.members.filter.all"), ACCENT,
                filter.contains(mouseX, mouseY), true);
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
        drawRows(graphics, memberListBounds(content), visibleMembers(), mouseX, mouseY, true);
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

    private void drawRegions(GuiGraphics graphics, Rect content, int mouseX, int mouseY) {
        Rect card = new Rect(content.x(), content.y() + 16, content.width() - 8, 96);
        if (!regionViewReceived) {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.moveearth_addtional.region.waiting"),
                    content.x() + content.width() / 2, content.y() + 42, MUTED);
            return;
        }
        if (!regionView.known()) {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.moveearth_addtional.region.none"),
                    content.x() + content.width() / 2, content.y() + 42, MUTED);
            return;
        }
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.region.subtitle"),
                content.x(), content.y() + 4, MUTED, false);
        drawCard(graphics, card, SUCCESS, true, false);

        int line = card.y() + 10;
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.region.here",
                        Component.translatable("message.moveearth_addtional.region.name", regionView.id())),
                card.x() + 13, line, SUCCESS, false);

        line += 18;
        graphics.drawString(font, regionView.exclusives().isEmpty()
                        ? Component.translatable("screen.moveearth_addtional.region.no_exclusive")
                        : Component.translatable("screen.moveearth_addtional.region.exclusive",
                                RegionMaterialNames.list(regionView.exclusives())),
                card.x() + 13, line, regionView.exclusives().isEmpty() ? MUTED : TEXT, false);

        if (!regionView.elsewhere().isEmpty()) {
            line += 13;
            // The question this tab answers is why a resource is not here, so
            // the absent ones are named rather than left to be inferred from a
            // list of what is.
            graphics.drawString(font, Component.translatable(
                            "screen.moveearth_addtional.region.elsewhere",
                            RegionMaterialNames.list(regionView.elsewhere())),
                    card.x() + 13, line, MUTED, false);
            if (regionView.traceShare() > 0.0) {
                line += 11;
                graphics.drawString(font, Component.translatable(
                                "screen.moveearth_addtional.region.trace",
                                Math.max(1, Math.round(100.0 * regionView.traceShare()))),
                        card.x() + 13, line, MUTED, false);
            }
        }

        if (!regionView.specialty().isBlank() || !regionView.shortage().isBlank()) {
            line += 15;
            graphics.drawString(font, Component.translatable(
                            "screen.moveearth_addtional.region.common",
                            RegionMaterialNames.of(regionView.specialty()),
                            RegionMaterialNames.of(regionView.shortage())),
                    card.x() + 13, line, TEXT, false);
        }
    }

    /** Takes the reply to the request the tab sent when it was chosen. */
    public void updateRegions(com.ruskserver.moveearth_addtional.region.RegionSnapshot updated) {
        this.regionView = updated;
        this.regionViewReceived = true;
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

    private void drawConflictRows(GuiGraphics graphics, Rect content, int mouseX, int mouseY,
                                  boolean includePeace, boolean includeTruces,
                                  boolean includePrisoners, boolean includeSieges) {
        graphics.drawString(font, Component.translatable(includePeace
                        ? "screen.moveearth_addtional.s2.diplomacy.peace.detail"
                        : includePrisoners ? "screen.moveearth_addtional.s2.war.prisoners.detail"
                        : "screen.moveearth_addtional.siege.detail"),
                content.x(), content.y() + 4, MUTED, false);
        if (includePrisoners) {
            Rect prisoners = prisonerManagementBounds(content);
            drawButton(graphics, font, prisoners,
                    Component.translatable("screen.moveearth_addtional.prisoner.open"), GOLD,
                    prisoners.contains(mouseX, mouseY), true);
        }
        Rect list = diplomacyListBounds(content);
        if ((!includeSieges || snapshot.sieges().isEmpty())
                && (!includePeace || snapshot.peaceProposals().isEmpty())
                && (!includeTruces || snapshot.truces().isEmpty())
                && (!includePrisoners || snapshot.prisoners().isEmpty())) {
            String emptyKey = includePeace ? "screen.moveearth_addtional.s2.diplomacy.peace.empty"
                    : includePrisoners ? "screen.moveearth_addtional.prisoner.list.empty"
                    : "screen.moveearth_addtional.siege.empty";
            graphics.drawCenteredString(font, Component.translatable(emptyKey),
                    list.x() + list.width() / 2, list.y() + 42, MUTED);
            return;
        }
        graphics.enableScissor(list.x(), list.y(), list.right(), list.bottom());
        int row = 0;
        if (includePeace) {
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
        }
        if (includeTruces) {
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
        }
        if (includePrisoners) {
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
        }
        if (includeSieges) {
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
        }
        graphics.disableScissor();
        drawScrollbar(graphics, new Rect(list.right() - 4, list.y(), 4, list.height()),
                list.height(), conflictRowCount() * SIEGE_ROW_HEIGHT, scrollOffset);
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

    private void drawLeaveConfirmation(GuiGraphics graphics, int mouseX, int mouseY) {
        drawModalBackdrop(graphics, width, height);
        Rect modal = kickModalBounds();
        drawPanel(graphics, modal);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.nation.leave_title"),
                modal.x() + 16, modal.y() + 15, DANGER, false);
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(
                        "screen.moveearth_addtional.nation.leave_detail", snapshot.nationName()).getString(),
                modal.width() - 32), modal.x() + 16, modal.y() + 38, TEXT, false);
        Rect cancel = modalCancelBounds(modal);
        Rect confirm = modalConfirmBounds(modal);
        drawButton(graphics, font, cancel,
                Component.translatable("screen.moveearth_addtional.nation.cancel"), MUTED,
                cancel.contains(mouseX, mouseY), true);
        drawButton(graphics, font, confirm,
                Component.translatable("screen.moveearth_addtional.nation.leave"), DANGER,
                confirm.contains(mouseX, mouseY), true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (leaveConfirmation) {
            Rect modal = kickModalBounds();
            if (modalCancelBounds(modal).contains(mouseX, mouseY)) {
                leaveConfirmation = false;
            } else if (modalConfirmBounds(modal).contains(mouseX, mouseY) && pendingRequestId < 0) {
                sendMembershipAction(C2S_NationMembershipPacket.Action.LEAVE, new UUID(0L, 0L));
                leaveConfirmation = false;
            }
            return true;
        }
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
        S2HubLayout.Layout layout = hubLayout();
        Rect panel = layout.panel();
        if (closeBounds(panel).contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (pendingRequestId < 0 && refreshBounds(panel).contains(mouseX, mouseY)) {
            int requestId = ++nextRequestId;
            pendingRequestId = requestId;
            pendingTicks = 0;
            PacketDistributor.sendToServer(new C2S_S2HubActionPacket(
                    requestId, snapshot.revision(), navigation.networkTab(), C2S_S2HubActionPacket.Action.REFRESH));
            return true;
        }
        if (snapshot.member() && pendingRequestId < 0 && headerSettingsBounds(panel).contains(mouseX, mouseY)) {
            if (isOwner()) minecraft.setScreen(new NationSettingsScreen(snapshot));
            else leaveConfirmation = true;
            return true;
        }
        for (S2HubNavigation.Section candidate : S2HubNavigation.Section.values()) {
            if (sectionBounds(layout, candidate).contains(mouseX, mouseY)) {
                selectSection(candidate);
                return true;
            }
        }
        List<S2HubNavigation.Page> pages = navigation.pages();
        if (pages.size() > 1) for (int index = 0; index < pages.size(); index++) {
            if (S2HubLayout.item(layout.subpages(), index, pages.size()).contains(mouseX, mouseY)) {
                selectPage(pages.get(index));
                return true;
            }
        }
        Rect content = layout.content();
        if (navigation.page() != S2HubNavigation.Page.REGION
                && (!snapshot.member() && navigation.section() != S2HubNavigation.Section.WAR)) {
            if (!snapshot.member()) {
                Rect create = new Rect(content.x() + 16,
                        content.y() + Math.min(118, content.height()) - 34, 138, 22);
                if (create.contains(mouseX, mouseY)) {
                    minecraft.setScreen(new NationCreateScreen(snapshot.revision()));
                    return true;
                }
                if (!snapshot.invitations().isEmpty() && pendingRequestId < 0) {
                    S2NationSnapshot.InvitationView invitation = snapshot.invitations().get(invitationIndex);
                    Rect invitationCard = invitationBounds(content);
                    if (snapshot.invitations().size() > 1
                            && invitationPreviousBounds(invitationCard).contains(mouseX, mouseY)) {
                        invitationIndex = Math.floorMod(invitationIndex - 1, snapshot.invitations().size());
                        return true;
                    }
                    if (snapshot.invitations().size() > 1
                            && invitationNextBounds(invitationCard).contains(mouseX, mouseY)) {
                        invitationIndex = (invitationIndex + 1) % snapshot.invitations().size();
                        return true;
                    }
                    if (invitationAcceptBounds(invitationCard).contains(mouseX, mouseY)) {
                        sendMembershipAction(C2S_NationMembershipPacket.Action.ACCEPT, invitation.nationId());
                        return true;
                    }
                    if (invitationDeclineBounds(invitationCard).contains(mouseX, mouseY)) {
                        sendMembershipAction(C2S_NationMembershipPacket.Action.DECLINE, invitation.nationId());
                        return true;
                    }
                }
            }
            Rect preview = new Rect(content.x() + 162,
                    content.y() + Math.min(118, content.height()) - 34, 148, 22);
            if (preview.contains(mouseX, mouseY)) {
                minecraft.setScreen(new TerritoryCoreWizardScreen());
                return true;
            }
        }
        if (snapshot.member() && navigation.page() == S2HubNavigation.Page.HOME) {
            for (int index = 0; index < 3; index++) if (homeQuickBounds(content, index).contains(mouseX, mouseY)) {
                selectPage(index == 0 ? S2HubNavigation.Page.TERRITORY
                        : index == 1 ? S2HubNavigation.Page.FINANCE : S2HubNavigation.Page.MEMBERS);
                return true;
            }
            if (content.height() >= 100 && homeAttentionBounds(content).contains(mouseX, mouseY)) {
                selectPage(!snapshot.peaceProposals().isEmpty()
                        ? S2HubNavigation.Page.PEACE : S2HubNavigation.Page.SIEGES);
                return true;
            }
        }
        if (snapshot.member() && navigation.page() == S2HubNavigation.Page.TERRITORY
                && territoryPreviewBounds(content).contains(mouseX, mouseY)) {
            minecraft.setScreen(new TerritoryCoreWizardScreen());
            return true;
        }
        if (snapshot.member() && navigation.page() == S2HubNavigation.Page.FINANCE) {
            if (financeTreasuryBounds(content).contains(mouseX, mouseY)) {
                PacketDistributor.sendToServer(new C2S_NationTreasuryPacket(
                        C2S_NationTreasuryPacket.Action.OPEN, null));
                return true;
            }
            if (canManageTerritory() && pendingRequestId < 0
                    && snapshot.vaultChangeCooldownTicks() <= 0L
                    && financeVaultBounds(content).contains(mouseX, mouseY)) {
                if (snapshot.vaultConfigured()) vaultChangeConfirmation = true;
                else sendSiegeAction(C2S_SiegeActionPacket.Action.SET_VAULT, new UUID(0L, 0L), 0L);
                return true;
            }
        }
        if (navigation.page() == S2HubNavigation.Page.RECOVERY
                && recoveryEntryBounds(content).contains(mouseX, mouseY)) {
            PacketDistributor.sendToServer(new C2S_RequestRecoveryDispatchPacket(true));
            return true;
        }
        if (snapshot.member() && navigation.page() == S2HubNavigation.Page.MEMBERS) {
            if (memberFilterBounds(content).contains(mouseX, mouseY)) {
                onlineMembersOnly = !onlineMembersOnly;
                scrollOffset = 0;
                navigation.saveScrollOffset(0);
                return true;
            }
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
                List<S2NationSnapshot.MemberView> visible = visibleMembers();
                for (int index = 0; index < visible.size(); index++) {
                    S2NationSnapshot.MemberView member = visible.get(index);
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
        if (snapshot.member() && navigation.page() == S2HubNavigation.Page.ROLES) {
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
        if (snapshot.member() && navigation.page() == S2HubNavigation.Page.RELATIONS && canManageDiplomacy()
                && pendingRequestId < 0) {
            Rect list = diplomacyListBounds(content);
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
        if ((navigation.page() == S2HubNavigation.Page.PEACE
                || navigation.page() == S2HubNavigation.Page.SIEGES
                || navigation.page() == S2HubNavigation.Page.PRISONERS)
                && (snapshot.member() || !snapshot.sieges().isEmpty() || !snapshot.prisoners().isEmpty())
                && pendingRequestId < 0) {
            if (navigation.page() == S2HubNavigation.Page.PRISONERS
                    && prisonerManagementBounds(content).contains(mouseX, mouseY)) {
                PacketDistributor.sendToServer(new C2S_RequestPrisonerScreenPacket(null));
                return true;
            }
            Rect list = diplomacyListBounds(content);
            if (list.contains(mouseX, mouseY)) {
                int row = 0;
                if (navigation.page() == S2HubNavigation.Page.PEACE) {
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
                }
                if (navigation.page() == S2HubNavigation.Page.PRISONERS) {
                for (S2NationSnapshot.PrisonerView prisoner : snapshot.prisoners()) {
                    Rect card = siegeCard(list, row++);
                    if (canManageDiplomacy() && siegePeaceBounds(card).contains(mouseX, mouseY)) {
                        minecraft.setScreen(new PeaceProposalScreen(snapshot.revision(),
                                prisoner.opponentNationId(), prisoner.opponentName()));
                        return true;
                    }
                }
                }
                if (navigation.page() == S2HubNavigation.Page.SIEGES) {
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
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Rect content = hubLayout().content();
        if (content.contains(mouseX, mouseY)
                && (snapshot.member() || navigation.section() == S2HubNavigation.Section.WAR)
                && rowCount() > 0) {
            int rows = rowCount();
            Rect viewport = listBounds(content);
            int rowHeight = isConflictPage() ? SIEGE_ROW_HEIGHT : ROW_HEIGHT;
            scrollOffset = MoveEarthUi.scroll(scrollOffset, scrollY, rowHeight,
                    rows * rowHeight, viewport.height());
            navigation.saveScrollOffset(scrollOffset);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void clampScroll() {
        int rows = rowCount();
        Rect content = hubLayout().content();
        int viewportHeight = listBounds(content).height();
        int rowHeight = isConflictPage() ? SIEGE_ROW_HEIGHT : ROW_HEIGHT;
        scrollOffset = Math.min(scrollOffset, Math.max(0, rows * rowHeight - viewportHeight));
        navigation.saveScrollOffset(scrollOffset);
    }

    private void sendMembershipAction(C2S_NationMembershipPacket.Action action, UUID targetId) {
        pendingRequestId = ++nextRequestId;
        pendingTicks = 0;
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

    private S2HubLayout.Layout hubLayout() {
        return S2HubLayout.calculate(width, height, navigation.pages().size() > 1);
    }

    private static Rect closeBounds(Rect panel) {
        return new Rect(panel.right() - 28, panel.y() + 8, 20, 20);
    }

    private static Rect refreshBounds(Rect panel) {
        return new Rect(panel.right() - 122, panel.y() + 10, 84, 20);
    }

    private static Rect headerSettingsBounds(Rect panel) {
        return new Rect(panel.right() - 182, panel.y() + 10, 52, 20);
    }

    private static Rect sectionBounds(S2HubLayout.Layout layout, S2HubNavigation.Section section) {
        return S2HubLayout.item(layout.sections(), section.ordinal(), S2HubNavigation.Section.values().length);
    }

    private static Rect homeAttentionBounds(Rect content) {
        int y = content.height() < 210 ? content.y() + 38 : Math.min(content.bottom() - 53, content.y() + 136);
        return new Rect(content.x(), y, content.width(), 42);
    }

    private static Rect homeQuickBounds(Rect content, int index) {
        Rect row = new Rect(content.x(), content.bottom() - 23, content.width(), 22);
        Rect raw = S2HubLayout.item(row, index, 3);
        int inset = index == 0 ? 0 : 3;
        int rightInset = index == 2 ? 0 : 3;
        return new Rect(raw.x() + inset, raw.y(), Math.max(1, raw.width() - inset - rightInset), raw.height());
    }

    private static Rect territoryPreviewBounds(Rect content) {
        return new Rect(content.x(), Math.min(content.bottom() - 23, content.y() + 92), content.width(), 22);
    }

    private static Rect financeTreasuryBounds(Rect content) {
        if (content.height() < 100) {
            Rect raw = S2HubLayout.item(new Rect(content.x(), content.bottom() - 23, content.width(), 22), 0, 2);
            return insetAction(raw, 0, 3);
        }
        return new Rect(content.x(), content.y() + 68, content.width(), 22);
    }

    private static Rect financeVaultBounds(Rect content) {
        if (content.height() < 100) {
            Rect raw = S2HubLayout.item(new Rect(content.x(), content.bottom() - 23, content.width(), 22), 1, 2);
            return insetAction(raw, 3, 0);
        }
        return new Rect(content.x(), content.y() + 98, content.width(), 22);
    }

    private static Rect recoveryEntryBounds(Rect content) {
        return new Rect(content.x() + 14, Math.min(content.bottom() - 30, content.y() + 78),
                Math.max(1, content.width() - 28), 22);
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

    private static Rect invitationPreviousBounds(Rect card) {
        return new Rect(card.right() - 48, card.y() + 4, 18, 18);
    }

    private static Rect invitationNextBounds(Rect card) {
        return new Rect(card.right() - 26, card.y() + 4, 18, 18);
    }

    private static Rect memberInviteBounds(Rect content) {
        if (content.width() >= 520) return memberActionSlice(content, 72, 100, 2, 0);
        return insetAction(S2HubLayout.item(new Rect(content.x(), content.y(), content.width(), 22), 2, 3), 2, 3);
    }

    private static Rect memberApplicationsBounds(Rect content) {
        if (content.width() >= 520) return memberActionSlice(content, 47, 72, 2, 2);
        return insetAction(S2HubLayout.item(new Rect(content.x(), content.y(), content.width(), 22), 1, 3), 2, 2);
    }

    private static Rect memberFilterBounds(Rect content) {
        if (content.width() >= 520) return memberActionSlice(content, 32, 47, 2, 2);
        return insetAction(S2HubLayout.item(new Rect(content.x(), content.y(), content.width(), 22), 0, 3), 3, 2);
    }

    private static Rect memberSearchBounds(Rect content) {
        return memberActionSlice(content, 0, 32, 0, 2);
    }

    private static Rect memberActionSlice(Rect content, int startPercent, int endPercent,
                                          int leftInset, int rightInset) {
        int left = content.x() + content.width() * startPercent / 100;
        int right = content.x() + content.width() * endPercent / 100;
        return new Rect(left + leftInset, content.y(), Math.max(1, right - left - leftInset - rightInset), 22);
    }

    private static Rect insetAction(Rect bounds, int left, int right) {
        return new Rect(bounds.x() + left, bounds.y(), Math.max(1, bounds.width() - left - right), bounds.height());
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

    private static Rect prisonerManagementBounds(Rect content) {
        return new Rect(content.right() - 136, content.y(), 136, 20);
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

    private static Component sectionLabel(S2HubNavigation.Section section) {
        return Component.translatable("screen.moveearth_addtional.s2.section."
                + section.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static Component pageLabel(S2HubNavigation.Page page) {
        return Component.translatable("screen.moveearth_addtional.s2.page."
                + page.name().toLowerCase(java.util.Locale.ROOT));
    }

    private int rowCount() {
        return switch (navigation.page()) {
            case MEMBERS -> visibleMembers().size();
            case ROLES -> snapshot.roles().size();
            case RELATIONS -> snapshot.diplomacy().size();
            case PEACE -> snapshot.peaceProposals().size() + snapshot.truces().size();
            case SIEGES -> snapshot.sieges().size();
            case PRISONERS -> snapshot.prisoners().size();
            default -> 0;
        };
    }

    private int conflictRowCount() {
        return rowCount();
    }

    private boolean isConflictPage() {
        return navigation.page() == S2HubNavigation.Page.PEACE
                || navigation.page() == S2HubNavigation.Page.SIEGES
                || navigation.page() == S2HubNavigation.Page.PRISONERS;
    }

    private List<S2NationSnapshot.MemberView> visibleMembers() {
        String query = memberSearch == null ? "" : memberSearch.getValue().strip().toLowerCase(java.util.Locale.ROOT);
        return snapshot.members().stream()
                .filter(member -> !onlineMembersOnly || member.online())
                .filter(member -> query.isEmpty()
                        || member.name().toLowerCase(java.util.Locale.ROOT).contains(query)
                        || member.roleName().toLowerCase(java.util.Locale.ROOT).contains(query))
                .toList();
    }

    private Rect listBounds(Rect content) {
        return switch (navigation.page()) {
            case MEMBERS -> memberListBounds(content);
            case ROLES -> roleListBounds(content);
            case RELATIONS, PEACE, SIEGES, PRISONERS -> diplomacyListBounds(content);
            default -> content;
        };
    }

    private void selectSection(S2HubNavigation.Section section) {
        navigation.saveScrollOffset(scrollOffset);
        navigation.selectSection(section);
        scrollOffset = navigation.scrollOffset();
        tab = navigation.networkTab();
        lastTab = tab;
        if (section == S2HubNavigation.Section.REGION) {
            regionViewReceived = false;
            PacketDistributor.sendToServer(new C2S_RequestRegionViewPacket());
        }
        clampScroll();
    }

    private void selectPage(S2HubNavigation.Page page) {
        navigation.saveScrollOffset(scrollOffset);
        if (page.section() != navigation.section()) navigation.selectSection(page.section());
        navigation.selectPage(page);
        scrollOffset = navigation.scrollOffset();
        tab = navigation.networkTab();
        lastTab = tab;
        if (page == S2HubNavigation.Page.REGION) {
            regionViewReceived = false;
            PacketDistributor.sendToServer(new C2S_RequestRegionViewPacket());
        }
        clampScroll();
    }

    private void sendDiplomacyAction(C2S_NationDiplomacyPacket.Action action, UUID targetNationId) {
        pendingRequestId = ++nextRequestId;
        pendingTicks = 0;
        PacketDistributor.sendToServer(new C2S_NationDiplomacyPacket(
                pendingRequestId, snapshot.revision(), action, targetNationId));
    }

    private void sendSiegeAction(C2S_SiegeActionPacket.Action action, UUID targetId, long gold) {
        pendingRequestId = ++nextRequestId;
        pendingTicks = 0;
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                && (kickTargetId != null || surrenderTargetId != null
                || vaultChangeConfirmation || leaveConfirmation)) {
            kickTargetId = null;
            kickTargetName = "";
            surrenderTargetId = null;
            surrenderTargetName = "";
            vaultChangeConfirmation = false;
            leaveConfirmation = false;
            return true;
        }
        if (memberSearch != null && memberSearch.isFocused()
                && keyCode != GLFW.GLFW_KEY_TAB && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            S2HubNavigation.Section[] sections = S2HubNavigation.Section.values();
            int direction = hasShiftDown() ? -1 : 1;
            selectSection(sections[Math.floorMod(navigation.section().ordinal() + direction, sections.length)]);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT) {
            List<S2HubNavigation.Page> pages = navigation.pages();
            if (pages.size() > 1) {
                int current = pages.indexOf(navigation.page());
                int direction = keyCode == GLFW.GLFW_KEY_LEFT ? -1 : 1;
                selectPage(pages.get(Math.floorMod(current + direction, pages.size())));
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

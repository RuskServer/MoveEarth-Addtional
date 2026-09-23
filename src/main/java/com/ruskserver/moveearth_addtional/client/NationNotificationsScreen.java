package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthTextField;
import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.network.C2S_LinkNationDiscordPacket;
import com.ruskserver.moveearth_addtional.network.C2S_LinkDiscordAccountPacket;
import com.ruskserver.moveearth_addtional.network.C2S_RequestS2HubPacket;
import com.ruskserver.moveearth_addtional.network.C2S_UpdateNationNotificationsPacket;
import com.ruskserver.moveearth_addtional.network.S2C_OpenNationNotificationsPacket;
import com.ruskserver.moveearth_addtional.network.S2C_S2ActionResultPacket;
import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

/** Nation-scoped notification settings; Discord credentials remain outside the game client and mod jar. */
public final class NationNotificationsScreen extends Screen implements SuppressesChatOverlay {
    private static int nextRequestId;
    private boolean canManage;
    private boolean linked;
    private boolean inGame;
    private boolean discord;
    private boolean coordinates;
    private boolean mentionSiege;
    private final int pendingCount;
    private long revision;
    private int pendingRequestId = -1;
    private boolean pendingLink;
    private boolean pendingAccountLink;
    private MoveEarthTextField linkCode;
    private Component toast;
    private int toastTicks;
    private int toastColor = SUCCESS;

    public NationNotificationsScreen(S2C_OpenNationNotificationsPacket packet) {
        super(Component.translatable("screen.moveearth_addtional.notifications.title"));
        canManage = packet.canManage();
        linked = packet.linked();
        inGame = packet.inGame();
        discord = packet.discord();
        coordinates = packet.includeCoordinates();
        mentionSiege = packet.mentionOnSiege();
        pendingCount = packet.pendingCount();
        revision = packet.revision();
    }

    @Override protected void init() {
        Rect panel = panelBounds();
        linkCode = new MoveEarthTextField(font, panel.x() + 28, panel.y() + 225, 186, 24,
                Component.translatable("screen.moveearth_addtional.notifications.link_code"));
        linkCode.setMaxLength(16);
        linkCode.setFilter(value -> value.matches("[A-Za-z0-9-]*"));
        linkCode.setEditable(canManage && pendingRequestId < 0);
        addRenderableWidget(linkCode);
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(graphics, width, height);
        Rect panel = panelBounds();
        drawPanel(graphics, panel);
        graphics.drawString(font, title, panel.x() + 20, panel.y() + 15, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.notifications.detail"),
                panel.x() + 20, panel.y() + 31, MUTED, false);
        drawClose(graphics, font, closeBounds(panel), closeBounds(panel).contains(mouseX, mouseY));

        drawToggle(graphics, toggleBounds(panel, 0),
                Component.translatable("screen.moveearth_addtional.notifications.ingame"), inGame,
                canManage, mouseX, mouseY);
        drawToggle(graphics, toggleBounds(panel, 1),
                Component.translatable("screen.moveearth_addtional.notifications.discord"), discord,
                canManage && linked, mouseX, mouseY);
        drawToggle(graphics, toggleBounds(panel, 2),
                Component.translatable("screen.moveearth_addtional.notifications.coordinates"), coordinates,
                canManage, mouseX, mouseY);
        drawToggle(graphics, toggleBounds(panel, 3),
                Component.translatable("screen.moveearth_addtional.notifications.mention_siege"), mentionSiege,
                canManage && linked, mouseX, mouseY);

        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.notifications.link_code"),
                panel.x() + 28, panel.y() + 210, MUTED, false);
        Rect link = linkBounds(panel);
        boolean linkEnabled = canManage && pendingRequestId < 0 && !linkCode.getValue().isBlank();
        drawButton(graphics, font, link,
                Component.translatable("screen.moveearth_addtional.notifications.link_submit"), ACCENT,
                linkEnabled && link.contains(mouseX, mouseY), linkEnabled);
        Rect account = accountLinkBounds(panel);
        drawButton(graphics, font, account,
                Component.translatable("screen.moveearth_addtional.notifications.account_link_submit"), SUCCESS,
                linkEnabled && account.contains(mouseX, mouseY), linkEnabled);

        int statusColor = linked ? SUCCESS : MUTED;
        graphics.drawString(font, Component.translatable(linked
                        ? "screen.moveearth_addtional.notifications.linked"
                        : "screen.moveearth_addtional.notifications.unlinked"),
                panel.x() + 28, panel.y() + 260, statusColor, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.notifications.pending", pendingCount),
                panel.x() + 28, panel.y() + 276, MUTED, false);
        if (!canManage) graphics.drawString(font,
                Component.translatable("screen.moveearth_addtional.notifications.read_only"),
                panel.x() + 28, panel.y() + 295, DANGER, false);

        Rect back = backBounds(panel);
        Rect save = saveBounds(panel);
        drawButton(graphics, font, back, Component.translatable("screen.moveearth_addtional.nation.cancel"),
                MUTED, back.contains(mouseX, mouseY), true);
        boolean saveEnabled = canManage && pendingRequestId < 0;
        drawButton(graphics, font, save, Component.translatable("screen.moveearth_addtional.notifications.save"),
                SUCCESS, saveEnabled && save.contains(mouseX, mouseY), saveEnabled);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (toastTicks > 0 && toast != null) drawToast(graphics, font, width, height, toast, toastColor);
    }

    private void drawToggle(GuiGraphics graphics, Rect bounds, Component label, boolean enabled,
                            boolean interactive, int mouseX, int mouseY) {
        drawCard(graphics, bounds, enabled ? SUCCESS : MUTED, enabled,
                interactive && bounds.contains(mouseX, mouseY));
        graphics.drawString(font, label, bounds.x() + 12, bounds.y() + 12,
                interactive ? TEXT : MUTED, false);
        Component state = Component.translatable(enabled
                ? "screen.moveearth_addtional.notifications.on"
                : "screen.moveearth_addtional.notifications.off");
        graphics.drawString(font, state, bounds.right() - 12 - font.width(state), bounds.y() + 12,
                enabled ? SUCCESS : MUTED, false);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        Rect panel = panelBounds();
        if (closeBounds(panel).contains(mouseX, mouseY) || backBounds(panel).contains(mouseX, mouseY)) {
            returnToHub();
            return true;
        }
        if (canManage) {
            if (toggleBounds(panel, 0).contains(mouseX, mouseY)) inGame = !inGame;
            else if (linked && toggleBounds(panel, 1).contains(mouseX, mouseY)) discord = !discord;
            else if (toggleBounds(panel, 2).contains(mouseX, mouseY)) coordinates = !coordinates;
            else if (linked && toggleBounds(panel, 3).contains(mouseX, mouseY)) mentionSiege = !mentionSiege;
            else if (pendingRequestId < 0 && saveBounds(panel).contains(mouseX, mouseY)) save();
            else if (pendingRequestId < 0 && !linkCode.getValue().isBlank()
                    && linkBounds(panel).contains(mouseX, mouseY)) link();
            else if (pendingRequestId < 0 && !linkCode.getValue().isBlank()
                    && accountLinkBounds(panel).contains(mouseX, mouseY)) linkAccount();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void save() {
        pendingRequestId = ++nextRequestId;
        pendingLink = false;
        PacketDistributor.sendToServer(new C2S_UpdateNationNotificationsPacket(pendingRequestId, revision,
                inGame, discord, coordinates, mentionSiege));
    }

    private void link() {
        pendingRequestId = ++nextRequestId;
        pendingLink = true;
        pendingAccountLink = false;
        linkCode.setEditable(false);
        PacketDistributor.sendToServer(new C2S_LinkNationDiscordPacket(pendingRequestId, linkCode.getValue()));
    }

    private void linkAccount() {
        pendingRequestId = ++nextRequestId;
        pendingLink = false;
        pendingAccountLink = true;
        linkCode.setEditable(false);
        PacketDistributor.sendToServer(new C2S_LinkDiscordAccountPacket(pendingRequestId, linkCode.getValue()));
    }

    public void handleResult(S2C_S2ActionResultPacket packet) {
        if (packet.requestId() != pendingRequestId) return;
        pendingRequestId = -1;
        if (packet.success()) {
            revision = packet.latestRevision();
            if (pendingLink) {
                linked = true;
                linkCode.setValue("");
            } else if (pendingAccountLink) {
                linkCode.setValue("");
            }
        }
        pendingLink = false;
        pendingAccountLink = false;
        linkCode.setEditable(canManage);
        toast = packet.success()
                ? MoveEarthMessage.success(Component.translatable(packet.messageKey()))
                : MoveEarthMessage.error(Component.translatable(packet.messageKey()));
        toastColor = packet.success() ? SUCCESS : DANGER;
        toastTicks = 80;
    }

    @Override public void tick() { super.tick(); if (toastTicks > 0) toastTicks--; }
    @Override public void onClose() { returnToHub(); }
    @Override public boolean isPauseScreen() { return false; }

    private void returnToHub() {
        PacketDistributor.sendToServer(new C2S_RequestS2HubPacket(S2HubTab.OVERVIEW));
    }

    private Rect panelBounds() {
        int w = Math.min(520, width - 20), h = Math.min(390, height - 20);
        return new Rect((width - w) / 2, (height - h) / 2, w, h);
    }
    private static Rect closeBounds(Rect panel) { return new Rect(panel.right() - 28, panel.y() + 8, 20, 20); }
    private static Rect toggleBounds(Rect panel, int index) {
        int column = index % 2, row = index / 2;
        return new Rect(panel.x() + 28 + column * 236, panel.y() + 68 + row * 68, 218, 48);
    }
    private static Rect backBounds(Rect panel) { return new Rect(panel.x() + 20, panel.bottom() - 39, 98, 23); }
    private static Rect saveBounds(Rect panel) { return new Rect(panel.right() - 138, panel.bottom() - 39, 118, 23); }
    private static Rect linkBounds(Rect panel) { return new Rect(panel.x() + 232, panel.y() + 224, 116, 24); }
    private static Rect accountLinkBounds(Rect panel) { return new Rect(panel.x() + 356, panel.y() + 224, 136, 24); }
}

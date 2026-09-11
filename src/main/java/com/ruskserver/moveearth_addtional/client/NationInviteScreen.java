package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;
import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.network.C2S_NationMembershipPacket;
import com.ruskserver.moveearth_addtional.network.C2S_RequestS2HubPacket;
import com.ruskserver.moveearth_addtional.network.S2C_S2ActionResultPacket;
import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import com.ruskserver.moveearth_addtional.s2.S2NationSnapshot;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

public final class NationInviteScreen extends Screen implements SuppressesChatOverlay {
    private static final int PANEL_WIDTH = 430;
    private static final int PANEL_HEIGHT = 300;
    private static int nextRequestId;
    private long revision;
    private final List<S2NationSnapshot.CandidateView> candidates;
    private int scrollOffset;
    private int pendingRequestId = -1;
    private Component toast;
    private int toastTicks;

    public NationInviteScreen(long revision, List<S2NationSnapshot.CandidateView> candidates) {
        super(Component.translatable("screen.moveearth_addtional.nation.invite_title"));
        this.revision = revision;
        this.candidates = List.copyOf(candidates);
    }

    public void handleResult(S2C_S2ActionResultPacket packet) {
        if (packet.requestId() != pendingRequestId) return;
        pendingRequestId = -1;
        revision = packet.latestRevision();
        if (packet.success()) return;
        toast = MoveEarthMessage.error(Component.translatable(packet.messageKey()));
        toastTicks = 80;
    }

    @Override public void tick() { if (toastTicks > 0) toastTicks--; }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(graphics, width, height);
        Rect panel = panelBounds();
        drawPanel(graphics, panel);
        graphics.drawString(font, title, panel.x() + 18, panel.y() + 15, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.nation.invite_detail"),
                panel.x() + 18, panel.y() + 31, MUTED, false);
        Rect close = closeBounds(panel);
        drawClose(graphics, font, close, close.contains(mouseX, mouseY));
        Rect list = listBounds(panel);
        if (candidates.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.moveearth_addtional.nation.invite_empty"),
                    list.x() + list.width() / 2, list.y() + 38, MUTED);
        } else {
            graphics.enableScissor(list.x(), list.y(), list.right(), list.bottom());
            for (int index = 0; index < candidates.size(); index++) {
                int y = list.y() + index * 39 - scrollOffset;
                S2NationSnapshot.CandidateView candidate = candidates.get(index);
                Rect card = new Rect(list.x(), y, list.width() - 7, 34);
                drawCard(graphics, card, SUCCESS, false, card.contains(mouseX, mouseY));
                graphics.drawString(font, candidate.name(), card.x() + 12, card.y() + 13, TEXT, false);
                Rect invite = new Rect(card.right() - 76, card.y() + 7, 64, 20);
                boolean enabled = pendingRequestId < 0;
                drawButton(graphics, font, invite,
                        Component.translatable("screen.moveearth_addtional.nation.invite"), SUCCESS,
                        enabled && invite.contains(mouseX, mouseY), enabled);
            }
            graphics.disableScissor();
            drawScrollbar(graphics, new Rect(list.right() - 4, list.y(), 4, list.height()),
                    list.height(), candidates.size() * 39, scrollOffset);
        }
        Rect back = new Rect(panel.x() + 18, panel.bottom() - 36, 92, 22);
        drawButton(graphics, font, back,
                Component.translatable("screen.moveearth_addtional.nation.cancel"), MUTED,
                back.contains(mouseX, mouseY), true);
        if (toastTicks > 0 && toast != null) drawToast(graphics, font, width, height, toast, DANGER);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        Rect panel = panelBounds();
        if (closeBounds(panel).contains(mouseX, mouseY)
                || new Rect(panel.x() + 18, panel.bottom() - 36, 92, 22).contains(mouseX, mouseY)) {
            returnToHub();
            return true;
        }
        Rect list = listBounds(panel);
        if (pendingRequestId < 0 && list.contains(mouseX, mouseY)) {
            for (int index = 0; index < candidates.size(); index++) {
                int y = list.y() + index * 39 - scrollOffset;
                Rect invite = new Rect(list.right() - 7 - 76, y + 7, 64, 20);
                if (invite.contains(mouseX, mouseY)) {
                    pendingRequestId = ++nextRequestId;
                    PacketDistributor.sendToServer(new C2S_NationMembershipPacket(
                            pendingRequestId, revision, C2S_NationMembershipPacket.Action.INVITE,
                            candidates.get(index).id()));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Rect list = listBounds(panelBounds());
        if (list.contains(mouseX, mouseY)) {
            scrollOffset = MoveEarthUi.scroll(scrollOffset, scrollY, 39,
                    candidates.size() * 39, list.height());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void returnToHub() {
        PacketDistributor.sendToServer(new C2S_RequestS2HubPacket(S2HubTab.MEMBERS));
    }

    @Override public void onClose() { returnToHub(); }

    private Rect panelBounds() {
        int panelWidth = Math.min(PANEL_WIDTH, width - 20);
        int panelHeight = Math.min(PANEL_HEIGHT, height - 20);
        return new Rect((width - panelWidth) / 2, (height - panelHeight) / 2, panelWidth, panelHeight);
    }
    private static Rect closeBounds(Rect panel) { return new Rect(panel.right() - 28, panel.y() + 8, 20, 20); }
    private static Rect listBounds(Rect panel) { return new Rect(panel.x() + 18, panel.y() + 54, panel.width() - 36, panel.height() - 104); }
    @Override public boolean isPauseScreen() { return false; }
}

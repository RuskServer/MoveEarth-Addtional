package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;
import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.network.C2S_NationApplicationActionPacket;
import com.ruskserver.moveearth_addtional.network.C2S_RequestS2HubPacket;
import com.ruskserver.moveearth_addtional.network.S2C_NationApplicationsPacket;
import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

public final class NationApplicationsScreen extends Screen implements SuppressesChatOverlay {
    private static final int ROW_HEIGHT = 43;
    private S2C_NationApplicationsPacket packet;
    private int scrollOffset;
    private Component toast;
    private int toastColor;
    private int toastTicks;

    public NationApplicationsScreen(S2C_NationApplicationsPacket packet) {
        super(Component.translatable("screen.moveearth_addtional.onboarding.applications.title"));
        update(packet);
    }

    public void update(S2C_NationApplicationsPacket packet) {
        this.packet = packet;
        if (!packet.messageKey().isBlank()) {
            Component body = Component.translatable(packet.messageKey());
            toast = packet.success() ? MoveEarthMessage.success(body) : MoveEarthMessage.error(body);
            toastColor = packet.success() ? SUCCESS : DANGER;
            toastTicks = 80;
        }
    }

    @Override public void tick() { if (toastTicks > 0) toastTicks--; }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(graphics, width, height);
        Rect panel = panelBounds();
        drawPanel(graphics, panel);
        graphics.drawString(font, title, panel.x() + 18, panel.y() + 15, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.onboarding.applications.detail"),
                panel.x() + 18, panel.y() + 31, MUTED, false);
        Rect close = new Rect(panel.right() - 28, panel.y() + 8, 20, 20);
        drawClose(graphics, font, close, close.contains(mouseX, mouseY));
        Rect list = listBounds(panel);
        if (packet.applications().isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.moveearth_addtional.onboarding.applications.empty"),
                    list.x() + list.width() / 2, list.y() + 45, MUTED);
        } else {
            graphics.enableScissor(list.x(), list.y(), list.right(), list.bottom());
            for (int index = 0; index < packet.applications().size(); index++) {
                var application = packet.applications().get(index);
                int y = list.y() + index * ROW_HEIGHT - scrollOffset;
                Rect card = new Rect(list.x(), y, list.width() - 8, ROW_HEIGHT - 5);
                drawCard(graphics, card, application.online() ? SUCCESS : MUTED, false,
                        card.contains(mouseX, mouseY));
                graphics.drawString(font, application.playerName(), card.x() + 12, card.y() + 8,
                        application.online() ? SUCCESS : TEXT, false);
                long minutes = Math.max(0L, (System.currentTimeMillis() - application.requestedAt()) / 60_000L);
                graphics.drawString(font, Component.translatable(
                        "screen.moveearth_addtional.onboarding.application.age", minutes),
                        card.x() + 12, card.y() + 22, MUTED, false);
                Rect reject = new Rect(card.right() - 166, card.y() + 8, 72, 22);
                Rect approve = new Rect(card.right() - 86, card.y() + 8, 74, 22);
                drawButton(graphics, font, reject,
                        Component.translatable("screen.moveearth_addtional.onboarding.reject"), DANGER,
                        reject.contains(mouseX, mouseY), true);
                drawButton(graphics, font, approve,
                        Component.translatable("screen.moveearth_addtional.onboarding.approve"), SUCCESS,
                        approve.contains(mouseX, mouseY), true);
            }
            graphics.disableScissor();
            drawScrollbar(graphics, new Rect(list.right() - 4, list.y(), 4, list.height()),
                    list.height(), packet.applications().size() * ROW_HEIGHT, scrollOffset);
        }
        Rect back = new Rect(panel.x() + 18, panel.bottom() - 37, 96, 22);
        drawButton(graphics, font, back, Component.translatable("screen.moveearth_addtional.nation.cancel"),
                MUTED, back.contains(mouseX, mouseY), true);
        if (toastTicks > 0 && toast != null) drawToast(graphics, font, width, height, toast, toastColor);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        Rect panel = panelBounds();
        if (new Rect(panel.right() - 28, panel.y() + 8, 20, 20).contains(mouseX, mouseY)
                || new Rect(panel.x() + 18, panel.bottom() - 37, 96, 22).contains(mouseX, mouseY)) {
            returnToHub();
            return true;
        }
        Rect list = listBounds(panel);
        if (list.contains(mouseX, mouseY)) {
            for (int index = 0; index < packet.applications().size(); index++) {
                int y = list.y() + index * ROW_HEIGHT - scrollOffset;
                Rect card = new Rect(list.x(), y, list.width() - 8, ROW_HEIGHT - 5);
                C2S_NationApplicationActionPacket.Action action = null;
                if (new Rect(card.right() - 166, card.y() + 8, 72, 22).contains(mouseX, mouseY)) {
                    action = C2S_NationApplicationActionPacket.Action.REJECT;
                } else if (new Rect(card.right() - 86, card.y() + 8, 74, 22).contains(mouseX, mouseY)) {
                    action = C2S_NationApplicationActionPacket.Action.APPROVE;
                }
                if (action != null) {
                    PacketDistributor.sendToServer(new C2S_NationApplicationActionPacket(packet.revision(), action,
                            packet.applications().get(index).playerId()));
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
            scrollOffset = MoveEarthUi.scroll(scrollOffset, scrollY, ROW_HEIGHT,
                    packet.applications().size() * ROW_HEIGHT, list.height());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void returnToHub() {
        PacketDistributor.sendToServer(new C2S_RequestS2HubPacket(S2HubTab.MEMBERS));
    }
    @Override public void onClose() { returnToHub(); }
    private Rect panelBounds() {
        int w = Math.min(520, width - 20);
        int h = Math.min(330, height - 20);
        return new Rect((width - w) / 2, (height - h) / 2, w, h);
    }
    private static Rect listBounds(Rect panel) {
        return new Rect(panel.x() + 18, panel.y() + 54, panel.width() - 36, panel.height() - 104);
    }
    @Override public boolean isPauseScreen() { return false; }
}

package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.network.C2S_RequestS2HubPacket;
import com.ruskserver.moveearth_addtional.network.C2S_SiegeActionPacket;
import com.ruskserver.moveearth_addtional.network.S2C_S2ActionResultPacket;
import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import com.ruskserver.moveearth_addtional.s2.siege.PeaceTermsPolicy;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;

import java.util.UUID;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

public final class PeaceProposalScreen extends Screen implements SuppressesChatOverlay {
    private static int nextRequestId;
    private final long revision;
    private final UUID opponentNationId;
    private final String opponentName;
    private EditBox goldEdit;
    private int pendingRequestId = -1;
    private Component toast;
    private int toastTicks;

    public PeaceProposalScreen(long revision, UUID opponentNationId, String opponentName) {
        super(Component.translatable("screen.moveearth_addtional.peace.title"));
        this.revision = revision;
        this.opponentNationId = opponentNationId;
        this.opponentName = opponentName;
    }

    @Override protected void init() {
        Rect panel = panelBounds();
        goldEdit = new EditBox(font, panel.x() + 28, panel.y() + 103,
                panel.width() - 56, 18, Component.translatable("screen.moveearth_addtional.peace.gold"));
        goldEdit.setMaxLength(7);
        goldEdit.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        goldEdit.setValue("0");
        goldEdit.setBordered(false);
        goldEdit.setTextColor(TEXT);
        addRenderableWidget(goldEdit);
        setInitialFocus(goldEdit);
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(graphics, width, height);
        Rect panel = panelBounds();
        drawPanel(graphics, panel);
        graphics.drawString(font, title, panel.x() + 20, panel.y() + 16, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.peace.detail", opponentName),
                panel.x() + 20, panel.y() + 36, MUTED, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.peace.gold"),
                panel.x() + 28, panel.y() + 84, GOLD, false);
        Rect field = new Rect(goldEdit.getX() - 5, goldEdit.getY() - 3,
                goldEdit.getWidth() + 10, goldEdit.getHeight() + 6);
        graphics.fill(field.x(), field.y(), field.right(), field.bottom(), CARD);
        drawBorder(graphics, field, goldEdit.isFocused() ? GOLD : BORDER);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.peace.payer_note"),
                panel.x() + 28, panel.y() + 132, MUTED, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.peace.prisoners_note"),
                panel.x() + 28, panel.y() + 147, SUCCESS, false);
        Rect cancel = cancelBounds(panel);
        Rect submit = submitBounds(panel);
        drawButton(graphics, font, cancel, Component.translatable("screen.moveearth_addtional.nation.cancel"),
                MUTED, cancel.contains(mouseX, mouseY), true);
        boolean valid = compensation() >= 0L;
        drawButton(graphics, font, submit, Component.translatable("screen.moveearth_addtional.peace.propose"),
                SUCCESS, valid && pendingRequestId < 0 && submit.contains(mouseX, mouseY),
                valid && pendingRequestId < 0);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (toastTicks > 0 && toast != null) drawToast(graphics, font, width, height, toast, DANGER);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            Rect panel = panelBounds();
            if (cancelBounds(panel).contains(mouseX, mouseY)) { returnToHub(); return true; }
            long gold = compensation();
            if (gold >= 0L && pendingRequestId < 0 && submitBounds(panel).contains(mouseX, mouseY)) {
                pendingRequestId = ++nextRequestId;
                PacketDistributor.sendToServer(new C2S_SiegeActionPacket(pendingRequestId, revision,
                        C2S_SiegeActionPacket.Action.PROPOSE_PEACE, opponentNationId, gold));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public void handleResult(S2C_S2ActionResultPacket packet) {
        if (packet.requestId() != pendingRequestId) return;
        pendingRequestId = -1;
        toast = MoveEarthMessage.error(Component.translatable(packet.messageKey()));
        toastTicks = 80;
    }

    @Override public void tick() {
        super.tick();
        if (toastTicks > 0) toastTicks--;
    }

    private long compensation() {
        try {
            long value = Long.parseLong(goldEdit.getValue());
            return PeaceTermsPolicy.validCompensation(value) ? value : -1L;
        } catch (NumberFormatException ignored) { return -1L; }
    }

    private void returnToHub() {
        PacketDistributor.sendToServer(new C2S_RequestS2HubPacket(S2HubTab.SIEGE));
    }

    @Override public void onClose() { returnToHub(); }
    @Override public boolean isPauseScreen() { return false; }

    private Rect panelBounds() {
        int panelWidth = Math.min(430, width - 20);
        int panelHeight = Math.min(224, height - 20);
        return new Rect((width - panelWidth) / 2, (height - panelHeight) / 2, panelWidth, panelHeight);
    }
    private static Rect cancelBounds(Rect panel) { return new Rect(panel.x() + 20, panel.bottom() - 39, 98, 23); }
    private static Rect submitBounds(Rect panel) { return new Rect(panel.right() - 140, panel.bottom() - 39, 120, 23); }
}

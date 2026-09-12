package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.network.C2S_NationSettingsPacket;
import com.ruskserver.moveearth_addtional.network.C2S_RequestS2HubPacket;
import com.ruskserver.moveearth_addtional.network.C2S_RequestNationNotificationsPacket;
import com.ruskserver.moveearth_addtional.network.S2C_S2ActionResultPacket;
import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import com.ruskserver.moveearth_addtional.s2.S2NationSnapshot;
import com.ruskserver.moveearth_addtional.s2.nation.NationNamePolicy;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

/** Owner-only nation identity, succession, and disband controls. */
public final class NationSettingsScreen extends Screen implements SuppressesChatOverlay {
    private static int nextRequestId;
    private S2NationSnapshot snapshot;
    private final List<S2NationSnapshot.MemberView> successorCandidates;
    private EditBox nameEdit;
    private EditBox tagEdit;
    private UUID selectedSuccessor;
    private String selectedSuccessorName = "";
    private Confirm confirm = Confirm.NONE;
    private int memberScroll;
    private int pendingRequestId = -1;
    private Component toast;
    private int toastTicks;
    private int toastColor = DANGER;

    public NationSettingsScreen(S2NationSnapshot snapshot) {
        super(Component.translatable("screen.moveearth_addtional.nation.settings.title"));
        this.snapshot = snapshot;
        this.successorCandidates = snapshot.members().stream()
                .filter(member -> !"owner".equals(member.roleId()))
                .toList();
    }

    @Override protected void init() {
        Rect panel = panelBounds();
        nameEdit = new EditBox(font, panel.x() + 28, panel.y() + 91, 220, 18,
                Component.translatable("screen.moveearth_addtional.nation.name"));
        tagEdit = new EditBox(font, panel.x() + 28, panel.y() + 142, 110, 18,
                Component.translatable("screen.moveearth_addtional.nation.tag"));
        nameEdit.setMaxLength(NationNamePolicy.MAX_NAME_LENGTH);
        tagEdit.setMaxLength(NationNamePolicy.MAX_TAG_LENGTH);
        nameEdit.setValue(snapshot.nationName());
        tagEdit.setValue(snapshot.nationTag());
        configure(nameEdit);
        configure(tagEdit);
        addRenderableWidget(nameEdit);
        addRenderableWidget(tagEdit);
        setInitialFocus(nameEdit);
    }

    private static void configure(EditBox edit) {
        edit.setBordered(false);
        edit.setTextColor(TEXT);
        edit.setTextColorUneditable(MUTED);
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(graphics, width, height);
        Rect panel = panelBounds();
        drawPanel(graphics, panel);
        graphics.drawString(font, title, panel.x() + 20, panel.y() + 15, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.nation.settings.detail"),
                panel.x() + 20, panel.y() + 31, MUTED, false);
        Rect close = closeBounds(panel);
        drawClose(graphics, font, close, close.contains(mouseX, mouseY));

        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.nation.name"),
                panel.x() + 28, panel.y() + 73, MUTED, false);
        drawField(graphics, nameEdit);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.nation.tag"),
                panel.x() + 28, panel.y() + 124, MUTED, false);
        drawField(graphics, tagEdit);
        NationNamePolicy.Validation validation = validation();
        if (!validation.valid()) {
            graphics.drawString(font, Component.translatable(
                            "screen.moveearth_addtional.nation.validation." + validation.reason()),
                    panel.x() + 28, panel.y() + 169, DANGER, false);
        }
        Rect save = saveBounds(panel);
        boolean changed = identityChanged();
        boolean saveEnabled = pendingRequestId < 0 && validation.valid() && changed;
        drawButton(graphics, font, save,
                Component.translatable("screen.moveearth_addtional.nation.settings.save"), SUCCESS,
                saveEnabled && save.contains(mouseX, mouseY), saveEnabled);

        graphics.drawString(font, Component.translatable(
                        "screen.moveearth_addtional.nation.settings.owner", snapshot.ownerName()),
                panel.x() + 282, panel.y() + 66, GOLD, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.nation.settings.successor"),
                panel.x() + 282, panel.y() + 82, MUTED, false);
        Rect members = memberListBounds(panel);
        graphics.enableScissor(members.x(), members.y(), members.right(), members.bottom());
        List<S2NationSnapshot.MemberView> candidates = candidates();
        for (int index = 0; index < candidates.size(); index++) {
            S2NationSnapshot.MemberView member = candidates.get(index);
            Rect card = memberCard(members, index);
            if (card.bottom() <= members.y() || card.y() >= members.bottom()) continue;
            boolean selected = member.id().equals(selectedSuccessor);
            drawCard(graphics, card, selected ? GOLD : ACCENT, selected, card.contains(mouseX, mouseY));
            graphics.drawString(font, member.name(), card.x() + 10, card.y() + 7, TEXT, false);
            graphics.drawString(font, member.roleName(), card.x() + 10, card.y() + 21, MUTED, false);
        }
        graphics.disableScissor();
        drawScrollbar(graphics, new Rect(members.right() - 4, members.y(), 4, members.height()),
                members.height(), candidates.size() * 39, memberScroll);
        Rect transfer = transferBounds(panel);
        boolean transferEnabled = selectedSuccessor != null && pendingRequestId < 0;
        drawButton(graphics, font, transfer,
                Component.translatable("screen.moveearth_addtional.nation.settings.transfer"), GOLD,
                transferEnabled && transfer.contains(mouseX, mouseY), transferEnabled);

        Rect back = backBounds(panel);
        Rect notifications = notificationsBounds(panel);
        Rect disband = disbandBounds(panel);
        drawButton(graphics, font, back, Component.translatable("screen.moveearth_addtional.nation.cancel"),
                MUTED, back.contains(mouseX, mouseY), true);
        drawButton(graphics, font, notifications,
                Component.translatable("screen.moveearth_addtional.notifications.open"), ACCENT,
                notifications.contains(mouseX, mouseY), true);
        drawButton(graphics, font, disband,
                Component.translatable("screen.moveearth_addtional.nation.settings.disband"), DANGER,
                pendingRequestId < 0 && disband.contains(mouseX, mouseY), pendingRequestId < 0);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (confirm != Confirm.NONE) drawConfirmation(graphics, mouseX, mouseY);
        if (toastTicks > 0 && toast != null) drawToast(graphics, font, width, height, toast, toastColor);
    }

    private void drawConfirmation(GuiGraphics graphics, int mouseX, int mouseY) {
        drawModalBackdrop(graphics, width, height);
        Rect modal = modalBounds();
        drawPanel(graphics, modal);
        String prefix = confirm == Confirm.TRANSFER ? "transfer" : "disband";
        graphics.drawString(font, Component.translatable(
                        "screen.moveearth_addtional.nation.settings." + prefix + "_title"),
                modal.x() + 16, modal.y() + 15, DANGER, false);
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(
                        "screen.moveearth_addtional.nation.settings." + prefix + "_detail",
                        selectedSuccessorName).getString(), modal.width() - 32),
                modal.x() + 16, modal.y() + 39, TEXT, false);
        drawButton(graphics, font, modalCancel(modal),
                Component.translatable("screen.moveearth_addtional.nation.cancel"), MUTED,
                modalCancel(modal).contains(mouseX, mouseY), true);
        drawButton(graphics, font, modalConfirm(modal),
                Component.translatable("screen.moveearth_addtional.nation.settings.confirm"), DANGER,
                modalConfirm(modal).contains(mouseX, mouseY), pendingRequestId < 0);
    }

    private static void drawField(GuiGraphics graphics, EditBox field) {
        Rect bounds = new Rect(field.getX() - 5, field.getY() - 3,
                field.getWidth() + 10, field.getHeight() + 6);
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), CARD);
        drawBorder(graphics, bounds, field.isFocused() ? ACCENT : BORDER);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (confirm != Confirm.NONE) {
            Rect modal = modalBounds();
            if (modalCancel(modal).contains(mouseX, mouseY)) confirm = Confirm.NONE;
            else if (pendingRequestId < 0 && modalConfirm(modal).contains(mouseX, mouseY)) {
                if (confirm == Confirm.TRANSFER) send(C2S_NationSettingsPacket.Action.TRANSFER_OWNER,
                        "", "", selectedSuccessor);
                else send(C2S_NationSettingsPacket.Action.DISBAND, "", "", null);
                confirm = Confirm.NONE;
            }
            return true;
        }
        Rect panel = panelBounds();
        if (closeBounds(panel).contains(mouseX, mouseY) || backBounds(panel).contains(mouseX, mouseY)) {
            returnToHub();
            return true;
        }
        if (notificationsBounds(panel).contains(mouseX, mouseY)) {
            PacketDistributor.sendToServer(new C2S_RequestNationNotificationsPacket());
            return true;
        }
        NationNamePolicy.Validation validation = validation();
        if (pendingRequestId < 0 && validation.valid() && identityChanged()
                && saveBounds(panel).contains(mouseX, mouseY)) {
            send(C2S_NationSettingsPacket.Action.UPDATE_IDENTITY,
                    validation.name(), validation.tag(), null);
            return true;
        }
        Rect members = memberListBounds(panel);
        if (members.contains(mouseX, mouseY)) {
            List<S2NationSnapshot.MemberView> candidates = candidates();
            for (int index = 0; index < candidates.size(); index++) {
                S2NationSnapshot.MemberView member = candidates.get(index);
                if (memberCard(members, index).contains(mouseX, mouseY)) {
                    selectedSuccessor = member.id();
                    selectedSuccessorName = member.name();
                    return true;
                }
            }
        }
        if (selectedSuccessor != null && transferBounds(panel).contains(mouseX, mouseY)) {
            confirm = Confirm.TRANSFER;
            return true;
        }
        if (disbandBounds(panel).contains(mouseX, mouseY)) {
            confirm = Confirm.DISBAND;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Rect list = memberListBounds(panelBounds());
        if (list.contains(mouseX, mouseY)) {
            memberScroll = com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.scroll(
                    memberScroll, scrollY, 39, candidates().size() * 39, list.height());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public void handleResult(S2C_S2ActionResultPacket packet) {
        if (packet.requestId() != pendingRequestId) return;
        pendingRequestId = -1;
        if (!packet.success() && packet.messageKey().endsWith(".stale")) {
            returnToHub();
            return;
        }
        toast = packet.success()
                ? MoveEarthMessage.success(Component.translatable(packet.messageKey()))
                : MoveEarthMessage.error(Component.translatable(packet.messageKey()));
        toastColor = packet.success() ? SUCCESS : DANGER;
        toastTicks = 80;
    }

    @Override public void tick() { super.tick(); if (toastTicks > 0) toastTicks--; }
    @Override public void onClose() { returnToHub(); }
    @Override public boolean isPauseScreen() { return false; }

    private void send(C2S_NationSettingsPacket.Action action, String name, String tag, UUID target) {
        pendingRequestId = ++nextRequestId;
        PacketDistributor.sendToServer(new C2S_NationSettingsPacket(
                pendingRequestId, snapshot.revision(), action, name, tag, target));
    }

    private void returnToHub() {
        PacketDistributor.sendToServer(new C2S_RequestS2HubPacket(S2HubTab.OVERVIEW));
    }

    private NationNamePolicy.Validation validation() {
        return NationNamePolicy.validate(nameEdit.getValue(), tagEdit.getValue());
    }

    private boolean identityChanged() {
        return !nameEdit.getValue().trim().equals(snapshot.nationName())
                || !tagEdit.getValue().trim().equalsIgnoreCase(snapshot.nationTag());
    }

    private List<S2NationSnapshot.MemberView> candidates() {
        return successorCandidates;
    }

    private Rect panelBounds() {
        int w = Math.min(570, width - 20), h = Math.min(360, height - 20);
        return new Rect((width - w) / 2, (height - h) / 2, w, h);
    }
    private Rect closeBounds(Rect panel) { return new Rect(panel.right() - 28, panel.y() + 8, 20, 20); }
    private static Rect saveBounds(Rect panel) { return new Rect(panel.x() + 28, panel.y() + 191, 130, 22); }
    private static Rect memberListBounds(Rect panel) { return new Rect(panel.x() + 282, panel.y() + 101, 260, 150); }
    private Rect memberCard(Rect list, int index) { return new Rect(list.x(), list.y() + index * 39 - memberScroll, list.width() - 8, 34); }
    private static Rect transferBounds(Rect panel) { return new Rect(panel.right() - 158, panel.y() + 261, 130, 22); }
    private static Rect backBounds(Rect panel) { return new Rect(panel.x() + 20, panel.bottom() - 39, 98, 23); }
    private static Rect notificationsBounds(Rect panel) { return new Rect(panel.x() + 130, panel.bottom() - 39, 140, 23); }
    private static Rect disbandBounds(Rect panel) { return new Rect(panel.right() - 158, panel.bottom() - 39, 130, 23); }
    private Rect modalBounds() { return new Rect((width - 390) / 2, (height - 132) / 2, 390, 132); }
    private static Rect modalCancel(Rect modal) { return new Rect(modal.right() - 184, modal.bottom() - 34, 82, 22); }
    private static Rect modalConfirm(Rect modal) { return new Rect(modal.right() - 94, modal.bottom() - 34, 82, 22); }
    private enum Confirm { NONE, TRANSFER, DISBAND }
}

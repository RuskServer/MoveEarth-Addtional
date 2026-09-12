package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;
import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.network.C2S_NationRolePacket;
import com.ruskserver.moveearth_addtional.network.C2S_RequestS2HubPacket;
import com.ruskserver.moveearth_addtional.network.S2C_S2ActionResultPacket;
import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import com.ruskserver.moveearth_addtional.s2.S2NationSnapshot;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.RoleNamePolicy;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

public final class NationRoleEditorScreen extends Screen implements SuppressesChatOverlay {
    private static final int PANEL_WIDTH = 470;
    private static final int PANEL_HEIGHT = 322;
    private static int nextRequestId;
    private static final List<S2Permission> EDITABLE_PERMISSIONS = Arrays.stream(S2Permission.values())
            .filter(permission -> permission != S2Permission.OWNER).toList();

    private long revision;
    private final String roleId;
    private final int roleMemberCount;
    private long permissionMask;
    private EditBox nameEdit;
    private int pendingRequestId = -1;
    private Component toast;
    private int toastTicks;
    private boolean confirmingDelete;

    public NationRoleEditorScreen(long revision, S2NationSnapshot.RoleView role) {
        super(Component.translatable(role == null
                ? "screen.moveearth_addtional.nation.role.create_title"
                : "screen.moveearth_addtional.nation.role.edit_title"));
        this.revision = revision;
        this.roleId = role == null ? "" : role.id();
        this.roleMemberCount = role == null ? 0 : role.memberCount();
        this.permissionMask = role == null ? 0L : role.permissionMask();
        this.initialName = role == null ? "" : role.displayName();
    }

    private final String initialName;

    @Override
    protected void init() {
        Rect panel = panelBounds();
        nameEdit = new EditBox(font, panel.x() + 25, panel.y() + 72,
                panel.width() - 50, 18, Component.translatable("screen.moveearth_addtional.nation.role.name"));
        nameEdit.setMaxLength(RoleNamePolicy.MAX_LENGTH);
        nameEdit.setValue(initialName);
        nameEdit.setBordered(false);
        nameEdit.setTextColor(TEXT);
        nameEdit.setTextColorUneditable(MUTED);
        addRenderableWidget(nameEdit);
        setInitialFocus(nameEdit);
    }

    public void handleResult(S2C_S2ActionResultPacket packet) {
        if (packet.requestId() != pendingRequestId) return;
        pendingRequestId = -1;
        revision = packet.latestRevision();
        if (packet.success()) return;
        toast = MoveEarthMessage.error(Component.translatable(packet.messageKey()));
        toastTicks = 80;
    }

    @Override public void tick() { super.tick(); if (toastTicks > 0) toastTicks--; }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(graphics, width, height);
        Rect panel = panelBounds();
        drawPanel(graphics, panel);
        graphics.drawString(font, title, panel.x() + 18, panel.y() + 15, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.nation.role.detail"),
                panel.x() + 18, panel.y() + 31, MUTED, false);
        Rect close = closeBounds(panel);
        drawClose(graphics, font, close, close.contains(mouseX, mouseY));

        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.nation.role.name"),
                panel.x() + 25, panel.y() + 57, MUTED, false);
        Rect field = new Rect(nameEdit.getX() - 5, nameEdit.getY() - 3,
                nameEdit.getWidth() + 10, nameEdit.getHeight() + 6);
        graphics.fill(field.x(), field.y(), field.right(), field.bottom(), CARD);
        drawBorder(graphics, field, nameEdit.isFocused() ? ACCENT : BORDER);

        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.nation.role.permissions"),
                panel.x() + 25, panel.y() + 107, MUTED, false);
        for (int index = 0; index < EDITABLE_PERMISSIONS.size(); index++) {
            S2Permission permission = EDITABLE_PERMISSIONS.get(index);
            Rect bounds = permissionBounds(panel, index);
            boolean selected = (permissionMask & permission.mask()) != 0L;
            drawCard(graphics, bounds, selected ? SUCCESS : ACCENT, selected, bounds.contains(mouseX, mouseY));
            graphics.drawString(font, selected ? "✓" : "○", bounds.x() + 10, bounds.y() + 10,
                    selected ? SUCCESS : MUTED, false);
            graphics.drawString(font, Component.translatable(permissionKey(permission)),
                    bounds.x() + 27, bounds.y() + 10, selected ? TEXT : MUTED, false);
        }

        RoleNamePolicy.Validation validation = RoleNamePolicy.validate(nameEdit.getValue());
        if (!validation.valid() && !nameEdit.getValue().isEmpty()) {
            graphics.drawString(font, Component.translatable(
                            "screen.moveearth_addtional.nation.role.validation." + validation.reason()),
                    panel.x() + 25, panel.bottom() - 63, DANGER, false);
        }
        Rect cancel = cancelBounds(panel);
        Rect save = saveBounds(panel);
        drawButton(graphics, font, cancel,
                Component.translatable("screen.moveearth_addtional.nation.cancel"), MUTED,
                cancel.contains(mouseX, mouseY), true);
        if (!roleId.isBlank()) {
            Rect delete = deleteBounds(panel);
            boolean canDelete = pendingRequestId < 0;
            drawButton(graphics, font, delete,
                    Component.translatable("screen.moveearth_addtional.nation.role.delete"), DANGER,
                    canDelete && delete.contains(mouseX, mouseY), canDelete);
        }
        boolean canSave = validation.valid() && pendingRequestId < 0;
        drawButton(graphics, font, save,
                Component.translatable("screen.moveearth_addtional.nation.role.save"), SUCCESS,
                canSave && save.contains(mouseX, mouseY), canSave);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (confirmingDelete) drawDeleteConfirmation(graphics, mouseX, mouseY);
        if (toastTicks > 0 && toast != null) drawToast(graphics, font, width, height, toast, DANGER);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (confirmingDelete) {
                Rect modal = deleteModalBounds();
                if (modalCancelBounds(modal).contains(mouseX, mouseY)) {
                    confirmingDelete = false;
                } else if (modalConfirmBounds(modal).contains(mouseX, mouseY) && pendingRequestId < 0) {
                    confirmingDelete = false;
                    pendingRequestId = ++nextRequestId;
                    PacketDistributor.sendToServer(new C2S_NationRolePacket(
                            pendingRequestId, revision, C2S_NationRolePacket.Action.DELETE,
                            roleId, "", 0L, new UUID(0L, 0L)));
                }
                return true;
            }
            Rect panel = panelBounds();
            if (closeBounds(panel).contains(mouseX, mouseY) || cancelBounds(panel).contains(mouseX, mouseY)) {
                returnToHub();
                return true;
            }
            if (pendingRequestId < 0) {
                if (!roleId.isBlank() && deleteBounds(panel).contains(mouseX, mouseY)) {
                    confirmingDelete = true;
                    return true;
                }
                for (int index = 0; index < EDITABLE_PERMISSIONS.size(); index++) {
                    if (permissionBounds(panel, index).contains(mouseX, mouseY)) {
                        permissionMask ^= EDITABLE_PERMISSIONS.get(index).mask();
                        return true;
                    }
                }
                RoleNamePolicy.Validation validation = RoleNamePolicy.validate(nameEdit.getValue());
                if (validation.valid() && saveBounds(panel).contains(mouseX, mouseY)) {
                    pendingRequestId = ++nextRequestId;
                    PacketDistributor.sendToServer(new C2S_NationRolePacket(
                            pendingRequestId, revision, C2S_NationRolePacket.Action.SAVE,
                            roleId, validation.name(), permissionMask, new UUID(0L, 0L)));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void returnToHub() {
        PacketDistributor.sendToServer(new C2S_RequestS2HubPacket(S2HubTab.ROLES));
    }

    @Override public void onClose() { returnToHub(); }

    private Rect panelBounds() {
        int panelWidth = Math.min(PANEL_WIDTH, width - 20);
        int panelHeight = Math.min(PANEL_HEIGHT, height - 20);
        return new Rect((width - panelWidth) / 2, (height - panelHeight) / 2, panelWidth, panelHeight);
    }
    private static Rect closeBounds(Rect panel) { return new Rect(panel.right() - 28, panel.y() + 8, 20, 20); }
    private static Rect permissionBounds(Rect panel, int index) {
        int gap = 8;
        int width = (panel.width() - 50 - gap * 2) / 3;
        return new Rect(panel.x() + 25 + (index % 3) * (width + gap),
                panel.y() + 122 + (index / 3) * 38, width, 31);
    }
    private static Rect cancelBounds(Rect panel) { return new Rect(panel.x() + 20, panel.bottom() - 38, 92, 22); }
    private static Rect deleteBounds(Rect panel) { return new Rect(panel.x() + 120, panel.bottom() - 38, 104, 22); }
    private static Rect saveBounds(Rect panel) { return new Rect(panel.right() - 120, panel.bottom() - 38, 100, 22); }
    private Rect deleteModalBounds() { return new Rect((width - 360) / 2, (height - 126) / 2, 360, 126); }
    private static Rect modalCancelBounds(Rect modal) { return new Rect(modal.right() - 198, modal.bottom() - 36, 88, 22); }
    private static Rect modalConfirmBounds(Rect modal) { return new Rect(modal.right() - 102, modal.bottom() - 36, 90, 22); }

    private void drawDeleteConfirmation(GuiGraphics graphics, int mouseX, int mouseY) {
        drawModalBackdrop(graphics, width, height);
        Rect modal = deleteModalBounds();
        drawPanel(graphics, modal);
        graphics.drawString(font,
                Component.translatable("screen.moveearth_addtional.nation.role.delete_title"),
                modal.x() + 16, modal.y() + 15, DANGER, false);
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(
                        "screen.moveearth_addtional.nation.role.delete_detail",
                        initialName, roleMemberCount).getString(), modal.width() - 32),
                modal.x() + 16, modal.y() + 39, TEXT, false);
        graphics.drawString(font,
                Component.translatable("screen.moveearth_addtional.nation.role.delete_reassign"),
                modal.x() + 16, modal.y() + 57, GOLD, false);
        Rect cancel = modalCancelBounds(modal);
        Rect confirm = modalConfirmBounds(modal);
        drawButton(graphics, font, cancel,
                Component.translatable("screen.moveearth_addtional.nation.cancel"), MUTED,
                cancel.contains(mouseX, mouseY), true);
        drawButton(graphics, font, confirm,
                Component.translatable("screen.moveearth_addtional.nation.role.delete_confirm"), DANGER,
                confirm.contains(mouseX, mouseY), true);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirmingDelete && keyCode == 256) {
            confirmingDelete = false;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    private static String permissionKey(S2Permission permission) {
        return "screen.moveearth_addtional.nation.permission."
                + permission.name().toLowerCase(java.util.Locale.ROOT);
    }
    @Override public boolean isPauseScreen() { return false; }
}

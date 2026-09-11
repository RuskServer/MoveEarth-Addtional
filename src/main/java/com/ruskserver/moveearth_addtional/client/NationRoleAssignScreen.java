package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;
import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.network.C2S_NationRolePacket;
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

public final class NationRoleAssignScreen extends Screen implements SuppressesChatOverlay {
    private static int nextRequestId;
    private long revision;
    private final S2NationSnapshot.MemberView member;
    private final List<S2NationSnapshot.RoleView> roles;
    private String selectedRoleId;
    private int scrollOffset;
    private int pendingRequestId = -1;
    private Component toast;
    private int toastTicks;

    public NationRoleAssignScreen(long revision, S2NationSnapshot.MemberView member,
                                  List<S2NationSnapshot.RoleView> roles) {
        super(Component.translatable("screen.moveearth_addtional.nation.role.assign_title"));
        this.revision = revision;
        this.member = member;
        this.roles = roles.stream().filter(role -> !"owner".equals(role.id())).toList();
        this.selectedRoleId = this.roles.stream()
                .filter(role -> role.displayName().equals(member.roleName()))
                .map(S2NationSnapshot.RoleView::id).findFirst().orElse("member");
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
        graphics.drawString(font, Component.translatable(
                        "screen.moveearth_addtional.nation.role.assign_detail", member.name()),
                panel.x() + 18, panel.y() + 31, MUTED, false);
        drawClose(graphics, font, closeBounds(panel), closeBounds(panel).contains(mouseX, mouseY));
        Rect list = listBounds(panel);
        graphics.enableScissor(list.x(), list.y(), list.right(), list.bottom());
        for (int index = 0; index < roles.size(); index++) {
            S2NationSnapshot.RoleView role = roles.get(index);
            Rect card = new Rect(list.x(), list.y() + index * 43 - scrollOffset, list.width() - 7, 38);
            boolean selected = role.id().equals(selectedRoleId);
            drawCard(graphics, card, SUCCESS, selected, card.contains(mouseX, mouseY));
            graphics.drawString(font, (selected ? "✓ " : "") + role.displayName(),
                    card.x() + 12, card.y() + 8, selected ? SUCCESS : TEXT, false);
            graphics.drawString(font, Component.translatable("screen.moveearth_addtional.s2.role_summary",
                    role.memberCount(), role.permissionCount()), card.x() + 12, card.y() + 22, MUTED, false);
        }
        graphics.disableScissor();
        drawScrollbar(graphics, new Rect(list.right() - 4, list.y(), 4, list.height()),
                list.height(), roles.size() * 43, scrollOffset);
        Rect back = backBounds(panel);
        Rect assign = assignBounds(panel);
        drawButton(graphics, font, back, Component.translatable("screen.moveearth_addtional.nation.cancel"),
                MUTED, back.contains(mouseX, mouseY), true);
        boolean enabled = pendingRequestId < 0 && selectedRoleId != null;
        drawButton(graphics, font, assign,
                Component.translatable("screen.moveearth_addtional.nation.role.assign"), SUCCESS,
                enabled && assign.contains(mouseX, mouseY), enabled);
        if (toastTicks > 0 && toast != null) drawToast(graphics, font, width, height, toast, DANGER);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        Rect panel = panelBounds();
        if (closeBounds(panel).contains(mouseX, mouseY) || backBounds(panel).contains(mouseX, mouseY)) {
            returnToHub();
            return true;
        }
        Rect list = listBounds(panel);
        if (pendingRequestId < 0 && list.contains(mouseX, mouseY)) {
            for (int index = 0; index < roles.size(); index++) {
                Rect card = new Rect(list.x(), list.y() + index * 43 - scrollOffset, list.width() - 7, 38);
                if (card.contains(mouseX, mouseY)) {
                    selectedRoleId = roles.get(index).id();
                    return true;
                }
            }
        }
        if (pendingRequestId < 0 && selectedRoleId != null && assignBounds(panel).contains(mouseX, mouseY)) {
            pendingRequestId = ++nextRequestId;
            PacketDistributor.sendToServer(new C2S_NationRolePacket(pendingRequestId, revision,
                    C2S_NationRolePacket.Action.ASSIGN, selectedRoleId, "", 0L, member.id()));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Rect list = listBounds(panelBounds());
        if (list.contains(mouseX, mouseY)) {
            scrollOffset = MoveEarthUi.scroll(scrollOffset, scrollY, 43, roles.size() * 43, list.height());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void returnToHub() { PacketDistributor.sendToServer(new C2S_RequestS2HubPacket(S2HubTab.MEMBERS)); }
    @Override public void onClose() { returnToHub(); }
    private Rect panelBounds() {
        int panelWidth = Math.min(430, width - 20);
        int panelHeight = Math.min(300, height - 20);
        return new Rect((width - panelWidth) / 2, (height - panelHeight) / 2, panelWidth, panelHeight);
    }
    private static Rect closeBounds(Rect panel) { return new Rect(panel.right() - 28, panel.y() + 8, 20, 20); }
    private static Rect listBounds(Rect panel) { return new Rect(panel.x() + 18, panel.y() + 54, panel.width() - 36, panel.height() - 104); }
    private static Rect backBounds(Rect panel) { return new Rect(panel.x() + 18, panel.bottom() - 36, 92, 22); }
    private static Rect assignBounds(Rect panel) { return new Rect(panel.right() - 118, panel.bottom() - 36, 100, 22); }
    @Override public boolean isPauseScreen() { return false; }
}

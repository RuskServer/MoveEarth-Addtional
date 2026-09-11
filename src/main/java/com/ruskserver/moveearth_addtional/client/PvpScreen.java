package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;
import com.ruskserver.moveearth_addtional.network.C2S_ExchangeWeaponCratePacket;
import com.ruskserver.moveearth_addtional.network.C2S_PvpActionPacket;
import com.ruskserver.moveearth_addtional.network.C2S_RequestPvpTasksPacket;
import com.ruskserver.moveearth_addtional.pvp.PvpLoadoutDefinition;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

public final class PvpScreen extends Screen implements com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay {
    private static final int PANEL_WIDTH = 590;
    private static final int PANEL_HEIGHT = 340;

    private boolean joined;
    private boolean active;
    private boolean hosting;
    private boolean matchRunning;
    private int entryCount;
    private final int points;
    private final String tasks;
    private final String serverSelectionId;
    private final Map<String, ItemStack> displayGuns = new HashMap<>();
    private final List<PvpLoadoutDefinition> loadouts = new ArrayList<>();
    private String selectedId;
    private int scrollOffset = 0;

    public PvpScreen(boolean joined, boolean active, boolean hosting, boolean matchRunning, int entryCount,
                     int points, String tasks,
                     String selectedLoadoutId) {
        super(Component.translatable("screen.moveearth_addtional.pvp.title"));
        this.joined = joined;
        this.active = active;
        this.hosting = hosting;
        this.matchRunning = matchRunning;
        this.entryCount = Math.max(0, entryCount);
        this.points = points;
        this.tasks = tasks;
        this.serverSelectionId = selectedLoadoutId != null ? selectedLoadoutId : "assault";
        this.selectedId = this.serverSelectionId;
        this.loadouts.addAll(PvpClientState.getLoadouts());
    }

    public void updateLoadouts(List<PvpLoadoutDefinition> list) {
        this.loadouts.clear();
        this.loadouts.addAll(list);
        this.displayGuns.clear();
    }

    public void updateEntryState(boolean joined, boolean active, boolean hosting, boolean matchRunning,
                                 int entryCount) {
        this.joined = joined;
        this.active = active;
        this.hosting = hosting;
        this.matchRunning = matchRunning;
        this.entryCount = Math.max(0, entryCount);
    }

    private PvpLoadoutDefinition getSelectedDefinition() {
        for (PvpLoadoutDefinition def : loadouts) {
            if (def.id().equals(selectedId)) return def;
        }
        return loadouts.isEmpty() ? null : loadouts.getFirst();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Layout layout = layout();
        drawBackground(graphics, width, height);
        drawPanel(graphics, layout.bounds());

        graphics.drawString(font, title, layout.contentLeft, layout.top + 13, TEXT, false);
        graphics.drawString(font, stateText(), layout.contentLeft, layout.top + 29, stateColor(), false);
        Component entries = Component.translatable("screen.moveearth_addtional.pvp.entries", entryCount);
        graphics.drawString(font, entries, layout.contentRight - font.width(entries), layout.top + 29, MUTED, false);
        String balance = points + " WEAPON PT";
        graphics.drawString(font, balance, layout.contentRight - font.width(balance), layout.top + 14, GOLD, false);

        MoveEarthUi.Rect closeBounds = layout.closeBounds();
        drawClose(graphics, font, closeBounds, closeBounds.contains(mouseX, mouseY));

        // ロードアウト一覧カード（スクロール領域）
        renderLoadoutGrid(graphics, layout, mouseX, mouseY);

        drawSelectionDetails(graphics, layout);
        drawFooter(graphics, layout, mouseX, mouseY);
    }

    private void renderLoadoutGrid(GuiGraphics graphics, Layout layout, int mouseX, int mouseY) {
        int gridX = layout.contentLeft;
        int gridY = layout.top + 46;
        int gridWidth = layout.contentRight - layout.contentLeft;
        int gridHeight = 155;

        graphics.enableScissor(gridX, gridY, gridX + gridWidth, gridY + gridHeight);

        int cardWidth = (gridWidth - 8) / 2;
        int cardHeight = 72;

        for (int i = 0; i < loadouts.size(); i++) {
            PvpLoadoutDefinition def = loadouts.get(i);
            int col = i % 2;
            int row = i / 2;
            int cx = gridX + col * (cardWidth + 8);
            int cy = gridY + row * (cardHeight + 6) - scrollOffset;

            if (cy + cardHeight < gridY || cy > gridY + gridHeight) {
                continue;
            }

            boolean isHovered = new MoveEarthUi.Rect(cx, cy, cardWidth, cardHeight).contains(mouseX, mouseY);
            drawLoadoutCard(graphics, def, cx, cy, cardWidth, cardHeight, isHovered);
        }

        graphics.disableScissor();

        int rows = (loadouts.size() + 1) / 2;
        int totalHeight = rows * 78;
        int maxScroll = Math.max(0, totalHeight - 155);
        if (maxScroll > 0) {
            drawScrollbar(graphics, new MoveEarthUi.Rect(gridX + gridWidth + 2, gridY, 4, gridHeight),
                    gridHeight, totalHeight, scrollOffset);
        }
    }

    private void drawLoadoutCard(GuiGraphics graphics, PvpLoadoutDefinition def, int x, int y, int cardWidth, int cardHeight, boolean hovered) {
        boolean chosen = def.id().equals(selectedId);
        int accent = def.color();

        drawCard(graphics, new MoveEarthUi.Rect(x, y, cardWidth, cardHeight), accent, chosen, hovered);

        ItemStack icon = displayGuns.computeIfAbsent(def.id(), k -> createDisplayGun(def));
        if (!icon.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(x + 13, y + 23, 0);
            graphics.pose().scale(1.35F, 1.35F, 1.0F);
            graphics.renderItem(icon, 0, 0);
            graphics.pose().popPose();
        }

        int textX = x + 43;
        graphics.drawString(font, def.displayName(), textX, y + 9, accent, false);
        graphics.drawString(font, font.plainSubstrByWidth(def.weaponSummary(), cardWidth - 52), textX, y + 25, TEXT, false);
        String metrics = "TTK: " + def.bodyTtk();
        graphics.drawString(font, metrics, textX, y + 41, MUTED, false);
        if (!def.attachmentSummary().isEmpty()) {
            graphics.drawString(font, font.plainSubstrByWidth(def.attachmentSummary(), cardWidth - 52), textX, y + 55, 0xFFB5C6D8, false);
        }
        if (chosen) {
            graphics.drawString(font, "✓", x + cardWidth - 17, y + 9, accent, false);
        }
    }

    private void drawSelectionDetails(GuiGraphics graphics, Layout layout) {
        PvpLoadoutDefinition selected = getSelectedDefinition();
        if (selected == null) return;

        int y = layout.detailsTop;
        if (y + 18 >= layout.footerY) return;
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.pvp.selected"),
                layout.contentLeft, y, MUTED, false);
        graphics.drawString(font, selected.displayName(), layout.contentLeft + 62, y, selected.color(), false);

        int descriptionWidth = layout.contentRight - layout.contentLeft;
        int lineY = y + 14;
        String desc = selected.description().isEmpty() ? selected.weaponSummary() : selected.description();
        for (FormattedCharSequence line : font.split(Component.literal(desc), descriptionWidth)) {
            if (lineY + 9 >= layout.footerY - 14) break;
            graphics.drawString(font, line, layout.contentLeft, lineY, TEXT, false);
            lineY += 10;
        }
    }

    private void drawFooter(GuiGraphics graphics, Layout layout, int mouseX, int mouseY) {
        Footer footer = layout.footer(joined);
        MoveEarthUi.Rect tasksBounds = footer.tasksBounds(layout.footerY);
        drawButton(graphics, font, tasksBounds, Component.translatable("screen.moveearth_addtional.pvp.tasks"),
                SUCCESS, tasksBounds.contains(mouseX, mouseY), true);

        boolean crateEnabled = !active && points >= 100;
        MoveEarthUi.Rect crateBounds = footer.crateBounds(layout.footerY);
        drawButton(graphics, font, crateBounds, Component.translatable("screen.moveearth_addtional.pvp.crate"),
                GOLD, crateBounds.contains(mouseX, mouseY), crateEnabled);

        if (joined) {
            MoveEarthUi.Rect leaveBounds = footer.leaveBounds(layout.footerY);
            drawButton(graphics, font, leaveBounds, Component.translatable("screen.moveearth_addtional.pvp.leave"),
                    DANGER, leaveBounds.contains(mouseX, mouseY), true);
        }

        boolean selectionChanged = !selectedId.equals(serverSelectionId);
        boolean actionEnabled = hosting && (!joined || selectionChanged);
        MoveEarthUi.Rect actionBounds = footer.actionBounds(layout.footerY);
        Component actionText = !hosting
                ? Component.translatable("screen.moveearth_addtional.pvp.closed")
                : active && !selectionChanged
                ? Component.translatable("screen.moveearth_addtional.pvp.in_match")
                : joined && !selectionChanged
                ? Component.translatable("screen.moveearth_addtional.pvp.registered")
                : joined
                ? Component.translatable("screen.moveearth_addtional.pvp.change")
                : matchRunning
                ? Component.translatable("screen.moveearth_addtional.pvp.join_running")
                : Component.translatable("screen.moveearth_addtional.pvp.join");
        drawButton(graphics, font, actionBounds, actionText, ACCENT,
                actionBounds.contains(mouseX, mouseY), actionEnabled);

        if (!tasks.isBlank() && layout.footerY - 13 > layout.detailsTop) {
            String summary = font.plainSubstrByWidth(tasks, layout.contentRight - layout.contentLeft);
            graphics.drawString(font, summary, layout.contentLeft, layout.footerY - 12, MUTED, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        Layout layout = layout();
        if (layout.closeBounds().contains(mouseX, mouseY)) {
            onClose();
            return true;
        }

        // カードクリック判定
        int gridX = layout.contentLeft;
        int gridY = layout.top + 46;
        int gridWidth = layout.contentRight - layout.contentLeft;
        int gridHeight = 155;

        if (mouseX >= gridX && mouseX <= gridX + gridWidth && mouseY >= gridY && mouseY <= gridY + gridHeight) {
            int cardWidth = (gridWidth - 8) / 2;
            int cardHeight = 72;
            for (int i = 0; i < loadouts.size(); i++) {
                int col = i % 2;
                int row = i / 2;
                int cx = gridX + col * (cardWidth + 8);
                int cy = gridY + row * (cardHeight + 6) - scrollOffset;
                if (new MoveEarthUi.Rect(cx, cy, cardWidth, cardHeight).contains(mouseX, mouseY)) {
                    selectedId = loadouts.get(i).id();
                    return true;
                }
            }
        }

        Footer footer = layout.footer(joined);
        if (footer.tasksBounds(layout.footerY).contains(mouseX, mouseY)) {
            PacketDistributor.sendToServer(new C2S_RequestPvpTasksPacket());
            return true;
        }
        if (footer.crateBounds(layout.footerY).contains(mouseX, mouseY)) {
            if (!active && points >= 100) {
                PacketDistributor.sendToServer(new C2S_ExchangeWeaponCratePacket());
                onClose();
            }
            return true;
        }
        if (joined && footer.leaveBounds(layout.footerY).contains(mouseX, mouseY)) {
            PacketDistributor.sendToServer(new C2S_PvpActionPacket(false, selectedId));
            onClose();
            return true;
        }
        if (footer.actionBounds(layout.footerY).contains(mouseX, mouseY)) {
            if (hosting && (!joined || !selectedId.equals(serverSelectionId))) {
                PacketDistributor.sendToServer(new C2S_PvpActionPacket(true, selectedId));
                onClose();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int rows = (loadouts.size() + 1) / 2;
        int totalHeight = rows * 78;
        scrollOffset = scroll(scrollOffset, scrollY, 24, totalHeight, 155);
        return true;
    }

    private ItemStack createDisplayGun(PvpLoadoutDefinition def) {
        if (def.primary() == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(com.tacz.guns.init.ModItems.MODERN_KINETIC_GUN.get());
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return ItemStack.EMPTY;
        gun.setGunId(stack, def.primary().gunId());
        return stack;
    }

    private Component stateText() {
        if (active) return Component.translatable("screen.moveearth_addtional.pvp.state.active");
        if (hosting) return Component.translatable("screen.moveearth_addtional.pvp.state.hosting");
        return Component.translatable("screen.moveearth_addtional.pvp.state.closed");
    }

    private int stateColor() {
        if (active) return ACCENT;
        if (hosting) return SUCCESS;
        return DANGER;
    }

    private Layout layout() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        int contentLeft = left + 18;
        int contentRight = left + PANEL_WIDTH - 18;
        int detailsTop = top + 210;
        int footerY = top + PANEL_HEIGHT - 44;
        return new Layout(left, top, PANEL_WIDTH, PANEL_HEIGHT, contentLeft, contentRight, detailsTop, footerY);
    }

    private record Layout(int left, int top, int panelWidth, int panelHeight, int contentLeft, int contentRight,
                          int detailsTop, int footerY) {
        int right() { return left + panelWidth; }
        int bottom() { return top + panelHeight; }
        MoveEarthUi.Rect bounds() { return new MoveEarthUi.Rect(left, top, panelWidth, panelHeight); }
        MoveEarthUi.Rect closeBounds() { return new MoveEarthUi.Rect(right() - 28, top + 8, 20, 20); }

        Footer footer(boolean joined) {
            int gap = 8;
            int tasksWidth = 84;
            int crateWidth = 98;
            int leaveWidth = joined ? 74 : 0;
            int reserved = tasksWidth + crateWidth + (joined ? leaveWidth + gap : 0) + gap * 2;
            int actionWidth = contentRight - contentLeft - reserved;
            int tasksX = contentLeft;
            int crateX = tasksX + tasksWidth + gap;
            int leaveX = crateX + crateWidth + gap;
            int actionX = joined ? leaveX + leaveWidth + gap : crateX + crateWidth + gap;
            return new Footer(tasksX, tasksWidth, crateX, crateWidth, leaveX, leaveWidth, actionX, actionWidth);
        }
    }

    private record Footer(int tasksX, int tasksWidth, int crateX, int crateWidth, int leaveX, int leaveWidth,
                          int actionX, int actionWidth) {
        MoveEarthUi.Rect tasksBounds(int y) { return new MoveEarthUi.Rect(tasksX, y, tasksWidth, 28); }
        MoveEarthUi.Rect crateBounds(int y) { return new MoveEarthUi.Rect(crateX, y, crateWidth, 28); }
        MoveEarthUi.Rect leaveBounds(int y) { return new MoveEarthUi.Rect(leaveX, y, leaveWidth, 28); }
        MoveEarthUi.Rect actionBounds(int y) { return new MoveEarthUi.Rect(actionX, y, actionWidth, 28); }
    }
}

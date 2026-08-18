package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.network.C2S_PvpActionPacket;
import com.ruskserver.moveearth_addtional.network.C2S_ExchangeWeaponCratePacket;
import com.ruskserver.moveearth_addtional.network.C2S_RequestPvpTasksPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PvpScreen extends Screen {
    private static final int PANEL_WIDTH = 500;
    private static final int PANEL_HEIGHT = 270;
    private static final int TEXT = 0xFFE8EDF3;
    private static final int MUTED = 0xFF8F9AA8;
    private static final int PANEL = 0xF012161D;
    private static final int CARD = 0xFF1B222C;
    private static final int CARD_HOVER = 0xFF242E3B;
    private static final int ACCENT = 0xFF5DCBFF;
    private final boolean joined;
    private final boolean active;
    private final boolean hosting;
    private final int points;
    private final String tasks;
    private int selectedSlot;

    public PvpScreen(boolean joined, boolean active, boolean hosting, int points, String tasks) {
        super(Component.literal("KOTH PvP EVENT"));
        this.joined = joined;
        this.active = active;
        this.hosting = hosting;
        this.points = points;
        this.tasks = tasks;
        this.selectedSlot = minecraft != null && minecraft.player != null ? minecraft.player.getInventory().selected : 0;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // StatsScreenと同じ独自背景を描画する。
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width, height, 0xD0080B10, 0xE010151D);
        int panelWidth = Math.min(PANEL_WIDTH, width - 20);
        int panelHeight = Math.min(PANEL_HEIGHT, height - 20);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        graphics.fill(left, top, left + panelWidth, top + panelHeight, PANEL);
        drawBorder(graphics, left, top, panelWidth, panelHeight, 0xFF354150);
        graphics.drawString(font, title, left + 16, top + 14, TEXT, false);
        String state = active ? "KOTH試合中" : joined ? "PvPキュー参加中" : hosting ? "持ち込むTaCZ銃をホットバーから選択" : "現在イベントは開催されていません";
        graphics.drawString(font, state, left + 16, top + 29,
                joined ? 0xFF68E09B : hosting ? ACCENT : 0xFFFF6577, false);
        String balance = points + " WEAPON PT";
        graphics.drawString(font, balance, left + panelWidth - 45 - font.width(balance), top + 15, 0xFFFFB454, false);

        boolean closeHovered = inside(mouseX, mouseY, left + panelWidth - 28, top + 8, 20, 20);
        graphics.drawCenteredString(font, "×", left + panelWidth - 18, top + 14,
                closeHovered ? 0xFFFF6577 : MUTED);

        int contentLeft = left + 16;
        int contentRight = left + panelWidth - 16;
        int contentTop = top + 55;
        if (!joined) {
            int gap = 6;
            int slotWidth = (contentRight - contentLeft - gap * 8) / 9;
            for (int slot = 0; slot < 9; slot++) {
                int x = contentLeft + slot * (slotWidth + gap);
                boolean hovered = inside(mouseX, mouseY, x, contentTop, slotWidth, 62);
                int color = slot == selectedSlot ? 0xFF26394A : hovered ? CARD_HOVER : CARD;
                graphics.fill(x, contentTop, x + slotWidth, contentTop + 62, color);
                graphics.fill(x, contentTop, x + slotWidth, contentTop + 3, slot == selectedSlot ? ACCENT : 0xFF354150);
                drawBorder(graphics, x, contentTop, slotWidth, 62, slot == selectedSlot ? ACCENT : 0xFF28323E);
                graphics.drawCenteredString(font, Integer.toString(slot + 1), x + slotWidth / 2, contentTop + 7, MUTED);
                ItemStack stack = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getInventory().getItem(slot);
                if (!stack.isEmpty()) {
                    int itemX = x + (slotWidth - 16) / 2;
                    graphics.renderItem(stack, itemX, contentTop + 27);
                    graphics.renderItemDecorations(font, stack, itemX, contentTop + 27);
                    if (hovered) graphics.renderTooltip(font, stack, mouseX, mouseY);
                }
            }
            ItemStack selected = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getInventory().getItem(selectedSlot);
            String selectedName = selected.isEmpty() ? "未選択" : selected.getHoverName().getString();
            graphics.drawString(font, "選択中", contentLeft, contentTop + 78, MUTED, false);
            graphics.drawString(font, selectedName, contentLeft, contentTop + 94, TEXT, false);
        } else {
            graphics.fill(contentLeft, contentTop, contentRight, contentTop + 112, CARD);
            graphics.fill(contentLeft, contentTop, contentLeft + 4, contentTop + 112, 0xFF68E09B);
            graphics.drawString(font, "KOTH MATCH", contentLeft + 16, contentTop + 16, 0xFF68E09B, false);
            graphics.drawString(font, active ? "現在KOTH試合に参加しています。" : "キューへ登録済みです。", contentLeft + 16, contentTop + 40, TEXT, false);
            graphics.drawString(font, active ? "退出時に元の所持品と状態を復元します。" : "開始までは現在地で通常どおり行動できます。", contentLeft + 16, contentTop + 60, MUTED, false);
        }

        int buttonX = left + panelWidth - 176;
        int buttonY = top + panelHeight - 48;
        boolean actionHovered = inside(mouseX, mouseY, buttonX, buttonY, 160, 28);
        int accent = joined ? 0xFFFF6577 : 0xFF5DCBFF;
        graphics.fill(buttonX, buttonY, buttonX + 160, buttonY + 28, actionHovered ? CARD_HOVER : CARD);
        graphics.fill(buttonX, buttonY, buttonX + 3, buttonY + 28, accent);
        drawBorder(graphics, buttonX, buttonY, 160, 28, actionHovered ? accent : 0xFF354150);
        graphics.drawCenteredString(font, joined ? "イベントから退出" : hosting ? "この銃で参加" : "受付停止中", buttonX + 80, buttonY + 10,
                !joined && !hosting ? MUTED : accent);
        boolean crateHovered = inside(mouseX, mouseY, left + 16, buttonY, 180, 28);
        graphics.fill(left + 16, buttonY, left + 196, buttonY + 28, crateHovered ? CARD_HOVER : CARD);
        graphics.fill(left + 16, buttonY, left + 19, buttonY + 28, 0xFFFFB454);
        drawBorder(graphics, left + 16, buttonY, 180, 28, crateHovered ? 0xFFFFB454 : 0xFF354150);
        graphics.drawCenteredString(font, "武器箱と交換 (100pt)", left + 106, buttonY + 10,
                points >= 100 && !active ? 0xFFFFB454 : MUTED);
        boolean tasksHovered = inside(mouseX, mouseY, left + 204, buttonY, 100, 28);
        graphics.fill(left + 204, buttonY, left + 304, buttonY + 28, tasksHovered ? CARD_HOVER : CARD);
        graphics.fill(left + 204, buttonY, left + 207, buttonY + 28, 0xFF68E09B);
        drawBorder(graphics, left + 204, buttonY, 100, 28, tasksHovered ? 0xFF68E09B : 0xFF354150);
        graphics.drawCenteredString(font, "タスク", left + 254, buttonY + 10, 0xFF68E09B);
        graphics.drawString(font, tasks, left + 16, buttonY - 16, MUTED, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int panelWidth = Math.min(PANEL_WIDTH, width - 20);
        int panelHeight = Math.min(PANEL_HEIGHT, height - 20);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        if (inside(mouseX, mouseY, left + panelWidth - 28, top + 8, 20, 20)) { onClose(); return true; }
        int contentLeft = left + 16;
        int contentRight = left + panelWidth - 16;
        int contentTop = top + 55;
        if (!joined) {
            int gap = 6;
            int slotWidth = (contentRight - contentLeft - gap * 8) / 9;
            for (int slot = 0; slot < 9; slot++) {
                int x = contentLeft + slot * (slotWidth + gap);
                if (inside(mouseX, mouseY, x, contentTop, slotWidth, 62)) { selectedSlot = slot; return true; }
            }
        }
        int buttonX = left + panelWidth - 176;
        int buttonY = top + panelHeight - 48;
        if (inside(mouseX, mouseY, buttonX, buttonY, 160, 28)) {
            if (!joined && !hosting) return true;
            PacketDistributor.sendToServer(new C2S_PvpActionPacket(!joined, selectedSlot));
            onClose();
            return true;
        }
        if (inside(mouseX, mouseY, left + 16, buttonY, 180, 28)) {
            if (active) return true;
            PacketDistributor.sendToServer(new C2S_ExchangeWeaponCratePacket());
            onClose();
            return true;
        }
        if (inside(mouseX, mouseY, left + 204, buttonY, 100, 28)) {
            PacketDistributor.sendToServer(new C2S_RequestPvpTasksPacket());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    @Override public boolean isPauseScreen() { return false; }
}

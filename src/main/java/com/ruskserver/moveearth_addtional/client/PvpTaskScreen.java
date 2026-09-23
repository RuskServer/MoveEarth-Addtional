package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;
import com.ruskserver.moveearth_addtional.network.C2S_ClaimPvpTaskPacket;
import com.ruskserver.moveearth_addtional.network.S2C_OpenPvpTasksPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

public final class PvpTaskScreen extends Screen implements com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay {
    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_HEIGHT = 290;
    private static String lastCategory = "DAILY";

    private S2C_OpenPvpTasksPacket packet;
    private String category = lastCategory;
    private int page;

    public PvpTaskScreen(S2C_OpenPvpTasksPacket packet) {
        super(Component.literal("PvP TASKS"));
        this.packet = packet;
    }

    public void update(S2C_OpenPvpTasksPacket packet) {
        this.packet = packet;
        page = Math.max(0, Math.min(page, pageCount() - 1));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(graphics, width, height);
        int panelWidth = Math.min(PANEL_WIDTH, width - 20);
        int panelHeight = Math.min(PANEL_HEIGHT, height - 20);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        drawPanel(graphics, new MoveEarthUi.Rect(left, top, panelWidth, panelHeight));

        graphics.drawString(font, title, left + 16, top + 14, TEXT, false);
        String points = packet.points() + " WEAPON PT";
        graphics.drawString(font, points, left + panelWidth - 44 - font.width(points), top + 15, 0xFFFFB454, false);
        MoveEarthUi.Rect closeBounds = new MoveEarthUi.Rect(left + panelWidth - 28, top + 8, 20, 20);
        drawClose(graphics, font, closeBounds, closeBounds.contains(mouseX, mouseY));

        drawTab(graphics, mouseX, mouseY, left + 16, top + 37, 92, "DAILY", "デイリー");
        drawTab(graphics, mouseX, mouseY, left + 114, top + 37, 92, "EVENT", "イベント");
        if ("DAILY".equals(category)) {
            graphics.drawString(font, "毎日19:00 (JST) 更新", left + 218, top + 46, MUTED, false);
        } else {
            graphics.drawString(font, "開催開始ごとに更新", left + 218, top + 46, MUTED, false);
        }

        List<S2C_OpenPvpTasksPacket.TaskEntry> visible = visibleTasks();
        int cardTop = top + 70;
        int cardHeight = Math.max(68, Math.min(82, (panelHeight - 116) / pageSize()));
        for (int index = 0; index < visible.size(); index++) {
            int y = cardTop + index * (cardHeight + 7);
            drawTask(graphics, visible.get(index), left + 16, y, panelWidth - 32, cardHeight, mouseX, mouseY);
        }

        int footerY = top + panelHeight - 27;
        int pageCount = pageCount();
        String pageText = (page + 1) + " / " + Math.max(1, pageCount);
        graphics.drawCenteredString(font, pageText, left + panelWidth / 2, footerY + 7, MUTED);
        drawPager(graphics, left + panelWidth / 2 - 62, footerY, "‹", page > 0, mouseX, mouseY);
        drawPager(graphics, left + panelWidth / 2 + 42, footerY, "›", page + 1 < pageCount, mouseX, mouseY);
    }

    private void drawTab(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width,
                         String id, String label) {
        boolean selected = id.equals(category);
        MoveEarthUi.Rect bounds = new MoveEarthUi.Rect(x, y, width, 25);
        MoveEarthUi.drawTab(graphics, font, bounds, Component.literal(label), selected,
                bounds.contains(mouseX, mouseY));
    }

    private void drawTask(GuiGraphics graphics, S2C_OpenPvpTasksPacket.TaskEntry task,
                          int x, int y, int width, int height, int mouseX, int mouseY) {
        MoveEarthUi.Rect claimBounds = new MoveEarthUi.Rect(x + width - 91, y + height - 28, 75, 20);
        boolean claimHovered = claimBounds.contains(mouseX, mouseY);
        int stripe = task.claimed() ? 0xFF56616D : task.complete() ? 0xFF68E09B : ACCENT;
        drawCard(graphics, new MoveEarthUi.Rect(x, y, width, height), stripe, false, false);
        graphics.drawString(font, task.title(), x + 14, y + 9, task.complete() ? 0xFF68E09B : TEXT, false);
        graphics.drawString(font, task.description(), x + 14, y + 23, MUTED, false);

        int barX = x + 14;
        int barY = y + height - 19;
        int barWidth = Math.max(70, width - 230);
        graphics.fill(barX, barY, barX + barWidth, barY + 6, BAR_BACKGROUND);
        int filled = (int) (barWidth * Math.min(1.0D, task.progress() / (double) Math.max(1, task.target())));
        graphics.fill(barX, barY, barX + filled, barY + 6, stripe);
        String progress = task.progress() + " / " + task.target();
        graphics.drawString(font, progress, barX + barWidth + 7, barY - 1, TEXT, false);

        var item = BuiltInRegistries.ITEM.get(task.itemReward());
        ItemStack reward = item != null ? new ItemStack(item, task.itemCount()) : ItemStack.EMPTY;
        int itemX = x + width - 171;
        if (!reward.isEmpty()) {
            graphics.renderItem(reward, itemX, y + height - 30);
            graphics.renderItemDecorations(font, reward, itemX, y + height - 30);
        }
        graphics.drawString(font, "+" + task.pointReward() + "pt", itemX + 20, y + height - 23, 0xFFFFB454, false);

        drawButton(graphics, font, claimBounds,
                Component.literal(task.claimed() ? "受取済" : task.complete() ? "受取" : "進行中"),
                SUCCESS, claimHovered, task.complete() && !task.claimed());

        if (!reward.isEmpty()
                && new MoveEarthUi.Rect(itemX, y + height - 30, 18, 18).contains(mouseX, mouseY)) {
            graphics.renderTooltip(font, reward, mouseX, mouseY);
        }
    }

    private void drawPager(GuiGraphics graphics, int x, int y, String text, boolean enabled,
                           int mouseX, int mouseY) {
        MoveEarthUi.Rect bounds = new MoveEarthUi.Rect(x, y, 20, 20);
        drawButton(graphics, font, bounds, Component.literal(text), ACCENT,
                enabled && bounds.contains(mouseX, mouseY), enabled);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int panelWidth = Math.min(PANEL_WIDTH, width - 20);
        int panelHeight = Math.min(PANEL_HEIGHT, height - 20);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        if (new MoveEarthUi.Rect(left + panelWidth - 28, top + 8, 20, 20).contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (new MoveEarthUi.Rect(left + 16, top + 37, 92, 25).contains(mouseX, mouseY)) {
            selectCategory("DAILY");
            return true;
        }
        if (new MoveEarthUi.Rect(left + 114, top + 37, 92, 25).contains(mouseX, mouseY)) {
            selectCategory("EVENT");
            return true;
        }

        List<S2C_OpenPvpTasksPacket.TaskEntry> visible = visibleTasks();
        int cardTop = top + 70;
        int cardHeight = Math.max(68, Math.min(82, (panelHeight - 116) / pageSize()));
        for (int index = 0; index < visible.size(); index++) {
            S2C_OpenPvpTasksPacket.TaskEntry task = visible.get(index);
            int y = cardTop + index * (cardHeight + 7);
            if (task.complete() && !task.claimed()
                    && new MoveEarthUi.Rect(left + panelWidth - 107, y + cardHeight - 28, 75, 20)
                    .contains(mouseX, mouseY)) {
                PacketDistributor.sendToServer(new C2S_ClaimPvpTaskPacket(task.id()));
                return true;
            }
        }

        int footerY = top + panelHeight - 27;
        if (page > 0 && new MoveEarthUi.Rect(left + panelWidth / 2 - 62, footerY, 20, 20)
                .contains(mouseX, mouseY)) {
            page--;
            return true;
        }
        if (page + 1 < pageCount()
                && new MoveEarthUi.Rect(left + panelWidth / 2 + 42, footerY, 20, 20)
                .contains(mouseX, mouseY)) {
            page++;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void selectCategory(String category) {
        this.category = category;
        lastCategory = category;
        page = 0;
    }

    private List<S2C_OpenPvpTasksPacket.TaskEntry> categoryTasks() {
        return packet.tasks().stream().filter(task -> task.category().equals(category)).toList();
    }

    private List<S2C_OpenPvpTasksPacket.TaskEntry> visibleTasks() {
        List<S2C_OpenPvpTasksPacket.TaskEntry> all = categoryTasks();
        int pageSize = pageSize();
        int from = Math.min(page * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());
        return all.subList(from, to);
    }

    private int pageCount() {
        int pageSize = pageSize();
        return Math.max(1, (categoryTasks().size() + pageSize - 1) / pageSize);
    }

    private int pageSize() {
        return Math.min(PANEL_HEIGHT, height - 20) < 260 ? 1 : 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.network.C2S_ClaimPvpTaskPacket;
import com.ruskserver.moveearth_addtional.network.S2C_OpenPvpTasksPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class PvpTaskScreen extends Screen {
    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_HEIGHT = 290;
    private static final int TEXT = 0xFFE8EDF3;
    private static final int MUTED = 0xFF8F9AA8;
    private static final int PANEL = 0xF012161D;
    private static final int CARD = 0xFF1B222C;
    private static final int CARD_HOVER = 0xFF242E3B;
    private static final int ACCENT = 0xFF5DCBFF;
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
        page = Mth.clamp(page, 0, Math.max(0, pageCount() - 1));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
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
        String points = packet.points() + " WEAPON PT";
        graphics.drawString(font, points, left + panelWidth - 44 - font.width(points), top + 15, 0xFFFFB454, false);
        boolean closeHovered = inside(mouseX, mouseY, left + panelWidth - 28, top + 8, 20, 20);
        graphics.drawCenteredString(font, "×", left + panelWidth - 18, top + 14, closeHovered ? 0xFFFF6577 : MUTED);

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
        boolean hovered = inside(mouseX, mouseY, x, y, width, 25);
        graphics.fill(x, y, x + width, y + 25, selected ? 0xFF26394A : hovered ? CARD_HOVER : CARD);
        graphics.fill(x, y + 22, x + width, y + 25, selected ? ACCENT : 0xFF354150);
        graphics.drawCenteredString(font, label, x + width / 2, y + 8, selected ? ACCENT : TEXT);
    }

    private void drawTask(GuiGraphics graphics, S2C_OpenPvpTasksPacket.TaskEntry task,
                          int x, int y, int width, int height, int mouseX, int mouseY) {
        boolean claimHovered = inside(mouseX, mouseY, x + width - 91, y + height - 28, 75, 20);
        int stripe = task.claimed() ? 0xFF56616D : task.complete() ? 0xFF68E09B : ACCENT;
        graphics.fill(x, y, x + width, y + height, CARD);
        graphics.fill(x, y, x + 4, y + height, stripe);
        drawBorder(graphics, x, y, width, height, 0xFF354150);
        graphics.drawString(font, task.title(), x + 14, y + 9, task.complete() ? 0xFF68E09B : TEXT, false);
        graphics.drawString(font, task.description(), x + 14, y + 23, MUTED, false);

        int barX = x + 14;
        int barY = y + height - 19;
        int barWidth = Math.max(70, width - 230);
        graphics.fill(barX, barY, barX + barWidth, barY + 6, 0xFF0C1015);
        int filled = (int) (barWidth * Math.min(1.0D, task.progress() / (double) Math.max(1, task.target())));
        graphics.fill(barX, barY, barX + filled, barY + 6, stripe);
        String progress = task.progress() + " / " + task.target();
        graphics.drawString(font, progress, barX + barWidth + 7, barY - 1, TEXT, false);

        ItemStack reward = new ItemStack(BuiltInRegistries.ITEM.get(task.itemReward()), task.itemCount());
        int itemX = x + width - 171;
        graphics.renderItem(reward, itemX, y + height - 30);
        graphics.renderItemDecorations(font, reward, itemX, y + height - 30);
        graphics.drawString(font, "+" + task.pointReward() + "pt", itemX + 20, y + height - 23, 0xFFFFB454, false);

        int buttonColor = task.claimed() ? 0xFF20262E
                : task.complete() ? (claimHovered ? 0xFF315A48 : 0xFF27483A) : 0xFF20262E;
        graphics.fill(x + width - 91, y + height - 28, x + width - 16, y + height - 8, buttonColor);
        drawBorder(graphics, x + width - 91, y + height - 28, 75, 20,
                task.complete() && !task.claimed() ? 0xFF68E09B : 0xFF354150);
        graphics.drawCenteredString(font, task.claimed() ? "受取済" : task.complete() ? "受取" : "進行中",
                x + width - 54, y + height - 21,
                task.complete() && !task.claimed() ? 0xFF68E09B : MUTED);

        if (inside(mouseX, mouseY, itemX, y + height - 30, 18, 18)) {
            graphics.renderTooltip(font, reward, mouseX, mouseY);
        }
    }

    private void drawPager(GuiGraphics graphics, int x, int y, String text, boolean enabled,
                           int mouseX, int mouseY) {
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, 20, 20);
        graphics.fill(x, y, x + 20, y + 20, hovered ? CARD_HOVER : CARD);
        drawBorder(graphics, x, y, 20, 20, enabled ? 0xFF354150 : 0xFF20262E);
        graphics.drawCenteredString(font, text, x + 10, y + 6, enabled ? TEXT : 0xFF4C5662);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int panelWidth = Math.min(PANEL_WIDTH, width - 20);
        int panelHeight = Math.min(PANEL_HEIGHT, height - 20);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        if (inside(mouseX, mouseY, left + panelWidth - 28, top + 8, 20, 20)) {
            onClose();
            return true;
        }
        if (inside(mouseX, mouseY, left + 16, top + 37, 92, 25)) {
            selectCategory("DAILY");
            return true;
        }
        if (inside(mouseX, mouseY, left + 114, top + 37, 92, 25)) {
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
                    && inside(mouseX, mouseY, left + panelWidth - 107, y + cardHeight - 28, 75, 20)) {
                PacketDistributor.sendToServer(new C2S_ClaimPvpTaskPacket(task.id()));
                return true;
            }
        }

        int footerY = top + panelHeight - 27;
        if (page > 0 && inside(mouseX, mouseY, left + panelWidth / 2 - 62, footerY, 20, 20)) {
            page--;
            return true;
        }
        if (page + 1 < pageCount()
                && inside(mouseX, mouseY, left + panelWidth / 2 + 42, footerY, 20, 20)) {
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

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

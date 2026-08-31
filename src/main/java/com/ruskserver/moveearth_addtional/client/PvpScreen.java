package com.ruskserver.moveearth_addtional.client;

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
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PvpScreen extends Screen {
    private static final int PANEL_WIDTH = 590;
    private static final int PANEL_HEIGHT = 340;
    private static final int TEXT = 0xFFE8EDF3;
    private static final int MUTED = 0xFF8F9AA8;
    private static final int PANEL = 0xF012161D;
    private static final int CARD = 0xFF1B222C;
    private static final int CARD_HOVER = 0xFF242E3B;
    private static final int ACCENT = 0xFF5DCBFF;
    private static final int SUCCESS = 0xFF68E09B;
    private static final int DANGER = 0xFFFF6577;
    private static final int GOLD = 0xFFFFB454;

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
        graphics.fillGradient(0, 0, width, height, 0xD0080B10, 0xE010151D);
        graphics.fill(layout.left, layout.top, layout.right(), layout.bottom(), PANEL);
        drawBorder(graphics, layout.left, layout.top, layout.panelWidth, layout.panelHeight, 0xFF354150);

        graphics.drawString(font, title, layout.contentLeft, layout.top + 13, TEXT, false);
        graphics.drawString(font, stateText(), layout.contentLeft, layout.top + 29, stateColor(), false);
        Component entries = Component.translatable("screen.moveearth_addtional.pvp.entries", entryCount);
        graphics.drawString(font, entries, layout.contentRight - font.width(entries), layout.top + 29, MUTED, false);
        String balance = points + " WEAPON PT";
        graphics.drawString(font, balance, layout.contentRight - font.width(balance), layout.top + 14, GOLD, false);

        boolean closeHovered = inside(mouseX, mouseY, layout.right() - 28, layout.top + 8, 20, 20);
        graphics.drawCenteredString(font, "×", layout.right() - 18, layout.top + 14,
                closeHovered ? DANGER : MUTED);

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

            boolean isHovered = inside(mouseX, mouseY, cx, cy, cardWidth, cardHeight);
            drawLoadoutCard(graphics, def, cx, cy, cardWidth, cardHeight, isHovered);
        }

        graphics.disableScissor();

        int rows = (loadouts.size() + 1) / 2;
        int totalHeight = rows * 78;
        int maxScroll = Math.max(0, totalHeight - 155);
        if (maxScroll > 0) {
            int scrollBarX = gridX + gridWidth + 2;
            int scrollBarY = gridY;
            int scrollBarHeight = gridHeight;
            int thumbHeight = Math.max(20, (int) ((float) gridHeight / totalHeight * gridHeight));
            int thumbY = gridY + (int) ((float) scrollOffset / maxScroll * (gridHeight - thumbHeight));

            graphics.fill(scrollBarX, scrollBarY, scrollBarX + 4, scrollBarY + scrollBarHeight, 0xFF202832);
            graphics.fill(scrollBarX, thumbY, scrollBarX + 4, thumbY + thumbHeight, 0xFF536173);
        }
    }

    private void drawLoadoutCard(GuiGraphics graphics, PvpLoadoutDefinition def, int x, int y, int cardWidth, int cardHeight, boolean hovered) {
        boolean chosen = def.id().equals(selectedId);
        int accent = def.color();

        graphics.fill(x, y, x + cardWidth, y + cardHeight,
                chosen ? 0xFF263542 : hovered ? CARD_HOVER : CARD);
        graphics.fill(x, y, x + (chosen ? 4 : 2), y + cardHeight,
                chosen ? accent : 0xFF354150);
        drawBorder(graphics, x, y, cardWidth, cardHeight,
                chosen ? accent : hovered ? 0xFF536173 : 0xFF28323E);

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
        boolean tasksHovered = inside(mouseX, mouseY, footer.tasksX, layout.footerY, footer.tasksWidth, 28);
        drawButton(graphics, footer.tasksX, layout.footerY, footer.tasksWidth, 28, SUCCESS,
                Component.translatable("screen.moveearth_addtional.pvp.tasks"), tasksHovered, true);

        boolean crateEnabled = !active && points >= 100;
        boolean crateHovered = inside(mouseX, mouseY, footer.crateX, layout.footerY, footer.crateWidth, 28);
        drawButton(graphics, footer.crateX, layout.footerY, footer.crateWidth, 28, GOLD,
                Component.translatable("screen.moveearth_addtional.pvp.crate"), crateHovered, crateEnabled);

        if (joined) {
            boolean leaveHovered = inside(mouseX, mouseY, footer.leaveX, layout.footerY, footer.leaveWidth, 28);
            drawButton(graphics, footer.leaveX, layout.footerY, footer.leaveWidth, 28, DANGER,
                    Component.translatable("screen.moveearth_addtional.pvp.leave"), leaveHovered, true);
        }

        boolean selectionChanged = !selectedId.equals(serverSelectionId);
        boolean actionEnabled = hosting && (!joined || selectionChanged);
        boolean actionHovered = inside(mouseX, mouseY, footer.actionX, layout.footerY, footer.actionWidth, 28);
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
        drawButton(graphics, footer.actionX, layout.footerY, footer.actionWidth, 28, ACCENT,
                actionText, actionHovered, actionEnabled);

        if (!tasks.isBlank() && layout.footerY - 13 > layout.detailsTop) {
            String summary = font.plainSubstrByWidth(tasks, layout.contentRight - layout.contentLeft);
            graphics.drawString(font, summary, layout.contentLeft, layout.footerY - 12, MUTED, false);
        }
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int buttonWidth, int buttonHeight, int accent,
                            Component label, boolean hovered, boolean enabled) {
        graphics.fill(x, y, x + buttonWidth, y + buttonHeight, hovered && enabled ? CARD_HOVER : CARD);
        graphics.fill(x, y, x + 3, y + buttonHeight, enabled ? accent : 0xFF4A515B);
        drawBorder(graphics, x, y, buttonWidth, buttonHeight,
                hovered && enabled ? accent : 0xFF354150);
        graphics.drawCenteredString(font, label, x + buttonWidth / 2, y + 10, enabled ? accent : MUTED);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        Layout layout = layout();
        if (inside(mouseX, mouseY, layout.right() - 28, layout.top + 8, 20, 20)) {
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
                if (inside(mouseX, mouseY, cx, cy, cardWidth, cardHeight)) {
                    selectedId = loadouts.get(i).id();
                    return true;
                }
            }
        }

        Footer footer = layout.footer(joined);
        if (inside(mouseX, mouseY, footer.tasksX, layout.footerY, footer.tasksWidth, 28)) {
            PacketDistributor.sendToServer(new C2S_RequestPvpTasksPacket());
            return true;
        }
        if (inside(mouseX, mouseY, footer.crateX, layout.footerY, footer.crateWidth, 28)) {
            if (!active && points >= 100) {
                PacketDistributor.sendToServer(new C2S_ExchangeWeaponCratePacket());
                onClose();
            }
            return true;
        }
        if (joined && inside(mouseX, mouseY, footer.leaveX, layout.footerY, footer.leaveWidth, 28)) {
            PacketDistributor.sendToServer(new C2S_PvpActionPacket(false, selectedId));
            onClose();
            return true;
        }
        if (inside(mouseX, mouseY, footer.actionX, layout.footerY, footer.actionWidth, 28)) {
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
        int maxScroll = Math.max(0, totalHeight - 155);
        scrollOffset = Mth.clamp(scrollOffset - (int) (scrollY * 24), 0, maxScroll);
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

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
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
                          int actionX, int actionWidth) {}
}

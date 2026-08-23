package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.network.C2S_ExchangeWeaponCratePacket;
import com.ruskserver.moveearth_addtional.network.C2S_PvpActionPacket;
import com.ruskserver.moveearth_addtional.network.C2S_RequestPvpTasksPacket;
import com.ruskserver.moveearth_addtional.pvp.PvpLoadoutPreset;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.EnumMap;
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

    private final boolean joined;
    private final boolean active;
    private final boolean hosting;
    private final int points;
    private final String tasks;
    private final PvpLoadoutPreset serverSelection;
    private final Map<PvpLoadoutPreset, ItemStack> displayGuns = new EnumMap<>(PvpLoadoutPreset.class);
    private PvpLoadoutPreset selected;

    public PvpScreen(boolean joined, boolean active, boolean hosting, int points, String tasks,
                     String selectedLoadoutId) {
        super(Component.translatable("screen.moveearth_addtional.pvp.title"));
        this.joined = joined;
        this.active = active;
        this.hosting = hosting;
        this.points = points;
        this.tasks = tasks;
        this.serverSelection = PvpLoadoutPreset.byId(selectedLoadoutId)
                .orElse(PvpLoadoutPreset.defaultPreset());
        this.selected = serverSelection;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The screen renders its own opaque event panel over the game view.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Layout layout = layout();
        graphics.fillGradient(0, 0, width, height, 0xD0080B10, 0xE010151D);
        graphics.fill(layout.left, layout.top, layout.right(), layout.bottom(), PANEL);
        drawBorder(graphics, layout.left, layout.top, layout.panelWidth, layout.panelHeight, 0xFF354150);

        graphics.drawString(font, title, layout.contentLeft, layout.top + 13, TEXT, false);
        graphics.drawString(font, stateText(), layout.contentLeft, layout.top + 29, stateColor(), false);
        String balance = points + " WEAPON PT";
        graphics.drawString(font, balance, layout.contentRight - font.width(balance), layout.top + 14, GOLD, false);

        boolean closeHovered = inside(mouseX, mouseY, layout.right() - 28, layout.top + 8, 20, 20);
        graphics.drawCenteredString(font, "×", layout.right() - 18, layout.top + 14,
                closeHovered ? DANGER : MUTED);

        PvpLoadoutPreset[] presets = PvpLoadoutPreset.values();
        for (int index = 0; index < presets.length; index++) {
            Card card = layout.card(index);
            drawLoadoutCard(graphics, presets[index], card, inside(mouseX, mouseY, card.x, card.y, card.width, card.height));
        }

        drawSelectionDetails(graphics, layout);
        drawFooter(graphics, layout, mouseX, mouseY);
    }

    private void drawLoadoutCard(GuiGraphics graphics, PvpLoadoutPreset preset, Card card, boolean hovered) {
        boolean chosen = preset == selected;
        int accent = loadoutColor(preset);
        graphics.fill(card.x, card.y, card.x + card.width, card.y + card.height,
                chosen ? 0xFF263542 : hovered && !active ? CARD_HOVER : CARD);
        graphics.fill(card.x, card.y, card.x + (chosen ? 4 : 2), card.y + card.height,
                chosen ? accent : 0xFF354150);
        drawBorder(graphics, card.x, card.y, card.width, card.height,
                chosen ? accent : hovered && !active ? 0xFF536173 : 0xFF28323E);

        ItemStack icon = displayGuns.computeIfAbsent(preset, this::createDisplayGun);
        if (!icon.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(card.x + 13, card.y + 23, 0);
            graphics.pose().scale(1.35F, 1.35F, 1.0F);
            graphics.renderItem(icon, 0, 0);
            graphics.pose().popPose();
        }

        int textX = card.x + 43;
        graphics.drawString(font, Component.translatable(preset.translationKey()), textX, card.y + 9, accent, false);
        graphics.drawString(font, preset.weaponSummary(), textX, card.y + 25, TEXT, false);
        String metrics = Component.translatable("screen.moveearth_addtional.pvp.metrics",
                preset.bodyTtk(), Component.translatable(preset.rangeKey())).getString();
        graphics.drawString(font, metrics, textX, card.y + 41, MUTED, false);
        if (card.height >= 74) {
            graphics.drawString(font, preset.attachmentSummary(), textX, card.y + 57, 0xFFB5C6D8, false);
        }
        if (chosen) {
            graphics.drawString(font, "✓", card.x + card.width - 17, card.y + 9, accent, false);
        }
    }

    private void drawSelectionDetails(GuiGraphics graphics, Layout layout) {
        int y = layout.detailsTop;
        if (y + 18 >= layout.footerY) return;
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.pvp.selected"),
                layout.contentLeft, y, MUTED, false);
        Component name = Component.translatable(selected.translationKey());
        graphics.drawString(font, name, layout.contentLeft + 62, y, loadoutColor(selected), false);

        int descriptionWidth = layout.contentRight - layout.contentLeft;
        int lineY = y + 14;
        for (FormattedCharSequence line : font.split(Component.translatable(selected.descriptionKey()), descriptionWidth)) {
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

        boolean selectionChanged = selected != serverSelection;
        boolean actionEnabled = !active && hosting && (!joined || selectionChanged);
        boolean actionHovered = inside(mouseX, mouseY, footer.actionX, layout.footerY, footer.actionWidth, 28);
        Component actionText = active
                ? Component.translatable("screen.moveearth_addtional.pvp.in_match")
                : !hosting
                ? Component.translatable("screen.moveearth_addtional.pvp.closed")
                : joined && !selectionChanged
                ? Component.translatable("screen.moveearth_addtional.pvp.registered")
                : joined
                ? Component.translatable("screen.moveearth_addtional.pvp.change")
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

        if (!active) {
            PvpLoadoutPreset[] presets = PvpLoadoutPreset.values();
            for (int index = 0; index < presets.length; index++) {
                Card card = layout.card(index);
                if (inside(mouseX, mouseY, card.x, card.y, card.width, card.height)) {
                    selected = presets[index];
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
            PacketDistributor.sendToServer(new C2S_PvpActionPacket(false, selected.id()));
            onClose();
            return true;
        }
        if (inside(mouseX, mouseY, footer.actionX, layout.footerY, footer.actionWidth, 28)) {
            if (!active && hosting && (!joined || selected != serverSelection)) {
                PacketDistributor.sendToServer(new C2S_PvpActionPacket(true, selected.id()));
                onClose();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private ItemStack createDisplayGun(PvpLoadoutPreset preset) {
        ItemStack stack = new ItemStack(com.tacz.guns.init.ModItems.MODERN_KINETIC_GUN.get());
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return ItemStack.EMPTY;
        gun.setGunId(stack, preset.primary().gunId());
        return stack;
    }

    private Component stateText() {
        if (active) return Component.translatable("screen.moveearth_addtional.pvp.state.active");
        if (joined) return Component.translatable("screen.moveearth_addtional.pvp.state.queued");
        if (hosting) return Component.translatable("screen.moveearth_addtional.pvp.state.open");
        return Component.translatable("screen.moveearth_addtional.pvp.state.closed");
    }

    private int stateColor() {
        if (active || joined) return SUCCESS;
        return hosting ? ACCENT : DANGER;
    }

    private Layout layout() {
        int panelWidth = Math.min(PANEL_WIDTH, width - 12);
        int panelHeight = Math.min(PANEL_HEIGHT, height - 12);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int contentLeft = left + 16;
        int contentRight = left + panelWidth - 16;
        int gridTop = top + 52;
        int footerY = top + panelHeight - 40;
        int gap = 8;
        int cardWidth = (contentRight - contentLeft - gap) / 2;
        int cardHeight = Math.max(38, (footerY - gridTop - gap - 48) / 2);
        int detailsTop = gridTop + cardHeight * 2 + gap + 6;
        return new Layout(left, top, panelWidth, panelHeight, contentLeft, contentRight,
                gridTop, footerY, gap, cardWidth, cardHeight, detailsTop);
    }

    private static int loadoutColor(PvpLoadoutPreset preset) {
        return switch (preset) {
            case ASSAULT -> 0xFF5DCBFF;
            case RUSHER -> 0xFFFFB454;
            case BREACHER -> 0xFFFF766D;
            case MARKSMAN -> 0xFFB38CFF;
        };
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int areaWidth, int areaHeight) {
        return mouseX >= x && mouseX < x + areaWidth && mouseY >= y && mouseY < y + areaHeight;
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int areaWidth, int areaHeight, int color) {
        graphics.fill(x, y, x + areaWidth, y + 1, color);
        graphics.fill(x, y + areaHeight - 1, x + areaWidth, y + areaHeight, color);
        graphics.fill(x, y, x + 1, y + areaHeight, color);
        graphics.fill(x + areaWidth - 1, y, x + areaWidth, y + areaHeight, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Card(int x, int y, int width, int height) {}

    private record Footer(int tasksX, int tasksWidth, int crateX, int crateWidth,
                          int leaveX, int leaveWidth, int actionX, int actionWidth) {}

    private record Layout(int left, int top, int panelWidth, int panelHeight, int contentLeft, int contentRight,
                          int gridTop, int footerY, int gap, int cardWidth, int cardHeight, int detailsTop) {
        int right() {
            return left + panelWidth;
        }

        int bottom() {
            return top + panelHeight;
        }

        Card card(int index) {
            int column = index % 2;
            int row = index / 2;
            return new Card(contentLeft + column * (cardWidth + gap), gridTop + row * (cardHeight + gap),
                    cardWidth, cardHeight);
        }

        Footer footer(boolean joined) {
            int actionWidth = Math.min(178, Math.max(128, (contentRight - contentLeft) / 3));
            int actionX = contentRight - actionWidth;
            int leaveWidth = joined ? 58 : 0;
            int leaveX = actionX - (joined ? leaveWidth + gap : 0);
            int utilityRight = joined ? leaveX - gap : actionX - gap;
            int utilityWidth = Math.max(100, utilityRight - contentLeft);
            int tasksWidth = Math.min(76, utilityWidth / 3);
            int crateX = contentLeft + tasksWidth + gap;
            int crateWidth = Math.max(16, utilityRight - crateX);
            return new Footer(contentLeft, tasksWidth, crateX, crateWidth,
                    leaveX, leaveWidth, actionX, actionWidth);
        }
    }
}

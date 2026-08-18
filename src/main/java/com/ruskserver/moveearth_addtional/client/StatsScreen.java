package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.network.S2C_OpenStatsScreenPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public final class StatsScreen extends Screen {
    private static final int PANEL_WIDTH = 500;
    private static final int PANEL_HEIGHT = 270;
    private static final int TEXT = 0xFFE8EDF3;
    private static final int MUTED = 0xFF8F9AA8;
    private static final int PANEL = 0xF012161D;
    private static final int CARD = 0xFF1B222C;
    private static final int CARD_HOVER = 0xFF242E3B;
    private final S2C_OpenStatsScreenPacket stats;

    public StatsScreen(S2C_OpenStatsScreenPacket stats) {
        super(Component.translatable("screen.moveearth_addtional.stats.title"));
        this.stats = stats;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 独自背景を描画するため、Minecraft標準の背景ブラーは適用しない。
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
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.stats.heading"), left + 16, top + 14, TEXT, false);
        graphics.drawString(font, stats.playerName(), left + 16, top + 29, 0xFF5DCBFF, false);
        boolean closeHovered = mouseX >= left + panelWidth - 28 && mouseX < left + panelWidth - 8
                && mouseY >= top + 8 && mouseY < top + 28;
        graphics.drawCenteredString(font, "×", left + panelWidth - 18, top + 14,
                closeHovered ? 0xFFFF6577 : MUTED);

        int modelWidth = Math.max(150, panelWidth * 36 / 100);
        int modelLeft = left + 12;
        int modelTop = top + 48;
        int modelBottom = top + panelHeight - 14;
        graphics.fill(modelLeft, modelTop, modelLeft + modelWidth, modelBottom, 0xFF0D1117);
        drawBorder(graphics, modelLeft, modelTop, modelWidth, modelBottom - modelTop, 0xFF28323E);
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            int scale = Mth.clamp((modelBottom - modelTop) / 4, 38, 54);
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, modelLeft, modelTop,
                    modelLeft + modelWidth, modelBottom - 23, scale, 0.06F, mouseX, mouseY, player);
            String health = String.format(Locale.ROOT, "%.0f / %.0f HP", player.getHealth(), player.getMaxHealth());
            graphics.drawCenteredString(font, health, modelLeft + modelWidth / 2, modelBottom - 17, 0xFF68E09B);
        }

        int cardsLeft = modelLeft + modelWidth + 12;
        int cardsRight = left + panelWidth - 12;
        int gap = 8;
        int cardWidth = (cardsRight - cardsLeft - gap) / 2;
        int cardHeight = 58;
        int firstY = modelTop;
        drawCard(graphics, cardsLeft, firstY, cardWidth, cardHeight, mouseX, mouseY,
                "screen.moveearth_addtional.stats.play_time", formatPlayTime(stats.playTimeTicks()), 0xFF5DCBFF);
        drawCard(graphics, cardsLeft + cardWidth + gap, firstY, cardWidth, cardHeight, mouseX, mouseY,
                "screen.moveearth_addtional.stats.player_kills", formatNumber(stats.playerKills()), 0xFFFF6577);
        drawCard(graphics, cardsLeft, firstY + cardHeight + gap, cardWidth, cardHeight, mouseX, mouseY,
                "screen.moveearth_addtional.stats.mob_kills", formatNumber(stats.mobKills()), 0xFFFFB454);
        drawCard(graphics, cardsLeft + cardWidth + gap, firstY + cardHeight + gap, cardWidth, cardHeight, mouseX, mouseY,
                "screen.moveearth_addtional.stats.deaths", formatNumber(stats.deaths()), 0xFFB7A7FF);
        drawCard(graphics, cardsLeft, firstY + (cardHeight + gap) * 2, cardWidth, cardHeight, mouseX, mouseY,
                "screen.moveearth_addtional.stats.kd", formatKd(stats.playerKills(), stats.deaths()), 0xFF68E09B);
        drawCard(graphics, cardsLeft + cardWidth + gap, firstY + (cardHeight + gap) * 2, cardWidth, cardHeight, mouseX, mouseY,
                "screen.moveearth_addtional.stats.distance", formatDistance(stats.distanceCm()), 0xFF60D6C6);
        String footer = Component.translatable("screen.moveearth_addtional.stats.details",
                formatDamage(stats.damageDealt()), formatDamage(stats.damageTaken()), formatNumber(stats.jumps())).getString();
        graphics.drawCenteredString(font, footer, cardsLeft + (cardsRight - cardsLeft) / 2,
                firstY + (cardHeight + gap) * 3 + 2, MUTED);
    }

    private void drawCard(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY,
                          String labelKey, String value, int accent) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        graphics.fill(x, y, x + width, y + height, hovered ? CARD_HOVER : CARD);
        graphics.fill(x, y, x + 3, y + height, accent);
        graphics.drawString(font, Component.translatable(labelKey), x + 11, y + 10, MUTED, false);
        graphics.drawString(font, value, x + 11, y + 29, accent, false);
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private static String formatPlayTime(int ticks) {
        long minutes = ticks / 20L / 60L;
        long days = minutes / 1440L;
        long hours = minutes / 60L % 24L;
        long remainingMinutes = minutes % 60L;
        return days > 0 ? String.format(Locale.ROOT, "%dd %02dh %02dm", days, hours, remainingMinutes)
                : String.format(Locale.ROOT, "%dh %02dm", hours, remainingMinutes);
    }

    private static String formatKd(int kills, int deaths) {
        return deaths == 0 ? String.format(Locale.ROOT, "%.2f", (double) kills)
                : String.format(Locale.ROOT, "%.2f", (double) kills / deaths);
    }

    private static String formatDistance(int centimeters) {
        return centimeters >= 100_000 ? String.format(Locale.ROOT, "%.1f km", centimeters / 100_000.0D)
                : String.format(Locale.ROOT, "%.0f m", centimeters / 100.0D);
    }

    private static String formatDamage(int value) {
        return formatNumber(Math.round(value / 10.0F));
    }

    private static String formatNumber(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelWidth = Math.min(PANEL_WIDTH, width - 20);
        int panelHeight = Math.min(PANEL_HEIGHT, height - 20);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        if (button == 0 && mouseX >= left + panelWidth - 28 && mouseX < left + panelWidth - 8
                && mouseY >= top + 8 && mouseY < top + 28) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}

package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.network.S2C_OpenVehicleCoreScreenPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

/** Readable, non-container vehicle status console using the shared /pvp visual language. */
public final class VehicleCoreScreen extends Screen implements SuppressesChatOverlay {
    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_HEIGHT = 230;
    private final S2C_OpenVehicleCoreScreenPacket snapshot;

    public VehicleCoreScreen(S2C_OpenVehicleCoreScreenPacket snapshot) {
        super(Component.translatable("screen.moveearth_addtional.vehicle_core.title"));
        this.snapshot = snapshot;
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(graphics, width, height);
        Rect panel = panelBounds();
        drawPanel(graphics, panel);
        graphics.drawString(font, title, panel.x() + 18, panel.y() + 16, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.vehicle_core.subtitle",
                snapshot.nationName()), panel.x() + 18, panel.y() + 32, MUTED, false);
        Rect close = new Rect(panel.right() - 28, panel.y() + 8, 20, 20);
        drawClose(graphics, font, close, close.contains(mouseX, mouseY));

        Rect healthCard = new Rect(panel.x() + 18, panel.y() + 56, panel.width() - 36, 56);
        int healthColor = snapshot.health() == 0 ? DANGER
                : snapshot.health() * 4 <= snapshot.maximumHealth() ? DANGER
                : snapshot.health() * 2 <= snapshot.maximumHealth() ? GOLD : SUCCESS;
        drawCard(graphics, healthCard, healthColor, false, healthCard.contains(mouseX, mouseY));
        Component state = Component.translatable(snapshot.health() == 0
                ? "screen.moveearth_addtional.vehicle_core.destroyed"
                : "screen.moveearth_addtional.vehicle_core.operational");
        graphics.drawString(font, state, healthCard.x() + 13, healthCard.y() + 10, healthColor, false);
        int barX = healthCard.x() + 13;
        int barY = healthCard.y() + 31;
        int barWidth = healthCard.width() - 26;
        graphics.fill(barX, barY, barX + barWidth, barY + 10, BAR_BACKGROUND);
        int fill = Math.round(barWidth * snapshot.health() / (float) snapshot.maximumHealth());
        if (fill > 0) graphics.fill(barX, barY, barX + fill, barY + 10, healthColor);
        graphics.drawCenteredString(font, Component.translatable("screen.moveearth_addtional.vehicle_core.health",
                snapshot.health(), snapshot.maximumHealth()), barX + barWidth / 2, barY + 1, TEXT);

        int gap = 8;
        int cardWidth = (panel.width() - 36 - gap * 2) / 3;
        drawStat(graphics, new Rect(panel.x() + 18, panel.y() + 122, cardWidth, 58),
                Component.translatable("screen.moveearth_addtional.vehicle_core.upkeep"),
                Component.translatable("screen.moveearth_addtional.vehicle_core.upkeep_value",
                        snapshot.upkeepCost(), Component.translatable(
                                "screen.moveearth_addtional.vehicle_core.upkeep_state." + snapshot.upkeepState())), GOLD,
                mouseX, mouseY);
        drawStat(graphics, new Rect(panel.x() + 18 + cardWidth + gap, panel.y() + 122, cardWidth, 58),
                Component.translatable("screen.moveearth_addtional.vehicle_core.bodies"),
                Component.literal(Integer.toString(snapshot.connectedBodies())), ACCENT, mouseX, mouseY);
        drawStat(graphics, new Rect(panel.x() + 18 + (cardWidth + gap) * 2, panel.y() + 122, cardWidth, 58),
                Component.translatable("screen.moveearth_addtional.vehicle_core.reinforced"),
                Component.literal(Integer.toString(snapshot.reinforcedBlocks())), SUCCESS, mouseX, mouseY);
        graphics.drawCenteredString(font, Component.translatable(
                "screen.moveearth_addtional.vehicle_core.damage_hint"), panel.x() + panel.width() / 2,
                panel.bottom() - 28, MUTED);
    }

    private void drawStat(GuiGraphics graphics, Rect bounds, Component label, Component value, int accent,
                          int mouseX, int mouseY) {
        drawCard(graphics, bounds, accent, false, bounds.contains(mouseX, mouseY));
        graphics.drawString(font, label, bounds.x() + 11, bounds.y() + 10, MUTED, false);
        graphics.drawString(font, value, bounds.x() + 11, bounds.y() + 30, accent, false);
    }

    private Rect panelBounds() {
        int panelWidth = Math.min(PANEL_WIDTH, Math.max(280, width - 24));
        int panelHeight = Math.min(PANEL_HEIGHT, Math.max(210, height - 24));
        return new Rect((width - panelWidth) / 2, (height - panelHeight) / 2, panelWidth, panelHeight);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Rect panel = panelBounds();
        if (button == 0 && new Rect(panel.right() - 28, panel.y() + 8, 20, 20).contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean isPauseScreen() { return false; }
}

package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;
import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.network.C2S_RequestS2HubPacket;
import com.ruskserver.moveearth_addtional.network.C2S_RequestTerritoryPreviewPacket;
import com.ruskserver.moveearth_addtional.network.C2S_SetTerritoryCoreRadiusPacket;
import com.ruskserver.moveearth_addtional.network.S2C_S2ActionResultPacket;
import com.ruskserver.moveearth_addtional.network.S2C_TerritoryPreviewPacket;
import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

public final class TerritoryCoreWizardScreen extends Screen implements SuppressesChatOverlay {
    private static final int PANEL_WIDTH = 560;
    private static final int PANEL_HEIGHT = 286;
    private static int lastRadius = 1;
    private static int nextRequestId;

    private final BlockPos corePos;
    private int radius;
    private S2C_TerritoryPreviewPacket preview;
    private int pendingRequestId = -1;
    private Component toast;
    private int toastColor = SUCCESS;
    private int toastTicks;

    public TerritoryCoreWizardScreen() {
        this(null, lastRadius);
    }

    public TerritoryCoreWizardScreen(BlockPos corePos, int radius) {
        super(Component.translatable("screen.moveearth_addtional.territory.title"));
        this.corePos = corePos;
        this.radius = Math.max(0, Math.min(4, radius));
    }

    @Override
    protected void init() {
        requestPreview();
    }

    public void update(S2C_TerritoryPreviewPacket packet) {
        preview = packet;
        radius = packet.radius();
        lastRadius = radius;
    }

    public void handleResult(S2C_S2ActionResultPacket packet) {
        if (packet.requestId() != pendingRequestId) return;
        pendingRequestId = -1;
        Component body = Component.translatable(packet.messageKey());
        toast = packet.success() ? MoveEarthMessage.success(body) : MoveEarthMessage.error(body);
        toastColor = packet.success() ? SUCCESS : DANGER;
        toastTicks = 70;
    }

    @Override
    public void tick() {
        if (toastTicks > 0) toastTicks--;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(graphics, width, height);
        Rect panel = panelBounds();
        drawPanel(graphics, panel);
        graphics.drawString(font, title, panel.x() + 18, panel.y() + 15, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.territory.step"),
                panel.x() + 18, panel.y() + 30, MUTED, false);
        if (corePos != null) {
            graphics.drawString(font, Component.translatable("screen.moveearth_addtional.territory.core_position",
                            corePos.getX(), corePos.getY(), corePos.getZ()),
                    panel.x() + 250, panel.y() + 30, GOLD, false);
        }

        Rect close = new Rect(panel.right() - 28, panel.y() + 8, 20, 20);
        drawClose(graphics, font, close, close.contains(mouseX, mouseY));
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.territory.radius"),
                panel.x() + 18, panel.y() + 58, TEXT, false);

        int gap = 7;
        int cardWidth = (panel.width() - 36 - gap * 4) / 5;
        for (int value = 0; value <= 4; value++) {
            Rect card = radiusBounds(panel, value, cardWidth, gap);
            boolean selected = radius == value;
            drawCard(graphics, card, ACCENT, selected, card.contains(mouseX, mouseY));
            graphics.drawCenteredString(font, Component.literal("R " + value),
                    card.x() + card.width() / 2, card.y() + 12, selected ? ACCENT : TEXT);
            int count = (value * 2 + 1) * (value * 2 + 1);
            graphics.drawCenteredString(font,
                    Component.translatable("screen.moveearth_addtional.territory.chunks", count),
                    card.x() + card.width() / 2, card.y() + 29, MUTED);
        }

        Rect summary = new Rect(panel.x() + 18, panel.y() + 128, panel.width() - 36, 72);
        drawCard(graphics, summary, SUCCESS, false, false);
        graphics.drawString(font,
                Component.translatable("screen.moveearth_addtional.territory.summary",
                        radius, (radius * 2 + 1), (radius * 2 + 1),
                        preview == null ? "…" : preview.chunkCount()),
                summary.x() + 14, summary.y() + 12, TEXT, false);
        graphics.drawString(font,
                Component.translatable("screen.moveearth_addtional.territory.center",
                        preview == null ? "…" : preview.centerChunkX(),
                        preview == null ? "…" : preview.centerChunkZ()),
                summary.x() + 14, summary.y() + 29, MUTED, false);
        graphics.drawString(font,
                Component.translatable("screen.moveearth_addtional.territory.upkeep_pending"),
                summary.x() + 14, summary.y() + 47, GOLD, false);

        Rect back = backBounds(panel);
        drawButton(graphics, font, back, Component.translatable("screen.moveearth_addtional.territory.back"),
                ACCENT, back.contains(mouseX, mouseY), true);
        Rect world = worldBounds(panel);
        drawButton(graphics, font, world,
                Component.translatable("screen.moveearth_addtional.territory.view_world"), SUCCESS,
                preview != null && world.contains(mouseX, mouseY), preview != null);
        if (corePos != null) {
            Rect save = saveBounds(panel);
            boolean enabled = preview != null && pendingRequestId < 0;
            drawButton(graphics, font, save,
                    Component.translatable("screen.moveearth_addtional.territory.save_core"), GOLD,
                    enabled && save.contains(mouseX, mouseY), enabled);
        }
        if (toastTicks > 0 && toast != null) {
            drawToast(graphics, font, width, height, toast, toastColor);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        Rect panel = panelBounds();
        if (new Rect(panel.right() - 28, panel.y() + 8, 20, 20).contains(mouseX, mouseY)) {
            TerritoryPreviewClientState.clear();
            onClose();
            return true;
        }
        int gap = 7;
        int cardWidth = (panel.width() - 36 - gap * 4) / 5;
        for (int value = 0; value <= 4; value++) {
            if (radiusBounds(panel, value, cardWidth, gap).contains(mouseX, mouseY)) {
                radius = value;
                lastRadius = value;
                requestPreview();
                return true;
            }
        }
        if (backBounds(panel).contains(mouseX, mouseY)) {
            TerritoryPreviewClientState.clear();
            PacketDistributor.sendToServer(new C2S_RequestS2HubPacket(S2HubTab.OVERVIEW));
            return true;
        }
        if (preview != null && worldBounds(panel).contains(mouseX, mouseY)) {
            minecraft.setScreen(null);
            return true;
        }
        if (corePos != null && preview != null && pendingRequestId < 0
                && saveBounds(panel).contains(mouseX, mouseY)) {
            pendingRequestId = ++nextRequestId;
            PacketDistributor.sendToServer(new C2S_SetTerritoryCoreRadiusPacket(
                    pendingRequestId, corePos, radius));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void requestPreview() {
        PacketDistributor.sendToServer(corePos == null
                ? new C2S_RequestTerritoryPreviewPacket(radius)
                : new C2S_RequestTerritoryPreviewPacket(radius, corePos));
    }

    private Rect panelBounds() {
        int panelWidth = Math.min(PANEL_WIDTH, width - 20);
        int panelHeight = Math.min(PANEL_HEIGHT, height - 20);
        return new Rect((width - panelWidth) / 2, (height - panelHeight) / 2, panelWidth, panelHeight);
    }

    private static Rect radiusBounds(Rect panel, int radius, int cardWidth, int gap) {
        return new Rect(panel.x() + 18 + radius * (cardWidth + gap), panel.y() + 76, cardWidth, 46);
    }

    private static Rect backBounds(Rect panel) {
        return new Rect(panel.x() + 18, panel.bottom() - 40, 98, 24);
    }

    private Rect worldBounds(Rect panel) {
        return new Rect(panel.right() - (corePos == null ? 164 : 316), panel.bottom() - 40, 146, 24);
    }

    private static Rect saveBounds(Rect panel) {
        return new Rect(panel.right() - 162, panel.bottom() - 40, 144, 24);
    }

    @Override
    public void onClose() {
        TerritoryPreviewClientState.clear();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

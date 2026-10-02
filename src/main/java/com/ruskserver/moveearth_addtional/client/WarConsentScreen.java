package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.network.c2s.siege.C2S_WarConsentPacket;
import com.ruskserver.moveearth_addtional.network.s2c.siege.S2C_WarConsentPromptPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

/**
 * Confirmation before a first attack starts a war with a nation that has no declared hostility or Siege with
 * the player's nation. The hit that opened it was already refused; cancelling changes nothing. The confirm
 * button arms only after a short delay, so a player still clicking to mine cannot confirm by accident.
 */
public final class WarConsentScreen extends Screen implements SuppressesChatOverlay {
    private static final int ARM_TICKS = 30;
    private static final int PADDING = 18;
    private final S2C_WarConsentPromptPacket prompt;
    private int ticksOpen;

    public WarConsentScreen(S2C_WarConsentPromptPacket prompt) {
        super(Component.translatable("screen.moveearth_addtional.war_consent.title"));
        this.prompt = prompt;
    }

    private List<Line> lines() {
        return List.of(
                new Line(Component.translatable("screen.moveearth_addtional.war_consent.target",
                        prompt.targetNationName()), TEXT),
                new Line(Component.translatable("screen.moveearth_addtional.war_consent.prisoners"), DANGER),
                new Line(Component.translatable("screen.moveearth_addtional.war_consent.lockout",
                        prompt.failedLockoutMinutes()), GOLD),
                new Line(Component.translatable("screen.moveearth_addtional.war_consent.hostility_hint"), ACCENT),
                new Line(Component.translatable("screen.moveearth_addtional.war_consent.validity",
                        prompt.consentMinutes()), MUTED));
    }

    private record Line(Component text, int color) { }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawModalBackdrop(graphics, width, height);
        Rect panel = panelBounds();
        drawPanel(graphics, panel);
        graphics.fill(panel.x(), panel.y(), panel.x() + 3, panel.bottom(), DANGER);
        graphics.drawString(font, title, panel.x() + PADDING, panel.y() + 14, DANGER, false);
        int textWidth = panel.width() - PADDING * 2;
        int y = panel.y() + 34;
        int textBottom = cancelBounds(panel).y() - 8;
        graphics.enableScissor(panel.x(), y, panel.right(), textBottom);
        for (Line line : lines()) {
            y += drawWrapped(graphics, font, line.text(), panel.x() + PADDING, y, textWidth, line.color()) + 6;
        }
        graphics.disableScissor();
        Rect cancel = cancelBounds(panel);
        Rect confirm = confirmBounds(panel);
        boolean armed = ticksOpen >= ARM_TICKS;
        drawButton(graphics, font, cancel, Component.translatable("screen.moveearth_addtional.war_consent.cancel"),
                MUTED, cancel.contains(mouseX, mouseY), true);
        drawButton(graphics, font, confirm, armed
                        ? Component.translatable("screen.moveearth_addtional.war_consent.confirm")
                        : Component.translatable("screen.moveearth_addtional.war_consent.arming",
                        (ARM_TICKS - ticksOpen + 19) / 20),
                DANGER, armed && confirm.contains(mouseX, mouseY), armed);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            Rect panel = panelBounds();
            if (cancelBounds(panel).contains(mouseX, mouseY)) {
                onClose();
                return true;
            }
            if (ticksOpen >= ARM_TICKS && confirmBounds(panel).contains(mouseX, mouseY)) {
                PacketDistributor.sendToServer(new C2S_WarConsentPacket(prompt.targetNationId()));
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void tick() {
        super.tick();
        if (ticksOpen < ARM_TICKS) ticksOpen++;
    }

    @Override
    public void onClose() {
        WarConsentClient.resolved();
        super.onClose();
    }

    @Override public boolean isPauseScreen() { return false; }

    private Rect panelBounds() {
        int panelWidth = Math.min(420, Math.max(200, width - 20));
        int textWidth = panelWidth - PADDING * 2;
        int content = 0;
        for (Line line : lines()) content += wrappedHeight(font, line.text(), textWidth) + 6;
        int panelHeight = Math.min(Math.max(120, height - 20), 34 + content + 8 + 23 + 16);
        return new Rect((width - panelWidth) / 2, (height - panelHeight) / 2, panelWidth, panelHeight);
    }

    private static Rect cancelBounds(Rect panel) {
        return new Rect(panel.x() + PADDING, panel.bottom() - 39, 110, 23);
    }

    private static Rect confirmBounds(Rect panel) {
        return new Rect(panel.right() - PADDING - 150, panel.bottom() - 39, 150, 23);
    }
}

package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;
import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.network.C2S_OnboardingActionPacket;
import com.ruskserver.moveearth_addtional.network.S2C_OnboardingPacket;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

public final class NationOnboardingScreen extends Screen implements SuppressesChatOverlay {
    private static final int PANEL_WIDTH = 620;
    private static final int PANEL_HEIGHT = 356;
    private static final int ROW_HEIGHT = 43;
    private S2C_OnboardingPacket packet;
    private int scrollOffset;
    private boolean gameVisible;
    private float birdY = 0.5F;
    private float birdVelocity;
    private float pipeX = 1.0F;
    private float gapY = 0.5F;
    private int score;
    private int best;
    private Component toast;
    private int toastColor = SUCCESS;
    private int toastTicks;

    public NationOnboardingScreen(S2C_OnboardingPacket packet) {
        super(Component.translatable("screen.moveearth_addtional.onboarding.title"));
        this.packet = packet;
        acceptMessage(packet);
    }

    public void update(S2C_OnboardingPacket packet) {
        boolean changedState = (this.packet.appliedNationId() == null) != (packet.appliedNationId() == null);
        this.packet = packet;
        if (changedState) scrollOffset = 0;
        acceptMessage(packet);
    }

    private void acceptMessage(S2C_OnboardingPacket packet) {
        if (packet.messageKey().isBlank()) return;
        Component body = Component.translatable(packet.messageKey());
        toast = packet.success() ? MoveEarthMessage.success(body) : MoveEarthMessage.error(body);
        toastColor = packet.success() ? SUCCESS : DANGER;
        toastTicks = 80;
    }

    @Override public void tick() {
        if (toastTicks > 0) toastTicks--;
        if (!gameVisible || packet.appliedNationId() == null) return;
        birdVelocity += 0.018F;
        birdY += birdVelocity;
        pipeX -= 0.012F;
        if (pipeX < -0.08F) {
            pipeX = 1.05F;
            gapY = 0.28F + (float) Math.random() * 0.44F;
            score++;
            best = Math.max(best, score);
        }
        boolean pipeHit = pipeX > 0.19F && pipeX < 0.36F
                && (birdY < gapY - 0.14F || birdY > gapY + 0.14F);
        if (birdY < 0.04F || birdY > 0.96F || pipeHit) resetGame();
    }

    private void resetGame() {
        best = Math.max(best, score);
        score = 0;
        birdY = 0.5F;
        birdVelocity = 0.0F;
        pipeX = 1.0F;
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(graphics, width, height);
        Rect panel = panelBounds();
        drawPanel(graphics, panel);
        graphics.fillGradient(panel.x() + 1, panel.y() + 1, panel.right() - 1, panel.y() + 5,
                0xFF57DF90, 0xFF1E7145);
        graphics.drawString(font, Component.literal("[MoveEarth] ").withColor(SUCCESS)
                        .append(Component.literal(">>> ").withColor(MUTED)).append(title),
                panel.x() + 18, panel.y() + 15, TEXT, false);

        if (packet.searching()) drawSearching(graphics, panel);
        else if (packet.appliedNationId() == null) drawSelection(graphics, panel, mouseX, mouseY);
        else drawWaiting(graphics, panel, mouseX, mouseY);
        if (toastTicks > 0 && toast != null) drawToast(graphics, font, width, height, toast, toastColor);
    }

    private void drawSearching(GuiGraphics graphics, Rect panel) {
        Rect card = new Rect(panel.x() + 18, panel.y() + 62, panel.width() - 36, 116);
        drawCard(graphics, card, SUCCESS, true, false);
        graphics.drawCenteredString(font,
                Component.translatable("screen.moveearth_addtional.onboarding.searching"),
                card.x() + card.width() / 2, card.y() + 39, SUCCESS);
        graphics.drawCenteredString(font,
                Component.translatable("screen.moveearth_addtional.onboarding.searching.detail"),
                card.x() + card.width() / 2, card.y() + 61, MUTED);
    }

    private void drawSelection(GuiGraphics graphics, Rect panel, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.onboarding.detail"),
                panel.x() + 18, panel.y() + 33, MUTED, false);
        Rect list = new Rect(panel.x() + 18, panel.y() + 58, panel.width() - 36, panel.height() - 111);
        List<S2C_OnboardingPacket.NationEntry> nations = packet.nations();
        if (nations.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.moveearth_addtional.onboarding.empty"),
                    list.x() + list.width() / 2, list.y() + 50, MUTED);
        } else {
            graphics.enableScissor(list.x(), list.y(), list.right(), list.bottom());
            for (int index = 0; index < nations.size(); index++) {
                S2C_OnboardingPacket.NationEntry nation = nations.get(index);
                int y = list.y() + index * ROW_HEIGHT - scrollOffset;
                Rect card = new Rect(list.x(), y, list.width() - 8, ROW_HEIGHT - 5);
                drawCard(graphics, card, ACCENT, false, card.contains(mouseX, mouseY));
                String name = nation.tag().isBlank() ? nation.name() : "[" + nation.tag() + "] " + nation.name();
                graphics.drawString(font, name, card.x() + 12, card.y() + 8, TEXT, false);
                graphics.drawString(font, Component.translatable("screen.moveearth_addtional.onboarding.nation_stats",
                        nation.members(), nation.activeCores()), card.x() + 12, card.y() + 22, MUTED, false);
                Rect apply = new Rect(card.right() - 92, card.y() + 8, 80, 22);
                drawButton(graphics, font, apply,
                        Component.translatable("screen.moveearth_addtional.onboarding.apply"), SUCCESS,
                        apply.contains(mouseX, mouseY), true);
            }
            graphics.disableScissor();
            drawScrollbar(graphics, new Rect(list.right() - 4, list.y(), 4, list.height()),
                    list.height(), nations.size() * ROW_HEIGHT, scrollOffset);
        }
        drawBottomButtons(graphics, panel, mouseX, mouseY, false);
    }

    private void drawWaiting(GuiGraphics graphics, Rect panel, int mouseX, int mouseY) {
        Rect status = new Rect(panel.x() + 18, panel.y() + 53, panel.width() - 36, 62);
        drawCard(graphics, status, GOLD, true, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.onboarding.waiting",
                packet.appliedNationName()), status.x() + 14, status.y() + 13, GOLD, false);
        long seconds = Math.max(0L, (System.currentTimeMillis() - packet.requestedAt()) / 1000L);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.onboarding.elapsed", seconds),
                status.x() + 14, status.y() + 34, MUTED, false);

        Rect game = gameBounds(panel);
        drawCard(graphics, game, SUCCESS, gameVisible, game.contains(mouseX, mouseY));
        if (!gameVisible) {
            graphics.drawCenteredString(font, Component.translatable("screen.moveearth_addtional.onboarding.flappy.open"),
                    game.x() + game.width() / 2, game.y() + game.height() / 2 - 4, SUCCESS);
        } else {
            drawGame(graphics, game);
        }
        drawBottomButtons(graphics, panel, mouseX, mouseY, true);
    }

    private void drawGame(GuiGraphics graphics, Rect game) {
        graphics.fill(game.x() + 2, game.y() + 2, game.right() - 2, game.bottom() - 2, 0xFF101B24);
        int birdX = game.x() + game.width() / 4;
        int birdPixelY = game.y() + Math.round(birdY * game.height());
        int pipePixelX = game.x() + Math.round(pipeX * game.width());
        int gapCenter = game.y() + Math.round(gapY * game.height());
        int gapHalf = Math.max(18, Math.round(game.height() * 0.14F));
        graphics.fill(pipePixelX, game.y() + 2, pipePixelX + 14, gapCenter - gapHalf, 0xFF3EBB72);
        graphics.fill(pipePixelX, gapCenter + gapHalf, pipePixelX + 14, game.bottom() - 2, 0xFF287A4C);
        graphics.fill(birdX - 4, birdPixelY - 4, birdX + 5, birdPixelY + 5, GOLD);
        graphics.drawString(font, "SCORE " + score + "  BEST " + best, game.x() + 8, game.y() + 7, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.onboarding.flappy.hint"),
                game.x() + 8, game.bottom() - 14, MUTED, false);
    }

    private void drawBottomButtons(GuiGraphics graphics, Rect panel, int mouseX, int mouseY, boolean waiting) {
        Rect wilderness = wildernessBounds(panel);
        drawButton(graphics, font, wilderness,
                Component.translatable("screen.moveearth_addtional.onboarding.wilderness"), ACCENT,
                wilderness.contains(mouseX, mouseY), true);
        if (waiting) {
            Rect cancel = cancelBounds(panel);
            drawButton(graphics, font, cancel,
                    Component.translatable("screen.moveearth_addtional.onboarding.cancel"), DANGER,
                    cancel.contains(mouseX, mouseY), true);
        }
        Rect refresh = refreshBounds(panel);
        drawButton(graphics, font, refresh,
                Component.translatable("screen.moveearth_addtional.s2.refresh"), MUTED,
                refresh.contains(mouseX, mouseY), true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        if (packet.searching()) return true;
        Rect panel = panelBounds();
        if (wildernessBounds(panel).contains(mouseX, mouseY)) {
            send(C2S_OnboardingActionPacket.Action.WILDERNESS, null);
            return true;
        }
        if (refreshBounds(panel).contains(mouseX, mouseY)) {
            send(C2S_OnboardingActionPacket.Action.REFRESH, null);
            return true;
        }
        if (packet.appliedNationId() != null) {
            if (cancelBounds(panel).contains(mouseX, mouseY)) {
                send(C2S_OnboardingActionPacket.Action.CANCEL, null);
            } else if (gameBounds(panel).contains(mouseX, mouseY)) {
                if (!gameVisible) gameVisible = true;
                else flap();
            }
            return true;
        }
        Rect list = new Rect(panel.x() + 18, panel.y() + 58, panel.width() - 36, panel.height() - 111);
        if (list.contains(mouseX, mouseY)) {
            for (int index = 0; index < packet.nations().size(); index++) {
                Rect apply = new Rect(list.right() - 8 - 92,
                        list.y() + index * ROW_HEIGHT - scrollOffset + 8, 80, 22);
                if (apply.contains(mouseX, mouseY)) {
                    send(C2S_OnboardingActionPacket.Action.APPLY, packet.nations().get(index).id());
                    return true;
                }
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!packet.searching() && packet.appliedNationId() == null) {
            Rect panel = panelBounds();
            Rect list = new Rect(panel.x() + 18, panel.y() + 58, panel.width() - 36, panel.height() - 111);
            scrollOffset = MoveEarthUi.scroll(scrollOffset, scrollY, ROW_HEIGHT,
                    packet.nations().size() * ROW_HEIGHT, list.height());
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (gameVisible && packet.appliedNationId() != null && keyCode == 32) {
            flap();
            return true;
        }
        return keyCode == 256 || super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void flap() { birdVelocity = -0.075F; }

    private void send(C2S_OnboardingActionPacket.Action action, java.util.UUID nationId) {
        PacketDistributor.sendToServer(new C2S_OnboardingActionPacket(packet.revision(), action, nationId));
    }

    private Rect panelBounds() {
        int w = Math.min(PANEL_WIDTH, width - 20);
        int h = Math.min(PANEL_HEIGHT, height - 20);
        return new Rect((width - w) / 2, (height - h) / 2, w, h);
    }
    private static Rect wildernessBounds(Rect panel) { return new Rect(panel.x() + 18, panel.bottom() - 37, 154, 22); }
    private static Rect cancelBounds(Rect panel) { return new Rect(panel.x() + 180, panel.bottom() - 37, 104, 22); }
    private static Rect refreshBounds(Rect panel) { return new Rect(panel.right() - 104, panel.bottom() - 37, 86, 22); }
    private static Rect gameBounds(Rect panel) { return new Rect(panel.x() + 18, panel.y() + 127, panel.width() - 36, 168); }
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public void onClose() { }
    @Override public boolean isPauseScreen() { return false; }
}

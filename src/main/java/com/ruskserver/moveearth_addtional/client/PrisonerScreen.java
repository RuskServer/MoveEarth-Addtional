package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.network.C2S_PrisonerActionPacket;
import com.ruskserver.moveearth_addtional.network.C2S_RequestPrisonerScreenPacket;
import com.ruskserver.moveearth_addtional.network.S2C_PrisonerSnapshotPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

public final class PrisonerScreen extends Screen implements SuppressesChatOverlay {
    private static final int PANEL_WIDTH = 620;
    private static final int PANEL_HEIGHT = 356;
    private static final int ROW_HEIGHT = 42;
    private S2C_PrisonerSnapshotPacket snapshot;
    private int scroll;
    private int refreshTicks;

    public PrisonerScreen(S2C_PrisonerSnapshotPacket snapshot) {
        super(Component.translatable("screen.moveearth_addtional.prisoner.title"));
        this.snapshot = snapshot;
    }

    public void update(S2C_PrisonerSnapshotPacket value) {
        snapshot = value;
        clampScroll();
    }

    public void updateState(S2C_PrisonerSnapshotPacket value) {
        snapshot = new S2C_PrisonerSnapshotPacket(true, value.state(), value.counterpart(),
                value.holdingNation(), value.remainingTicks(), value.jailDimension(), value.jailPos(),
                snapshot.intakePos(), snapshot.intake(), snapshot.entries(), snapshot.candidates());
    }

    @Override public void tick() {
        if (++refreshTicks >= 40) {
            refreshTicks = 0;
            PacketDistributor.sendToServer(new C2S_RequestPrisonerScreenPacket(snapshot.intakePos()));
        }
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(graphics, width, height);
        Rect panel = panel();
        drawPanel(graphics, panel);
        graphics.drawString(font, title, panel.x() + 18, panel.y() + 14, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.prisoner.subtitle"),
                panel.x() + 18, panel.y() + 29, MUTED, false);
        drawClose(graphics, font, close(panel), close(panel).contains(mouseX, mouseY));

        Rect status = status(panel);
        int stateColor = snapshot.state() == 0 ? MUTED : snapshot.state() == 1 ? GOLD : DANGER;
        drawCard(graphics, status, stateColor, snapshot.state() != 0, false);
        graphics.drawString(font, Component.translatable(
                "screen.moveearth_addtional.prisoner.state." + snapshot.state()),
                status.x() + 12, status.y() + 8, stateColor, false);
        Component stateDetail = snapshot.state() == 0
                ? Component.translatable("screen.moveearth_addtional.prisoner.state.none.detail")
                : Component.translatable("screen.moveearth_addtional.prisoner.state.detail",
                snapshot.counterpart().isBlank() ? snapshot.holdingNation() : snapshot.counterpart(),
                formatTicks(snapshot.remainingTicks()));
        graphics.drawString(font, font.plainSubstrByWidth(stateDetail.getString(), status.width() - 24),
                status.x() + 12, status.y() + 24, TEXT, false);

        int listTop = status.bottom() + 8;
        if (snapshot.intake().present()) {
            Rect intake = intake(panel);
            drawCard(graphics, intake, snapshot.intake().canImprison() ? SUCCESS : GOLD, false, false);
            graphics.drawString(font, Component.translatable("screen.moveearth_addtional.prisoner.intake"),
                    intake.x() + 12, intake.y() + 8, ACCENT, false);
            graphics.drawString(font, intakeStatus(), intake.x() + 12, intake.y() + 24,
                    snapshot.intake().canImprison() ? SUCCESS : DANGER, false);
            Rect imprison = imprison(intake);
            drawButton(graphics, font, imprison,
                    Component.translatable("screen.moveearth_addtional.prisoner.imprison"), SUCCESS,
                    snapshot.intake().canImprison() && imprison.contains(mouseX, mouseY),
                    snapshot.intake().canImprison());
            listTop = intake.bottom() + 8;
        }

        Rect list = new Rect(panel.x() + 18, listTop, panel.width() - 36,
                panel.bottom() - listTop - 18);
        graphics.enableScissor(list.x(), list.y(), list.right(), list.bottom());
        int y = list.y() - scroll;
        if (snapshot.entries().isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.moveearth_addtional.prisoner.list.empty"),
                    list.x() + 4, y + 8, MUTED, false);
        }
        for (S2C_PrisonerSnapshotPacket.EntryView entry : snapshot.entries()) {
            Rect row = new Rect(list.x(), y, list.width() - 7, ROW_HEIGHT - 4);
            if (row.bottom() > list.y() && row.y() < list.bottom()) {
                int color = entry.heldByViewer() ? GOLD : DANGER;
                drawCard(graphics, row, color, false, row.contains(mouseX, mouseY));
                graphics.drawString(font, Component.literal(entry.playerName()), row.x() + 11, row.y() + 7, TEXT, false);
                Component detail = Component.translatable(entry.state() == 0
                                ? "screen.moveearth_addtional.prisoner.row.escort"
                                : "screen.moveearth_addtional.prisoner.row.imprisoned",
                        entry.opponentName(), formatTicks(entry.remainingTicks()));
                graphics.drawString(font, font.plainSubstrByWidth(detail.getString(), row.width() - 122),
                        row.x() + 11, row.y() + 22, color, false);
                if (entry.canRelease()) drawButton(graphics, font, release(row),
                        Component.translatable("screen.moveearth_addtional.prisoner.release"), DANGER,
                        release(row).contains(mouseX, mouseY), true);
            }
            y += ROW_HEIGHT;
        }
        if (!snapshot.candidates().isEmpty()) {
            y += 4;
            graphics.drawString(font, Component.translatable("screen.moveearth_addtional.prisoner.transfer"),
                    list.x() + 4, y, MUTED, false);
            y += 15;
            for (S2C_PrisonerSnapshotPacket.CandidateView candidate : snapshot.candidates()) {
                Rect row = new Rect(list.x(), y, list.width() - 7, 28);
                drawButton(graphics, font, row, Component.translatable(
                        "screen.moveearth_addtional.prisoner.transfer_to", candidate.playerName()), ACCENT,
                        row.contains(mouseX, mouseY), true);
                y += 32;
            }
        }
        graphics.disableScissor();
        drawScrollbar(graphics, new Rect(list.right() - 4, list.y(), 4, list.height()),
                list.height(), contentHeight(), scroll);
    }

    private Component intakeStatus() {
        var intake = snapshot.intake();
        if (!intake.activeTerritory()) return Component.translatable("screen.moveearth_addtional.prisoner.intake.invalid_territory");
        if (!intake.safeSpace()) return Component.translatable("screen.moveearth_addtional.prisoner.intake.unsafe");
        if (!intake.captivePresent()) return Component.translatable("screen.moveearth_addtional.prisoner.intake.no_captive");
        if (!intake.sameDimension()) return Component.translatable("screen.moveearth_addtional.prisoner.intake.dimension");
        if (intake.distance() > 6) return Component.translatable("screen.moveearth_addtional.prisoner.intake.distance", intake.distance());
        return Component.translatable("screen.moveearth_addtional.prisoner.intake.ready", intake.ownerName());
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        Rect panel = panel();
        if (close(panel).contains(mouseX, mouseY)) { onClose(); return true; }
        if (snapshot.intake().present() && snapshot.intake().canImprison()
                && imprison(intake(panel)).contains(mouseX, mouseY)) {
            send(C2S_PrisonerActionPacket.Action.IMPRISON, null);
            return true;
        }
        Rect list = list(panel);
        int y = list.y() - scroll;
        for (var entry : snapshot.entries()) {
            Rect row = new Rect(list.x(), y, list.width() - 7, ROW_HEIGHT - 4);
            if (entry.canRelease() && release(row).contains(mouseX, mouseY)) {
                send(C2S_PrisonerActionPacket.Action.RELEASE, entry.playerId());
                return true;
            }
            y += ROW_HEIGHT;
        }
        if (!snapshot.candidates().isEmpty()) {
            y += 19;
            for (var candidate : snapshot.candidates()) {
                Rect row = new Rect(list.x(), y, list.width() - 7, 28);
                if (row.contains(mouseX, mouseY)) {
                    send(C2S_PrisonerActionPacket.Action.TRANSFER, candidate.playerId());
                    return true;
                }
                y += 32;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Rect list = list(panel());
        if (list.contains(mouseX, mouseY)) {
            scroll = scroll(scroll, scrollY, ROW_HEIGHT, contentHeight(), list.height());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void send(C2S_PrisonerActionPacket.Action action, java.util.UUID target) {
        PacketDistributor.sendToServer(new C2S_PrisonerActionPacket(action, target, snapshot.intakePos()));
    }

    private void clampScroll() { scroll = Math.min(scroll, Math.max(0, contentHeight() - list(panel()).height())); }
    private int contentHeight() { return Math.max(1, snapshot.entries().size() * ROW_HEIGHT
            + (snapshot.candidates().isEmpty() ? 0 : 19 + snapshot.candidates().size() * 32)); }
    private Rect panel() { return new Rect((width - PANEL_WIDTH) / 2, (height - PANEL_HEIGHT) / 2, PANEL_WIDTH, PANEL_HEIGHT); }
    private Rect close(Rect panel) { return new Rect(panel.right() - 31, panel.y() + 11, 18, 18); }
    private Rect status(Rect panel) { return new Rect(panel.x() + 18, panel.y() + 48, panel.width() - 36, 43); }
    private Rect intake(Rect panel) { return new Rect(panel.x() + 18, panel.y() + 99, panel.width() - 36, 48); }
    private Rect imprison(Rect intake) { return new Rect(intake.right() - 126, intake.y() + 12, 112, 24); }
    private Rect list(Rect panel) {
        int top = snapshot.intake().present() ? panel.y() + 155 : panel.y() + 99;
        return new Rect(panel.x() + 18, top, panel.width() - 36, panel.bottom() - top - 18);
    }
    private Rect release(Rect row) { return new Rect(row.right() - 98, row.y() + 7, 86, 24); }
    private static String formatTicks(long ticks) {
        long seconds = Math.max(0L, (ticks + 19L) / 20L);
        return "%d:%02d:%02d".formatted(seconds / 3600L, seconds / 60L % 60L, seconds % 60L);
    }
}

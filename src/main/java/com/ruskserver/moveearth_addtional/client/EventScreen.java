package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;
import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.network.C2S_EventScreenActionPacket;
import com.ruskserver.moveearth_addtional.network.S2C_EventScreenPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

public final class EventScreen extends Screen implements SuppressesChatOverlay {
    private static final int PANEL_WIDTH = 590;
    private static final int PANEL_HEIGHT = 340;
    private S2C_EventScreenPacket snapshot;
    private int tab;
    private int refreshTicks;
    private String notice = "";
    private int noticeTicks;

    public EventScreen(S2C_EventScreenPacket snapshot) {
        super(Component.literal("EVENTS"));
        this.snapshot = snapshot;
    }

    public static void receive(S2C_EventScreenPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof EventScreen screen) {
            screen.snapshot = packet;
            if (!packet.result().isEmpty()) {
                screen.notice = packet.result();
                screen.noticeTicks = 80;
            }
        }
        else if (packet.open()) minecraft.setScreen(new EventScreen(packet));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    @Override
    public void tick() {
        if (noticeTicks > 0) noticeTicks--;
        if (++refreshTicks >= 40) {
            refreshTicks = 0;
            PacketDistributor.sendToServer(new C2S_EventScreenActionPacket("refresh"));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Rect panel = panel();
        drawBackground(graphics, width, height);
        drawPanel(graphics, panel);
        int left = panel.x() + 16;
        int right = panel.right() - 16;
        graphics.drawString(font, title, left, panel.y() + 13, TEXT, false);
        String status = snapshot.active() ? "開催中 · 残り" + snapshot.remainingMinutes() + "分"
                : "次回自動開催まで約" + snapshot.nextMinutes() + "分";
        status = fit(status, Math.max(75, panel.width() - 145));
        graphics.drawString(font, status, right - 28 - font.width(status), panel.y() + 15,
                snapshot.active() ? SUCCESS : MUTED, false);
        Rect close = new Rect(panel.right() - 32, panel.y() + 8, 20, 20);
        drawClose(graphics, font, close, close.contains(mouseX, mouseY));

        int tabY = panel.y() + 39;
        drawTab(graphics, font, new Rect(left, tabY, 100, 24), Component.literal("イベント"),
                tab == 0, inside(mouseX, mouseY, left, tabY, 100, 24));
        drawTab(graphics, font, new Rect(left + 106, tabY, 100, 24),
                Component.literal("報酬 " + snapshot.claims().size()), tab == 1,
                inside(mouseX, mouseY, left + 106, tabY, 100, 24));

        Rect content = new Rect(left, panel.y() + 72, right - left, panel.height() - 119);
        if (tab == 0) renderEvent(graphics, content);
        else renderRewards(graphics, content);

        int footerY = panel.bottom() - 33;
        if (noticeTicks > 0)
            graphics.drawString(font, fit(notice, content.width() - 150), left, footerY + 7,
                    notice.contains("受け取りました") ? SUCCESS : MUTED, false);
        else if (tab == 0)
            graphics.drawString(font, "報酬通貨は終了時に自動入金", left, footerY + 7, MUTED, false);
        Rect claim = new Rect(right - 136, footerY, 136, 23);
        drawButton(graphics, font, claim, Component.literal("現物をまとめて受取"), GOLD,
                claim.contains(mouseX, mouseY), !snapshot.claims().isEmpty());
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderEvent(GuiGraphics graphics, Rect content) {
        int gap = 8;
        int leftWidth = Math.max(130, content.width() * 56 / 100);
        Rect primary = new Rect(content.x(), content.y(), leftWidth, content.height());
        Rect ranking = new Rect(primary.right() + gap, content.y(),
                Math.max(0, content.right() - primary.right() - gap), content.height());
        drawCard(graphics, primary, ACCENT, false, false);
        drawCard(graphics, ranking, GOLD, false, false);
        int textX = primary.x() + 12;
        int textWidth = primary.width() - 24;
        if (!snapshot.hasEvent()) {
            graphics.drawString(font, "次のイベントを準備中", textX, primary.y() + 15, TEXT, false);
            graphics.drawString(font, "開放時間で3時間ごとに自動開催", textX, primary.y() + 36, MUTED, false);
        } else {
            String heading = snapshot.active() ? snapshot.title() : "直近: " + snapshot.title();
            graphics.drawString(font, fit(heading, textWidth), textX, primary.y() + 14, GOLD, false);
            graphics.drawString(font, fit(snapshot.target(), textWidth), textX, primary.y() + 34, TEXT, false);
            String personal = "自分  " + snapshot.score() + "点  ·  "
                    + (snapshot.rank() == 0 ? "未参加" : snapshot.rank() + "位");
            graphics.drawString(font, fit(personal, textWidth), textX, primary.y() + 61, TEXT, false);
            if (primary.height() >= 110) {
                graphics.drawString(font, snapshot.jobBonus() ? "職業特典 +10% 適用" : "職業特典なし",
                        textX, primary.y() + 79, snapshot.jobBonus() ? SUCCESS : MUTED, false);
            }
            if (primary.height() >= 155) {
                int barY = primary.y() + 107;
                int barWidth = textWidth;
                graphics.fill(textX, barY, textX + barWidth, barY + 5, BAR_BACKGROUND);
                int filled = Math.min(barWidth, snapshot.score() * barWidth / 100);
                if (filled > 0) graphics.fill(textX, barY, textX + filled, barY + 5, SUCCESS);
                graphics.drawString(font, "参加ライン 100点 " + (snapshot.score() >= 100 ? "達成" : "未達成"),
                        textX, barY + 12, snapshot.score() >= 100 ? SUCCESS : MUTED, false);
                if (!snapshot.active() && snapshot.paidCurrency() > 0 && primary.height() >= 177)
                    graphics.drawString(font, "通貨報酬 +" + snapshot.paidCurrency() + " TC 入金済み",
                            textX, barY + 34, GOLD, false);
            }
        }
        int rankX = ranking.x() + 10;
        graphics.drawString(font, snapshot.active() ? "現在の順位" : "直近の順位",
                rankX, ranking.y() + 14, GOLD, false);
        List<S2C_EventScreenPacket.Leader> leaders = snapshot.leaders();
        int visibleLeaders = Math.min(leaders.size(), Math.max(0, (ranking.height() - 55) / 23));
        for (int index = 0; index < visibleLeaders; index++) {
            S2C_EventScreenPacket.Leader leader = leaders.get(index);
            String label = (index + 1) + ". " + leader.name() + "  " + leader.score() + "点";
            graphics.drawString(font, fit(label, ranking.width() - 20), rankX,
                    ranking.y() + 38 + index * 23, index == 0 ? GOLD : TEXT, false);
        }
        if (leaders.isEmpty()) graphics.drawString(font, "まだ参加者はいません", rankX,
                ranking.y() + 40, MUTED, false);
        if (snapshot.rank() > 5 && ranking.height() >= 175)
            graphics.drawString(font, "自分  " + snapshot.rank() + "位 · " + snapshot.score() + "点",
                    rankX, ranking.bottom() - 24, SUCCESS, false);
    }

    private void renderRewards(GuiGraphics graphics, Rect content) {
        drawCard(graphics, content, GOLD, false, false);
        graphics.drawString(font, "受取待ちの現物", content.x() + 13, content.y() + 12, GOLD, false);
        if (snapshot.claims().isEmpty()) {
            graphics.drawString(font, "受取待ちの報酬はありません", content.x() + 13,
                    content.y() + 43, MUTED, false);
            return;
        }
        int cellWidth = (content.width() - 38) / 2;
        int visibleClaims = Math.min(snapshot.claims().size(),
                2 * Math.max(0, (content.height() - 51) / 34));
        for (int index = 0; index < visibleClaims; index++) {
            S2C_EventScreenPacket.ClaimItem claim = snapshot.claims().get(index);
            int column = index % 2;
            int row = index / 2;
            int cellX = content.x() + 12 + column * (cellWidth + 10);
            int cellY = content.y() + 35 + row * 34;
            Rect cell = new Rect(cellX, cellY, cellWidth, 29);
            drawCard(graphics, cell, GOLD, false, false);
            Item item = BuiltInRegistries.ITEM.get(claim.itemId());
            if (item != Items.AIR) {
                graphics.renderItem(new ItemStack(item), cellX + 7, cellY + 6);
                String name = new ItemStack(item).getHoverName().getString();
                graphics.drawString(font, fit(name, cellWidth - 66), cellX + 29, cellY + 10, TEXT, false);
            }
            String count = "×" + claim.count();
            graphics.drawString(font, count, cell.right() - 7 - font.width(count), cellY + 10, GOLD, false);
        }
        if (snapshot.claims().size() > visibleClaims)
            graphics.drawString(font, "ほか " + (snapshot.claims().size() - visibleClaims) + "種類", content.x() + 13,
                    content.bottom() - 15, MUTED, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        Rect panel = panel();
        int left = panel.x() + 16;
        int tabY = panel.y() + 39;
        if (new Rect(panel.right() - 32, panel.y() + 8, 20, 20).contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (new Rect(left, tabY, 100, 24).contains(mouseX, mouseY)) {
            tab = 0;
            return true;
        }
        if (new Rect(left + 106, tabY, 100, 24).contains(mouseX, mouseY)) {
            tab = 1;
            return true;
        }
        if (!snapshot.claims().isEmpty()
                && new Rect(panel.right() - 152, panel.bottom() - 33, 136, 23).contains(mouseX, mouseY)) {
            PacketDistributor.sendToServer(new C2S_EventScreenActionPacket("claim"));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private Rect panel() {
        int panelWidth = Math.min(PANEL_WIDTH, width - 20);
        int panelHeight = Math.min(PANEL_HEIGHT, height - 20);
        return new Rect((width - panelWidth) / 2, (height - panelHeight) / 2,
                panelWidth, panelHeight);
    }

    private String fit(String value, int pixels) {
        return font.plainSubstrByWidth(value, Math.max(0, pixels));
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return new MoveEarthUi.Rect(x, y, width, height).contains(mouseX, mouseY);
    }
}

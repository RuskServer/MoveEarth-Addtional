package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.network.C2S_VoteMapPacket;
import com.ruskserver.moveearth_addtional.pvp.PvpMapDefinition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PvpMapVoteScreen extends Screen {
    private static final int PANEL_WIDTH = 540;
    private static final int PANEL_HEIGHT = 280;
    private static final int TEXT = 0xFFE8EDF3;
    private static final int MUTED = 0xFF8F9AA8;
    private static final int PANEL = 0xF012161D;
    private static final int CARD = 0xFF1B222C;
    private static final int CARD_HOVER = 0xFF242E3B;
    private static final int CARD_SELECTED = 0xFF1E3547;
    private static final int ACCENT = 0xFF5DCBFF;

    private final List<PvpMapDefinition> candidates;
    private final int totalDurationSeconds;
    private Map<String, Integer> votes = new HashMap<>();
    private int secondsRemaining;
    private String selectedMapId = "";

    public PvpMapVoteScreen(List<PvpMapDefinition> candidates, int durationSeconds) {
        super(Component.literal("MAP VOTING"));
        this.candidates = candidates;
        this.totalDurationSeconds = Math.max(1, durationSeconds);
        this.secondsRemaining = durationSeconds;
    }

    public void updateVotes(Map<String, Integer> votes, int secondsRemaining) {
        this.votes = new HashMap<>(votes);
        this.secondsRemaining = secondsRemaining;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // 投票フェーズ中はESCで画面を閉じない
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

        // ヘッダー
        graphics.drawString(font, "§lMAP VOTING", left + 16, top + 14, ACCENT, false);
        String timerText = secondsRemaining + "s";
        int timerColor = secondsRemaining <= 3 ? 0xFFFF6577 : 0xFFFFB454;
        graphics.drawString(font, "残り時間: " + timerText, left + panelWidth - 16 - font.width("残り時間: " + timerText), top + 14, timerColor, false);

        // タイマーバー
        int barX = left + 16;
        int barY = top + 28;
        int barWidth = panelWidth - 32;
        graphics.fill(barX, barY, barX + barWidth, barY + 3, 0xFF0C1015);
        float progress = Mth.clamp((float) secondsRemaining / totalDurationSeconds, 0.0F, 1.0F);
        graphics.fill(barX, barY, barX + (int) (barWidth * progress), barY + 3, timerColor);

        // カード描画（2列または1列グリッド）
        int count = candidates.size();
        int cols = count > 1 ? 2 : 1;
        int rows = (count + cols - 1) / cols;
        int gap = 10;
        int cardW = (panelWidth - 32 - (cols - 1) * gap) / cols;
        int cardH = Math.min(95, (panelHeight - 75 - (rows - 1) * gap) / Math.max(1, rows));

        int totalVotes = 0;
        for (int v : votes.values()) totalVotes += v;

        int startY = top + 38;
        for (int i = 0; i < count; i++) {
            PvpMapDefinition map = candidates.get(i);
            int c = i % cols;
            int r = i / cols;
            int cx = left + 16 + c * (cardW + gap);
            int cy = startY + r * (cardH + gap);
            drawMapCard(graphics, map, cx, cy, cardW, cardH, mouseX, mouseY, totalVotes);
        }

        // フッター
        String footer = "カードをクリックして投票します。最も票を集めたマップで対戦が行われます。";
        graphics.drawCenteredString(font, footer, left + panelWidth / 2, top + panelHeight - 16, MUTED);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawMapCard(GuiGraphics graphics, PvpMapDefinition map, int x, int y, int w, int h,
                             int mouseX, int mouseY, int totalVotes) {
        boolean selected = map.id().equals(selectedMapId);
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);

        int bg = selected ? CARD_SELECTED : hovered ? CARD_HOVER : CARD;
        graphics.fill(x, y, x + w, y + h, bg);
        drawBorder(graphics, x, y, w, h, selected ? ACCENT : hovered ? 0xFF5DCBFF : 0xFF354150);

        // 左ストライプ（マップテーマカラー）
        int stripeColor = map.cardColor() != 0 ? map.cardColor() : ACCENT;
        graphics.fill(x, y, x + 4, y + h, stripeColor);

        // タイトル
        graphics.drawString(font, map.displayName(), x + 12, y + 10, selected ? ACCENT : TEXT, false);

        // 得票数
        int voteCount = votes.getOrDefault(map.id(), 0);
        String voteText = voteCount + " 票";
        graphics.drawString(font, voteText, x + w - 12 - font.width(voteText), y + 10, 0xFFFFB454, false);

        // 説明文
        String desc = font.plainSubstrByWidth(map.description().isEmpty() ? "対称アリーナマップ" : map.description(), w - 24);
        graphics.drawString(font, desc, x + 12, y + 26, MUTED, false);

        // 得票率バー
        int barX = x + 12;
        int barY = y + h - 22;
        int barW = w - 24;
        graphics.fill(barX, barY, barX + barW, barY + 6, 0xFF0C1015);
        if (totalVotes > 0 && voteCount > 0) {
            int fillW = (int) (barW * ((float) voteCount / totalVotes));
            graphics.fill(barX, barY, barX + fillW, barY + 6, stripeColor);
        }

        // 投票状態表示
        if (selected) {
            String status = "✓ 投票中";
            graphics.drawString(font, status, x + w - 12 - font.width(status), y + h - 14, 0xFF68E09B, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int panelWidth = Math.min(PANEL_WIDTH, width - 20);
            int panelHeight = Math.min(PANEL_HEIGHT, height - 20);
            int left = (width - panelWidth) / 2;
            int top = (height - panelHeight) / 2;

            int count = candidates.size();
            int cols = count > 1 ? 2 : 1;
            int rows = (count + cols - 1) / cols;
            int gap = 10;
            int cardW = (panelWidth - 32 - (cols - 1) * gap) / cols;
            int cardH = Math.min(95, (panelHeight - 75 - (rows - 1) * gap) / Math.max(1, rows));

            int startY = top + 38;
            for (int i = 0; i < count; i++) {
                PvpMapDefinition map = candidates.get(i);
                int c = i % cols;
                int r = i / cols;
                int cx = left + 16 + c * (cardW + gap);
                int cy = startY + r * (cardH + gap);
                if (inside(mouseX, mouseY, cx, cy, cardW, cardH)) {
                    selectedMapId = map.id();
                    PacketDistributor.sendToServer(new C2S_VoteMapPacket(map.id()));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }
}

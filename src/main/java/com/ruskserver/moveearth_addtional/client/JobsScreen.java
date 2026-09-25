package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;
import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.jobs.JobXpFormat;
import com.ruskserver.moveearth_addtional.network.C2S_JobsActionPacket;
import com.ruskserver.moveearth_addtional.network.S2C_JobsLeaderboardPacket;
import com.ruskserver.moveearth_addtional.network.S2C_OpenJobsScreenPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

/** Job progress, action income, and rankings. */
public final class JobsScreen extends Screen implements SuppressesChatOverlay {
    private static final int W = 570;
    private static final int H = 318;
    private S2C_OpenJobsScreenPacket packet;
    private final Map<String, List<S2C_JobsLeaderboardPacket.Entry>> leaderboards = new HashMap<>();
    private int tab;
    private int selected;
    private int scroll;

    public JobsScreen(S2C_OpenJobsScreenPacket packet) {
        super(Component.literal("JOBS"));
        this.packet = packet;
    }

    public void update(S2C_OpenJobsScreenPacket updated) {
        this.packet = updated;
        selected = Mth.clamp(selected, 0, Math.max(0, entries() - 1));
        scroll = Mth.clamp(scroll, 0, Math.max(0, entries() - 7));
    }

    public void updateLeaderboard(S2C_JobsLeaderboardPacket updated) {
        leaderboards.put(updated.jobId().toString(), updated.entries());
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawBackground(g, width, height);
        int x = left(), y = top();
        drawPanel(g, new MoveEarthUi.Rect(x, y, W, H));
        g.drawString(font, title, x + 16, y + 13, TEXT, false);
        g.drawString(font, fit(packet.subjectName(), 300), x + 16, y + 27, MUTED, false);
        String balance = "残高 " + packet.balance() + " TC";
        g.drawString(font, balance, x + W - 45 - font.width(balance), y + 15, GOLD, false);
        tab(g, x + 16, y + 40, 90, "職業", tab == 0, mouseX, mouseY);
        tab(g, x + 112, y + 40, 90, "ランキング", tab == 1, mouseX, mouseY);
        MoveEarthUi.Rect close = new MoveEarthUi.Rect(x + W - 36, y + 10, 20, 20);
        drawClose(g, font, close, close.contains(mouseX, mouseY));
        drawCard(g, new MoveEarthUi.Rect(x + 12, y + 68, 253, H - 80), ACCENT, false, false);
        drawCard(g, new MoveEarthUi.Rect(x + 270, y + 68, W - 282, H - 80), ACCENT, false, false);
        drawList(g, mouseX, mouseY);
        drawJob(g, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawList(GuiGraphics g, int mouseX, int mouseY) {
        int x = left() + 20, y = top() + 76;
        int count = entries();
        if (count == 0) g.drawString(font, "職業がありません", x, y, MUTED, false);
        for (int row = 0; row < 7 && row + scroll < count; row++) {
            int index = row + scroll;
            int yy = y + row * 31;
            drawCard(g, new MoveEarthUi.Rect(x, yy, 234, 27), ACCENT, index == selected,
                    inside(mouseX, mouseY, x, yy, 234, 27));
            var job = packet.jobs().get(index);
            g.drawString(font, fit(job.displayName(), 155), x + 10, yy + 5, TEXT, false);
            g.drawString(font, "Lv." + job.level() + (job.active() ? "  選択中" : ""),
                    x + 10, yy + 16, job.active() ? ACCENT : MUTED, false);
        }
    }

    private void drawJob(GuiGraphics g, int mouseX, int mouseY) {
        if (packet.jobs().isEmpty() || selected >= packet.jobs().size()) return;
        var job = packet.jobs().get(selected);
        int x = left() + 282, y = top() + 80;
        g.drawString(font, job.displayName(), x, y, TEXT, false);
        g.drawString(font, "Lv." + job.level() + " / " + job.maxLevel(), x, y + 20, ACCENT, false);
        String xp = job.xpForNextLevel() > 0
                ? JobXpFormat.format(job.xpInLevel()) + " / " + JobXpFormat.format(job.xpForNextLevel()) + " XP"
                : "MAX";
        g.drawString(font, xp, x, y + 36, MUTED, false);
        g.drawWordWrap(font, Component.literal(job.description()), x, y + 56, 255, MUTED);
        if (tab == 1) {
            List<S2C_JobsLeaderboardPacket.Entry> ranking = leaderboards.get(job.id().toString());
            if (ranking == null) g.drawString(font, "読み込み中...", x, y + 110, MUTED, false);
            else for (int i = 0; i < Math.min(7, ranking.size()); i++) {
                var entry = ranking.get(i);
                g.drawString(font, (i + 1) + ". " + entry.playerName() + " Lv." + entry.level(),
                        x, y + 98 + i * 17, TEXT, false);
            }
        } else if (packet.selfView()) {
            g.drawString(font, "行動報酬  25 XP = 1 TC", x, y + 137, GOLD, false);
            g.drawString(font, "次の通貨まで " + JobXpFormat.format(packet.xpTowardsCurrency()) + " / 25 XP",
                    x, y + 155, MUTED, false);
            g.drawString(font, "今の時間帯 " + packet.incomeThisHour() + " / 20",
                    x, y + 173, TEXT, false);
            g.drawString(font, "本日 " + packet.incomeToday() + " / 80", x, y + 191, TEXT, false);
            action(g, x, top() + H - 47, 125, 24, job.active() ? "職業を解除" : "職業を選択",
                    job.active() ? DANGER : ACCENT, mouseX, mouseY);
            g.drawString(font, "選択 " + packet.jobs().stream().filter(S2C_OpenJobsScreenPacket.JobEntry::active).count()
                    + " / " + packet.maxActiveJobs(), x, top() + H - 64, MUTED, false);
        }
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int x = left(), y = top();
        if (inside(mouseX, mouseY, x + W - 36, y + 10, 20, 20)) { onClose(); return true; }
        for (int i = 0; i < 2; i++) {
            if (inside(mouseX, mouseY, x + 16 + i * 96, y + 40, 90, 22)) {
                tab = i; selected = 0; scroll = 0;
                requestRanking(); return true;
            }
        }
        for (int row = 0; row < 7; row++) {
            int index = row + scroll;
            if (index < entries() && inside(mouseX, mouseY, x + 20, y + 76 + row * 31, 234, 27)) {
                selected = index; requestRanking(); return true;
            }
        }
        if (tab == 0 && packet.selfView() && selected < packet.jobs().size()
                && inside(mouseX, mouseY, x + 282, y + H - 47, 125, 24)) {
            var job = packet.jobs().get(selected);
            send(job.active() ? "LEAVE" : "JOIN", job.id().toString(), 0);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (inside(mouseX, mouseY, left() + 20, top() + 76, 234, 217)) {
            scroll = Mth.clamp(scroll - (int) Math.signum(vertical), 0, Math.max(0, entries() - 7));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    private void requestRanking() {
        if (tab == 1 && selected < packet.jobs().size())
            send("RANKING", packet.jobs().get(selected).id().toString(), 0);
    }

    private void send(String action, String id, int amount) {
        PacketDistributor.sendToServer(new C2S_JobsActionPacket(action, id, "", amount));
    }

    private int entries() { return packet.jobs().size(); }
    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
    private String fit(String value, int pixels) { return font.plainSubstrByWidth(value, pixels); }
    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
    private void tab(GuiGraphics g, int x, int y, int w, String label, boolean selected, int mx, int my) {
        MoveEarthUi.Rect bounds = new MoveEarthUi.Rect(x, y, w, 22);
        drawTab(g, font, bounds, Component.literal(label), selected, bounds.contains(mx, my));
    }
    private void action(GuiGraphics g, int x, int y, int w, int h, String label,
                        int accent, int mouseX, int mouseY) {
        MoveEarthUi.Rect bounds = new MoveEarthUi.Rect(x, y, w, h);
        drawButton(g, font, bounds, Component.literal(label), accent, bounds.contains(mouseX, mouseY), true);
    }
}

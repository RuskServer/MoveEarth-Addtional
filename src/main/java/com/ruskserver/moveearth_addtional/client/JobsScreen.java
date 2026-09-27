package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;
import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.jobs.JobXpFormat;
import com.ruskserver.moveearth_addtional.network.c2s.other.C2S_JobsActionPacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_JobsLeaderboardPacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_OpenJobsScreenPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

/** Job progress, action income, and rankings. */
public final class JobsScreen extends Screen implements SuppressesChatOverlay {
    private static final String KEY = "screen.moveearth_addtional.jobs.";
    private static final int W = 570;
    private static final int H = 318;
    /** Income caps are counted per UTC hour and UTC day on the server (EconomyLedgerSavedData). */
    private static final long HOUR_MILLIS = 3_600_000L;
    private static final long DAY_MILLIS = 86_400_000L;
    private static final DateTimeFormatter RESET_TIME = DateTimeFormatter.ofPattern("H:mm");
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
        drawPanel(g, new Rect(x, y, W, H));
        g.drawString(font, title, x + 16, y + 13, TEXT, false);
        g.drawString(font, fit(packet.subjectName(), 300), x + 16, y + 27, MUTED, false);
        Component balance = Component.translatable(KEY + "balance", packet.balance());
        g.drawString(font, balance, x + W - 45 - font.width(balance), y + 15, GOLD, false);
        for (int index = 0; index < 2; index++) {
            Rect bounds = tabBounds(index);
            drawTab(g, font, bounds, Component.translatable(KEY + (index == 0 ? "tab.jobs" : "tab.ranking")),
                    tab == index, bounds.contains(mouseX, mouseY));
        }
        Rect close = closeBounds();
        drawClose(g, font, close, close.contains(mouseX, mouseY));
        drawCard(g, new Rect(x + 12, y + 68, 253, H - 80), ACCENT, false, false);
        drawCard(g, new Rect(x + 270, y + 68, W - 282, H - 80), ACCENT, false, false);
        drawList(g, mouseX, mouseY);
        drawJob(g, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawList(GuiGraphics g, int mouseX, int mouseY) {
        int count = entries();
        if (count == 0) g.drawString(font, Component.translatable(KEY + "empty"),
                left() + 20, top() + 76, MUTED, false);
        for (int row = 0; row < 7 && row + scroll < count; row++) {
            int index = row + scroll;
            Rect bounds = rowBounds(row);
            drawCard(g, bounds, ACCENT, index == selected, bounds.contains(mouseX, mouseY));
            var job = packet.jobs().get(index);
            g.drawString(font, fit(job.displayName(), 155), bounds.x() + 10, bounds.y() + 5, TEXT, false);
            g.drawString(font, Component.translatable(job.active() ? KEY + "level_active" : KEY + "level", job.level()),
                    bounds.x() + 10, bounds.y() + 16, job.active() ? ACCENT : MUTED, false);
        }
    }

    private void drawJob(GuiGraphics g, int mouseX, int mouseY) {
        if (packet.jobs().isEmpty() || selected >= packet.jobs().size()) return;
        var job = packet.jobs().get(selected);
        int x = left() + 282, y = top() + 80;
        g.drawString(font, job.displayName(), x, y, TEXT, false);
        g.drawString(font, Component.translatable(KEY + "level_of", job.level(), job.maxLevel()), x, y + 20, ACCENT, false);
        Component xp = job.xpForNextLevel() > 0
                ? Component.translatable(KEY + "xp", JobXpFormat.format(job.xpInLevel()),
                JobXpFormat.format(job.xpForNextLevel()))
                : Component.translatable(KEY + "max");
        g.drawString(font, xp, x, y + 36, MUTED, false);

        int descriptionTop = y + 56;
        if (tab == 1) {
            // Ranking sits under a short description.
            drawDescription(g, job.description(), x, descriptionTop, descriptionTop + 4 * font.lineHeight);
            List<S2C_JobsLeaderboardPacket.Entry> ranking = leaderboards.get(job.id().toString());
            if (ranking == null) g.drawString(font, Component.translatable(KEY + "loading"), x, y + 110, MUTED, false);
            else for (int i = 0; i < Math.min(7, ranking.size()); i++) {
                var entry = ranking.get(i);
                g.drawString(font, Component.translatable(KEY + "rank_row", i + 1, entry.playerName(), entry.level()),
                        x, y + 98 + i * 17, TEXT, false);
            }
            return;
        }
        if (!packet.selfView()) {
            drawDescription(g, job.description(), x, descriptionTop, actionBounds().y() - 6);
            return;
        }
        // Fixed, non-overlapping blocks: description, then income, then the button row.
        int incomeTop = actionBounds().y() - 8 - 4 * 13;
        drawDescription(g, job.description(), x, descriptionTop, incomeTop - 6);
        g.drawString(font, Component.translatable(KEY + "income.rate"), x, incomeTop, GOLD, false);
        g.drawString(font, Component.translatable(KEY + "income.next",
                JobXpFormat.format(packet.xpTowardsCurrency())), x, incomeTop + 13, MUTED, false);
        long now = System.currentTimeMillis();
        g.drawString(font, Component.translatable(KEY + "income.hour", packet.incomeThisHour(),
                resetTime(now, HOUR_MILLIS)), x, incomeTop + 26, TEXT, false);
        g.drawString(font, Component.translatable(KEY + "income.day", packet.incomeToday(),
                resetTime(now, DAY_MILLIS)), x, incomeTop + 39, TEXT, false);

        Rect action = actionBounds();
        drawButton(g, font, action, Component.translatable(job.active() ? KEY + "leave" : KEY + "join"),
                job.active() ? DANGER : ACCENT, action.contains(mouseX, mouseY), true);
        long activeJobs = packet.jobs().stream().filter(S2C_OpenJobsScreenPacket.JobEntry::active).count();
        g.drawString(font, Component.translatable(KEY + "active_count", activeJobs, packet.maxActiveJobs()),
                action.right() + 10, action.y() + (action.height() - 8) / 2, MUTED, false);
    }

    /** Word-wrapped description clipped to the space above the next block, ending with an ellipsis. */
    private void drawDescription(GuiGraphics g, String description, int x, int top, int bottom) {
        List<FormattedCharSequence> lines = font.split(Component.literal(description), 255);
        int fit = Math.max(0, (bottom - top) / font.lineHeight);
        for (int i = 0; i < Math.min(fit, lines.size()); i++) {
            int lineY = top + i * font.lineHeight;
            g.drawString(font, lines.get(i), x, lineY, MUTED, false);
            if (i == fit - 1 && lines.size() > fit) {
                g.drawString(font, "…", x + font.width(lines.get(i)), lineY, MUTED, false);
            }
        }
    }

    /** Next boundary of the server's UTC window, shown in the player's own time zone. */
    private static String resetTime(long now, long windowMillis) {
        long next = (Math.floorDiv(now, windowMillis) + 1) * windowMillis;
        return RESET_TIME.format(Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault()));
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (closeBounds().contains(mouseX, mouseY)) { onClose(); return true; }
        for (int i = 0; i < 2; i++) {
            if (tabBounds(i).contains(mouseX, mouseY)) {
                tab = i; selected = 0; scroll = 0;
                requestRanking(); return true;
            }
        }
        for (int row = 0; row < 7; row++) {
            int index = row + scroll;
            if (index < entries() && rowBounds(row).contains(mouseX, mouseY)) {
                selected = index; requestRanking(); return true;
            }
        }
        if (tab == 0 && packet.selfView() && selected < packet.jobs().size()
                && actionBounds().contains(mouseX, mouseY)) {
            var job = packet.jobs().get(selected);
            send(job.active() ? "LEAVE" : "JOIN", job.id().toString(), 0);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (new Rect(left() + 20, top() + 76, 234, 217).contains(mouseX, mouseY)) {
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

    private Rect closeBounds() { return new Rect(left() + W - 36, top() + 10, 20, 20); }
    private Rect tabBounds(int index) { return new Rect(left() + 16 + index * 96, top() + 40, 90, 22); }
    private Rect rowBounds(int row) { return new Rect(left() + 20, top() + 76 + row * 31, 234, 27); }
    private Rect actionBounds() { return new Rect(left() + 282, top() + H - 47, 125, 24); }
}

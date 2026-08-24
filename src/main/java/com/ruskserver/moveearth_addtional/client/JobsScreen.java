package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.network.C2S_JobsActionPacket;
import com.ruskserver.moveearth_addtional.network.S2C_OpenJobsScreenPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** One-screen job browser, progress view, selection control and operator panel. */
public final class JobsScreen extends Screen {
    private static final int PANEL_WIDTH = 600;
    private static final int PANEL_HEIGHT = 350;
    private static final int TEXT = 0xFFE8EDF3;
    private static final int MUTED = 0xFF8F9AA8;
    private static final int PANEL = 0xF012161D;
    private static final int CARD = 0xFF1B222C;
    private static final int CARD_HOVER = 0xFF242E3B;
    private static final int ACCENT = 0xFF62C6FF;
    private static final int ACTIVE = 0xFF68E09B;
    private static final int DANGER = 0xFFFF6577;

    private S2C_OpenJobsScreenPacket packet;
    private int selectedJob;
    private int scroll;
    private boolean adminMode;
    private EditBox amountBox;
    private long resetArmedUntil;

    public JobsScreen(S2C_OpenJobsScreenPacket packet) {
        super(Component.literal("JOBS"));
        this.packet = packet;
    }

    @Override
    protected void init() {
        Layout layout = layout();
        amountBox = new EditBox(font, layout.right + 12, layout.top + 155,
                Math.max(70, layout.rightWidth - 24), 20, Component.literal("数量"));
        amountBox.setMaxLength(7);
        amountBox.setValue("100");
        amountBox.visible = adminMode && packet.canAdmin();
        addRenderableWidget(amountBox);
    }

    public void update(S2C_OpenJobsScreenPacket updated) {
        String selectedId = selectedEntry() == null ? "" : selectedEntry().id().toString();
        this.packet = updated;
        for (int i = 0; i < updated.jobs().size(); i++) {
            if (updated.jobs().get(i).id().toString().equals(selectedId)) {
                selectedJob = i;
                break;
            }
        }
        selectedJob = Mth.clamp(selectedJob, 0, Math.max(0, updated.jobs().size() - 1));
        clampScroll();
        if (amountBox != null) {
            amountBox.visible = adminMode && updated.canAdmin();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width, height, 0xD0080B10, 0xE010151D);
        Layout layout = layout();
        graphics.fill(layout.left, layout.top, layout.left + layout.width, layout.top + layout.height, PANEL);
        drawBorder(graphics, layout.left, layout.top, layout.width, layout.height, 0xFF354150);

        graphics.drawString(font, title, layout.left + 16, layout.top + 14, TEXT, false);
        String subject = adminMode ? "管理対象: " + packet.subjectName() : packet.subjectName();
        graphics.drawString(font, subject, layout.left + 72, layout.top + 14,
                adminMode ? 0xFFFFB454 : MUTED, false);
        String summary = packet.points() + " PT  |  選択 " + activeCount() + "/" + packet.maxActiveJobs();
        graphics.drawString(font, summary, layout.left + layout.width - 190, layout.top + 14,
                0xFFFFB454, false);

        if (packet.canAdmin()) {
            drawButton(graphics, layout.left + layout.width - 84, layout.top + 34, 52, 18,
                    adminMode ? "自分" : "管理", ACCENT, mouseX, mouseY, true);
        }
        drawButton(graphics, layout.left + layout.width - 26, layout.top + 8, 18, 18,
                "×", DANGER, mouseX, mouseY, true);

        drawJobList(graphics, layout, mouseX, mouseY);
        drawDetails(graphics, layout, mouseX, mouseY);
        if (adminMode && packet.canAdmin()) {
            drawAdminControls(graphics, layout, mouseX, mouseY);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawJobList(GuiGraphics graphics, Layout layout, int mouseX, int mouseY) {
        int x = layout.left + 12;
        int y = layout.top + 58;
        graphics.drawString(font, "職業一覧", x, y - 15, MUTED, false);
        int rows = visibleRows(layout);
        List<S2C_OpenJobsScreenPacket.JobEntry> jobs = packet.jobs();
        if (jobs.isEmpty()) {
            graphics.drawString(font, "職業定義が読み込まれていません", x + 8, y + 12, DANGER, false);
            return;
        }
        for (int row = 0; row < rows && scroll + row < jobs.size(); row++) {
            int index = scroll + row;
            S2C_OpenJobsScreenPacket.JobEntry job = jobs.get(index);
            int cardY = y + row * 44;
            boolean hovered = inside(mouseX, mouseY, x, cardY, layout.leftWidth - 24, 38);
            int background = index == selectedJob ? 0xFF26394A : hovered ? CARD_HOVER : CARD;
            graphics.fill(x, cardY, x + layout.leftWidth - 24, cardY + 38, background);
            graphics.fill(x, cardY, x + 4, cardY + 38, job.active() ? ACTIVE : 0xFF56616D);
            drawBorder(graphics, x, cardY, layout.leftWidth - 24, 38,
                    index == selectedJob ? ACCENT : 0xFF354150);
            graphics.drawString(font, fit(job.displayName(), layout.leftWidth - 80), x + 12, cardY + 7,
                    job.active() ? ACTIVE : TEXT, false);
            graphics.drawString(font, "Lv." + job.level(), x + 12, cardY + 21, MUTED, false);
            if (job.active()) {
                graphics.drawString(font, "選択中", x + layout.leftWidth - 64, cardY + 14, ACTIVE, false);
            }
        }
        if (jobs.size() > rows) {
            graphics.drawCenteredString(font, "マウスホイールでスクロール",
                    x + (layout.leftWidth - 24) / 2, layout.top + layout.height - 18, MUTED);
        }
    }

    private void drawDetails(GuiGraphics graphics, Layout layout, int mouseX, int mouseY) {
        S2C_OpenJobsScreenPacket.JobEntry job = selectedEntry();
        if (job == null) return;
        int x = layout.right + 12;
        int y = layout.top + 62;
        graphics.drawString(font, job.displayName(), x, y, TEXT, false);
        graphics.drawString(font, job.id().toString(), x, y + 14, MUTED, false);
        graphics.drawString(font, fit(job.description(), layout.rightWidth - 24), x, y + 29, MUTED, false);

        if (adminMode) {
            String compactProgress = "Lv." + job.level() + "/" + job.maxLevel() + "  |  "
                    + (job.xpForNextLevel() <= 0 ? "MAX" : job.xpInLevel() + "/" + job.xpForNextLevel() + " XP");
            graphics.drawString(font, fit(compactProgress, layout.rightWidth - 24), x, y + 44, ACCENT, false);
            return;
        }

        String level = "レベル " + job.level() + " / " + job.maxLevel();
        graphics.drawString(font, level, x, y + 53, job.level() >= job.maxLevel() ? ACTIVE : ACCENT, false);
        int barY = y + 70;
        int barWidth = Math.max(80, layout.rightWidth - 24);
        graphics.fill(x, barY, x + barWidth, barY + 9, 0xFF0C1015);
        double ratio = job.xpForNextLevel() <= 0 ? 1.0D
                : Math.min(1.0D, job.xpInLevel() / (double) job.xpForNextLevel());
        graphics.fill(x, barY, x + (int) (barWidth * ratio), barY + 9,
                job.level() >= job.maxLevel() ? ACTIVE : ACCENT);
        String xp = job.xpForNextLevel() <= 0 ? "MAX"
                : job.xpInLevel() + " / " + job.xpForNextLevel() + " XP";
        graphics.drawString(font, xp, x, barY + 14, TEXT, false);
        graphics.drawString(font, "累計XP: " + job.totalXp(), x, barY + 29, MUTED, false);
        graphics.drawString(font, "レベルアップ報酬: " + job.pointsPerLevel() + " PT", x, barY + 44,
                0xFFFFB454, false);

        if (!adminMode) {
            String buttonText = job.active() ? "職業を解除" : "この職業を選択";
            boolean enabled = job.active() || activeCount() < packet.maxActiveJobs();
            drawButton(graphics, x, layout.top + layout.height - 48, layout.rightWidth - 24, 26,
                    buttonText, job.active() ? DANGER : ACTIVE, mouseX, mouseY, enabled);
            if (!enabled) {
                graphics.drawCenteredString(font, "選択枠が上限です", x + (layout.rightWidth - 24) / 2,
                        layout.top + layout.height - 62, DANGER);
            }
        }
    }

    private void drawAdminControls(GuiGraphics graphics, Layout layout, int mouseX, int mouseY) {
        int x = layout.right + 12;
        int width = layout.rightWidth - 24;
        int targetY = layout.top + 119;
        drawButton(graphics, x, targetY, 22, 20, "‹", ACCENT, mouseX, mouseY,
                packet.onlinePlayers().size() > 1);
        graphics.drawCenteredString(font, fit(packet.subjectName(), width - 60), x + width / 2, targetY + 6, TEXT);
        drawButton(graphics, x + width - 22, targetY, 22, 20, "›", ACCENT, mouseX, mouseY,
                packet.onlinePlayers().size() > 1);

        graphics.drawString(font, "操作量", x, layout.top + 144, MUTED, false);
        int buttonY = layout.top + 179;
        drawButton(graphics, x, buttonY, width, 20, "選択職業へXPを追加", ACTIVE, mouseX, mouseY,
                selectedEntry() != null);
        drawButton(graphics, x, buttonY + 24, (width - 5) / 2, 20, "ポイント加算", ACCENT,
                mouseX, mouseY, true);
        drawButton(graphics, x + (width + 5) / 2, buttonY + 24, (width - 5) / 2, 20, "ポイント減算", DANGER,
                mouseX, mouseY, true);
        boolean armed = System.currentTimeMillis() < resetArmedUntil;
        drawButton(graphics, x, buttonY + 48, width, 20,
                armed ? "もう一度押してリセット確定" : "全職業データをリセット",
                DANGER, mouseX, mouseY, true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        Layout layout = layout();
        if (inside(mouseX, mouseY, layout.left + layout.width - 26, layout.top + 8, 18, 18)) {
            onClose();
            return true;
        }
        if (packet.canAdmin() && inside(mouseX, mouseY,
                layout.left + layout.width - 84, layout.top + 34, 52, 18)) {
            adminMode = !adminMode;
            resetArmedUntil = 0;
            if (!adminMode) send("REFRESH", "", "", 0);
            else send("VIEW", "", packet.subjectName(), 0);
            if (amountBox != null) amountBox.visible = adminMode;
            return true;
        }

        int listX = layout.left + 12;
        int listY = layout.top + 58;
        for (int row = 0; row < visibleRows(layout) && scroll + row < packet.jobs().size(); row++) {
            if (inside(mouseX, mouseY, listX, listY + row * 44, layout.leftWidth - 24, 38)) {
                selectedJob = scroll + row;
                resetArmedUntil = 0;
                return true;
            }
        }

        S2C_OpenJobsScreenPacket.JobEntry job = selectedEntry();
        int rightX = layout.right + 12;
        int rightWidth = layout.rightWidth - 24;
        if (!adminMode && job != null && inside(mouseX, mouseY, rightX,
                layout.top + layout.height - 48, rightWidth, 26)) {
            if (job.active() || activeCount() < packet.maxActiveJobs()) {
                send(job.active() ? "LEAVE" : "JOIN", job.id().toString(), "", 0);
            }
            return true;
        }

        if (adminMode && packet.canAdmin()) {
            int targetY = layout.top + 119;
            if (inside(mouseX, mouseY, rightX, targetY, 22, 20)) {
                cycleTarget(-1);
                return true;
            }
            if (inside(mouseX, mouseY, rightX + rightWidth - 22, targetY, 22, 20)) {
                cycleTarget(1);
                return true;
            }
            int operationY = layout.top + 179;
            if (job != null && inside(mouseX, mouseY, rightX, operationY, rightWidth, 20)) {
                send("ADD_XP", job.id().toString(), packet.subjectName(), amount());
                return true;
            }
            if (inside(mouseX, mouseY, rightX, operationY + 24, (rightWidth - 5) / 2, 20)) {
                send("ADD_POINTS", "", packet.subjectName(), amount());
                return true;
            }
            if (inside(mouseX, mouseY, rightX + (rightWidth + 5) / 2,
                    operationY + 24, (rightWidth - 5) / 2, 20)) {
                send("ADD_POINTS", "", packet.subjectName(), -amount());
                return true;
            }
            if (inside(mouseX, mouseY, rightX, operationY + 48, rightWidth, 20)) {
                long now = System.currentTimeMillis();
                if (now < resetArmedUntil) {
                    resetArmedUntil = 0;
                    send("RESET", "", packet.subjectName(), 0);
                } else {
                    resetArmedUntil = now + 3_000L;
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Layout layout = layout();
        if (inside(mouseX, mouseY, layout.left + 8, layout.top + 52, layout.leftWidth - 8, layout.height - 64)) {
            scroll -= (int) Math.signum(scrollY);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void cycleTarget(int direction) {
        List<String> players = packet.onlinePlayers();
        if (players.isEmpty()) return;
        int current = 0;
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).equalsIgnoreCase(packet.subjectName())) {
                current = i;
                break;
            }
        }
        int next = Math.floorMod(current + direction, players.size());
        send("VIEW", "", players.get(next), 0);
    }

    private int amount() {
        if (amountBox == null) return 1;
        try {
            return Mth.clamp(Integer.parseInt(amountBox.getValue()), 1,
                    C2S_JobsActionPacket.MAX_ABSOLUTE_AMOUNT);
        } catch (NumberFormatException ignored) {
            amountBox.setValue("1");
            return 1;
        }
    }

    private void send(String action, String jobId, String target, int amount) {
        PacketDistributor.sendToServer(new C2S_JobsActionPacket(action, jobId, target, amount));
    }

    private S2C_OpenJobsScreenPacket.JobEntry selectedEntry() {
        if (packet.jobs().isEmpty()) return null;
        selectedJob = Mth.clamp(selectedJob, 0, packet.jobs().size() - 1);
        return packet.jobs().get(selectedJob);
    }

    private int activeCount() {
        return (int) packet.jobs().stream().filter(S2C_OpenJobsScreenPacket.JobEntry::active).count();
    }

    private int visibleRows(Layout layout) {
        return Math.max(1, (layout.height - 82) / 44);
    }

    private void clampScroll() {
        int rows = visibleRows(layout());
        scroll = Mth.clamp(scroll, 0, Math.max(0, packet.jobs().size() - rows));
        if (selectedJob < scroll) scroll = selectedJob;
        if (selectedJob >= scroll + rows) scroll = selectedJob - rows + 1;
    }

    private String fit(String text, int maxWidth) {
        return font.plainSubstrByWidth(text, Math.max(10, maxWidth));
    }

    private Layout layout() {
        int panelWidth = Math.min(PANEL_WIDTH, width - 16);
        int panelHeight = Math.min(PANEL_HEIGHT, height - 16);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int leftWidth = Math.min(250, Math.max(150, panelWidth * 43 / 100));
        return new Layout(left, top, panelWidth, panelHeight, leftWidth,
                left + leftWidth, panelWidth - leftWidth);
    }

    private static void drawButton(GuiGraphics graphics, int x, int y, int width, int height,
                                   String text, int accent, double mouseX, double mouseY, boolean enabled) {
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, width, height);
        graphics.fill(x, y, x + width, y + height, enabled ? (hovered ? CARD_HOVER : CARD) : 0xFF171B21);
        drawBorder(graphics, x, y, width, height, enabled ? accent : 0xFF303740);
        graphics.drawCenteredString(MinecraftAccess.font(), text, x + width / 2, y + (height - 8) / 2,
                enabled ? accent : 0xFF4C5662);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Layout(int left, int top, int width, int height, int leftWidth, int right, int rightWidth) {
    }

    /** Avoids passing a font parameter through every small draw helper. */
    private static final class MinecraftAccess {
        private static net.minecraft.client.gui.Font font() {
            return net.minecraft.client.Minecraft.getInstance().font;
        }
    }
}

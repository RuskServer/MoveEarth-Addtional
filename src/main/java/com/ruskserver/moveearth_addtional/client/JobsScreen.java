package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.network.C2S_JobsActionPacket;
import com.ruskserver.moveearth_addtional.network.C2S_JobShopActionPacket;
import com.ruskserver.moveearth_addtional.network.S2C_JobShopPacket;
import com.ruskserver.moveearth_addtional.network.S2C_OpenJobsScreenPacket;
import com.ruskserver.moveearth_addtional.network.S2C_JobsLeaderboardPacket;
import com.ruskserver.moveearth_addtional.jobs.JobXpFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private boolean rankingMode;
    private boolean shopMode;
    private boolean shopAdminMode;
    private final Map<String, List<S2C_JobsLeaderboardPacket.Entry>> leaderboards = new HashMap<>();
    private EditBox amountBox;
    private EditBox shopPriceBox;
    private EditBox shopLimitBox;
    private S2C_JobShopPacket shopPacket;
    private int selectedProduct;
    private int productScroll;
    private long resetArmedUntil;
    private long deleteProductArmedUntil;

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
        amountBox.visible = adminMode && !rankingMode && !shopMode && packet.canAdmin();
        addRenderableWidget(amountBox);

        shopPriceBox = new EditBox(font, layout.right + 12, layout.top + 151,
                Math.max(70, layout.rightWidth - 24), 20, Component.literal("商品価格"));
        shopPriceBox.setMaxLength(7);
        shopPriceBox.setValue("100");
        addRenderableWidget(shopPriceBox);
        shopLimitBox = new EditBox(font, layout.right + 12, layout.top + 191,
                Math.max(70, layout.rightWidth - 24), 20, Component.literal("購入上限"));
        shopLimitBox.setMaxLength(7);
        shopLimitBox.setValue("0");
        addRenderableWidget(shopLimitBox);
        updateBoxVisibility();
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
            amountBox.visible = adminMode && !rankingMode && !shopMode && updated.canAdmin();
        }
    }

    public void updateLeaderboard(S2C_JobsLeaderboardPacket updated) {
        leaderboards.put(updated.jobId().toString(), updated.entries());
    }

    public void updateShop(S2C_JobShopPacket updated) {
        String selectedId = selectedProductEntry() == null ? "" : selectedProductEntry().id().toString();
        this.shopPacket = updated;
        selectedProduct = 0;
        List<S2C_JobShopPacket.ProductEntry> products = visibleProducts();
        for (int i = 0; i < products.size(); i++) {
            if (products.get(i).id().toString().equals(selectedId)) {
                selectedProduct = i;
                break;
            }
        }
        clampProductScroll();
        if (shopAdminMode) loadSelectedProductFields();
        updateBoxVisibility();
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
        String subject = shopMode ? "ポイントショップ"
                : adminMode ? "管理対象: " + packet.subjectName() : packet.subjectName();
        graphics.drawString(font, subject, layout.left + 72, layout.top + 14,
                adminMode || shopMode ? 0xFFFFB454 : MUTED, false);
        int displayedPoints = shopMode && shopPacket != null ? shopPacket.points() : packet.points();
        String summary = shopMode ? displayedPoints + " PT"
                : displayedPoints + " PT  |  選択 " + activeCount() + "/" + packet.maxActiveJobs();
        graphics.drawString(font, summary, layout.left + layout.width - 190, layout.top + 14,
                0xFFFFB454, false);

        if (packet.canAdmin()) {
            drawButton(graphics, layout.left + layout.width - 84, layout.top + 34, 52, 18,
                    shopMode ? (shopAdminMode ? "購入" : "管理") : adminMode ? "自分" : "管理",
                    ACCENT, mouseX, mouseY, true);
        }
        drawButton(graphics, layout.left + layout.width - 228, layout.top + 34, 68, 18,
                shopMode ? "職業" : "ショップ", shopMode ? ACTIVE : 0xFFFFB454,
                mouseX, mouseY, true);
        drawButton(graphics, layout.left + layout.width - 154, layout.top + 34, 64, 18,
                rankingMode ? "進捗" : "ランキング", rankingMode ? ACTIVE : ACCENT,
                mouseX, mouseY, !shopMode);
        drawButton(graphics, layout.left + layout.width - 26, layout.top + 8, 18, 18,
                "×", DANGER, mouseX, mouseY, true);

        if (shopMode) {
            drawProductList(graphics, layout, mouseX, mouseY);
            drawShopDetails(graphics, layout, mouseX, mouseY);
        } else {
            drawJobList(graphics, layout, mouseX, mouseY);
            if (rankingMode) {
                drawLeaderboard(graphics, layout);
            } else {
                drawDetails(graphics, layout, mouseX, mouseY);
            }
            if (!rankingMode && adminMode && packet.canAdmin()) {
                drawAdminControls(graphics, layout, mouseX, mouseY);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        if (shopMode) renderShopTooltip(graphics, layout, mouseX, mouseY);
    }

    private void drawProductList(GuiGraphics graphics, Layout layout, int mouseX, int mouseY) {
        int x = layout.left + 12;
        int y = layout.top + 58;
        graphics.drawString(font, shopAdminMode ? "商品管理" : "商品一覧", x, y - 15, MUTED, false);
        if (shopPacket == null) {
            graphics.drawString(font, "読み込み中...", x + 8, y + 12, MUTED, false);
            return;
        }
        List<S2C_JobShopPacket.ProductEntry> products = visibleProducts();
        if (products.isEmpty()) {
            graphics.drawString(font, shopAdminMode ? "登録商品がありません" : "販売中の商品がありません",
                    x + 8, y + 12, MUTED, false);
            return;
        }
        int rows = visibleRows(layout);
        for (int row = 0; row < rows && productScroll + row < products.size(); row++) {
            int index = productScroll + row;
            S2C_JobShopPacket.ProductEntry product = products.get(index);
            int cardY = y + row * 44;
            boolean hovered = inside(mouseX, mouseY, x, cardY, layout.leftWidth - 24, 38);
            int background = index == selectedProduct ? 0xFF26394A : hovered ? CARD_HOVER : CARD;
            graphics.fill(x, cardY, x + layout.leftWidth - 24, cardY + 38, background);
            graphics.fill(x, cardY, x + 4, cardY + 38, product.enabled() ? ACTIVE : DANGER);
            drawBorder(graphics, x, cardY, layout.leftWidth - 24, 38,
                    index == selectedProduct ? ACCENT : 0xFF354150);
            graphics.renderItem(product.template(), x + 9, cardY + 11);
            graphics.drawString(font, fit(product.template().getHoverName().getString(), layout.leftWidth - 82),
                    x + 31, cardY + 7, product.enabled() ? TEXT : MUTED, false);
            graphics.drawString(font, product.price() + " PT  x" + product.template().getCount(),
                    x + 31, cardY + 21, 0xFFFFB454, false);
            if (!product.enabled()) {
                graphics.drawString(font, "停止中", x + layout.leftWidth - 64, cardY + 21, DANGER, false);
            }
        }
    }

    private void drawShopDetails(GuiGraphics graphics, Layout layout, int mouseX, int mouseY) {
        int x = layout.right + 12;
        int y = layout.top + 62;
        int areaWidth = layout.rightWidth - 24;
        S2C_JobShopPacket.ProductEntry product = selectedProductEntry();
        if (product != null) {
            graphics.renderItem(product.template(), x, y);
            graphics.drawString(font, fit(product.template().getHoverName().getString(), areaWidth - 28),
                    x + 26, y + 1, TEXT, false);
            graphics.drawString(font, "販売数: " + product.template().getCount(), x + 26, y + 14, MUTED, false);
            graphics.drawString(font, "価格: " + product.price() + " PT", x, y + 35, 0xFFFFB454, false);
            int remaining = product.remainingPurchases();
            String limit = remaining < 0 ? "購入上限: 無制限"
                    : "残り購入回数: " + remaining + " / " + product.purchaseLimit();
            graphics.drawString(font, limit, x, y + 50, remaining == 0 ? DANGER : MUTED, false);
        }

        if (shopAdminMode && packet.canAdmin()) {
            graphics.drawString(font, "価格（1～1000000 PT）", x, layout.top + 139, MUTED, false);
            graphics.drawString(font, "1人あたり購入上限（0で無制限）", x, layout.top + 179, MUTED, false);
            int buttonY = layout.top + 216;
            drawButton(graphics, x, buttonY, areaWidth, 20, "選択商品の設定を更新", ACCENT,
                    mouseX, mouseY, product != null);
            drawButton(graphics, x, buttonY + 24, areaWidth, 20,
                    product != null && product.enabled() ? "販売を停止" : "販売を再開", 0xFFFFB454,
                    mouseX, mouseY, product != null);
            boolean armed = System.currentTimeMillis() < deleteProductArmedUntil;
            drawButton(graphics, x, buttonY + 48, areaWidth, 20,
                    armed ? "もう一度押して商品を削除" : "選択商品を削除", DANGER,
                    mouseX, mouseY, product != null);
            drawButton(graphics, x, layout.top + layout.height - 34, areaWidth, 22,
                    "メインハンドの商品を追加", ACTIVE, mouseX, mouseY, true);
        } else if (product != null) {
            boolean enabled = product.enabled() && product.remainingPurchases() != 0
                    && shopPacket != null && shopPacket.points() >= product.price();
            drawButton(graphics, x, layout.top + layout.height - 48, areaWidth, 26,
                    enabled ? "購入する" : product.remainingPurchases() == 0 ? "購入上限に到達"
                    : shopPacket != null && shopPacket.points() < product.price() ? "ポイント不足" : "販売停止中",
                    ACTIVE, mouseX, mouseY, enabled);
        }
    }

    private void renderShopTooltip(GuiGraphics graphics, Layout layout, int mouseX, int mouseY) {
        if (shopPacket == null) return;
        int x = layout.left + 12;
        int y = layout.top + 58;
        List<S2C_JobShopPacket.ProductEntry> products = visibleProducts();
        for (int row = 0; row < visibleRows(layout) && productScroll + row < products.size(); row++) {
            if (inside(mouseX, mouseY, x + 6, y + row * 44 + 7, 24, 24)) {
                graphics.renderTooltip(font, products.get(productScroll + row).template(), mouseX, mouseY);
                return;
            }
        }
    }

    private void drawLeaderboard(GuiGraphics graphics, Layout layout) {
        S2C_OpenJobsScreenPacket.JobEntry job = selectedEntry();
        if (job == null) return;
        int x = layout.right + 12;
        int y = layout.top + 62;
        int availableWidth = layout.rightWidth - 24;
        graphics.drawString(font, job.displayName() + " ランキング", x, y, TEXT, false);
        graphics.drawString(font, "順位", x + 4, y + 19, MUTED, false);
        graphics.drawString(font, "プレイヤー", x + 27, y + 19, MUTED, false);
        graphics.drawString(font, "Lv", x + availableWidth - 137, y + 19, MUTED, false);
        graphics.drawString(font, "現在XP", x + availableWidth - 108, y + 19, MUTED, false);
        graphics.drawString(font, "累計XP", x + availableWidth - 55, y + 19, MUTED, false);
        List<S2C_JobsLeaderboardPacket.Entry> entries = leaderboards.get(job.id().toString());
        if (entries == null) {
            graphics.drawCenteredString(font, "読み込み中...", x + availableWidth / 2, y + 64, MUTED);
            return;
        }
        if (entries.isEmpty()) {
            graphics.drawCenteredString(font, "まだランキング対象者がいません",
                    x + availableWidth / 2, y + 64, MUTED);
            return;
        }
        int maxRows = Math.min(entries.size(), 10);
        for (int i = 0; i < maxRows; i++) {
            S2C_JobsLeaderboardPacket.Entry entry = entries.get(i);
            int rowY = y + 37 + i * 21;
            int color = i == 0 ? 0xFFFFD76A : i == 1 ? 0xFFDDE5EC : i == 2 ? 0xFFD89A67 : TEXT;
            if ((i & 1) == 0) {
                graphics.fill(x, rowY - 4, x + availableWidth, rowY + 13, 0x551B222C);
            }
            graphics.drawString(font, Integer.toString(i + 1), x + 4, rowY, color, false);
            graphics.drawString(font, fit(entry.playerName(), Math.max(50, availableWidth - 174)),
                    x + 27, rowY, color, false);
            graphics.drawString(font, Integer.toString(entry.level()), x + availableWidth - 137, rowY,
                    ACCENT, false);
            String currentXp = JobXpFormat.format(entry.xpInLevel());
            String totalXp = JobXpFormat.format(entry.totalXp());
            graphics.drawString(font, fit(currentXp, 47), x + availableWidth - 108, rowY, MUTED, false);
            graphics.drawString(font, fit(totalXp, 52), x + availableWidth - 55, rowY, MUTED, false);
        }
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
                    + (job.xpForNextLevel() <= 0 ? "MAX" : JobXpFormat.format(job.xpInLevel()) + "/"
                    + JobXpFormat.format(job.xpForNextLevel()) + " XP");
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
                : JobXpFormat.format(job.xpInLevel()) + " / "
                + JobXpFormat.format(job.xpForNextLevel()) + " XP";
        graphics.drawString(font, xp, x, barY + 14, TEXT, false);
        graphics.drawString(font, "累計XP: " + JobXpFormat.format(job.totalXp()), x, barY + 29, MUTED, false);
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
            if (shopMode) {
                shopAdminMode = !shopAdminMode;
                deleteProductArmedUntil = 0;
                clampProductScroll();
                if (shopAdminMode) loadSelectedProductFields();
                updateBoxVisibility();
                return true;
            }
            adminMode = !adminMode;
            rankingMode = false;
            resetArmedUntil = 0;
            if (!adminMode) send("REFRESH", "", "", 0);
            else send("VIEW", "", packet.subjectName(), 0);
            if (amountBox != null) amountBox.visible = adminMode;
            return true;
        }
        if (inside(mouseX, mouseY, layout.left + layout.width - 228, layout.top + 34, 68, 18)) {
            shopMode = !shopMode;
            shopAdminMode = false;
            rankingMode = false;
            adminMode = false;
            resetArmedUntil = 0;
            deleteProductArmedUntil = 0;
            if (shopMode) sendShop("REQUEST", null, 0, 0);
            else send("REFRESH", "", "", 0);
            updateBoxVisibility();
            return true;
        }
        if (inside(mouseX, mouseY, layout.left + layout.width - 154, layout.top + 34, 64, 18)) {
            if (shopMode) return true;
            rankingMode = !rankingMode;
            adminMode = false;
            resetArmedUntil = 0;
            if (amountBox != null) amountBox.visible = false;
            if (rankingMode) requestSelectedLeaderboard();
            return true;
        }

        if (shopMode) {
            return handleShopClick(mouseX, mouseY, layout) || super.mouseClicked(mouseX, mouseY, button);
        }

        int listX = layout.left + 12;
        int listY = layout.top + 58;
        for (int row = 0; row < visibleRows(layout) && scroll + row < packet.jobs().size(); row++) {
            if (inside(mouseX, mouseY, listX, listY + row * 44, layout.leftWidth - 24, 38)) {
                selectedJob = scroll + row;
                resetArmedUntil = 0;
                if (rankingMode) requestSelectedLeaderboard();
                return true;
            }
        }

        S2C_OpenJobsScreenPacket.JobEntry job = selectedEntry();
        int rightX = layout.right + 12;
        int rightWidth = layout.rightWidth - 24;
        if (!adminMode && !rankingMode && job != null && inside(mouseX, mouseY, rightX,
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

    private boolean handleShopClick(double mouseX, double mouseY, Layout layout) {
        int listX = layout.left + 12;
        int listY = layout.top + 58;
        if (shopPacket != null) {
            List<S2C_JobShopPacket.ProductEntry> products = visibleProducts();
            for (int row = 0; row < visibleRows(layout)
                    && productScroll + row < products.size(); row++) {
                if (inside(mouseX, mouseY, listX, listY + row * 44, layout.leftWidth - 24, 38)) {
                    selectedProduct = productScroll + row;
                    deleteProductArmedUntil = 0;
                    if (shopAdminMode) loadSelectedProductFields();
                    return true;
                }
            }
        }

        int x = layout.right + 12;
        int areaWidth = layout.rightWidth - 24;
        S2C_JobShopPacket.ProductEntry product = selectedProductEntry();
        if (shopAdminMode && packet.canAdmin()) {
            int buttonY = layout.top + 216;
            if (product != null && inside(mouseX, mouseY, x, buttonY, areaWidth, 20)) {
                sendShop("UPDATE", product, shopPrice(), shopLimit());
                return true;
            }
            if (product != null && inside(mouseX, mouseY, x, buttonY + 24, areaWidth, 20)) {
                sendShop("TOGGLE", product, 0, 0);
                return true;
            }
            if (product != null && inside(mouseX, mouseY, x, buttonY + 48, areaWidth, 20)) {
                long now = System.currentTimeMillis();
                if (now < deleteProductArmedUntil) {
                    deleteProductArmedUntil = 0;
                    sendShop("REMOVE", product, 0, 0);
                } else {
                    deleteProductArmedUntil = now + 3_000L;
                }
                return true;
            }
            if (inside(mouseX, mouseY, x, layout.top + layout.height - 34, areaWidth, 22)) {
                sendShop("ADD_HELD", null, shopPrice(), shopLimit());
                return true;
            }
        } else if (product != null && inside(mouseX, mouseY, x,
                layout.top + layout.height - 48, areaWidth, 26)) {
            if (product.enabled() && product.remainingPurchases() != 0
                    && shopPacket != null && shopPacket.points() >= product.price()) {
                sendShop("BUY", product, 0, 0);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Layout layout = layout();
        if (inside(mouseX, mouseY, layout.left + 8, layout.top + 52, layout.leftWidth - 8, layout.height - 64)) {
            if (shopMode) {
                productScroll -= (int) Math.signum(scrollY);
                clampProductScroll();
            } else {
                scroll -= (int) Math.signum(scrollY);
                clampScroll();
            }
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

    private void sendShop(String action, S2C_JobShopPacket.ProductEntry product, int price, int limit) {
        PacketDistributor.sendToServer(new C2S_JobShopActionPacket(action,
                product == null ? "" : product.id().toString(), price, limit));
    }

    private int shopPrice() {
        return parseShopBox(shopPriceBox, 1, 1_000_000, 100);
    }

    private int shopLimit() {
        return parseShopBox(shopLimitBox, 0, 1_000_000, 0);
    }

    private int parseShopBox(EditBox box, int minimum, int maximum, int fallback) {
        if (box == null) return fallback;
        try {
            int parsed = Mth.clamp(Integer.parseInt(box.getValue()), minimum, maximum);
            box.setValue(Integer.toString(parsed));
            return parsed;
        } catch (NumberFormatException ignored) {
            box.setValue(Integer.toString(fallback));
            return fallback;
        }
    }

    private void loadSelectedProductFields() {
        S2C_JobShopPacket.ProductEntry product = selectedProductEntry();
        if (product == null || shopPriceBox == null || shopLimitBox == null) return;
        shopPriceBox.setValue(Integer.toString(product.price()));
        shopLimitBox.setValue(Integer.toString(product.purchaseLimit()));
    }

    private void updateBoxVisibility() {
        if (amountBox != null) {
            amountBox.visible = adminMode && !rankingMode && !shopMode && packet.canAdmin();
        }
        boolean shopFields = shopMode && shopAdminMode && packet.canAdmin();
        if (shopPriceBox != null) shopPriceBox.visible = shopFields;
        if (shopLimitBox != null) shopLimitBox.visible = shopFields;
    }

    private void requestSelectedLeaderboard() {
        S2C_OpenJobsScreenPacket.JobEntry job = selectedEntry();
        if (job != null) {
            leaderboards.remove(job.id().toString());
            send("RANKING", job.id().toString(), "", 0);
        }
    }

    private S2C_OpenJobsScreenPacket.JobEntry selectedEntry() {
        if (packet.jobs().isEmpty()) return null;
        selectedJob = Mth.clamp(selectedJob, 0, packet.jobs().size() - 1);
        return packet.jobs().get(selectedJob);
    }

    private S2C_JobShopPacket.ProductEntry selectedProductEntry() {
        List<S2C_JobShopPacket.ProductEntry> products = visibleProducts();
        if (products.isEmpty()) return null;
        selectedProduct = Mth.clamp(selectedProduct, 0, products.size() - 1);
        return products.get(selectedProduct);
    }

    private List<S2C_JobShopPacket.ProductEntry> visibleProducts() {
        if (shopPacket == null) return List.of();
        if (shopAdminMode) return shopPacket.products();
        return shopPacket.products().stream().filter(S2C_JobShopPacket.ProductEntry::enabled).toList();
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

    private void clampProductScroll() {
        int size = visibleProducts().size();
        int rows = visibleRows(layout());
        productScroll = Mth.clamp(productScroll, 0, Math.max(0, size - rows));
        selectedProduct = Mth.clamp(selectedProduct, 0, Math.max(0, size - 1));
        if (selectedProduct < productScroll) productScroll = selectedProduct;
        if (selectedProduct >= productScroll + rows) productScroll = selectedProduct - rows + 1;
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

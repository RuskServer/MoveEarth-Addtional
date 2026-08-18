package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.network.C2S_UpdateWhitelistPacket;
import com.ruskserver.moveearth_addtional.network.C2S_ConfigurePaymentPacket;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.BankReference;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Collections;

@OnlyIn(Dist.CLIENT)
public class PlayerDetectorScreen extends Screen {

    private final String ownerName;
    private List<String> whitelist;
    private List<String> onlinePlayers;
    private EditBox nameInput;

    private int activeTab = 0; // 0: ホワイトリスト設定, 1: 維持費決済設定

    private int whitelistPage = 0;
    private int onlinePage = 0;
    private static final int ITEMS_PER_PAGE = 5;

    // 決済データ
    private BlockPos blockPos = null;
    private boolean isPaymentActive = false;
    private long nextPaymentTime = 0L;
    private long placedTime = 0L;
    private BankReference currentReference = null;
    private List<BankReference> availableAccounts = Collections.emptyList();
    private List<String> availableAccountNames = Collections.emptyList();
    private int selectedAccountIndex = -1;

    public PlayerDetectorScreen(Component title, String ownerName, List<String> whitelist, List<String> onlinePlayers) {
        super(title);
        this.ownerName = ownerName;
        this.whitelist = whitelist;
        this.onlinePlayers = onlinePlayers;
    }

    public void updateData(List<String> whitelist, List<String> onlinePlayers) {
        this.whitelist = whitelist;
        this.onlinePlayers = onlinePlayers;
        int maxWhitelistPage = Math.max(0, (this.whitelist.size() - 1) / ITEMS_PER_PAGE);
        if (this.whitelistPage > maxWhitelistPage) {
            this.whitelistPage = maxWhitelistPage;
        }
        int maxOnlinePage = Math.max(0, (this.onlinePlayers.size() - 1) / ITEMS_PER_PAGE);
        if (this.onlinePage > maxOnlinePage) {
            this.onlinePage = maxOnlinePage;
        }
        rebuildWidgets();
    }

    public void updatePaymentData(BlockPos pos, boolean isActive, long nextPaymentTime, long placedTime,
                                  BankReference currentReference, List<BankReference> availableAccounts,
                                  List<String> availableAccountNames) {
        this.blockPos = pos;
        this.isPaymentActive = isActive;
        this.nextPaymentTime = nextPaymentTime;
        this.placedTime = placedTime;
        this.currentReference = currentReference;
        this.availableAccounts = availableAccounts;
        this.availableAccountNames = availableAccountNames;

        this.selectedAccountIndex = -1;
        if (currentReference != null) {
            for (int i = 0; i < this.availableAccounts.size(); i++) {
                if (this.availableAccounts.get(i).equals(currentReference)) {
                    this.selectedAccountIndex = i;
                    break;
                }
            }
        }
        rebuildWidgets();
    }

    @Override
    protected void init() {
        super.init();

        int windowWidth = 400;
        int windowHeight = 230;
        int leftPos = (this.width - windowWidth) / 2;
        int topPos = (this.height - windowHeight) / 2;

        // 上部 タブ切り替えボタン
        Button whitelistTabBtn = Button.builder(Component.literal(this.activeTab == 0 ? "▶ ホワイトリスト ◀" : "ホワイトリスト"), b -> {
            this.activeTab = 0;
            rebuildWidgets();
        }).bounds(leftPos + 10, topPos + 25, 185, 20).build();

        Button paymentTabBtn = Button.builder(Component.literal(this.activeTab == 1 ? "▶ 維持費・決済 ◀" : "維持費・決済"), b -> {
            this.activeTab = 1;
            rebuildWidgets();
        }).bounds(leftPos + 205, topPos + 25, 185, 20).build();

        addRenderableWidget(whitelistTabBtn);
        addRenderableWidget(paymentTabBtn);

        if (this.activeTab == 0) {
            // --- タブ 0: ホワイトリスト設定 ---
            this.nameInput = new EditBox(this.font, leftPos + 10, topPos + 50, 120, 20, Component.literal("プレイヤー名"));
            this.nameInput.setMaxLength(16);
            addRenderableWidget(this.nameInput);

            addRenderableWidget(Button.builder(Component.literal("追加"), b -> {
                String name = this.nameInput.getValue().trim();
                if (!name.isEmpty()) {
                    PacketDistributor.sendToServer(new C2S_UpdateWhitelistPacket(name, true));
                    this.nameInput.setValue("");
                }
            }).bounds(leftPos + 135, topPos + 50, 55, 20).build());

            int listStartY = topPos + 75;

            // 左カラム: ホワイトリスト一覧
            int whitelistStartIdx = this.whitelistPage * ITEMS_PER_PAGE;
            int whitelistEndIdx = Math.min(whitelistStartIdx + ITEMS_PER_PAGE, this.whitelist.size());
            for (int i = whitelistStartIdx; i < whitelistEndIdx; i++) {
                String name = this.whitelist.get(i);
                int btnY = listStartY + (i - whitelistStartIdx) * 22;
                addRenderableWidget(Button.builder(Component.literal("除籍"), b -> {
                    PacketDistributor.sendToServer(new C2S_UpdateWhitelistPacket(name, false));
                }).bounds(leftPos + 135, btnY, 55, 20).build());
            }

            Button prevWhitelistBtn = Button.builder(Component.literal("<"), b -> {
                if (this.whitelistPage > 0) {
                    this.whitelistPage--;
                    rebuildWidgets();
                }
            }).bounds(leftPos + 10, listStartY + 5 * 22, 20, 20).build();
            prevWhitelistBtn.active = this.whitelistPage > 0;
            addRenderableWidget(prevWhitelistBtn);

            Button nextWhitelistBtn = Button.builder(Component.literal(">"), b -> {
                if ((this.whitelistPage + 1) * ITEMS_PER_PAGE < this.whitelist.size()) {
                    this.whitelistPage++;
                    rebuildWidgets();
                }
            }).bounds(leftPos + 170, listStartY + 5 * 22, 20, 20).build();
            nextWhitelistBtn.active = (this.whitelistPage + 1) * ITEMS_PER_PAGE < this.whitelist.size();
            addRenderableWidget(nextWhitelistBtn);

            // 右カラム: オンラインプレイヤー
            int onlineStartIdx = this.onlinePage * ITEMS_PER_PAGE;
            int onlineEndIdx = Math.min(onlineStartIdx + ITEMS_PER_PAGE, this.onlinePlayers.size());
            for (int i = onlineStartIdx; i < onlineEndIdx; i++) {
                String name = this.onlinePlayers.get(i);
                int btnY = listStartY + (i - onlineStartIdx) * 22;
                boolean alreadyIn = this.whitelist.contains(name);

                Button addBtn = Button.builder(alreadyIn ? Component.literal("登録済") : Component.literal("追加"), b -> {
                    if (!alreadyIn) {
                        PacketDistributor.sendToServer(new C2S_UpdateWhitelistPacket(name, true));
                    }
                }).bounds(leftPos + 335, btnY, 55, 20).build();
                addBtn.active = !alreadyIn;
                addRenderableWidget(addBtn);
            }

            Button prevOnlineBtn = Button.builder(Component.literal("<"), b -> {
                if (this.onlinePage > 0) {
                    this.onlinePage--;
                    rebuildWidgets();
                }
            }).bounds(leftPos + 210, listStartY + 5 * 22, 20, 20).build();
            prevOnlineBtn.active = this.onlinePage > 0;
            addRenderableWidget(prevOnlineBtn);

            Button nextOnlineBtn = Button.builder(Component.literal(">"), b -> {
                if ((this.onlinePage + 1) * ITEMS_PER_PAGE < this.onlinePlayers.size()) {
                    this.onlinePage++;
                    rebuildWidgets();
                }
            }).bounds(leftPos + 370, listStartY + 5 * 22, 20, 20).build();
            nextOnlineBtn.active = (this.onlinePage + 1) * ITEMS_PER_PAGE < this.onlinePlayers.size();
            addRenderableWidget(nextOnlineBtn);

            this.nameInput.setFocused(false);
        } else {
            // --- タブ 1: 維持費決済設定 ---
            int payY = topPos + 115;

            Button prevAccountBtn = Button.builder(Component.literal("◀"), b -> {
                if (!this.availableAccounts.isEmpty()) {
                    this.selectedAccountIndex--;
                    if (this.selectedAccountIndex < -1) {
                        this.selectedAccountIndex = this.availableAccounts.size() - 1;
                    }
                    rebuildWidgets();
                }
            }).bounds(leftPos + 40, payY, 25, 20).build();

            Button nextAccountBtn = Button.builder(Component.literal("▶"), b -> {
                if (!this.availableAccounts.isEmpty()) {
                    this.selectedAccountIndex++;
                    if (this.selectedAccountIndex >= this.availableAccounts.size()) {
                        this.selectedAccountIndex = -1;
                    }
                    rebuildWidgets();
                }
            }).bounds(leftPos + 335, payY, 25, 20).build();

            prevAccountBtn.active = (blockPos != null) && !this.availableAccounts.isEmpty();
            nextAccountBtn.active = (blockPos != null) && !this.availableAccounts.isEmpty();
            addRenderableWidget(prevAccountBtn);
            addRenderableWidget(nextAccountBtn);

            // 口座保存ボタン
            Button saveAccountBtn = Button.builder(Component.literal("口座保存"), b -> {
                BankReference newRef = null;
                if (this.selectedAccountIndex >= 0 && this.selectedAccountIndex < this.availableAccounts.size()) {
                    newRef = this.availableAccounts.get(this.selectedAccountIndex);
                }
                PacketDistributor.sendToServer(new C2S_ConfigurePaymentPacket(this.blockPos, false, newRef));
            }).bounds(leftPos + 90, topPos + 160, 100, 20).build();
            saveAccountBtn.active = (blockPos != null);
            addRenderableWidget(saveAccountBtn);

            // 有効化・支払うボタン
            Button activateBtn = Button.builder(Component.literal("支払 / 有効化"), b -> {
                BankReference newRef = null;
                if (this.selectedAccountIndex >= 0 && this.selectedAccountIndex < this.availableAccounts.size()) {
                    newRef = this.availableAccounts.get(this.selectedAccountIndex);
                }
                PacketDistributor.sendToServer(new C2S_ConfigurePaymentPacket(this.blockPos, true, newRef));
            }).bounds(leftPos + 210, topPos + 160, 100, 20).build();
            
            boolean isExpiredOrInactive = !this.isPaymentActive || (System.currentTimeMillis() >= this.nextPaymentTime);
            activateBtn.active = (blockPos != null) && (selectedAccountIndex >= 0) && isExpiredOrInactive;
            addRenderableWidget(activateBtn);
        }

        // 閉じるボタン
        addRenderableWidget(Button.builder(Component.literal("閉じる"), b -> this.onClose())
                .bounds(leftPos + 160, topPos + 200, 80, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x70000000);

        int windowWidth = 400;
        int windowHeight = 230;
        int leftPos = (this.width - windowWidth) / 2;
        int topPos = (this.height - windowHeight) / 2;

        // メインウィンドウ背景
        guiGraphics.fill(leftPos, topPos, leftPos + windowWidth, topPos + windowHeight, 0xDD111111);

        // 枠線
        guiGraphics.fill(leftPos, topPos, leftPos + windowWidth, topPos + 1, 0xFF444444);
        guiGraphics.fill(leftPos, topPos + windowHeight - 1, leftPos + windowWidth, topPos + windowHeight, 0xFF444444);
        guiGraphics.fill(leftPos, topPos, leftPos + 1, topPos + windowHeight, 0xFF444444);
        guiGraphics.fill(leftPos + windowWidth - 1, topPos, leftPos + windowWidth, topPos + windowHeight, 0xFF444444);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // タイトル
        guiGraphics.drawCenteredString(this.font, "プレイヤー検知ブロック設定 (所有者: " + this.ownerName + ")", leftPos + 200, topPos + 8, 0xFFFFFF);

        if (this.activeTab == 0) {
            // --- タブ 0: ホワイトリスト設定 ---
            guiGraphics.fill(leftPos + 200, topPos + 50, leftPos + 201, topPos + 190, 0xFF333333);

            // カラム名
            guiGraphics.drawString(this.font, "ホワイトリスト", leftPos + 10, topPos + 75, 0xFFBB00);
            guiGraphics.drawString(this.font, "オンラインプレイヤー", leftPos + 210, topPos + 75, 0xFFBB00);

            int listStartY = topPos + 75;

            // 左カラム: ホワイトリストプレイヤー
            int whitelistStartIdx = this.whitelistPage * ITEMS_PER_PAGE;
            int whitelistEndIdx = Math.min(whitelistStartIdx + ITEMS_PER_PAGE, this.whitelist.size());
            for (int i = whitelistStartIdx; i < whitelistEndIdx; i++) {
                String name = this.whitelist.get(i);
                int textY = listStartY + (i - whitelistStartIdx) * 22 + 6;
                guiGraphics.drawString(this.font, name, leftPos + 10, textY, 0xFFFFFF);
            }

            int maxWhitelistPage = Math.max(1, (this.whitelist.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
            String whitelistPageStr = (this.whitelistPage + 1) + "/" + maxWhitelistPage;
            guiGraphics.drawCenteredString(this.font, whitelistPageStr, leftPos + 100, listStartY + 5 * 22 + 6, 0xAAAAAA);

            // 右カラム: オンラインプレイヤー
            int onlineStartIdx = this.onlinePage * ITEMS_PER_PAGE;
            int onlineEndIdx = Math.min(onlineStartIdx + ITEMS_PER_PAGE, this.onlinePlayers.size());
            for (int i = onlineStartIdx; i < onlineEndIdx; i++) {
                String name = this.onlinePlayers.get(i);
                int textY = listStartY + (i - onlineStartIdx) * 22 + 6;
                guiGraphics.drawString(this.font, name, leftPos + 210, textY, 0xFFFFFF);
            }

            int maxOnlinePage = Math.max(1, (this.onlinePlayers.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
            String onlinePageStr = (this.onlinePage + 1) + "/" + maxOnlinePage;
            guiGraphics.drawCenteredString(this.font, onlinePageStr, leftPos + 300, listStartY + 5 * 22 + 6, 0xAAAAAA);
        } else {
            // --- タブ 1: 維持費決済設定 ---
            // カード風の枠
            guiGraphics.fill(leftPos + 20, topPos + 55, leftPos + 380, topPos + 190, 0xFF222222);
            guiGraphics.fill(leftPos + 20, topPos + 55, leftPos + 380, topPos + 56, 0xFF555555);

            // コスト説明
            guiGraphics.drawCenteredString(this.font, "維持費用: 2時間ごとに 5 ゴールド", leftPos + 200, topPos + 65, 0xFFBB00);

            // ステータス表示
            String statusText;
            int statusColor;
            long currentTime = System.currentTimeMillis();
            long warmUpRemaining = (this.placedTime + 20 * 60 * 1000L) - currentTime;

            if (this.isPaymentActive && warmUpRemaining > 0) {
                long minutes = (warmUpRemaining / (1000 * 60)) % 60;
                long seconds = (warmUpRemaining / 1000) % 60;
                statusText = String.format("準備中 (残り %02d:%02d)", minutes, seconds);
                statusColor = 0xFFFF55;
            } else if (this.isPaymentActive) {
                long remaining = this.nextPaymentTime - currentTime;
                if (remaining <= 0) {
                    statusText = "停止中 (期限切れ)";
                    statusColor = 0xFF5555;
                } else {
                    long hours = (remaining / (1000 * 60 * 60)) % 24;
                    long minutes = (remaining / (1000 * 60)) % 60;
                    long seconds = (remaining / 1000) % 60;
                    statusText = String.format("稼働中 (%02d:%02d:%02d)", hours, minutes, seconds);
                    statusColor = 0x55FF55;
                }
            } else {
                statusText = "停止中 (維持費未払)";
                statusColor = 0xFF5555;
            }

            guiGraphics.drawCenteredString(this.font, "稼働状態: " + statusText, leftPos + 200, topPos + 85, statusColor);

            // 口座選択のラベル
            guiGraphics.drawCenteredString(this.font, "引き落とし口座の選択", leftPos + 200, topPos + 105, 0xAAAAAA);

            // 口座名
            String accountName;
            if (this.selectedAccountIndex == -1) {
                accountName = "§7未設定 (稼働停止)";
            } else if (this.selectedAccountIndex < this.availableAccountNames.size()) {
                accountName = this.availableAccountNames.get(this.selectedAccountIndex);
            } else {
                accountName = "§c口座名の同期に失敗";
            }
            guiGraphics.drawCenteredString(this.font, accountName, leftPos + 200, topPos + 121, 0xFFFFFF);
        }
    }
}

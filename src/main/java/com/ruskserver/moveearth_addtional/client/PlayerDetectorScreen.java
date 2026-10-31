package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.block.entity.PlayerDetectorBlockEntity;
import com.ruskserver.moveearth_addtional.client.ui.MoveEarthTextField;
import com.ruskserver.moveearth_addtional.client.ui.ScreenFit;
import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.detector.DetectorNamePolicy;
import com.ruskserver.moveearth_addtional.network.c2s.other.C2S_ConfigurePaymentPacket;
import com.ruskserver.moveearth_addtional.network.c2s.other.C2S_SetDetectorNamePacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_DetectorStatePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

/**
 * The player detector's screen: who owns it and hears its alerts, whether it is running and what pays for it,
 * and its name. Follows the S2 Hub / PvP visual and interaction design ({@link com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi}).
 */
@OnlyIn(Dist.CLIENT)
public class PlayerDetectorScreen extends Screen implements SuppressesChatOverlay {
    private static final int TOAST_DURATION_TICKS = 100;

    private S2C_DetectorStatePacket state;
    private MoveEarthTextField nameInput;
    private String status = "";
    private int statusColor = MUTED;
    private int toastTicks = 0;

    public PlayerDetectorScreen(S2C_DetectorStatePacket state) {
        super(Component.literal("プレイヤー検知ブロック"));
        this.state = state;
        if (!state.message().isEmpty()) {
            this.status = state.message();
            this.statusColor = state.success() ? SUCCESS : DANGER;
            this.toastTicks = TOAST_DURATION_TICKS;
        }
    }

    /** A refresh from the server after renaming or paying, with the outcome to show. */
    public void update(S2C_DetectorStatePacket next) {
        if (!next.pos().equals(this.state.pos())) return;
        // A refresh after paying rebuilds the widgets; a name still being typed survives it.
        String typing = this.nameInput != null && !this.nameInput.getValue().equals(this.state.name())
                && next.name().equals(this.state.name()) ? this.nameInput.getValue() : null;
        this.state = next;
        if (!next.message().isEmpty()) {
            this.status = next.message();
            this.statusColor = next.success() ? SUCCESS : DANGER;
            this.toastTicks = TOAST_DURATION_TICKS;
        }
        rebuildWidgets();
        if (typing != null && this.nameInput != null) this.nameInput.setValue(typing);
    }

    @Override
    protected void init() {
        ScreenFit.fit(this, PlayerDetectorScreenLayout.FIT_WIDTH, PlayerDetectorScreenLayout.FIT_HEIGHT);
        super.init();
        PlayerDetectorScreenLayout.Layout layout = PlayerDetectorScreenLayout.calculate(this.width, this.height);

        if (this.state.canManage()) {
            this.nameInput = new MoveEarthTextField(
                    this.font,
                    layout.nameInput(),
                    Component.literal("検知ブロック名称")
            ).focusBorder(GOLD);
            this.nameInput.setMaxLength(DetectorNamePolicy.MAX_LENGTH);
            this.nameInput.setValue(this.state.name());
            this.nameInput.setHint(Component.literal("名称未設定"));
            addRenderableWidget(this.nameInput);
        } else {
            this.nameInput = null;
        }
    }

    private void saveName(String value) {
        DetectorNamePolicy.Validation validation = DetectorNamePolicy.validate(value);
        if (!validation.valid()) {
            this.status = validation.errorMessage();
            this.statusColor = DANGER;
            this.toastTicks = TOAST_DURATION_TICKS;
            return;
        }
        this.status = "保存しています…";
        this.statusColor = GOLD;
        this.toastTicks = 40;
        PacketDistributor.sendToServer(new C2S_SetDetectorNamePacket(this.state.pos(), validation.normalized()));
    }

    private boolean canPayToRestart(long now) {
        return this.state.canManage()
                && PlayerDetectorScreenLayout.stopped(this.state.active(), this.state.nextPaymentTime(), now);
    }

    private boolean canSaveName() {
        return this.state.canManage() && this.nameInput != null;
    }

    private boolean canResetName() {
        if (!this.state.canManage()) return false;
        String currentInput = this.nameInput != null ? this.nameInput.getValue() : "";
        return !this.state.name().isEmpty() || !currentInput.isEmpty();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The screen draws its own translucent backdrop, so operators can keep the surroundings visible.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(graphics, this.width, this.height);
        PlayerDetectorScreenLayout.Layout layout = PlayerDetectorScreenLayout.calculate(this.width, this.height);
        Rect panel = layout.panel();
        drawPanel(graphics, panel);

        // Header: S2 Hub / PvP standard left-aligned title + subtitle + top-right close button.
        String displayName = this.state.name().isBlank() ? "名称未設定" : this.state.name();
        BlockPos pos = this.state.pos();
        // Up to 32 characters of name: cut short of the close button rather than running under it.
        graphics.drawString(this.font, this.font.plainSubstrByWidth("プレイヤー検知ブロック「" + displayName + "」",
                layout.close().x() - panel.x() - 24), panel.x() + 16, panel.y() + 10, TEXT, false);
        String subtitle = String.format("座標 %d, %d, %d  •  検知半径 %.0fm",
                pos.getX(), pos.getY(), pos.getZ(), PlayerDetectorBlockEntity.DETECTION_RANGE);
        graphics.drawString(this.font, subtitle, panel.x() + 16, panel.y() + 22, MUTED, false);
        drawClose(graphics, this.font, layout.close(), layout.close().contains(mouseX, mouseY));

        // 1. Owner and who hears the alerts.
        Rect ownerCard = layout.ownerCard();
        drawCard(graphics, ownerCard, ACCENT, true, false);
        String ownerBadge = this.state.nationOwned() ? "国家所有" : "個人所有";
        graphics.drawString(this.font, ownerBadge, ownerCard.x() + 12, ownerCard.y() + 6, ACCENT, false);
        int ownerNameX = ownerCard.x() + 12 + this.font.width(ownerBadge) + 10;
        graphics.drawString(this.font, this.state.owner(), ownerNameX, ownerCard.y() + 6, TEXT, false);
        drawWrapped(
                graphics,
                this.font,
                Component.literal(this.state.nationOwned()
                        ? "侵入警告はオンラインの国民全員に届きます。同盟国のメンバーは訪問者として扱い、警告しません。"
                        : "侵入警告は所有者だけに届きます。国家に加入すると、この検知ブロックは国家の所有に移行します。"),
                ownerCard.x() + 12,
                ownerCard.y() + 19,
                ownerCard.width() - 24,
                MUTED
        );

        // 2. Running state, upkeep, balance, and payment control.
        long now = System.currentTimeMillis();
        int stateColor = PlayerDetectorScreenLayout.stateColor(
                this.state.active(), this.state.placedTime(), this.state.nextPaymentTime(), now);
        Rect upkeepCard = layout.upkeepCard();
        drawCard(graphics, upkeepCard, stateColor, true, false);
        graphics.drawString(
                this.font,
                "稼働状態: " + PlayerDetectorScreenLayout.stateText(
                        this.state.active(), this.state.placedTime(), this.state.nextPaymentTime(), now),
                upkeepCard.x() + 12,
                upkeepCard.y() + 7,
                stateColor,
                false
        );
        graphics.drawString(
                this.font,
                PlayerDetectorScreenLayout.upkeepSummaryText(this.state.upkeepCost(), this.state.upkeepCycleHours()),
                upkeepCard.x() + 12,
                upkeepCard.y() + 21,
                MUTED,
                false
        );
        graphics.drawString(
                this.font,
                (this.state.nationOwned() ? "国庫残高: " : "口座残高: ")
                        + PlayerDetectorScreenLayout.money(this.state.balance()),
                upkeepCard.x() + 12,
                upkeepCard.y() + 34,
                TEXT,
                false
        );

        // Progress bar for warmup or remaining paid cycle.
        Rect bar = layout.progressBar();
        graphics.fill(bar.x(), bar.y(), bar.right(), bar.bottom(), BAR_BACKGROUND);
        float ratio = PlayerDetectorScreenLayout.progressRatio(
                this.state.active(), this.state.placedTime(), this.state.nextPaymentTime(), now,
                this.state.upkeepCycleHours() * 3_600_000L);
        int fillWidth = Math.round(bar.width() * ratio);
        if (fillWidth > 0) {
            graphics.fill(bar.x(), bar.y(), bar.x() + fillWidth, bar.bottom(), stateColor);
        }
        drawBorder(graphics, bar, BORDER);

        if (!this.state.canManage()) {
            graphics.drawString(
                    this.font,
                    "※ 再開と名称変更には拠点操作または国庫管理の権限が必要です",
                    upkeepCard.x() + 12,
                    upkeepCard.y() + 56,
                    MUTED,
                    false
            );
        }

        Rect payRect = layout.payButton();
        boolean payEnabled = canPayToRestart(now);
        String payLabel = PlayerDetectorScreenLayout.stopped(this.state.active(), this.state.nextPaymentTime(), now)
                ? (this.state.nationOwned()
                        ? "国庫で再開 (" + this.state.upkeepCost() + " TC)"
                        : "支払って再開 (" + this.state.upkeepCost() + " TC)")
                : "稼働中";
        drawButton(
                graphics,
                this.font,
                payRect,
                Component.literal(payLabel),
                payEnabled ? SUCCESS : ACCENT,
                payEnabled && payRect.contains(mouseX, mouseY),
                payEnabled
        );

        // 3. Detector name.
        Rect nameCard = layout.nameCard();
        drawCard(graphics, nameCard, GOLD, true, false);
        graphics.drawString(
                this.font,
                "名称（最大" + DetectorNamePolicy.MAX_LENGTH + "文字・侵入警告に表示）",
                nameCard.x() + 12,
                nameCard.y() + 7,
                GOLD,
                false
        );
        if (this.state.canManage()) {
            Rect saveRect = layout.saveButton();
            boolean saveEnabled = canSaveName();
            drawButton(
                    graphics,
                    this.font,
                    saveRect,
                    Component.literal("保存"),
                    GOLD,
                    saveEnabled && saveRect.contains(mouseX, mouseY),
                    saveEnabled
            );

            Rect resetRect = layout.resetButton();
            boolean resetEnabled = canResetName();
            drawButton(
                    graphics,
                    this.font,
                    resetRect,
                    Component.literal("未設定に戻す"),
                    MUTED,
                    resetEnabled && resetRect.contains(mouseX, mouseY),
                    resetEnabled
            );
        } else {
            graphics.drawString(this.font, displayName, nameCard.x() + 12, nameCard.y() + 25, TEXT, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        if (this.toastTicks > 0 && !this.status.isEmpty()) {
            drawToast(graphics, this.font, this.width, this.height, Component.literal(this.status), this.statusColor);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            PlayerDetectorScreenLayout.Layout layout = PlayerDetectorScreenLayout.calculate(this.width, this.height);
            if (layout.close().contains(mouseX, mouseY)) {
                onClose();
                return true;
            }
            long now = System.currentTimeMillis();
            if (canPayToRestart(now) && layout.payButton().contains(mouseX, mouseY)) {
                this.status = "維持費の支払いを送信しています…";
                this.statusColor = GOLD;
                this.toastTicks = 40;
                PacketDistributor.sendToServer(new C2S_ConfigurePaymentPacket(this.state.pos()));
                return true;
            }
            if (canSaveName() && layout.saveButton().contains(mouseX, mouseY)) {
                saveName(this.nameInput.getValue());
                return true;
            }
            if (canResetName() && layout.resetButton().contains(mouseX, mouseY)) {
                if (this.nameInput != null) {
                    this.nameInput.setValue("");
                }
                saveName("");
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.nameInput != null && this.nameInput.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            saveName(this.nameInput.getValue());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.toastTicks > 0) {
            this.toastTicks--;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

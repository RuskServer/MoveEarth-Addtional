package com.ruskserver.moveearth_addtional.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class OxygenClientOverlay {

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isCreative() || mc.player.isSpectator()) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        long now = System.currentTimeMillis();

        // 1. ガスマスク装着時の視界オーバーレイ（レンズ枠 & 曇り）
        if (OxygenClientState.hasGasMask) {
            renderGasMaskVignette(guiGraphics, screenWidth, screenHeight, now);
        }

        // 2. 低酸素時の危機演出（赤色パルス）
        if (OxygenClientState.oxygenPercent < 0.4f) {
            renderLowOxygenPanic(guiGraphics, screenWidth, screenHeight, now);
        }

        // 3. 酸素・フィルターHUDメーター（危険地帯 or マスク装着 or 酸素減少時）
        if (OxygenClientState.isDangerZone || OxygenClientState.hasGasMask || OxygenClientState.oxygenPercent < 1.0f) {
            renderOxygenMeter(guiGraphics, font, screenWidth, screenHeight, now);
        }
    }

    private static void renderGasMaskVignette(GuiGraphics guiGraphics, int w, int h, long now) {
        // 四隅のダークフレーム
        int cornerSize = Math.min(w, h) / 5;
        int frameAlpha = 0xAA;
        int frameColor = (frameAlpha << 24) | 0x0A0A0A;

        guiGraphics.fill(0, 0, cornerSize, 12, frameColor);
        guiGraphics.fill(0, 0, 12, cornerSize, frameColor);
        guiGraphics.fill(w - cornerSize, 0, w, 12, frameColor);
        guiGraphics.fill(w - 12, 0, w, cornerSize, frameColor);
        guiGraphics.fill(0, h - 12, cornerSize, h, frameColor);
        guiGraphics.fill(0, h - cornerSize, 12, h, frameColor);
        guiGraphics.fill(w - cornerSize, h - 12, w, h, frameColor);
        guiGraphics.fill(w - 12, h - cornerSize, w, h, frameColor);

        // フィルター残量低下時のレンズ曇り演出（20%以下で白っぽくパルス）
        if (OxygenClientState.filterPercent <= 0.2f) {
            float pulse = (float) ((Math.sin(now / 200.0) + 1.0) / 2.0);
            int fogAlpha = (int) (40 * (1.0f - (OxygenClientState.filterPercent / 0.2f)) * (0.6f + 0.4f * pulse));
            if (fogAlpha > 0) {
                int fogColor = (fogAlpha << 24) | 0xCCCCCC;
                guiGraphics.fill(0, 0, w, h, fogColor);
            }
        }
    }

    private static void renderLowOxygenPanic(GuiGraphics guiGraphics, int w, int h, long now) {
        float severity = 1.0f - (OxygenClientState.oxygenPercent / 0.4f);
        float pulse = (float) ((Math.sin(now / 150.0) + 1.0) / 2.0);
        int alpha = (int) (65 * severity * pulse);
        if (alpha > 0) {
            int redColor = (alpha << 24) | 0xCC0000;
            guiGraphics.fill(0, 0, w, h, redColor);
        }
    }

    private static void renderOxygenMeter(GuiGraphics guiGraphics, Font font, int screenWidth, int screenHeight, long now) {
        int hudX = 10;
        int hudY = screenHeight - 65;
        int boxWidth = 140;
        int boxHeight = 48;

        // 背景
        guiGraphics.fill(hudX, hudY, hudX + boxWidth, hudY + boxHeight, 0xD0101015);
        // アクセントボーダー
        int borderCol = OxygenClientState.isExtremeZone ? 0xFFFF2222 : (OxygenClientState.isDangerZone ? 0xFFFFAA00 : 0xFF00AAFF);
        guiGraphics.fill(hudX, hudY, hudX + 3, hudY + boxHeight, borderCol);

        // タイトル
        String statusText;
        int statusColor;
        if (OxygenClientState.isExtremeZone) {
            statusText = "DEAD ZONE [EXTREME]";
            statusColor = 0xFF5555;
        } else if (OxygenClientState.isDangerZone) {
            statusText = "HAZARD ZONE [NO O2]";
            statusColor = 0xFFAA33;
        } else {
            statusText = "SURFACE [SAFE]";
            statusColor = 0x55FF55;
        }
        guiGraphics.drawString(font, statusText, hudX + 8, hudY + 4, statusColor, false);

        // フィルター残量バー
        int filterY = hudY + 16;
        guiGraphics.drawString(font, "FILTER", hudX + 8, filterY, 0xAAAAAA, false);
        int barX = hudX + 48;
        int barW = 55;
        int barH = 7;
        // バー枠
        guiGraphics.fill(barX - 1, filterY - 1, barX + barW + 1, filterY + barH + 1, 0xFF333333);
        guiGraphics.fill(barX, filterY, barX + barW, filterY + barH, 0xFF1A1A1A);

        float fPct = Math.max(0f, Math.min(1f, OxygenClientState.filterPercent));
        int fFillW = Math.round(barW * fPct);
        int fColor = fPct > 0.5f ? 0xFF00FF66 : (fPct > 0.2f ? 0xFFFFAA00 : 0xFFFF2222);
        if (fPct <= 0.2f && (now / 250) % 2 == 0) {
            fColor = 0xFFFFFFFF; // 危険時白赤点滅
        }
        if (fFillW > 0) {
            guiGraphics.fill(barX, filterY, barX + fFillW, filterY + barH, fColor);
        }
        int fPctNum = Math.round(fPct * 100f);
        guiGraphics.drawString(font, fPctNum + "%", barX + barW + 5, filterY, fColor, false);

        // 酸素残量バー
        int oxygenY = hudY + 27;
        guiGraphics.drawString(font, "O2", hudX + 8, oxygenY, 0xAAAAAA, false);
        guiGraphics.fill(barX - 1, oxygenY - 1, barX + barW + 1, oxygenY + barH + 1, 0xFF333333);
        guiGraphics.fill(barX, oxygenY, barX + barW, oxygenY + barH, 0xFF1A1A1A);

        float oPct = Math.max(0f, Math.min(1f, OxygenClientState.oxygenPercent));
        int oFillW = Math.round(barW * oPct);
        int oColor = oPct > 0.5f ? 0xFF00E5FF : (oPct > 0.25f ? 0xFFFFAA00 : 0xFFFF0033);
        if (oPct < 0.3f && (now / 200) % 2 == 0) {
            oColor = 0xFFFFFFFF;
        }
        if (oFillW > 0) {
            guiGraphics.fill(barX, oxygenY, barX + oFillW, oxygenY + barH, oColor);
        }
        int oPctNum = Math.round(oPct * 100f);
        guiGraphics.drawString(font, oPctNum + "%", barX + barW + 5, oxygenY, oColor, false);

        // 負荷レート表示
        int rateY = hudY + 38;
        float rate = OxygenClientState.consumptionRate;
        String rateStr;
        int rateColor;
        if (rate >= 2.5f) {
            rateStr = "RATE: x" + String.format("%.1f", rate) + " [COMBAT]";
            rateColor = 0xFFFF3333;
        } else if (rate >= 2.0f) {
            rateStr = "RATE: x" + String.format("%.1f", rate) + " [MINING]";
            rateColor = 0xFFFF8800;
        } else if (rate >= 1.5f) {
            rateStr = "RATE: x" + String.format("%.1f", rate) + " [SPRINT]";
            rateColor = 0xFFFFFF55;
        } else {
            rateStr = "RATE: x1.0 [IDLE]";
            rateColor = 0xFF888888;
        }
        guiGraphics.drawString(font, rateStr, hudX + 8, rateY, rateColor, false);
    }
}

package com.ruskserver.moveearth_addtional.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(value = Dist.CLIENT)
public class OxygenClientOverlay {
    private static float maskOpacity;
    private static long previousFrameNanos;

    @SubscribeEvent
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isCreative() || mc.player.isSpectator()) {
            maskOpacity = 0.0F;
            previousFrameNanos = 0L;
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        long now = System.currentTimeMillis();
        long frameNanos = System.nanoTime();
        float elapsedSeconds = previousFrameNanos == 0L
                ? 1.0F / 60.0F : (frameNanos - previousFrameNanos) / 1_000_000_000.0F;
        previousFrameNanos = frameNanos;
        maskOpacity = GasMaskVisualPolicy.approach(maskOpacity,
                OxygenClientState.hasGasMask ? 1.0F : 0.0F, elapsedSeconds);

        // 1. Smooth procedural lenses, with the old rectangle frame retained as fallback.
        boolean smoothMask = false;
        if (maskOpacity > 0.01F) {
            float fog = GasMaskVisualPolicy.fogStrength(OxygenClientState.filterPercent);
            float panic = GasMaskVisualPolicy.panicStrength(OxygenClientState.oxygenPercent);
            smoothMask = GasMaskShaderRenderer.render(guiGraphics, screenWidth, screenHeight,
                    maskOpacity, fog, panic, now);
            if (!smoothMask) renderGasMaskFallback(guiGraphics, screenWidth, screenHeight, fog, maskOpacity, now);
        }

        // 2. 低酸素時の危機演出（赤色パルス）
        if (OxygenClientState.oxygenPercent < 0.4f && !smoothMask) {
            renderLowOxygenPanic(guiGraphics, screenWidth, screenHeight, now);
        }
    }

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isCreative() || mc.player.isSpectator()) return;

        // Draw the readable meter after vanilla HUD elements; the lens itself is rendered in Pre.
        if (OxygenClientState.isDangerZone || OxygenClientState.hasGasMask || OxygenClientState.oxygenPercent < 1.0f) {
            GuiGraphics guiGraphics = event.getGuiGraphics();
            renderOxygenMeter(guiGraphics, mc.font, mc.getWindow().getGuiScaledHeight(),
                    System.currentTimeMillis());
        }
    }

    private static void renderGasMaskFallback(GuiGraphics guiGraphics, int w, int h,
                                              float fogStrength, float opacity, long now) {
        // 四隅のダークフレーム
        int cornerSize = Math.min(w, h) / 5;
        int frameAlpha = Math.round(0xAA * opacity);
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
        if (fogStrength > 0.0F) {
            float pulse = (float) ((Math.sin(now / 200.0) + 1.0) / 2.0);
            int fogAlpha = (int) (40 * fogStrength * opacity * (0.6f + 0.4f * pulse));
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

    private static void renderOxygenMeter(GuiGraphics guiGraphics, Font font, int screenHeight, long now) {
        int hudX = 10;
        int boxHeight = OxygenClientState.hasGasMask ? 27 : 16;
        int hudY = screenHeight - 17 - boxHeight;
        guiGraphics.fill(hudX, hudY, hudX + 108, hudY + boxHeight, 0xD0101015);
        int borderColor = OxygenClientState.isExtremeZone ? 0xFFFF2222
                : OxygenClientState.isDangerZone ? 0xFFFFAA00 : 0xFF00AAFF;
        guiGraphics.fill(hudX, hudY, hudX + 2, hudY + boxHeight, borderColor);

        float oxygen = Math.max(0.0F, Math.min(1.0F, OxygenClientState.oxygenPercent));
        int oxygenColor = oxygen > 0.5F ? 0xFF00E5FF : oxygen > 0.25F ? 0xFFFFAA00 : 0xFFFF0033;
        if (oxygen < 0.3F && (now / 200) % 2 == 0) oxygenColor = 0xFFFFFFFF;
        renderMeterRow(guiGraphics, font, hudX, hudY + 4, "O2", oxygen, oxygenColor);

        if (OxygenClientState.hasGasMask) {
            float filter = Math.max(0.0F, Math.min(1.0F, OxygenClientState.filterPercent));
            int filterColor = filter > 0.5F ? 0xFF00FF66 : filter > 0.2F ? 0xFFFFAA00 : 0xFFFF2222;
            if (filter <= 0.2F && (now / 250) % 2 == 0) filterColor = 0xFFFFFFFF;
            renderMeterRow(guiGraphics, font, hudX, hudY + 15, "FLT", filter, filterColor);
        }
    }

    private static void renderMeterRow(GuiGraphics guiGraphics, Font font, int hudX, int rowY,
                                       String label, float percent, int color) {
        int barX = hudX + 29;
        int barWidth = 43;
        guiGraphics.drawString(font, label, hudX + 7, rowY, 0xAAAAAA, false);
        guiGraphics.fill(barX - 1, rowY + 1, barX + barWidth + 1, rowY + 8, 0xFF333333);
        guiGraphics.fill(barX, rowY + 2, barX + barWidth, rowY + 7, 0xFF1A1A1A);
        int fillWidth = Math.round(barWidth * percent);
        if (fillWidth > 0) guiGraphics.fill(barX, rowY + 2, barX + fillWidth, rowY + 7, color);
        guiGraphics.drawString(font, Math.round(percent * 100.0F) + "%", barX + barWidth + 4, rowY, color, false);
    }
}

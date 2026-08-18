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
public class AnnouncementOverlay {

    private static String currentMessage = "";
    private static long displayStartTime = 0;
    private static final long DISPLAY_DURATION = 10000; // 10秒
    private static final long ANIMATION_TIME = 500; // 0.5秒スライド

    public static void showAnnouncement(String message) {
        currentMessage = message;
        displayStartTime = System.currentTimeMillis();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (currentMessage.isEmpty() || displayStartTime == 0) return;

        long elapsed = System.currentTimeMillis() - displayStartTime;
        if (elapsed > DISPLAY_DURATION) {
            currentMessage = ""; // 終了
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        GuiGraphics guiGraphics = event.getGuiGraphics();

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int boxWidth = Math.max(300, font.width(currentMessage) + 60);
        int boxHeight = 30;

        int x = (screenWidth - boxWidth) / 2;
        int y = 20;

        // アニメーション (スライドイン・フェードアウト)
        float alpha = 1.0f;
        int yOffset = 0;

        if (elapsed < ANIMATION_TIME) {
            // スライドイン (上から)
            float progress = (float) elapsed / ANIMATION_TIME;
            // イーズアウト (1 - (1-t)^3 など)
            float ease = 1.0f - (float) Math.pow(1.0f - progress, 3);
            yOffset = (int) (-50 * (1.0f - ease));
        } else if (elapsed > DISPLAY_DURATION - ANIMATION_TIME) {
            // フェードアウト
            float remaining = DISPLAY_DURATION - elapsed;
            alpha = remaining / ANIMATION_TIME;
        }

        int currentY = y + yOffset;

        int bgAlpha = (int) (0xAA * alpha);
        int bgColor = (bgAlpha << 24) | 0x111111; // 黒背景

        int lineAlpha = (int) (0xFF * alpha);
        int lineColor = (lineAlpha << 24) | 0xFF3333; // 赤いアクセント

        int textAlpha = (int) (0xFF * alpha);
        int textColor = (textAlpha << 24) | 0xFFFFFF; // 白文字

        // 描画: 背景枠
        guiGraphics.fill(x, currentY, x + boxWidth, currentY + boxHeight, bgColor);
        
        // 描画: 左側のアクセントライン
        guiGraphics.fill(x, currentY, x + 4, currentY + boxHeight, lineColor);

        // 描画: テキスト (中央揃え)
        int textX = x + (boxWidth - font.width(currentMessage)) / 2;
        int textY = currentY + (boxHeight - font.lineHeight) / 2 + 1; // 縦中央揃え
        guiGraphics.drawString(font, currentMessage, textX, textY, textColor, true); // true = ドロップシャドウ
    }
}

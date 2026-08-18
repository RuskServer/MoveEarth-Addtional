package com.ruskserver.moveearth_addtional.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.Random;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class VhsDeathScreen extends Screen {
    private static final int WAIT_TICKS = 100;
    private static final String[] MESSAGES = {
            "まだ止まる時ではない…",
            "さぁ、起きろ。",
            "ここで終わるな。",
            "息をしろ。まだ戻れる。",
            "死ぬには早すぎる。",
            "聞こえるか？ 立て。"
    };

    private final Random random = new Random();
    private final String message;
    private SoundInstance deathMusic;
    private int ticks;
    private boolean respawnRequested;

    public VhsDeathScreen() {
        super(Component.empty());
        this.message = MESSAGES[random.nextInt(MESSAGES.length)];
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getScreen() instanceof DeathScreen) {
            event.setNewScreen(new VhsDeathScreen());
        }
    }

    @Override
    protected void init() {
        this.minecraft.mouseHandler.releaseMouse();
        this.deathMusic = SimpleSoundInstance.forMusic(SoundEvents.MUSIC_MENU.value());
        this.minecraft.getSoundManager().play(this.deathMusic);
    }

    @Override
    public void tick() {
        if (++ticks >= WAIT_TICKS) {
            requestRespawn();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            requestRespawn();
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return true;
    }

    private void requestRespawn() {
        if (respawnRequested || minecraft == null || minecraft.player == null) return;
        respawnRequested = true;
        stopMusic();
        minecraft.player.connection.send(new ServerboundClientCommandPacket(
                ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
    }

    @Override
    public void removed() {
        stopMusic();
        super.removed();
    }

    private void stopMusic() {
        if (minecraft != null && deathMusic != null) {
            minecraft.getSoundManager().stop(deathMusic);
            deathMusic = null;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int width = this.width;
        int height = this.height;
        long time = System.currentTimeMillis();

        graphics.fill(0, 0, width, height, 0xFF000000);

        Random noise = new Random(time / 45L);
        for (int i = 0; i < 36; i++) {
            int y = noise.nextInt(Math.max(1, height));
            int alpha = 18 + noise.nextInt(35);
            int color = (alpha << 24) | (noise.nextBoolean() ? 0xB8C4CC : 0x5E6670);
            graphics.fill(0, y, width, Math.min(height, y + 1 + noise.nextInt(3)), color);
        }
        for (int y = 0; y < height; y += 4) {
            graphics.fill(0, y, width, y + 1, 0x22000000);
        }

        // Strong VHS tracking distortion at the top and bottom of the frame.
        graphics.fill(0, 0, width, 42, 0xAA050608);
        graphics.fill(0, height - 42, width, height, 0xAA050608);
        for (int i = 0; i < 14; i++) {
            int bandHeight = 1 + noise.nextInt(4);
            int topY = noise.nextInt(48);
            int bottomY = height - 48 + noise.nextInt(48);
            int alpha = 35 + noise.nextInt(80);
            int color = (alpha << 24) | (noise.nextBoolean() ? 0xD5E5EA : 0x7E2635);
            int offset = noise.nextInt(15) - 7;
            graphics.fill(offset, topY, width + offset, topY + bandHeight, color);
            graphics.fill(-offset, bottomY, width - offset, bottomY + bandHeight, color);
        }
        for (int i = 0; i < 5; i++) {
            int glitchY = 55 + noise.nextInt(Math.max(1, height - 110));
            int glitchHeight = 1 + noise.nextInt(3);
            int glitchOffset = noise.nextInt(25) - 12;
            graphics.fill(glitchOffset, glitchY, width + glitchOffset, glitchY + glitchHeight, 0x443E8490);
            graphics.fill(-glitchOffset, glitchY + glitchHeight, width - glitchOffset, glitchY + glitchHeight + 1, 0x445E1F2B);
        }

        Font font = this.minecraft.font;
        int visibleChars = Math.min(message.length(), Math.max(0, (ticks + 2) / 3));
        String visibleMessage = message.substring(0, visibleChars);
        float fade = Math.min(1.0F, ticks / 20.0F);
        int textWidth = font.width(visibleMessage);
        int x = (width - textWidth) / 2;
        int y = height / 2 - font.lineHeight / 2;
        int jitter = noise.nextInt(3) - 1;
        int textAlpha = (int) (255 * fade);
        int shadowAlpha = (int) (150 * fade);
        graphics.drawString(font, visibleMessage, x + jitter - 2, y, (shadowAlpha << 24) | 0x435A66, false);
        graphics.drawString(font, visibleMessage, x + jitter + 2, y, (shadowAlpha << 24) | 0x8A3340, false);
        graphics.drawString(font, visibleMessage, x + jitter, y, (textAlpha << 24) | 0xF0F0F0, true);

        String countdown = String.format("%02d", Math.max(0, (WAIT_TICKS - ticks + 19) / 20));
        String hint = "ESC で起きる";
        graphics.drawCenteredString(font, countdown, width / 2, y + 28, 0xFF8C9298);
        graphics.drawString(font, hint, width - font.width(hint) - 12, height - font.lineHeight - 10, 0xAA666666, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

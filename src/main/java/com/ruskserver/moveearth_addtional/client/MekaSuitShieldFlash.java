package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.config.ClientDisplayConfig;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Green screen-edge flash when the MekaSuit heavy-hit shield stops a hit. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.CLIENT)
public final class MekaSuitShieldFlash {
    private static final long DURATION_MILLIS = 550L;
    private static final long DEPLETED_DURATION_MILLIS = 900L;
    private static final int GREEN = 0x3CFF78;
    private static final int DEPLETED_GREEN = 0x9CFF3C;

    private static long startedAt = -1L;
    private static boolean depleted;

    private MekaSuitShieldFlash() { }

    public static void trigger(boolean lastCharge) {
        startedAt = Util.getMillis();
        depleted = lastCharge;
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        if (startedAt < 0L) return;
        long duration = depleted ? DEPLETED_DURATION_MILLIS : DURATION_MILLIS;
        long elapsed = Util.getMillis() - startedAt;
        if (elapsed >= duration || Minecraft.getInstance().options.hideGui) {
            if (elapsed >= duration) startedAt = -1L;
            return;
        }
        float setting = ClientDisplayConfig.SHIELD_FLASH_STRENGTH.get().floatValue();
        if (setting <= 0.0F) return;
        // Fades out quickly after a sharp start.
        float strength = 1.0F - (float) elapsed / duration;
        strength *= strength * setting;
        int rgb = depleted ? DEPLETED_GREEN : GREEN;
        GuiGraphics graphics = event.getGuiGraphics();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        graphics.fill(0, 0, width, height, argb(0.10F * strength, rgb));
        int band = Math.max(24, height / 5);
        int edge = argb(0.55F * strength, rgb);
        int clear = argb(0.0F, rgb);
        graphics.fillGradient(0, 0, width, band, edge, clear);
        graphics.fillGradient(0, height - band, width, height, clear, edge);
        int side = Math.max(24, width / 8);
        for (int x = 0; x < side; x++) {
            float fade = 1.0F - (float) x / side;
            int color = argb(0.55F * strength * fade * fade, rgb);
            graphics.fill(x, 0, x + 1, height, color);
            graphics.fill(width - x - 1, 0, width - x, height, color);
        }
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return (a << 24) | rgb;
    }
}

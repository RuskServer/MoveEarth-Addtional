package com.ruskserver.moveearth_addtional.client.ui;

import com.mojang.blaze3d.platform.Window;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Shows a fixed-size screen at a smaller GUI scale when it does not fit the player's, the way a game
 * shrinks a menu rather than cut it off. A whole-number scale is used when one is close enough, and an
 * exact fractional one otherwise ({@link ScreenFitMath}). The player's own scale comes back as soon as the screen
 * closes; switching to another screen that needs the same treatment simply applies it again.
 *
 * <p>Minecraft converts the cursor with whatever scale is current, so clicks keep landing where they
 * are drawn without any help from the screen.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.CLIENT)
public final class ScreenFit {
    private static boolean lowered;

    private ScreenFit() { }

    /**
     * Call first in the screen's {@code init()}: picks the largest GUI scale, no larger than the player's,
     * at which {@code needWidth} by {@code needHeight} fits, and resizes the screen to it.
     */
    public static void fit(Screen screen, int needWidth, int needHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft.getWindow();
        int normal = normalScale(minecraft);
        double scale = ScreenFitMath.scale(window.getWidth(), window.getHeight(), normal, needWidth, needHeight);
        if (scale != window.getGuiScale()) window.setGuiScale(scale);
        lowered = scale != normal;
        screen.width = window.getGuiScaledWidth();
        screen.height = window.getGuiScaledHeight();
    }

    @SubscribeEvent
    public static void onClosing(ScreenEvent.Closing event) {
        if (!lowered) return;
        lowered = false;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getWindow().setGuiScale(normalScale(minecraft));
    }

    private static int normalScale(Minecraft minecraft) {
        return minecraft.getWindow().calculateScale(minecraft.options.guiScale().get(), minecraft.isEnforceUnicode());
    }
}

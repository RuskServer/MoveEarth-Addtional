package com.ruskserver.moveearth_addtional.client.ui;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.config.ClientDisplayConfig;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Hover and click sounds for every menu.
 *
 * <p>Whatever is under the cursor reports itself while it is drawn: vanilla buttons, sliders and tabs
 * through a mixin, and MoveEarth's own controls through {@link MoveEarthUi}. When that changes from one
 * frame to the next, the hover sound plays. MoveEarth's controls are drawn rather than built from
 * widgets and never played a click, so pressing one now plays it too; vanilla widgets keep playing
 * their own. The click itself is vanilla's button click, which MoveEarth's resources replace.
 *
 * <p>The hover sound is played by name and not registered: a registry entry would have to exist on the
 * server as well, and these sounds only ever play on the client.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.CLIENT)
public final class UiSounds {
    private static final SoundEvent HOVER = SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "ui.hover"));
    /** Sweeping across a row of buttons should tick, not buzz. */
    private static final long MIN_HOVER_GAP_MS = 45L;
    /** A screen that plays its own click as well as being pressed should not click twice. */
    private static final long CLICK_DEDUPE_MS = 60L;
    private static final float UI_VOLUME = 0.25F;

    private static Object current;
    private static boolean currentClicks;
    private static Object last;
    private static boolean lastClicks;
    private static Screen screen;
    private static boolean primed;
    private static long lastHoverAt;
    private static long lastClickAt;

    private UiSounds() { }

    /** A MoveEarth control under the cursor this frame; pressing it plays the click. */
    public static void hoveredControl(Object key) {
        current = key;
        currentClicks = true;
    }

    /** A vanilla widget under the cursor this frame; it plays its own click. */
    public static void hoveredWidget(Object key) {
        current = key;
        currentClicks = false;
    }

    /** Plays the button click, once however many places ask for it at the same moment. */
    public static void click() {
        long now = Util.getMillis();
        if (now - lastClickAt < CLICK_DEDUPE_MS) return;
        lastClickAt = now;
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, UI_VOLUME));
    }

    @SubscribeEvent
    public static void onRendered(ScreenEvent.Render.Post event) {
        if (event.getScreen() != screen) {
            screen = event.getScreen();
            // Opening a screen under a resting cursor is not a hover.
            primed = false;
        }
        Object now = current;
        boolean clicks = currentClicks;
        current = null;
        long time = Util.getMillis();
        float volume = (float) ClientDisplayConfig.UI_HOVER_VOLUME.getAsDouble();
        if (primed && now != null && !now.equals(last) && volume > 0.0F && time - lastHoverAt >= MIN_HOVER_GAP_MS) {
            lastHoverAt = time;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(HOVER, 1.0F, UI_VOLUME * volume));
        }
        primed = true;
        last = now;
        lastClicks = clicks;
    }

    @SubscribeEvent
    public static void onPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() == 0 && event.getScreen() == screen && last != null && lastClicks) click();
    }
}

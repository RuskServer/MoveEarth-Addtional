package com.ruskserver.moveearth_addtional.client.loading;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Ends stale presentation state and recovers a terrain-receive screen after transport loss. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.CLIENT)
public final class MoveEarthLoadingLifecycle {
    private MoveEarthLoadingLifecycle() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!isCustomLoadingScreen(event.getNewScreen())) {
            MoveEarthLoadingRenderer.resetSession();
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        MoveEarthLoadingRenderer.resetSession();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean receiving = minecraft.screen instanceof ReceivingLevelScreen;
        var listener = minecraft.getConnection();
        boolean connectionPresent = listener != null;
        boolean connectionOpen = connectionPresent && listener.getConnection().isConnected();
        if (!LoadingScreenLifecyclePolicy.shouldRecoverReceiving(
                receiving, minecraft.player != null, connectionPresent, connectionOpen)) return;

        // Normally Minecraft handles this from its connection tick. Hybrid servers and
        // disconnects during the level hand-off can leave ReceivingLevelScreen current,
        // so finish the vanilla disconnection path immediately and preserve its reason.
        MoveEarthLoadingRenderer.resetSession();
        listener.getConnection().handleDisconnection();
    }

    public static boolean isGenericLoadingMessage(Component title) {
        return title != null && title.getContents() instanceof TranslatableContents translatable
                && LoadingScreenLifecyclePolicy.isGenericLoadingKey(translatable.getKey());
    }

    private static boolean isCustomLoadingScreen(Screen screen) {
        return screen instanceof ConnectScreen
                || screen instanceof LevelLoadingScreen
                || screen instanceof ReceivingLevelScreen
                || screen instanceof ProgressScreen
                || (screen instanceof GenericMessageScreen
                && isGenericLoadingMessage(screen.getTitle()));
    }
}

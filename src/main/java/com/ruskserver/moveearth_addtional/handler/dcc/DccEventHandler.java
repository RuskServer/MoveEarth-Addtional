package com.ruskserver.moveearth_addtional.handler.dcc;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Delayed Chunk Cache（DCC）のサーバーTickおよびライフサイクルイベントハンドラー。
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public class DccEventHandler {

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        DelayedChunkCacheManager.tickServer(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DelayedChunkCacheManager.flushPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DelayedChunkCacheManager.flushPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DelayedChunkCacheManager.clearAll();
    }
}

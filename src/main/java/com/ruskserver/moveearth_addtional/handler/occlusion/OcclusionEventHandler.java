package com.ruskserver.moveearth_addtional.handler.occlusion;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * サブチャンク透過キャッシュおよびプレイヤー可視キャッシュのライフサイクル管理イベントハンドラー。
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public class OcclusionEventHandler {

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PlayerVisibilityTracker.removePlayer(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        PlayerVisibilityTracker.removePlayer(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            SectionOcclusionStorage.invalidate(serverLevel, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            SectionOcclusionStorage.invalidate(serverLevel, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onBlockMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            SectionOcclusionStorage.invalidate(serverLevel, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            SectionOcclusionStorage.invalidateChunk(serverLevel, event.getChunk().getPos().x, event.getChunk().getPos().z);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SectionOcclusionStorage.clearAll();
        PlayerVisibilityTracker.clearAll();
    }
}

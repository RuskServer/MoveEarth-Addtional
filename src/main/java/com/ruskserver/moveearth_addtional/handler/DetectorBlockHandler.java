package com.ruskserver.moveearth_addtional.handler;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.block.ModBlocks;
import com.ruskserver.moveearth_addtional.block.entity.PlayerDetectorBlockEntity;
import com.ruskserver.moveearth_addtional.data.DetectorBlockPositionSavedData;
import com.ruskserver.moveearth_addtional.detector.LoadedDetectorRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.ruskserver.moveearth_addtional.data.PlayerWhitelistSavedData;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public class DetectorBlockHandler {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().getServer() != null) {
            PlayerWhitelistSavedData.get(event.getEntity().getServer()).tryResolveUnresolved(event.getEntity().getServer());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (PlayerDetectorBlockEntity.isDetectorDummy(event.getTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (PlayerDetectorBlockEntity.isDetectorDummy(event.getTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && event.getEntity() instanceof Shulker shulker
                && PlayerDetectorBlockEntity.isDetectorDummy(shulker)) {
            // Delay validation until block entities in a loading chunk are available.
            level.getServer().execute(() -> PlayerDetectorBlockEntity.validateLoadedDummy(level, shulker));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTick(ServerTickEvent.Post event) {
        LoadedDetectorRegistry.maintainLoadedDetectors(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LoadedDetectorRegistry.clear(event.getServer());
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        LevelAccessor levelAccessor = event.getLevel();
        if (levelAccessor instanceof ServerLevel serverLevel) {
            if (event.getPlacedBlock().is(ModBlocks.PLAYER_DETECTOR.get())) {
                BlockPos pos = event.getPos();
                DetectorBlockPositionSavedData data = DetectorBlockPositionSavedData.get(serverLevel);
                BlockPos overlapPos = data.getOverlapPosition(pos);

                if (overlapPos != null) {
                    // 設置をキャンセル
                    event.setCanceled(true);

                    // プレイヤーに詳細な警告メッセージを表示
                    if (event.getEntity() instanceof Player player) {
                        player.sendSystemMessage(Component.literal(String.format(
                                "§c半径3チャンク以内に既にプレイヤー検知ブロックが設置されているため、設置できません。(重複元の位置: X=%d, Y=%d, Z=%d)",
                                overlapPos.getX(), overlapPos.getY(), overlapPos.getZ()
                        )));
                    }
                }
            }
        }
    }
}

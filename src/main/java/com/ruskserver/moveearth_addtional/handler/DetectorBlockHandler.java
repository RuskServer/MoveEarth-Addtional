package com.ruskserver.moveearth_addtional.handler;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.block.ModBlocks;
import com.ruskserver.moveearth_addtional.block.entity.PlayerDetectorBlockEntity;
import com.ruskserver.moveearth_addtional.data.DetectorBlockPositionSavedData;
import com.ruskserver.moveearth_addtional.detector.DetectorOwnership;
import com.ruskserver.moveearth_addtional.detector.LoadedDetectorRegistry;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public class DetectorBlockHandler {

    private static final ConcurrentHashMap<MinecraftServer, Queue<PendingDummyValidation>> PENDING_DUMMY_VALIDATIONS =
            new ConcurrentHashMap<>();

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
            MinecraftServer server = level.getServer();
            PENDING_DUMMY_VALIDATIONS
                    .computeIfAbsent(server, ignored -> new ConcurrentLinkedQueue<>())
                    .add(new PendingDummyValidation(level, shulker, server.getTickCount() + 1));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTick(ServerTickEvent.Post event) {
        validatePendingDummies(event.getServer());
        LoadedDetectorRegistry.maintainLoadedDetectors(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING_DUMMY_VALIDATIONS.remove(event.getServer());
        LoadedDetectorRegistry.clear(event.getServer());
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        LevelAccessor levelAccessor = event.getLevel();
        if (levelAccessor instanceof ServerLevel serverLevel) {
            if (event.getPlacedBlock().is(ModBlocks.PLAYER_DETECTOR.get())) {
                if (event.getEntity() instanceof ServerPlayer placer && !DetectorOwnership.mayPlace(placer)) {
                    event.setCanceled(true);
                    placer.sendSystemMessage(MoveEarthMessage.error(
                            "検知ブロックは国家の物になり維持費が国庫から払われるため、置くには拠点操作か国庫管理の権限が必要です。"));
                    return;
                }
                BlockPos pos = event.getPos();
                DetectorBlockPositionSavedData data = DetectorBlockPositionSavedData.get(serverLevel);
                BlockPos overlapPos = data.getOverlapPosition(pos);

                if (overlapPos != null) {
                    // 設置をキャンセル
                    event.setCanceled(true);

                    // プレイヤーに詳細な警告メッセージを表示
                    if (event.getEntity() instanceof Player player) {
                        player.sendSystemMessage(MoveEarthMessage.error(String.format(
                                "半径3チャンク以内に既にプレイヤー検知ブロックが設置されているため、設置できません。(重複元の位置: X=%d, Y=%d, Z=%d)",
                                overlapPos.getX(), overlapPos.getY(), overlapPos.getZ()
                        )));
                    }
                }
            }
        }
    }

    /** A citizen without the rights to manage their nation's detector may not remove it either. */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)
                || !(event.getLevel().getBlockEntity(event.getPos()) instanceof PlayerDetectorBlockEntity detector)) return;
        UUID nation = detector.getOwnerNation();
        if (nation == null || DetectorOwnership.mayManage(player, detector)
                || player.getUUID().equals(detector.getOwnerUUID())
                || !nation.equals(NationSavedData.get(player.server).nationIdFor(player.getUUID()).orElse(null))) return;
        event.setCanceled(true);
        player.sendSystemMessage(MoveEarthMessage.error("国家の検知ブロックを撤去するには、拠点操作か国庫管理の権限が必要です。"));
    }

    private static void validatePendingDummies(MinecraftServer server) {
        Queue<PendingDummyValidation> pending = PENDING_DUMMY_VALIDATIONS.get(server);
        if (pending == null) return;

        int entriesToCheck = pending.size();
        int currentTick = server.getTickCount();
        for (int i = 0; i < entriesToCheck; i++) {
            PendingDummyValidation validation = pending.poll();
            if (validation == null) break;
            if (validation.validateAtTick() > currentTick) {
                pending.add(validation);
                continue;
            }
            if (!validation.shulker().isRemoved()) {
                PlayerDetectorBlockEntity.validateLoadedDummy(validation.level(), validation.shulker());
            }
        }

        if (pending.isEmpty()) {
            PENDING_DUMMY_VALIDATIONS.remove(server, pending);
        }
    }

    private record PendingDummyValidation(ServerLevel level, Shulker shulker, int validateAtTick) {
    }
}

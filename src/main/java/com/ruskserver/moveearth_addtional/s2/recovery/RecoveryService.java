package com.ruskserver.moveearth_addtional.s2.recovery;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.config.RecoveryDispatchConfig;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationService;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.s2.time.OpenTimeService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class RecoveryService {
    private RecoveryService() { }

    public static void recordBattleWalls(ServerLevel level,
            com.ruskserver.moveearth_addtional.s2.siege.SiegeSavedData.SiegeRecord siege,
            TerritorySavedData.CoreRecord core) {
        if (!RecoveryDispatchConfig.recoveryEnabled() || core.type() != TerritorySavedData.CoreType.CAPITAL) return;
        NationRecoverySavedData data = NationRecoverySavedData.get(level.getServer());
        if (data.hasWallBaseline(siege.id())) return;
        var walls = ReinforcementSavedData.get(level).recoveryHealth(level, core.pos(), core.radius(),
                RecoveryDispatchConfig.wallTargetCap(), true, pos -> TerritorySavedData.get(level.getServer())
                        .controllingCore(level.getServer(), core.dimension(), pos)
                        .map(owner -> owner.id().equals(core.id())).orElse(false));
        data.recordWallBaseline(siege.id(), walls.health(), walls.blocks());
    }

    public static NationRecoverySavedData.OpenResult openEpisode(MinecraftServer server, UUID sourceSiege,
                                                                  UUID nationId, UUID attackerId,
                                                                  boolean individualAttacker, UUID coreId,
                                                                  ResourceLocation dimension, BlockPos pos,
                                                                  int originalRadius, int wallTarget) {
        if (!RecoveryDispatchConfig.recoveryEnabled()) return new NationRecoverySavedData.OpenResult(false, null);
        long now = OpenTimeService.now(server);
        NationRecoverySavedData data = NationRecoverySavedData.get(server);
        boolean weighted = data.hasWallBaseline(sourceSiege);
        NationRecoverySavedData.OpenResult result = data.open(sourceSiege,
                nationId, attackerId, individualAttacker, coreId, dimension, pos, originalRadius,
                weighted ? data.wallBaseline(sourceSiege, 0)
                        : Math.min(RecoveryDispatchConfig.wallTargetCap(), Math.max(0, wallTarget)), now,
                now + RecoveryDispatchConfig.eligibilityOpenTicks());
        if (result.created()) {
            if (weighted) data.useWeightedWalls(result.episode().id(), sourceSiege);
            WarHistorySavedData.get(server).append(now, WarHistorySavedData.Type.RECOVERY_STARTED,
                    WarHistorySavedData.Visibility.PUBLIC, nationId,
                    individualAttacker ? null : attackerId, result.episode().id(), List.of());
            NationNotificationService.publish(server, List.of(nationId),
                    NationNotificationSavedData.EventType.RECOVERY_STARTED, null, null,
                    Component.translatable("message.moveearth_addtional.recovery.started"), List.of());
        }
        return result;
    }

    public static int countHealthyWalls(ServerLevel level, BlockPos corePos, int chunkRadius, int cap) {
        int radius = Math.max(0, chunkRadius);
        int minChunkX = (corePos.getX() >> 4) - radius;
        int maxChunkX = (corePos.getX() >> 4) + radius;
        int minChunkZ = (corePos.getZ() >> 4) - radius;
        int maxChunkZ = (corePos.getZ() >> 4) + radius;
        return (int) ReinforcementSavedData.get(level).inside(level, minChunkX << 4, level.getMinBuildHeight(),
                        minChunkZ << 4, (maxChunkX << 4) + 15, level.getMaxBuildHeight() - 1,
                        (maxChunkZ << 4) + 15).stream()
                .filter(value -> value.entry() != null && value.entry().enabled()
                        && value.entry().durability() > 0)
                .limit(Math.max(0, cap)).count();
    }

    public static void onUpkeepPaid(MinecraftServer server, UUID nationId, long amount) {
        if (amount <= 0L || !RecoveryDispatchConfig.recoveryEnabled()) return;
        NationRecoverySavedData.Episode before = NationRecoverySavedData.get(server)
                .activeForNation(nationId).orElse(null);
        if (before == null || before.upkeepPaid()) return;
        NationRecoverySavedData.Episode after = NationRecoverySavedData.get(server).markUpkeepPaid(nationId);
        publishProgress(server, before, after, "upkeep");
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!RecoveryDispatchConfig.recoveryEnabled() || server.overworld().getGameTime() % 200L != 41L) return;
        long now = OpenTimeService.now(server);
        NationRecoverySavedData data = NationRecoverySavedData.get(server);
        var sieges = com.ruskserver.moveearth_addtional.s2.siege.SiegeSavedData.get(server);
        data.pruneWallBaselines(id -> sieges.activeById(id).isPresent() || sieges.fallenBySiegeId(id).isPresent());
        for (NationRecoverySavedData.Episode before : data.active()) {
            if (data.expire(before.id(), now)) {
                WarHistorySavedData.get(server).append(now, WarHistorySavedData.Type.RECOVERY_EXPIRED,
                        WarHistorySavedData.Visibility.NATION, before.nationId(), null, before.id(), List.of());
                NationNotificationService.publish(server, List.of(before.nationId()),
                        NationNotificationSavedData.EventType.RECOVERY_EXPIRED, null, null, null, List.of());
                continue;
            }
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, before.dimension()));
            if (level == null || !level.hasChunkAt(before.pos())) continue;
            TerritorySavedData.CoreRecord core = TerritorySavedData.get(server).coreById(before.coreId()).orElse(null);
            boolean sealed = core != null && core.state() == TerritorySavedData.CoreState.ACTIVE;
            long resealStart = sealed ? (before.resealStartedAt() > 0L ? before.resealStartedAt() : now) : 0L;
            boolean resealed = sealed && now - resealStart >= RecoveryDispatchConfig.resealOpenTicks();
            int walls = data.weighted(before.id())
                    ? ReinforcementSavedData.get(level).recoveryHealth(level, before.pos(), before.originalRadius(),
                        data.wallLimit(before.id()), false, pos -> TerritorySavedData.get(server)
                                .allowsReinforcement(server, before.nationId(), before.dimension(), pos)).health()
                    : countHealthyWalls(level, before.pos(), before.originalRadius(), before.wallTarget());
            RecoveryObjectivePolicy.Progress progress = RecoveryObjectivePolicy.evaluate(resealed,
                    before.wallTarget(), walls, before.upkeepPaid());
            NationRecoverySavedData.Episode after = data.updateProgress(before.id(), resealStart, walls, progress);
            publishProgress(server, before, after, objectiveDetail(before, after));
        }
    }

    private static String objectiveDetail(NationRecoverySavedData.Episode before,
                                          NationRecoverySavedData.Episode after) {
        if (after == null) return "";
        if (!before.resealed() && after.resealed()) return "resealed";
        if (!before.wallsRestored() && after.wallsRestored()) return "walls";
        if (!before.upkeepPaid() && after.upkeepPaid()) return "upkeep";
        return "";
    }

    private static void publishProgress(MinecraftServer server, NationRecoverySavedData.Episode before,
                                        NationRecoverySavedData.Episode after, String detail) {
        if (before == null || after == null || after.equals(before)) return;
        long now = OpenTimeService.now(server);
        if (!detail.isBlank()) {
            WarHistorySavedData.get(server).append(now, WarHistorySavedData.Type.RECOVERY_OBJECTIVE,
                    WarHistorySavedData.Visibility.NATION, after.nationId(), null, after.id(), List.of(detail));
            NationNotificationService.publish(server, List.of(after.nationId()),
                    NationNotificationSavedData.EventType.RECOVERY_OBJECTIVE, null, null, null, List.of(detail));
        }
        if (before.state() == NationRecoverySavedData.State.ACTIVE
                && after.state() == NationRecoverySavedData.State.COMPLETED) {
            WarHistorySavedData.get(server).append(now, WarHistorySavedData.Type.RECOVERY_COMPLETED,
                    WarHistorySavedData.Visibility.PUBLIC, after.nationId(), null, after.id(), List.of());
            NationNotificationService.publish(server, List.of(after.nationId()),
                    NationNotificationSavedData.EventType.RECOVERY_COMPLETED, null, null, null, List.of());
        }
    }
}

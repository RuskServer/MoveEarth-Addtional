package com.ruskserver.moveearth_addtional.s2.territory;

import com.ruskserver.moveearth_addtional.block.entity.TerritoryCoreBlockEntity;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.List;

public final class TerritoryClosureRecheckManager {
    public static final long RECHECK_DELAY_TICKS = 5L * 20L;
    private static final long UNLOADED_RETRY_TICKS = 10L * 20L;
    private static final int MAX_SCANS_PER_TICK = 2;
    private static final TerritoryRecheckQueue<CoreKey> QUEUE = new TerritoryRecheckQueue<>();

    private TerritoryClosureRecheckManager() {
    }

    public static void markPotentialOpening(ServerLevel level, BlockPos pos) {
        mark(level, List.of(pos), true);
    }

    public static void markPotentialOpenings(ServerLevel level, Collection<BlockPos> positions) {
        mark(level, positions, true);
    }

    public static void markPotentialSeal(ServerLevel level, Collection<BlockPos> positions) {
        mark(level, positions, false);
    }

    private static void mark(ServerLevel level, Collection<BlockPos> positions, boolean expose) {
        if (positions == null || positions.isEmpty()) return;
        TerritorySavedData territories = TerritorySavedData.get(level.getServer());
        java.util.Set<java.util.UUID> scheduled = new java.util.HashSet<>();
        for (BlockPos changedPos : positions) {
            for (TerritorySavedData.CoreRecord core : territories.coresNear(
                    level.dimension().location(), changedPos, TerritoryClosureScanner.SEARCH_RANGE)) {
                if (core.state() == TerritorySavedData.CoreState.CONFIGURING
                        || !scheduled.add(core.id())) continue;
                TerritorySavedData.CoreRecord queuedCore = core;
                if (expose && core.state() == TerritorySavedData.CoreState.ACTIVE) {
                    queuedCore = territories.updateState(core.nationId(), core.dimension(), core.pos(),
                            TerritorySavedData.CoreState.EXPOSED).orElse(core);
                    bind(level, queuedCore);
                    notifyManagers(level.getServer(), queuedCore, false);
                }
                QUEUE.schedule(new CoreKey(queuedCore.dimension(), queuedCore.pos()),
                        level.getServer().getTickCount() + RECHECK_DELAY_TICKS);
            }
        }
    }

    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        for (CoreKey key : QUEUE.pollDue(now, MAX_SCANS_PER_TICK)) {
            ServerLevel level = findLevel(server, key.dimension());
            if (level == null) continue;
            TerritorySavedData.CoreRecord core = TerritorySavedData.get(server)
                    .core(key.dimension(), key.pos()).orElse(null);
            if (core == null || core.state() == TerritorySavedData.CoreState.CONFIGURING) continue;
            TerritoryClosureScanner.Result result = TerritoryClosureService.scanAndUpdate(level, key.pos());
            if (core.state() == TerritorySavedData.CoreState.EXPOSED && result.sealed()) {
                TerritorySavedData.CoreRecord active = TerritorySavedData.get(server)
                        .core(key.dimension(), key.pos()).orElse(core);
                notifyManagers(server, active, true);
            }
            if (result.status() == TerritoryClosureScanner.Status.UNLOADED) {
                QUEUE.schedule(key, now + UNLOADED_RETRY_TICKS);
            }
        }
    }

    public static void clear() {
        QUEUE.clear();
    }

    private static ServerLevel findLevel(MinecraftServer server, ResourceLocation dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().equals(dimension)) return level;
        }
        return null;
    }

    private static void bind(ServerLevel level, TerritorySavedData.CoreRecord core) {
        if (level.hasChunkAt(core.pos())
                && level.getBlockEntity(core.pos()) instanceof TerritoryCoreBlockEntity blockEntity) {
            blockEntity.bind(core);
        }
    }

    private static void notifyManagers(MinecraftServer server, TerritorySavedData.CoreRecord core,
                                       boolean active) {
        NationSavedData nations = NationSavedData.get(server);
        for (var player : server.getPlayerList().getPlayers()) {
            if (!nations.nationIdFor(player.getUUID()).filter(core.nationId()::equals).isPresent()
                    || !nations.can(player.getUUID(), S2Permission.MANAGE_TERRITORY)) continue;
            var body = net.minecraft.network.chat.Component.translatable(active
                            ? "message.moveearth_addtional.territory_core.resealed"
                            : "message.moveearth_addtional.territory_core.exposed",
                    core.pos().getX(), core.pos().getY(), core.pos().getZ());
            player.sendSystemMessage(active ? MoveEarthMessage.success(body) : MoveEarthMessage.warning(body));
        }
    }

    private record CoreKey(ResourceLocation dimension, BlockPos pos) {
        private CoreKey {
            pos = pos.immutable();
        }
    }
}

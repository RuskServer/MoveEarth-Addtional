package com.ruskserver.moveearth_addtional.s2.territory;

import com.ruskserver.moveearth_addtional.block.entity.TerritoryCoreBlockEntity;
import com.ruskserver.moveearth_addtional.network.S2C_TerritoryClosurePacket;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TerritoryClosureRecheckManager {
    public static final long RECHECK_DELAY_TICKS = 5L * 20L;
    private static final long UNLOADED_RETRY_TICKS = 10L * 20L;
    private static final int MAX_NEW_SCANS_PER_TICK = 2;
    private static final int MAX_CELLS_PER_TICK = 4_096;
    private static final int MAX_CELLS_PER_SCAN_SLICE = 1_024;
    private static final TerritoryRecheckQueue<CoreKey> QUEUE = new TerritoryRecheckQueue<>();
    private static final Map<CoreKey, TerritoryClosureScanner.Session> ACTIVE = new LinkedHashMap<>();
    private static final ArrayDeque<CoreKey> ACTIVE_ORDER = new ArrayDeque<>();
    private static final Map<CoreKey, List<ValidationRequest>> VALIDATION_REQUESTS = new LinkedHashMap<>();

    private TerritoryClosureRecheckManager() {
    }

    /** Queues a GUI validation without running the bounded flood fill on the packet-handling tick. */
    public static void requestValidation(net.minecraft.server.level.ServerPlayer player,
                                         BlockPos pos, int requestId) {
        CoreKey key = new CoreKey(player.level().dimension().location(), pos);
        UUID nationId = NationSavedData.get(player.server).nationIdFor(player.getUUID()).orElse(null);
        VALIDATION_REQUESTS.computeIfAbsent(key, ignored -> new java.util.ArrayList<>())
                .add(new ValidationRequest(player.getUUID(), nationId, requestId));
        if (!ACTIVE.containsKey(key)) {
            QUEUE.schedule(key, player.server.getTickCount());
        }
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
                if (!scheduled.add(core.id())) continue;
                if (core.state() == TerritorySavedData.CoreState.CONFIGURING) {
                    CoreKey key = new CoreKey(core.dimension(), core.pos());
                    if (ACTIVE.remove(key) != null || VALIDATION_REQUESTS.containsKey(key)) {
                        QUEUE.schedule(key, level.getServer().getTickCount() + RECHECK_DELAY_TICKS);
                    }
                    continue;
                }
                TerritorySavedData.CoreRecord queuedCore = core;
                if (expose && core.state() == TerritorySavedData.CoreState.ACTIVE) {
                    queuedCore = territories.updateState(core.nationId(), core.dimension(), core.pos(),
                            TerritorySavedData.CoreState.EXPOSED).orElse(core);
                    bind(level, queuedCore);
                    notifyManagers(level.getServer(), queuedCore, false);
                }
                CoreKey key = new CoreKey(queuedCore.dimension(), queuedCore.pos());
                ACTIVE.remove(key);
                QUEUE.schedule(key,
                        level.getServer().getTickCount() + RECHECK_DELAY_TICKS);
            }
        }
    }

    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        for (CoreKey key : QUEUE.pollDue(now, MAX_NEW_SCANS_PER_TICK)) {
            ServerLevel level = findLevel(server, key.dimension());
            if (level == null) {
                rejectValidationRequests(server, key, S2C_TerritoryClosurePacket.Status.NOT_FOUND);
                continue;
            }
            TerritorySavedData.CoreRecord core = TerritorySavedData.get(server)
                    .core(key.dimension(), key.pos()).orElse(null);
            if (core == null || !isRecheckable(core, key)) {
                rejectValidationRequests(server, key, S2C_TerritoryClosurePacket.Status.NOT_FOUND);
                continue;
            }
            ACTIVE.put(key, TerritoryClosureService.begin(level, key.pos()));
            ACTIVE_ORDER.addLast(key);
        }

        int remainingBudget = MAX_CELLS_PER_TICK;
        while (remainingBudget > 0 && !ACTIVE_ORDER.isEmpty()) {
            CoreKey key = ACTIVE_ORDER.removeFirst();
            TerritoryClosureScanner.Session session = ACTIVE.get(key);
            if (session == null) continue;
            ServerLevel level = findLevel(server, key.dimension());
            TerritorySavedData.CoreRecord core = level == null ? null : TerritorySavedData.get(server)
                    .core(key.dimension(), key.pos()).orElse(null);
            if (level == null || core == null || !isRecheckable(core, key)) {
                ACTIVE.remove(key);
                rejectValidationRequests(server, key, S2C_TerritoryClosurePacket.Status.NOT_FOUND);
                continue;
            }
            TerritoryClosureScanner.Progress progress = session.advance(
                    Math.min(MAX_CELLS_PER_SCAN_SLICE, remainingBudget));
            remainingBudget -= progress.examinedCells();
            if (!progress.complete()) {
                ACTIVE_ORDER.addLast(key);
                if (progress.examinedCells() <= 0) break;
                continue;
            }
            ACTIVE.remove(key);
            TerritoryClosureScanner.Result result = progress.result();
            TerritoryClosureService.applyResult(level, key.pos(), result);
            completeValidationRequests(server, level, key, result);
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
        ACTIVE.clear();
        ACTIVE_ORDER.clear();
        VALIDATION_REQUESTS.clear();
    }

    private static ServerLevel findLevel(MinecraftServer server, ResourceLocation dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().equals(dimension)) return level;
        }
        return null;
    }

    private static boolean isRecheckable(TerritorySavedData.CoreRecord core, CoreKey key) {
        return (core.state() == TerritorySavedData.CoreState.CONFIGURING
                && VALIDATION_REQUESTS.containsKey(key))
                || core.state() == TerritorySavedData.CoreState.ACTIVE
                || core.state() == TerritorySavedData.CoreState.EXPOSED;
    }

    private static void completeValidationRequests(MinecraftServer server, ServerLevel level,
                                                   CoreKey key, TerritoryClosureScanner.Result result) {
        List<ValidationRequest> requests = VALIDATION_REQUESTS.remove(key);
        if (requests == null) return;
        TerritorySavedData territories = TerritorySavedData.get(server);
        TerritorySavedData.CoreRecord core = territories.core(key.dimension(), key.pos()).orElse(null);
        for (ValidationRequest request : requests) {
            var player = server.getPlayerList().getPlayer(request.playerId());
            if (player == null) continue;
            NationSavedData nations = NationSavedData.get(server);
            UUID currentNation = nations.nationIdFor(player.getUUID()).orElse(null);
            if (core == null || currentNation == null || !currentNation.equals(request.nationId())
                    || !currentNation.equals(core.nationId())
                    || !nations.can(player.getUUID(), S2Permission.MANAGE_TERRITORY)) {
                PacketDistributor.sendToPlayer(player, S2C_TerritoryClosurePacket.rejected(
                        key.dimension(), key.pos(), request.requestId(),
                        S2C_TerritoryClosurePacket.Status.DENIED));
                continue;
            }
            PacketDistributor.sendToPlayer(player, S2C_TerritoryClosurePacket.scanned(
                    key.dimension(), key.pos(), request.requestId(), result, core.state(),
                    TerritoryClosureService.unreinforcedLeakBlocks(level, result)));
        }
    }

    private static void rejectValidationRequests(MinecraftServer server, CoreKey key,
                                                 S2C_TerritoryClosurePacket.Status status) {
        List<ValidationRequest> requests = VALIDATION_REQUESTS.remove(key);
        if (requests == null) return;
        for (ValidationRequest request : requests) {
            var player = server.getPlayerList().getPlayer(request.playerId());
            if (player != null) PacketDistributor.sendToPlayer(player,
                    S2C_TerritoryClosurePacket.rejected(key.dimension(), key.pos(),
                            request.requestId(), status));
        }
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

    private record ValidationRequest(UUID playerId, UUID nationId, int requestId) { }
}

package com.ruskserver.moveearth_addtional.handler;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Builds the wilderness spawn pool ahead of time, at no more than one chunk at once. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class RandomSpawnMappingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RandomSpawnMappingService.class);
    private static final int TICKET_DISTANCE = 0;
    private static final int WORK_TIMEOUT_TICKS = 60 * 20;
    private static final long AUTOMATIC_RECHECK_TICKS = 6L * 60L * 60L * 20L;
    private static final double MINIMUM_POINT_SPACING_SQR = 96.0D * 96.0D;
    private static final TicketType<UUID> MAPPING_TICKET = TicketType.create(
            "moveearth_spawn_mapping", Comparator.comparing(UUID::toString), WORK_TIMEOUT_TICKS + 40);

    private static Work activeWork;
    private static int nextStartTick;
    private static long nextAutomaticCheckGameTime;

    private RandomSpawnMappingService() { }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        RandomSpawnSavedData pool = RandomSpawnSavedData.get(server);
        RandomSpawnSavedData.MappingState mapping = pool.mapping();
        if (mapping == null) {
            release(level);
            maybeStartAutomaticMapping(server, level, pool);
            return;
        }
        if (!mapping.active() || mapping.paused()) {
            release(level);
            return;
        }

        if (pool.size() >= mapping.target()) {
            finish(level, pool, mapping, "target reached");
            return;
        }
        if (RandomSpawnMappingPolicy.exhausted(mapping.checked(), mapping.target())) {
            finish(level, pool, mapping, "candidate budget exhausted");
            return;
        }

        // Mapping is background maintenance. Any online player, and especially
        // a relocation search, takes priority over disk reads or generation.
        if (server.getPlayerCount() > 0 || RandomSpawnHandler.hasPendingSearches()) {
            release(level);
            return;
        }

        if (activeWork == null) {
            if (server.getTickCount() < nextStartTick) return;
            startNext(level, pool, mapping, server.getTickCount());
            return;
        }
        if (!activeWork.mappingId.equals(mapping.id())) {
            release(level);
            return;
        }
        if (server.getTickCount() - activeWork.startedTick >= WORK_TIMEOUT_TICKS) {
            completeCandidate(level, pool, mapping, false, "timed out");
            return;
        }

        if (activeWork.storageProbe != null && activeWork.storageProbe.isDone()) {
            Optional<CompoundTag> stored = Optional.empty();
            try {
                stored = activeWork.storageProbe.join();
            } catch (CompletionException exception) {
                LOGGER.debug("Could not inspect spawn-mapping chunk {}", activeWork.chunk, exception.getCause());
            }
            activeWork.storageProbe = null;
            boolean full = stored.filter(tag -> RandomSpawnPolicy.isStoredFullChunk(tag.getString("Status")))
                    .isPresent();
            if (!full && !mapping.generate()) {
                completeCandidate(level, pool, mapping, false, null);
                return;
            }
            activeWork.ticketActive = true;
            level.getChunkSource().addRegionTicket(
                    MAPPING_TICKET, activeWork.chunk, TICKET_DISTANCE, mapping.id());
        }

        if (!activeWork.ticketActive) return;
        LevelChunk chunk = level.getChunkSource().getChunkNow(activeWork.chunk.x, activeWork.chunk.z);
        if (chunk == null) return;

        BlockPos selected = RandomSpawnHandler.mappingSurfaces(level, chunk).stream()
                .filter(position -> withinRing(position, mapping))
                .filter(position -> !pool.hasNearby(position, MINIMUM_POINT_SPACING_SQR))
                .findFirst().orElse(null);
        if (selected != null) {
            pool.remember(selected, RandomSpawnSavedData.Source.MAPPED, level.getGameTime());
        }
        completeCandidate(level, pool, mapping, selected != null, null);
    }

    private static void startNext(ServerLevel level, RandomSpawnSavedData pool,
                                  RandomSpawnSavedData.MappingState mapping, int currentTick) {
        RandomSpawnMappingPolicy.Column column = RandomSpawnMappingPolicy.column(
                mapping.nextIndex(), mapping.centerX(), mapping.centerZ(),
                mapping.minimumRadius(), mapping.maximumRadius());
        BlockPos position = new BlockPos(column.x(), level.getSeaLevel(), column.z());
        if (!level.getWorldBorder().isWithinBounds(position)
                || !withinRing(position, mapping)
                || !RandomSpawnHandler.insideTerrainFootprint(position)
                || !RandomSpawnHandler.isAllowedTerritory(level, position, null)
                || pool.hasNearby(position, MINIMUM_POINT_SPACING_SQR)) {
            pool.updateMapping(mapping.advance(false));
            return;
        }

        ChunkPos chunk = new ChunkPos(column.x() >> 4, column.z() >> 4);
        activeWork = new Work(mapping.id(), chunk, currentTick);
        LevelChunk loaded = level.getChunkSource().getChunkNow(chunk.x, chunk.z);
        if (loaded != null) {
            activeWork.ticketActive = true;
            level.getChunkSource().addRegionTicket(MAPPING_TICKET, chunk, TICKET_DISTANCE, mapping.id());
            return;
        }
        activeWork.storageProbe = level.getChunkSource().chunkMap.read(chunk);
    }

    private static void completeCandidate(ServerLevel level, RandomSpawnSavedData pool,
                                          RandomSpawnSavedData.MappingState mapping,
                                          boolean accepted, String reason) {
        if (reason != null) LOGGER.debug("Spawn-mapping candidate {} {}", activeWork.chunk, reason);
        release(level);
        nextStartTick = level.getServer().getTickCount() + (mapping.generate() ? 100 : 1);
        pool.updateMapping(mapping.advance(accepted));
    }

    private static void finish(ServerLevel level, RandomSpawnSavedData pool,
                               RandomSpawnSavedData.MappingState mapping, String reason) {
        release(level);
        pool.clearMapping();
        LOGGER.info("Random-spawn mapping finished: {} (pool={}, checked={}, accepted={}, mode={}).",
                reason, pool.size(), mapping.checked(), mapping.accepted(),
                mapping.generate() ? "generate" : "existing-only");
    }

    public static Result start(MinecraftServer server, boolean generate, int target) {
        RandomSpawnSavedData pool = RandomSpawnSavedData.get(server);
        if (pool.mapping() != null) return new Result(false, "既に事前マッピングが存在します。", status(server));
        ServerLevel level = server.overworld();
        BlockPos center = level.getSharedSpawnPos();
        int minimum = (int) Math.ceil(RandomSpawnHandler.mappingMinRadius(level, center));
        int maximum = (int) Math.floor(RandomSpawnHandler.mappingMaxRadius(level, center));
        if (maximum <= minimum) {
            return new Result(false, "ワールド境界内に十分な探索範囲がありません。", status(server));
        }
        pruneInvalid(level, pool, center);
        if (pool.size() >= target) {
            return new Result(true, "安全地点プールは既に目標数を満たしています。", status(server));
        }
        pool.startMapping(generate, target, center, minimum, maximum);
        return new Result(true, generate
                ? "新規チャンク生成を許可して事前マッピングを予約しました。プレイヤー不在時に進行します。"
                : "生成済みチャンク限定で事前マッピングを予約しました。プレイヤー不在時に進行します。",
                status(server));
    }

    public static int recommendedTarget(MinecraftServer server) {
        return RandomSpawnHandler.recommendedPoolTarget(server.overworld());
    }

    public static Result pause(MinecraftServer server) {
        RandomSpawnSavedData pool = RandomSpawnSavedData.get(server);
        RandomSpawnSavedData.MappingState mapping = pool.mapping();
        if (mapping == null) return new Result(false, "事前マッピングはありません。", status(server));
        pool.updateMapping(mapping.withPaused(true));
        release(server.overworld());
        return new Result(true, "事前マッピングを一時停止しました。", status(server));
    }

    public static Result resume(MinecraftServer server) {
        RandomSpawnSavedData pool = RandomSpawnSavedData.get(server);
        RandomSpawnSavedData.MappingState mapping = pool.mapping();
        if (mapping == null) return new Result(false, "再開できる事前マッピングはありません。", status(server));
        pool.updateMapping(mapping.withPaused(false));
        return new Result(true, "事前マッピングを再開しました。", status(server));
    }

    public static Result stop(MinecraftServer server) {
        RandomSpawnSavedData pool = RandomSpawnSavedData.get(server);
        if (pool.mapping() == null) return new Result(false, "事前マッピングはありません。", status(server));
        release(server.overworld());
        pool.clearMapping();
        return new Result(true, "事前マッピングを停止しました。既存の安全地点は維持されます。", status(server));
    }

    public static Result prune(MinecraftServer server) {
        ServerLevel level = server.overworld();
        RandomSpawnSavedData pool = RandomSpawnSavedData.get(server);
        BlockPos center = level.getSharedSpawnPos();
        int removed = pruneInvalid(level, pool, center);
        return new Result(true, removed + "件の無効な安全地点を削除しました。", status(server));
    }

    private static int pruneInvalid(ServerLevel level, RandomSpawnSavedData pool, BlockPos center) {
        double minimumSquared = square(RandomSpawnHandler.mappingMinRadius(level, center));
        double maximumSquared = square(RandomSpawnHandler.mappingMaxRadius(level, center));
        return pool.removeIf(position -> {
            double dx = position.getX() - center.getX();
            double dz = position.getZ() - center.getZ();
            double distance = dx * dx + dz * dz;
            return !level.getWorldBorder().isWithinBounds(position)
                    || distance < minimumSquared || distance > maximumSquared
                    || !RandomSpawnHandler.insideTerrainFootprint(position)
                    || !RandomSpawnHandler.isAllowedTerritory(level, position, null);
        });
    }

    public static Status status(MinecraftServer server) {
        RandomSpawnSavedData pool = RandomSpawnSavedData.get(server);
        RandomSpawnSavedData.MappingState mapping = pool.mapping();
        int recommended = recommendedTarget(server);
        boolean waiting = server.getPlayerCount() > 0;
        if (mapping == null) return new Status(pool.size(), recommended,
                false, false, false, waiting, 0, 0, 0);
        return new Status(pool.size(), recommended, true, mapping.paused(), mapping.generate(),
                waiting, mapping.target(), mapping.checked(), mapping.accepted());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        release(event.getServer().overworld());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        activeWork = null;
        nextStartTick = 0;
        nextAutomaticCheckGameTime = 0L;
    }

    private static void release(ServerLevel level) {
        if (activeWork != null && activeWork.ticketActive) {
            level.getChunkSource().removeRegionTicket(
                    MAPPING_TICKET, activeWork.chunk, TICKET_DISTANCE, activeWork.mappingId);
        }
        activeWork = null;
    }

    private static double square(double value) { return value * value; }

    private static void maybeStartAutomaticMapping(MinecraftServer server, ServerLevel level,
                                                   RandomSpawnSavedData pool) {
        if (!server.isDedicatedServer() || server.getPlayerCount() > 0
                || level.getGameTime() < nextAutomaticCheckGameTime) return;
        nextAutomaticCheckGameTime = level.getGameTime() + AUTOMATIC_RECHECK_TICKS;
        BlockPos center = level.getSharedSpawnPos();
        pruneInvalid(level, pool, center);
        int target = RandomSpawnHandler.recommendedPoolTarget(level);
        int minimum = (int) Math.ceil(RandomSpawnHandler.mappingMinRadius(level, center));
        int maximum = (int) Math.floor(RandomSpawnHandler.mappingMaxRadius(level, center));
        if (target <= 0 || pool.size() >= target || maximum <= minimum) return;
        pool.startMapping(false, target, center, minimum, maximum);
        LOGGER.info("Automatically started existing-chunk spawn mapping while server is empty "
                        + "(pool={}, target={}, radius={}..{}).",
                pool.size(), target, minimum, maximum);
    }

    private static boolean withinRing(BlockPos position, RandomSpawnSavedData.MappingState mapping) {
        double dx = position.getX() - mapping.centerX();
        double dz = position.getZ() - mapping.centerZ();
        double distance = dx * dx + dz * dz;
        return distance >= square(mapping.minimumRadius()) && distance <= square(mapping.maximumRadius());
    }

    public record Result(boolean success, String message, Status status) { }

    public record Status(int poolSize, int recommendedTarget, boolean active, boolean paused,
                         boolean generate, boolean waitingForEmptyServer,
                         int target, int checked, int accepted) { }

    private static final class Work {
        private final UUID mappingId;
        private final ChunkPos chunk;
        private final int startedTick;
        private CompletableFuture<Optional<CompoundTag>> storageProbe;
        private boolean ticketActive;

        private Work(UUID mappingId, ChunkPos chunk, int startedTick) {
            this.mappingId = mappingId;
            this.chunk = chunk;
            this.startedTick = startedTick;
        }
    }
}

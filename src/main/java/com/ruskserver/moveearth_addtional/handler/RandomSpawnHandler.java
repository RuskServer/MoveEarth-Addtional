package com.ruskserver.moveearth_addtional.handler;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class RandomSpawnHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RandomSpawnHandler.class);
    private static final String NBT_KEY_SPAWNED = "MoveEarthRandomSpawned";
    private static final String NBT_KEY_LAST_POS = "MoveEarthLastRandomSpawn";
    private static final String NBT_KEY_LAST_DIMENSION = "MoveEarthLastRandomSpawnDimension";

    private static final int MIN_WORLD_SPAWN_RADIUS = 750;
    private static final int MAX_WORLD_SPAWN_RADIUS = 4_000;
    private static final int MIN_PLAYER_DISTANCE = 384;
    private static final int MIN_LAST_SPAWN_DISTANCE = 768;
    private static final int MAX_CANDIDATES = 24;
    private static final int MAX_CONCURRENT_CHUNK_LOADS = 2;
    private static final int SEARCH_TIMEOUT_TICKS = 20 * 20;
    // Distance 0 requests only a FULL target chunk. Distance 1 would promote the
    // target to BLOCK_TICKING and needlessly increase the generated region.
    private static final int RANDOM_SPAWN_TICKET_DISTANCE = 0;
    private static final int SPAWN_PROTECTION_TICKS = 10 * 20;
    private static final TicketType<UUID> RANDOM_SPAWN_TICKET = TicketType.create(
            "moveearth_random_spawn", Comparator.comparing(UUID::toString), SEARCH_TIMEOUT_TICKS + 40);
    private static final Map<UUID, SpawnSearch> PENDING_SEARCHES = new HashMap<>();

    private RandomSpawnHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag data = persistedData(player);
        if (data.getBoolean(NBT_KEY_SPAWNED)) return;
        if (hasPriorPlayerHistory(player)) {
            data.putBoolean(NBT_KEY_SPAWNED, true);
            LOGGER.info("Player {} predates random-spawn tracking. Marking random spawn as initialized without teleporting.",
                    player.getName().getString());
            return;
        }

        LOGGER.info("Player {} logged in for the first time. Starting a non-blocking random-spawn search.",
                player.getName().getString());
        beginRandomSpawnSearch(player, true);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getRespawnPosition() != null) return;

        LOGGER.info("Player {} respawned without a bed or anchor. Starting a non-blocking random-spawn search.",
                player.getName().getString());
        beginRandomSpawnSearch(player, false);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SpawnSearch search = PENDING_SEARCHES.remove(player.getUUID());
        if (search != null) releaseTicket(player.server.overworld(), player.getUUID(), search);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING_SEARCHES.isEmpty()) return;

        ServerLevel level = event.getServer().overworld();
        int currentTick = event.getServer().getTickCount();
        Iterator<Map.Entry<UUID, SpawnSearch>> iterator = PENDING_SEARCHES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, SpawnSearch> entry = iterator.next();
            UUID playerId = entry.getKey();
            SpawnSearch search = entry.getValue();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerId);
            if (player == null) {
                releaseTicket(level, playerId, search);
                iterator.remove();
                continue;
            }
            if (player.isRemoved()) continue;

            if (search.ticketActive) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(
                        search.requestedChunk.x, search.requestedChunk.z);
                if (chunk != null && evaluateLoadedChunk(player, level, playerId, search, chunk)) {
                    iterator.remove();
                    continue;
                }
            }

            if (search.startedTick >= 0 && currentTick >= search.startedTick + SEARCH_TIMEOUT_TICKS) {
                failSearch(player, level, playerId, search, "timed out");
                iterator.remove();
            }
        }

        int availableSlots = MAX_CONCURRENT_CHUNK_LOADS - activeTicketCount();
        if (availableSlots <= 0 || PENDING_SEARCHES.isEmpty()) return;

        iterator = PENDING_SEARCHES.entrySet().iterator();
        while (iterator.hasNext() && availableSlots > 0) {
            Map.Entry<UUID, SpawnSearch> entry = iterator.next();
            UUID playerId = entry.getKey();
            SpawnSearch search = entry.getValue();
            if (search.ticketActive) continue;

            ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerId);
            if (player == null) {
                iterator.remove();
                continue;
            }
            if (player.isRemoved()) continue;

            SpawnColumn next = search.nextRequest();
            if (next == null) {
                failSearch(player, level, playerId, search, "exhausted all candidates");
                iterator.remove();
                continue;
            }

            search.requestedColumn = next;
            search.requestedChunk = new ChunkPos(next.x >> 4, next.z >> 4);
            search.ticketActive = true;
            if (search.startedTick < 0) search.startedTick = currentTick;
            level.getChunkSource().addRegionTicket(
                    RANDOM_SPAWN_TICKET, search.requestedChunk, RANDOM_SPAWN_TICKET_DISTANCE, playerId);
            availableSlots--;
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ServerLevel level = event.getServer().overworld();
        PENDING_SEARCHES.forEach((playerId, search) -> releaseTicket(level, playerId, search));
        PENDING_SEARCHES.clear();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING_SEARCHES.clear();
    }

    private static void beginRandomSpawnSearch(ServerPlayer player, boolean markInitializedOnSuccess) {
        ServerLevel level = player.server.overworld();
        SpawnSearch previous = PENDING_SEARCHES.remove(player.getUUID());
        if (previous != null) releaseTicket(level, player.getUUID(), previous);

        CompoundTag data = persistedData(player);
        BlockPos worldSpawn = level.getSharedSpawnPos();
        BlockPos lastSpawn = null;
        if (data.contains(NBT_KEY_LAST_POS)
                && level.dimension().location().toString().equals(data.getString(NBT_KEY_LAST_DIMENSION))) {
            lastSpawn = BlockPos.of(data.getLong(NBT_KEY_LAST_POS));
        }

        RandomSource random = player.getRandom();
        double minRadius = effectiveMinRadius(level, worldSpawn);
        double maxRadius = effectiveMaxRadius(level, worldSpawn);
        List<SpawnColumn> columns = new ArrayList<>(MAX_CANDIDATES);
        int rejectedByBorder = 0;
        for (int attempt = 0; attempt < MAX_CANDIDATES; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = Math.sqrt(random.nextDouble()
                    * (maxRadius * maxRadius - minRadius * minRadius) + minRadius * minRadius);
            int x = MthFloor(worldSpawn.getX() + Math.cos(angle) * distance);
            int z = MthFloor(worldSpawn.getZ() + Math.sin(angle) * distance);
            BlockPos column = new BlockPos(x, worldSpawn.getY(), z);
            if (!level.getWorldBorder().isWithinBounds(column)) {
                rejectedByBorder++;
                continue;
            }
            columns.add(new SpawnColumn(x, z, random.nextDouble() * 10_000.0D));
        }

        if (columns.isEmpty()) {
            LOGGER.warn("Could not queue a random-spawn search for {} because every candidate was outside the world border.",
                    player.getName().getString());
            player.displayClientMessage(Component.literal(
                    "安全なランダムスポーン地点を選べなかったため、通常のスポーン地点を使用します。"), true);
            return;
        }

        PENDING_SEARCHES.put(player.getUUID(), new SpawnSearch(
                columns, lastSpawn, random.nextFloat() * 360.0F - 180.0F,
                markInitializedOnSuccess, worldSpawn, minRadius, maxRadius, rejectedByBorder));
        player.displayClientMessage(Component.literal("安全なランダムスポーン地点を探索しています…"), true);
    }

    private static boolean evaluateLoadedChunk(ServerPlayer player, ServerLevel level, UUID playerId,
                                               SpawnSearch search, LevelChunk chunk) {
        SpawnColumn column = search.requestedColumn;
        BlockPos spawn = findSafeSurface(level, chunk, column.x, column.z);

        if (search.loadingFallback) {
            boolean teleported = spawn != null && applyRandomTeleport(player, level, spawn, search.yaw);
            releaseTicket(level, playerId, search);
            if (teleported) {
                finishSuccessfulSearch(player, search);
            } else {
                LOGGER.warn("The fallback random spawn for {} was no longer safe; keeping vanilla spawn.",
                        player.getName().getString());
                player.displayClientMessage(Component.literal(
                        "安全なランダムスポーン地点を確保できなかったため、通常のスポーン地点を使用します。"), true);
            }
            return true;
        }

        if (spawn == null) {
            search.rejectedBySurface++;
            releaseTicket(level, playerId, search);
            return false;
        }

        List<ServerPlayer> nearbyThreats = eligibleOtherPlayers(player, level);
        double playerDistance = minimumDistanceSqr(spawn, nearbyThreats);
        double lastDistance = search.lastSpawn == null
                ? Double.POSITIVE_INFINITY : horizontalDistanceSqr(spawn, search.lastSpawn);
        double score = RandomSpawnPolicy.score(
                playerDistance, lastDistance, column.tieBreaker, square(MAX_WORLD_SPAWN_RADIUS));
        if (search.safestFallback == null || score > search.safestFallback.score) {
            search.safestFallback = new SpawnCandidate(spawn, score, column);
        }

        if (RandomSpawnPolicy.meetsDistanceRequirements(
                playerDistance, lastDistance,
                square(MIN_PLAYER_DISTANCE), square(MIN_LAST_SPAWN_DISTANCE))) {
            boolean teleported = applyRandomTeleport(player, level, spawn, search.yaw);
            releaseTicket(level, playerId, search);
            if (teleported) {
                finishSuccessfulSearch(player, search);
                return true;
            }
        } else {
            releaseTicket(level, playerId, search);
        }
        return false;
    }

    private static void finishSuccessfulSearch(ServerPlayer player, SpawnSearch search) {
        if (search.markInitializedOnSuccess) persistedData(player).putBoolean(NBT_KEY_SPAWNED, true);
    }

    private static void failSearch(ServerPlayer player, ServerLevel level, UUID playerId,
                                   SpawnSearch search, String reason) {
        releaseTicket(level, playerId, search);
        LOGGER.warn("Random-spawn search for {} {} after evaluating {} of {} queued candidates in {} "
                        + "(worldSpawn={}, radius={}..{}, borderRejected={}, surfaceRejected={}, "
                        + "border=[{}..{}, {}..{}]); keeping vanilla spawn.",
                player.getName().getString(), reason, search.nextColumnIndex, search.columns.size(),
                level.dimension().location(), search.worldSpawn,
                Math.round(search.minRadius), Math.round(search.maxRadius),
                search.rejectedByBorder, search.rejectedBySurface,
                Math.round(level.getWorldBorder().getMinX()), Math.round(level.getWorldBorder().getMaxX()),
                Math.round(level.getWorldBorder().getMinZ()), Math.round(level.getWorldBorder().getMaxZ()));
        player.displayClientMessage(Component.literal(
                "安全なランダムスポーン地点を確保できなかったため、通常のスポーン地点を使用します。"), true);
    }

    private static void releaseTicket(ServerLevel level, UUID playerId, SpawnSearch search) {
        if (!search.ticketActive || search.requestedChunk == null) return;
        level.getChunkSource().removeRegionTicket(
                RANDOM_SPAWN_TICKET, search.requestedChunk, RANDOM_SPAWN_TICKET_DISTANCE, playerId);
        search.ticketActive = false;
        search.requestedChunk = null;
        search.requestedColumn = null;
    }

    private static int activeTicketCount() {
        int count = 0;
        for (SpawnSearch search : PENDING_SEARCHES.values()) {
            if (search.ticketActive) count++;
        }
        return count;
    }

    private static boolean applyRandomTeleport(ServerPlayer player, ServerLevel level, BlockPos spawn, float yaw) {
        double targetX = spawn.getX() + 0.5D;
        double targetY = spawn.getY();
        double targetZ = spawn.getZ() + 0.5D;
        player.teleportTo(level, targetX, targetY, targetZ, yaw, 0.0F);

        double dx = player.getX() - targetX;
        double dy = player.getY() - targetY;
        double dz = player.getZ() - targetZ;
        if (!player.serverLevel().dimension().equals(level.dimension())
                || dx * dx + dy * dy + dz * dz > 1.0D) return false;

        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                SPAWN_PROTECTION_TICKS, 4, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,
                SPAWN_PROTECTION_TICKS, 0, false, true, true));
        player.displayClientMessage(Component.literal(
                "ランダム地点へリスポーンしました。10秒間の保護が付与されています。"), true);

        CompoundTag data = persistedData(player);
        data.putLong(NBT_KEY_LAST_POS, spawn.asLong());
        data.putString(NBT_KEY_LAST_DIMENSION, level.dimension().location().toString());
        List<ServerPlayer> nearbyThreats = eligibleOtherPlayers(player, level);
        LOGGER.info("Random-spawned {} at {}, {}, {}; nearest player distance={} blocks.",
                player.getName().getString(), spawn.getX(), spawn.getY(), spawn.getZ(),
                nearbyThreats.isEmpty() ? "none" : Math.round(Math.sqrt(minimumDistanceSqr(spawn, nearbyThreats))));
        return true;
    }

    private static CompoundTag persistedData(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG)) {
            root.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
    }

    private static boolean hasPriorPlayerHistory(ServerPlayer player) {
        Path playerDataDirectory = player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        String fileName = player.getStringUUID() + ".dat";
        if (Files.isRegularFile(playerDataDirectory.resolve(fileName))
                || Files.isRegularFile(playerDataDirectory.resolve(fileName + "_old"))) return true;
        return player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) > 0
                || player.getStats().getValue(Stats.CUSTOM.get(Stats.LEAVE_GAME)) > 0;
    }

    private static boolean isSafe(ServerLevel level, BlockPos spawn) {
        BlockPos floorPos = spawn.below();
        BlockState floor = level.getBlockState(floorPos);
        BlockState feet = level.getBlockState(spawn);
        BlockState head = level.getBlockState(spawn.above());
        if (!level.getFluidState(spawn).isEmpty() || !level.getFluidState(spawn.above()).isEmpty()) return false;
        if (floor.getCollisionShape(level, floorPos).isEmpty()) return false;
        if (!feet.getCollisionShape(level, spawn).isEmpty()
                || !head.getCollisionShape(level, spawn.above()).isEmpty()) return false;
        return !isDangerous(floor) && !isDangerous(feet) && !isDangerous(head);
    }

    private static BlockPos findSafeSurface(ServerLevel level, LevelChunk chunk, int x, int z) {
        int localX = x & 15;
        int localZ = z & 15;
        int motionBlockingY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, localX, localZ) + 1;
        int worldSurfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ) + 1;
        BlockPos result = findSafeNear(level, x, z, motionBlockingY);
        if (result != null || worldSurfaceY == motionBlockingY) return result;
        return findSafeNear(level, x, z, worldSurfaceY);
    }

    private static BlockPos findSafeNear(ServerLevel level, int x, int z, int startY) {
        int minimumY = level.getMinBuildHeight() + 1;
        int maximumY = level.getMaxBuildHeight() - 2;
        int clampedY = Math.max(minimumY, Math.min(maximumY, startY));
        BlockPos direct = new BlockPos(x, clampedY, z);
        if (isSafe(level, direct)) return direct;

        for (int offset = 1; offset <= 12; offset++) {
            int belowY = clampedY - offset;
            if (belowY >= minimumY) {
                BlockPos below = new BlockPos(x, belowY, z);
                if (isSafe(level, below)) return below;
            }
            if (offset <= 4) {
                int aboveY = clampedY + offset;
                if (aboveY <= maximumY) {
                    BlockPos above = new BlockPos(x, aboveY, z);
                    if (isSafe(level, above)) return above;
                }
            }
        }
        return null;
    }

    private static boolean isDangerous(BlockState state) {
        return state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE);
    }

    private static double effectiveMinRadius(ServerLevel level, BlockPos center) {
        double maximum = effectiveMaxRadius(level, center);
        if (maximum < 64.0D) return 0.0D;
        return Math.min(MIN_WORLD_SPAWN_RADIUS, maximum * 0.4D);
    }

    private static double effectiveMaxRadius(ServerLevel level, BlockPos center) {
        double borderDistance = Math.min(
                Math.min(center.getX() - level.getWorldBorder().getMinX(),
                        level.getWorldBorder().getMaxX() - center.getX()),
                Math.min(center.getZ() - level.getWorldBorder().getMinZ(),
                        level.getWorldBorder().getMaxZ() - center.getZ()));
        return Math.max(0.0D, Math.min(MAX_WORLD_SPAWN_RADIUS, borderDistance - 16.0D));
    }

    private static List<ServerPlayer> eligibleOtherPlayers(ServerPlayer player, ServerLevel level) {
        return player.server.getPlayerList().getPlayers().stream()
                .filter(other -> other != player)
                .filter(other -> other.level().dimension().equals(level.dimension()))
                .filter(other -> !other.isSpectator())
                .toList();
    }

    private static double minimumDistanceSqr(BlockPos spawn, List<ServerPlayer> players) {
        double minimum = Double.POSITIVE_INFINITY;
        for (ServerPlayer other : players) {
            double dx = spawn.getX() + 0.5D - other.getX();
            double dz = spawn.getZ() + 0.5D - other.getZ();
            minimum = Math.min(minimum, dx * dx + dz * dz);
        }
        return minimum;
    }

    private static double horizontalDistanceSqr(BlockPos first, BlockPos second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    private static double square(double value) {
        return value * value;
    }

    private static int MthFloor(double value) {
        return (int) Math.floor(value);
    }

    private record SpawnColumn(int x, int z, double tieBreaker) {
    }

    private record SpawnCandidate(BlockPos position, double score, SpawnColumn column) {
    }

    private static final class SpawnSearch {
        private final List<SpawnColumn> columns;
        private final BlockPos lastSpawn;
        private final float yaw;
        private final boolean markInitializedOnSuccess;
        private final BlockPos worldSpawn;
        private final double minRadius;
        private final double maxRadius;
        private final int rejectedByBorder;
        private int rejectedBySurface;
        private int nextColumnIndex;
        private int startedTick = -1;
        private SpawnCandidate safestFallback;
        private boolean fallbackRequested;
        private boolean loadingFallback;
        private boolean ticketActive;
        private SpawnColumn requestedColumn;
        private ChunkPos requestedChunk;

        private SpawnSearch(List<SpawnColumn> columns, BlockPos lastSpawn, float yaw,
                            boolean markInitializedOnSuccess, BlockPos worldSpawn,
                            double minRadius, double maxRadius, int rejectedByBorder) {
            this.columns = columns;
            this.lastSpawn = lastSpawn;
            this.yaw = yaw;
            this.markInitializedOnSuccess = markInitializedOnSuccess;
            this.worldSpawn = worldSpawn;
            this.minRadius = minRadius;
            this.maxRadius = maxRadius;
            this.rejectedByBorder = rejectedByBorder;
        }

        private SpawnColumn nextRequest() {
            loadingFallback = false;
            if (nextColumnIndex < columns.size()) return columns.get(nextColumnIndex++);
            if (!fallbackRequested && safestFallback != null) {
                fallbackRequested = true;
                loadingFallback = true;
                return safestFallback.column;
            }
            return null;
        }
    }
}

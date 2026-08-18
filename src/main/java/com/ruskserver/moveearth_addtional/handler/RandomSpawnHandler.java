package com.ruskserver.moveearth_addtional.handler;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final int MAX_TRIES = 96;
    private static final int SPAWN_PROTECTION_TICKS = 10 * 20;
    private static final int PENDING_TELEPORT_TIMEOUT_TICKS = 40;
    private static final Map<UUID, PendingSpawn> PENDING_SPAWNS = new HashMap<>();

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

        LOGGER.info("Player {} logged in for the first time. Selecting a spread random spawn.",
                player.getName().getString());
        if (randomTeleport(player, data, false)) data.putBoolean(NBT_KEY_SPAWNED, true);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getRespawnPosition() != null) return;

        LOGGER.info("Player {} respawned without a bed or anchor. Selecting a spread random spawn.",
                player.getName().getString());
        randomTeleport(player, persistedData(player), true);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING_SPAWNS.isEmpty()) return;

        int currentTick = event.getServer().getTickCount();
        Iterator<Map.Entry<UUID, PendingSpawn>> iterator = PENDING_SPAWNS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingSpawn> entry = iterator.next();
            PendingSpawn pending = entry.getValue();
            if (currentTick < pending.executeAfterTick) continue;

            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }
            if (player.isRemoved()) {
                if (currentTick >= pending.expiresAtTick) {
                    LOGGER.warn("Random spawn for {} expired because the current player entity remained removed.",
                            player.getName().getString());
                    iterator.remove();
                }
                continue;
            }

            ServerLevel level = event.getServer().overworld();
            if (applyRandomTeleport(player, level, pending.position, pending.yaw)) {
                iterator.remove();
            } else if (currentTick >= pending.expiresAtTick) {
                LOGGER.warn("Random spawn teleport for {} was not applied before its timeout; keeping the current position.",
                        player.getName().getString());
                iterator.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING_SPAWNS.clear();
    }

    private static boolean randomTeleport(ServerPlayer player, CompoundTag data, boolean deferUntilNextTick) {
        // A vanilla respawn without a valid bed/anchor belongs in the Overworld.  Some
        // respawn-related mods leave player.serverLevel() pointing at the death
        // dimension while this event is being handled, which made every candidate
        // fail in void/custom dimensions.
        ServerLevel level = player.server.overworld();
        BlockPos worldSpawn = level.getSharedSpawnPos();
        RandomSource random = player.getRandom();
        List<ServerPlayer> nearbyThreats = player.server.getPlayerList().getPlayers().stream()
                .filter(other -> other != player)
                .filter(other -> other.level().dimension().equals(level.dimension()))
                .filter(other -> !other.isSpectator())
                .toList();

        BlockPos lastSpawn = null;
        if (data.contains(NBT_KEY_LAST_POS)
                && level.dimension().location().toString().equals(data.getString(NBT_KEY_LAST_DIMENSION))) {
            lastSpawn = BlockPos.of(data.getLong(NBT_KEY_LAST_POS));
        }

        SpawnCandidate best = null;
        SpawnCandidate safestFallback = null;
        double minRadius = effectiveMinRadius(level, worldSpawn);
        double maxRadius = effectiveMaxRadius(level, worldSpawn);
        int rejectedByBorder = 0;
        int rejectedBySurface = 0;
        for (int attempt = 0; attempt < MAX_TRIES; attempt++) {
            // Area-uniform annulus selection avoids clustering near either edge of the range.
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
            BlockPos spawn = findSafeSurface(level, x, z);
            if (spawn == null) {
                rejectedBySurface++;
                continue;
            }
            double playerDistance = minimumDistanceSqr(spawn, nearbyThreats);
            double lastDistance = lastSpawn == null ? Double.POSITIVE_INFINITY : horizontalDistanceSqr(spawn, lastSpawn);
            double score = Math.min(playerDistance, square(MAX_WORLD_SPAWN_RADIUS))
                    + Math.min(lastDistance, square(MAX_WORLD_SPAWN_RADIUS)) * 0.35D
                    + random.nextDouble() * 10_000.0D;
            if (safestFallback == null || score > safestFallback.score) {
                safestFallback = new SpawnCandidate(spawn, score);
            }
            if (playerDistance < square(MIN_PLAYER_DISTANCE) || lastDistance < square(MIN_LAST_SPAWN_DISTANCE)) continue;
            best = new SpawnCandidate(spawn, score);
            // Loading/generating dozens of random chunks on the server thread is
            // much more expensive than any benefit gained by ranking every valid
            // location. The first candidate satisfying all distance constraints is
            // already random and safe.
            break;
        }

        if (best == null) best = safestFallback;
        if (best == null) {
            LOGGER.warn("Could not find any safe random spawn for {} after {} attempts in {} "
                            + "(worldSpawn={}, radius={}..{}, borderRejected={}, surfaceRejected={}, "
                            + "border=[{}..{}, {}..{}]); keeping vanilla spawn.",
                    player.getName().getString(), MAX_TRIES, level.dimension().location(), worldSpawn,
                    Math.round(minRadius), Math.round(maxRadius), rejectedByBorder, rejectedBySurface,
                    Math.round(level.getWorldBorder().getMinX()), Math.round(level.getWorldBorder().getMaxX()),
                    Math.round(level.getWorldBorder().getMinZ()), Math.round(level.getWorldBorder().getMaxZ()));
            return false;
        }

        BlockPos spawn = best.position;
        float yaw = random.nextFloat() * 360.0F - 180.0F;
        if (deferUntilNextTick) {
            int currentTick = player.server.getTickCount();
            PENDING_SPAWNS.put(player.getUUID(), new PendingSpawn(
                    spawn, yaw, currentTick + 1, currentTick + PENDING_TELEPORT_TIMEOUT_TICKS));
            LOGGER.info("Scheduled random spawn for {} at {}, {}, {} after respawn finalization.",
                    player.getName().getString(), spawn.getX(), spawn.getY(), spawn.getZ());
            return true;
        }
        return applyRandomTeleport(player, level, spawn, yaw);
    }

    private static boolean applyRandomTeleport(ServerPlayer player, ServerLevel level, BlockPos spawn, float yaw) {
        double targetX = spawn.getX() + 0.5D;
        double targetY = spawn.getY();
        double targetZ = spawn.getZ() + 0.5D;
        player.teleportTo(level, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, yaw, 0.0F);

        double dx = player.getX() - targetX;
        double dy = player.getY() - targetY;
        double dz = player.getZ() - targetZ;
        if (!player.serverLevel().dimension().equals(level.dimension())
                || dx * dx + dy * dy + dz * dz > 1.0D) {
            return false;
        }

        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                SPAWN_PROTECTION_TICKS, 4, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,
                SPAWN_PROTECTION_TICKS, 0, false, true, true));
        player.displayClientMessage(Component.literal("ランダム地点へリスポーンしました。10秒間の保護が付与されています。"), true);

        CompoundTag data = persistedData(player);
        data.putLong(NBT_KEY_LAST_POS, spawn.asLong());
        data.putString(NBT_KEY_LAST_DIMENSION, level.dimension().location().toString());
        List<ServerPlayer> nearbyThreats = player.server.getPlayerList().getPlayers().stream()
                .filter(other -> other != player)
                .filter(other -> other.level().dimension().equals(level.dimension()))
                .filter(other -> !other.isSpectator())
                .toList();
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
                || Files.isRegularFile(playerDataDirectory.resolve(fileName + "_old"))) {
            return true;
        }

        return player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) > 0
                || player.getStats().getValue(Stats.CUSTOM.get(Stats.LEAVE_GAME)) > 0;
    }

    private static boolean isSafe(ServerLevel level, BlockPos spawn) {
        BlockPos floorPos = spawn.below();
        BlockState floor = level.getBlockState(floorPos);
        BlockState feet = level.getBlockState(spawn);
        BlockState head = level.getBlockState(spawn.above());
        if (!level.getFluidState(spawn).isEmpty()
                || !level.getFluidState(spawn.above()).isEmpty()) return false;
        // Requiring a fully sturdy upper face rejects otherwise valid terrain such
        // as snow layers, slabs and many modded surface blocks. A non-empty floor
        // collision shape is sufficient as long as the player's two blocks are
        // unobstructed and neither the floor nor the air space is hazardous.
        if (floor.getCollisionShape(level, floorPos).isEmpty()) return false;
        if (!feet.getCollisionShape(level, spawn).isEmpty()
                || !head.getCollisionShape(level, spawn.above()).isEmpty()) return false;
        return !isDangerous(floor) && !isDangerous(feet) && !isDangerous(head);
    }

    private static BlockPos findSafeSurface(ServerLevel level, int x, int z) {
        // Level#getHeight does not load a missing chunk. For an unloaded random
        // coordinate it returns the world's minimum build height, causing every
        // safety check to fail. Load the target chunk explicitly and query its
        // heightmaps using local coordinates.
        LevelChunk chunk = level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
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

        // Heightmaps can point at foliage, snow layers, modded decorations, or a
        // low ceiling. Check the immediate surface neighbourhood before rejecting
        // the entire X/Z column. The downward-first order avoids floating spawns.
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
        return state.is(Blocks.LAVA)
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE);
    }

    private static double effectiveMinRadius(ServerLevel level, BlockPos center) {
        double maximum = effectiveMaxRadius(level, center);
        if (maximum < 64.0D) return 0.0D;
        return Math.min(MIN_WORLD_SPAWN_RADIUS, maximum * 0.4D);
    }

    private static double effectiveMaxRadius(ServerLevel level, BlockPos center) {
        double borderDistance = Math.min(
                Math.min(center.getX() - level.getWorldBorder().getMinX(), level.getWorldBorder().getMaxX() - center.getX()),
                Math.min(center.getZ() - level.getWorldBorder().getMinZ(), level.getWorldBorder().getMaxZ() - center.getZ()));
        return Math.max(0.0D, Math.min(MAX_WORLD_SPAWN_RADIUS, borderDistance - 16.0D));
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

    private record SpawnCandidate(BlockPos position, double score) {
    }

    private record PendingSpawn(BlockPos position, float yaw, int executeAfterTick, int expiresAtTick) {
    }
}

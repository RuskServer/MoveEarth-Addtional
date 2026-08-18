package com.ruskserver.moveearth_addtional.territory.create;

import com.ruskserver.moveearth_addtional.territory.data.TerritoryCoreSavedData;
import com.ruskserver.moveearth_addtional.territory.domain.TerritoryCore;
import com.ruskserver.moveearth_addtional.territory.domain.TerritoryOwnerId;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CreateStressScanner {
    private static final ResourceLocation CREATIVE_MOTOR =
            ResourceLocation.fromNamespaceAndPath("create", "creative_motor");
    private static final double DISTANCE_EPSILON = 1.0E-6D;

    private CreateStressScanner() {
    }

    public static void refresh(MinecraftServer server) {
        int scanRadius = TerritoryCreateConfig.SCAN_RADIUS.get();
        Collection<TerritoryCore> allCores = TerritoryCoreSavedData.get(server).allCores();
        Map<TerritoryOwnerId, MutableSnapshot> totals = new HashMap<>();
        allCores.stream().filter(TerritoryCore::active)
                .forEach(core -> totals.computeIfAbsent(core.ownerId(), ignored -> new MutableSnapshot()));

        for (ServerLevel level : server.getAllLevels()) {
            String dimensionId = level.dimension().location().toString();
            List<TerritoryCore> cores = allCores.stream()
                    .filter(TerritoryCore::active)
                    .filter(core -> core.position().dimensionId().equals(dimensionId))
                    .toList();
            if (!cores.isEmpty()) {
                scanLevel(level, cores, totals, scanRadius);
            }
        }

        long gameTime = server.overworld().getGameTime();
        Map<TerritoryOwnerId, CreateStressSnapshot> snapshots = new HashMap<>();
        totals.forEach((ownerId, total) -> snapshots.put(ownerId, total.freeze(gameTime)));
        CreateStressCache.replace(server, snapshots);
    }

    private static void scanLevel(ServerLevel level, List<TerritoryCore> cores,
                                  Map<TerritoryOwnerId, MutableSnapshot> totals, int scanRadius) {
        Set<Long> chunksToScan = chunksAround(cores, scanRadius);
        Map<KineticNetwork, List<OwnedSource>> sourcesByNetwork = new IdentityHashMap<>();

        for (long packedChunk : chunksToScan) {
            int chunkX = ChunkPos.getX(packedChunk);
            int chunkZ = ChunkPos.getZ(packedChunk);
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (chunk == null) {
                continue;
            }
            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                if (!(blockEntity instanceof KineticBlockEntity source)
                        || !source.isSource()
                        || !source.hasNetwork()
                        || source.getGeneratedSpeed() == 0.0F
                        || isCreativeMotor(source)) {
                    continue;
                }

                TerritoryOwnerId ownerId = nearestOwner(source.getBlockPos(), cores, scanRadius);
                if (ownerId == null) {
                    continue;
                }

                KineticNetwork network = source.getOrCreateNetwork();
                if (!network.sources.containsKey(source)) {
                    continue;
                }
                double capacity = network.getActualCapacityOf(source);
                if (!Double.isFinite(capacity) || capacity <= 0.0D) {
                    continue;
                }
                sourcesByNetwork.computeIfAbsent(network, ignored -> new ArrayList<>())
                        .add(new OwnedSource(ownerId, capacity));
            }
        }

        for (Map.Entry<KineticNetwork, List<OwnedSource>> entry : sourcesByNetwork.entrySet()) {
            KineticNetwork network = entry.getKey();
            double capacity = network.sources.keySet().stream()
                    .filter(CreateStressScanner::isStillLoaded)
                    .mapToDouble(network::getActualCapacityOf)
                    .sum();
            double stress = network.members.keySet().stream()
                    .filter(CreateStressScanner::isStillLoaded)
                    .mapToDouble(network::getActualStressOf)
                    .sum();
            double utilization = CreateStressBalance.utilization(stress, capacity);
            Set<TerritoryOwnerId> ownersInNetwork = new HashSet<>();

            for (OwnedSource source : entry.getValue()) {
                MutableSnapshot total = totals.computeIfAbsent(source.ownerId(), ignored -> new MutableSnapshot());
                total.generatedCapacity += source.capacity();
                total.usedStress += source.capacity() * utilization;
                total.sourceCount++;
                ownersInNetwork.add(source.ownerId());
            }
            ownersInNetwork.forEach(ownerId -> totals.get(ownerId).networkCount++);
        }
    }

    private static Set<Long> chunksAround(List<TerritoryCore> cores, int scanRadius) {
        int chunkRadius = (scanRadius + 15) / 16;
        Set<Long> chunks = new HashSet<>();
        for (TerritoryCore core : cores) {
            int centerX = ((int) Math.floor(core.position().x())) >> 4;
            int centerZ = ((int) Math.floor(core.position().z())) >> 4;
            for (int x = centerX - chunkRadius; x <= centerX + chunkRadius; x++) {
                for (int z = centerZ - chunkRadius; z <= centerZ + chunkRadius; z++) {
                    chunks.add(ChunkPos.asLong(x, z));
                }
            }
        }
        return chunks;
    }

    private static TerritoryOwnerId nearestOwner(BlockPos sourcePos, List<TerritoryCore> cores, int scanRadius) {
        double bestDistanceSquared = scanRadius * (double) scanRadius;
        TerritoryOwnerId bestOwner = null;
        boolean contestedTie = false;

        for (TerritoryCore core : cores) {
            double dx = sourcePos.getX() + 0.5D - core.position().x();
            double dz = sourcePos.getZ() + 0.5D - core.position().z();
            double distanceSquared = dx * dx + dz * dz;
            if (distanceSquared > bestDistanceSquared + DISTANCE_EPSILON) {
                continue;
            }
            if (bestOwner == null || distanceSquared < bestDistanceSquared - DISTANCE_EPSILON) {
                bestDistanceSquared = distanceSquared;
                bestOwner = core.ownerId();
                contestedTie = false;
            } else if (!bestOwner.equals(core.ownerId())) {
                contestedTie = true;
            }
        }
        return contestedTie ? null : bestOwner;
    }

    private static boolean isCreativeMotor(KineticBlockEntity source) {
        return CREATIVE_MOTOR.equals(BuiltInRegistries.BLOCK.getKey(source.getBlockState().getBlock()));
    }

    private static boolean isStillLoaded(KineticBlockEntity blockEntity) {
        return blockEntity.getLevel() != null
                && blockEntity.getLevel().getBlockEntity(blockEntity.getBlockPos()) == blockEntity;
    }

    private record OwnedSource(TerritoryOwnerId ownerId, double capacity) {
    }

    private static final class MutableSnapshot {
        private double generatedCapacity;
        private double usedStress;
        private int sourceCount;
        private int networkCount;

        private CreateStressSnapshot freeze(long gameTime) {
            return new CreateStressSnapshot(
                    generatedCapacity,
                    usedStress,
                    CreateStressBalance.industrialScore(
                            usedStress,
                            TerritoryCreateConfig.MAX_COUNTED_STRESS.get(),
                            TerritoryCreateConfig.SCORE_SCALE.get()),
                    sourceCount,
                    networkCount,
                    gameTime
            );
        }
    }
}

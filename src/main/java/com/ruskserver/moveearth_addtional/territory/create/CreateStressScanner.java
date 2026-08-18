package com.ruskserver.moveearth_addtional.territory.create;

import com.ruskserver.moveearth_addtional.block.entity.TerritoryCoreBlockEntity;
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
        Map<KineticNetwork, NetworkMetrics> metricsByNetwork = new IdentityHashMap<>();

        collectDirectCoreStress(level, cores, totals, metricsByNetwork);

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
            NetworkMetrics metrics = metricsByNetwork.computeIfAbsent(network, NetworkMetrics::read);
            double coreStress = network.members.keySet().stream()
                    .filter(TerritoryCoreBlockEntity.class::isInstance)
                    .filter(CreateStressScanner::isStillLoaded)
                    .mapToDouble(network::getActualStressOf)
                    .sum();
            double factoryStress = Math.max(0.0D, metrics.stress() - coreStress);
            double utilization = metrics.overloaded()
                    ? 0.0D
                    : CreateStressBalance.utilization(factoryStress, metrics.capacity());

            for (OwnedSource source : entry.getValue()) {
                MutableSnapshot total = totals.computeIfAbsent(source.ownerId(), ignored -> new MutableSnapshot());
                total.generatedCapacity += source.capacity();
                total.usedStress += source.capacity() * utilization;
                total.sourceCount++;
                total.networks.add(network);
            }
        }
    }

    private static void collectDirectCoreStress(ServerLevel level, List<TerritoryCore> cores,
                                                Map<TerritoryOwnerId, MutableSnapshot> totals,
                                                Map<KineticNetwork, NetworkMetrics> metricsByNetwork) {
        for (TerritoryCore core : cores) {
            BlockPos pos = BlockPos.containing(core.position().x(), core.position().y(), core.position().z());
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null
                    || !(chunk.getBlockEntity(pos) instanceof TerritoryCoreBlockEntity blockEntity)
                    || !blockEntity.isActive()
                    || blockEntity.getOwnerUUID() == null
                    || !blockEntity.getOwnerUUID().equals(core.ownerId().value())
                    || !blockEntity.hasNetwork()) {
                continue;
            }

            KineticNetwork network = blockEntity.getOrCreateNetwork();
            if (!network.members.containsKey(blockEntity)) {
                continue;
            }
            NetworkMetrics metrics = metricsByNetwork.computeIfAbsent(network, NetworkMetrics::read);
            if (metrics.overloaded()) {
                continue;
            }
            double directStress = network.getActualStressOf(blockEntity) * metrics.countableCapacityRatio();
            if (!Double.isFinite(directStress) || directStress <= 0.0D) {
                continue;
            }

            MutableSnapshot total = totals.computeIfAbsent(core.ownerId(), ignored -> new MutableSnapshot());
            total.usedStress += directStress;
            total.directCoreStress += directStress;
            total.networks.add(network);
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

    private record NetworkMetrics(double capacity, double countableCapacity, double stress) {
        private static NetworkMetrics read(KineticNetwork network) {
            double capacity = network.sources.keySet().stream()
                    .filter(CreateStressScanner::isStillLoaded)
                    .mapToDouble(network::getActualCapacityOf)
                    .sum();
            double countableCapacity = network.sources.keySet().stream()
                    .filter(CreateStressScanner::isStillLoaded)
                    .filter(source -> !isCreativeMotor(source))
                    .mapToDouble(network::getActualCapacityOf)
                    .sum();
            double stress = network.members.keySet().stream()
                    .filter(CreateStressScanner::isStillLoaded)
                    .mapToDouble(network::getActualStressOf)
                    .sum();
            return new NetworkMetrics(capacity, countableCapacity, stress);
        }

        private boolean overloaded() {
            return !Double.isFinite(capacity) || !Double.isFinite(stress)
                    || capacity <= 0.0D || stress > capacity;
        }

        private double countableCapacityRatio() {
            if (capacity <= 0.0D || countableCapacity <= 0.0D) {
                return 0.0D;
            }
            return Math.min(1.0D, countableCapacity / capacity);
        }
    }

    private static final class MutableSnapshot {
        private double generatedCapacity;
        private double usedStress;
        private double directCoreStress;
        private int sourceCount;
        private final Set<KineticNetwork> networks = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

        private CreateStressSnapshot freeze(long gameTime) {
            return new CreateStressSnapshot(
                    generatedCapacity,
                    usedStress,
                    directCoreStress,
                    CreateStressBalance.industrialScore(
                            usedStress,
                            TerritoryCreateConfig.MAX_COUNTED_STRESS.get(),
                            TerritoryCreateConfig.SCORE_SCALE.get()),
                    sourceCount,
                    networks.size(),
                    gameTime
            );
        }
    }
}

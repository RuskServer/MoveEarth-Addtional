package com.ruskserver.moveearth_addtional.compat.create;

import com.ruskserver.moveearth_addtional.config.WaterWheelBalanceConfig;
import com.ruskserver.moveearth_addtional.terrain.RiverChannelWater;
import com.ruskserver.moveearth_addtional.terrain.RiverCurrent;
import com.ruskserver.moveearth_addtional.terrain.TerrainTile;
import com.ruskserver.moveearth_addtional.terrain.TerrainTileStore;
import com.simibubi.create.content.kinetics.waterwheel.LargeWaterWheelBlockEntity;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Set;

/** Computes bounded source-quality and local-density multipliers for Create water wheels. */
public final class WaterWheelBalance {
    private WaterWheelBalance() {
    }

    public static double capacityMultiplier(WaterWheelBlockEntity wheel, Set<BlockPos> offsets) {
        if (!WaterWheelBalanceConfig.enabled()) return 1.0D;
        BlockEntity wheelBlockEntity = (BlockEntity) wheel;
        Level level = wheelBlockEntity.getLevel();
        if (level == null) return WaterWheelBalanceConfig.baseCapacityMultiplier();

        double source = sourceQuality(level, wheelBlockEntity.getBlockPos(), offsets, wheel.flowScore);
        double units = nearbyActiveUnits(level, wheelBlockEntity.getBlockPos());
        double density = WaterWheelBalanceMath.densityMultiplier(units, WaterWheelBalanceConfig.fullPowerUnits());
        return WaterWheelBalanceMath.combinedMultiplier(
                WaterWheelBalanceConfig.baseCapacityMultiplier(), source, density,
                WaterWheelBalanceConfig.sourceQualityPenaltyStrength());
    }

    public static RiverCurrent riverCurrentAt(Level level, BlockPos pos) {
        TerrainTileStore store = TerrainTileStore.active();
        if (level == null || level.dimension() != Level.OVERWORLD
                || store == null || store.seaY() != level.getSeaLevel()) return RiverCurrent.NONE;
        if (!level.getFluidState(pos).is(FluidTags.WATER)) return RiverCurrent.NONE;
        TerrainTile tile = store.tileAt(pos.getX(), pos.getZ());
        if (tile == null) return RiverCurrent.NONE;
        RiverChannelWater.Fill fill = RiverChannelWater.decide(
                tile.channelInfluence(pos.getX(), pos.getZ()),
                tile.riverWaterLevel(pos.getX(), pos.getZ()), pos.getY());
        return fill == RiverChannelWater.Fill.WATER
                ? tile.riverCurrent(pos.getX(), pos.getZ())
                : RiverCurrent.NONE;
    }

    private static double sourceQuality(Level level, BlockPos origin, Set<BlockPos> offsets, int flowScore) {
        double quality = 0.0D;
        int contributors = 0;
        for (BlockPos offset : offsets) {
            BlockPos pos = origin.offset(offset);
            RiverCurrent current = riverCurrentAt(level, pos);
            if (current.present()) {
                quality += Math.max(WaterWheelBalanceConfig.naturalRiverMinimumMultiplier(),
                        current.strength());
                contributors++;
                continue;
            }
            var fluid = level.getFluidState(pos);
            boolean artificialFlow = fluid.is(FluidTags.WATER)
                    && (fluid.getFlow(level, pos).lengthSqr() > 1.0E-6D
                    || level.getBlockState(pos).is(Blocks.BUBBLE_COLUMN));
            if (artificialFlow) {
                quality += WaterWheelBalanceConfig.artificialFlowMultiplier();
                contributors++;
            }
        }
        if (contributors > 0) return clamp01(quality / contributors);
        return flowScore == 0 ? 0.0D : WaterWheelBalanceConfig.artificialFlowMultiplier();
    }

    private static double nearbyActiveUnits(Level level, BlockPos origin) {
        int radius = WaterWheelBalanceConfig.sharedRadiusBlocks();
        if (radius <= 0) return 1.0D;
        int minChunkX = (origin.getX() - radius) >> 4;
        int maxChunkX = (origin.getX() + radius) >> 4;
        int minChunkZ = (origin.getZ() - radius) >> 4;
        int maxChunkZ = (origin.getZ() + radius) >> 4;
        double radiusSquared = (double) radius * radius;
        double units = 0.0D;

        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                if (!level.hasChunk(chunkX, chunkZ)) continue;
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof WaterWheelBlockEntity other) || other.flowScore == 0) continue;
                    if (origin.distSqr(((BlockEntity) other).getBlockPos()) > radiusSquared) continue;
                    units += other instanceof LargeWaterWheelBlockEntity
                            ? WaterWheelBalanceConfig.largeWheelUnits() : 1.0D;
                }
            }
        }
        return Math.max(1.0D, units);
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}

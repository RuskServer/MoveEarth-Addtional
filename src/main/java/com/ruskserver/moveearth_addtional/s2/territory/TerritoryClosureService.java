package com.ruskserver.moveearth_addtional.s2.territory;

import com.ruskserver.moveearth_addtional.block.entity.TerritoryCoreBlockEntity;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementEntry;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public final class TerritoryClosureService {
    private TerritoryClosureService() {
    }

    public static TerritoryClosureScanner.Result scan(ServerLevel level, BlockPos corePos) {
        ReinforcementSavedData reinforcements = ReinforcementSavedData.get(level);
        return TerritoryClosureScanner.scan(corePos, new TerritoryClosureScanner.WorldView() {
            @Override
            public boolean isLoaded(BlockPos pos) {
                return level.hasChunkAt(pos);
            }

            @Override
            public boolean isBarrier(BlockPos pos) {
                return isReinforcedBarrier(level, reinforcements, pos);
            }
        });
    }

    public static TerritoryClosureScanner.Result scanAndUpdate(ServerLevel level, BlockPos corePos) {
        TerritoryClosureScanner.Result result = scan(level, corePos);
        TerritorySavedData territories = TerritorySavedData.get(level.getServer());
        TerritorySavedData.CoreRecord current = territories
                .core(level.dimension().location(), corePos).orElse(null);
        if (current == null) return result;
        TerritorySavedData.CoreState next = result.sealed()
                ? TerritorySavedData.CoreState.ACTIVE
                : current.state() == TerritorySavedData.CoreState.ACTIVE
                ? TerritorySavedData.CoreState.EXPOSED
                : current.state();
        TerritorySavedData.CoreRecord updated = territories.updateState(
                current.nationId(), current.dimension(), current.pos(), next).orElse(current);
        if (level.getBlockEntity(corePos) instanceof TerritoryCoreBlockEntity core) core.bind(updated);
        return result;
    }

    static boolean isReinforcedBarrier(ServerLevel level, ReinforcementSavedData data, BlockPos pos) {
        ReinforcementEntry entry = data.get(pos).orElse(null);
        if (entry == null || !entry.enabled() || entry.durability() <= 0) return false;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock) {
            if (!state.hasProperty(BlockStateProperties.OPEN) || state.getValue(BlockStateProperties.OPEN)) return false;
            DoubleBlockHalf half = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
            BlockPos otherPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState other = level.getBlockState(otherPos);
            ReinforcementEntry otherEntry = data.get(otherPos).orElse(null);
            return other.is(state.getBlock()) && other.hasProperty(BlockStateProperties.OPEN)
                    && !other.getValue(BlockStateProperties.OPEN)
                    && otherEntry != null && otherEntry.enabled() && otherEntry.durability() > 0;
        }
        if (state.getBlock() instanceof TrapDoorBlock) {
            return state.hasProperty(BlockStateProperties.OPEN) && !state.getValue(BlockStateProperties.OPEN);
        }
        return state.isCollisionShapeFullBlock(level, pos);
    }
}

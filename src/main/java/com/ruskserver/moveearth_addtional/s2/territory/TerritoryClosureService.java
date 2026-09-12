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
import com.ruskserver.moveearth_addtional.s2.siege.SiegeSavedData;

import java.util.List;

public final class TerritoryClosureService {
    private TerritoryClosureService() {
    }

    public static TerritoryClosureScanner.Result scan(ServerLevel level, BlockPos corePos) {
        TerritoryClosureScanner.Session session = begin(level, corePos);
        while (true) {
            TerritoryClosureScanner.Progress progress = session.advance(4_096);
            if (progress.complete()) return progress.result();
        }
    }

    public static TerritoryClosureScanner.Session begin(ServerLevel level, BlockPos corePos) {
        ReinforcementSavedData reinforcements = ReinforcementSavedData.get(level);
        SiegeSavedData sieges = SiegeSavedData.get(level.getServer());
        return TerritoryClosureScanner.begin(corePos, new TerritoryClosureScanner.WorldView() {
            @Override
            public boolean isLoaded(BlockPos pos) {
                return level.hasChunkAt(pos);
            }

            @Override
            public boolean isBarrier(BlockPos pos) {
                return isReinforcedBarrier(level, reinforcements, sieges, pos);
            }
        });
    }

    public static TerritoryClosureScanner.Result scanAndUpdate(ServerLevel level, BlockPos corePos) {
        TerritoryClosureScanner.Result result = scan(level, corePos);
        applyResult(level, corePos, result);
        return result;
    }

    public static void applyResult(ServerLevel level, BlockPos corePos,
                                   TerritoryClosureScanner.Result result) {
        TerritorySavedData territories = TerritorySavedData.get(level.getServer());
        TerritorySavedData.CoreRecord current = territories
                .core(level.dimension().location(), corePos).orElse(null);
        if (current == null) return;
        TerritorySavedData.CoreState next = result.sealed()
                ? TerritorySavedData.CoreState.ACTIVE
                : current.state() == TerritorySavedData.CoreState.ACTIVE
                ? TerritorySavedData.CoreState.EXPOSED
                : current.state();
        TerritorySavedData.CoreRecord updated = territories.updateState(
                current.nationId(), current.dimension(), current.pos(), next).orElse(current);
        if (level.getBlockEntity(corePos) instanceof TerritoryCoreBlockEntity core) core.bind(updated);
    }

    /** Returns solid wall candidates on the escape route which have never been reinforced. */
    public static List<BlockPos> unreinforcedLeakBlocks(ServerLevel level,
                                                        TerritoryClosureScanner.Result result) {
        ReinforcementSavedData data = ReinforcementSavedData.get(level);
        return result.escapePath().stream()
                .filter(pos -> data.get(pos).isEmpty())
                .filter(pos -> canFormClosureBarrier(level, pos))
                .toList();
    }

    static boolean isReinforcedBarrier(ServerLevel level, ReinforcementSavedData data,
                                       SiegeSavedData sieges, BlockPos pos) {
        if (sieges.isReinforcementDisabled(
                level.dimension().location(), pos)) return false;
        ReinforcementEntry entry = data.get(pos).orElse(null);
        if (entry == null || !entry.enabled() || entry.durability() <= 0) return false;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock) {
            DoubleBlockHalf half = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
            BlockPos otherPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState other = level.getBlockState(otherPos);
            ReinforcementEntry otherEntry = data.get(otherPos).orElse(null);
            return other.is(state.getBlock()) && otherEntry != null
                    && otherEntry.enabled() && otherEntry.durability() > 0;
        }
        if (state.getBlock() instanceof TrapDoorBlock) {
            return true;
        }
        return state.isCollisionShapeFullBlock(level, pos);
    }

    private static boolean canFormClosureBarrier(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
                || state.isCollisionShapeFullBlock(level, pos);
    }
}

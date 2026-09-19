package com.ruskserver.moveearth_addtional.block;

import com.ruskserver.moveearth_addtional.s2.siege.StorageWreckageService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Non-container recovery target: automation cannot bypass the explicit loot policy. */
public final class StorageWreckageBlock extends Block {
    public StorageWreckageBlock(Properties properties) { super(properties); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer) StorageWreckageService.recover(serverPlayer, pos);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide()) StorageWreckageService.removed(level, pos);
        super.onRemove(state, level, pos, replacement, moving);
    }
}

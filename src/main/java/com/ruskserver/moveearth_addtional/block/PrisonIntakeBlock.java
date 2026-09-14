package com.ruskserver.moveearth_addtional.block;

import com.ruskserver.moveearth_addtional.s2.siege.PrisonerService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class PrisonIntakeBlock extends Block {
    public PrisonIntakeBlock(Properties properties) { super(properties); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        return player instanceof ServerPlayer serverPlayer && PrisonerService.tryImprisonAt(serverPlayer, pos)
                ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide()) {
            PrisonerService.onPrisonIntakeRemoved(level, pos);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}

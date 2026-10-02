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
        if (player instanceof ServerPlayer serverPlayer) PrisonerService.openIntakeScreen(serverPlayer, pos);
        return InteractionResult.CONSUME;
    }

    /**
     * Breaking the intake releases its prisoners. Being moved does not: pistons,
     * Create contraptions and Sable assembly are all refused, and should one get
     * through anyway, a move must not free everyone held here.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!moving && !state.is(replacement.getBlock()) && !level.isClientSide()) {
            PrisonerService.onPrisonIntakeRemoved(level, pos);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}

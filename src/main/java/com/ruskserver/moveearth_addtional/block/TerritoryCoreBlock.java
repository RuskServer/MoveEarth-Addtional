package com.ruskserver.moveearth_addtional.block;

import com.ruskserver.moveearth_addtional.block.entity.TerritoryCoreBlockEntity;
import com.ruskserver.moveearth_addtional.territory.data.TerritoryCoreSavedData;
import com.ruskserver.moveearth_addtional.territory.domain.TerritoryOwnerId;
import com.ruskserver.moveearth_addtional.territory.service.TerritoryMembershipService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class TerritoryCoreBlock extends Block implements EntityBlock {
    public TerritoryCoreBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TerritoryCoreBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel
                && placer instanceof ServerPlayer player
                && level.getBlockEntity(pos) instanceof TerritoryCoreBlockEntity core) {
            core.setOwner(player.getUUID(), player.getScoreboardName());
            TerritoryCoreSavedData.get(serverLevel.getServer()).register(core.toDomain(serverLevel));
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)
                || !(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof TerritoryCoreBlockEntity core)) {
            return InteractionResult.PASS;
        }

        if (core.getOwnerUUID() == null) {
            core.setOwner(serverPlayer.getUUID(), serverPlayer.getScoreboardName());
            TerritoryCoreSavedData.get(serverLevel.getServer()).register(core.toDomain(serverLevel));
            serverPlayer.sendSystemMessage(Component.translatable("message.moveearth_addtional.territory_core.claimed"));
            return InteractionResult.SUCCESS;
        }

        TerritoryOwnerId ownerId = TerritoryOwnerId.of(core.getOwnerUUID());
        boolean member = TerritoryMembershipService.isMember(serverLevel, serverPlayer, ownerId);
        serverPlayer.sendSystemMessage(Component.translatable(
                member
                        ? "message.moveearth_addtional.territory_core.member"
                        : "message.moveearth_addtional.territory_core.owner",
                core.getOwnerName()
        ));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            if (level.getBlockEntity(pos) instanceof TerritoryCoreBlockEntity core) {
                TerritoryCoreSavedData.get(serverLevel.getServer()).unregister(core.getCoreId());
            } else {
                TerritoryCoreSavedData.get(serverLevel.getServer())
                        .unregisterAt(serverLevel.dimension().location().toString(), pos);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}

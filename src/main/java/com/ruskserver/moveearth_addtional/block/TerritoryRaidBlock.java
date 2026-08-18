package com.ruskserver.moveearth_addtional.block;

import com.ruskserver.moveearth_addtional.block.entity.ModBlockEntities;
import com.ruskserver.moveearth_addtional.block.entity.TerritoryRaidBlockEntity;
import com.ruskserver.moveearth_addtional.territory.domain.TerritoryOwnerId;
import com.ruskserver.moveearth_addtional.territory.raid.TerritoryRaidService;
import com.ruskserver.moveearth_addtional.territory.service.TerritoryMembershipService;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class TerritoryRaidBlock extends KineticBlock implements IBE<TerritoryRaidBlockEntity> {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    public TerritoryRaidBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Y));
    }

    @Override
    public Class<TerritoryRaidBlockEntity> getBlockEntityClass() {
        return TerritoryRaidBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends TerritoryRaidBlockEntity> getBlockEntityType() {
        return ModBlockEntities.TERRITORY_RAID.get();
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(AXIS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        Direction.Axis axis = state.getValue(AXIS);
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            if (axis == Direction.Axis.X) {
                return state.setValue(AXIS, Direction.Axis.Z);
            }
            if (axis == Direction.Axis.Z) {
                return state.setValue(AXIS, Direction.Axis.X);
            }
        }
        return state;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(AXIS);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer player
                && level.getBlockEntity(pos) instanceof TerritoryRaidBlockEntity raidBlock) {
            raidBlock.setOwner(player.getUUID(), player.getScoreboardName());
            raidBlock.refreshRaidState();
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
                || !(level.getBlockEntity(pos) instanceof TerritoryRaidBlockEntity raidBlock)) {
            return InteractionResult.PASS;
        }

        if (raidBlock.getOwnerUUID() == null) {
            raidBlock.setOwner(serverPlayer.getUUID(), serverPlayer.getScoreboardName());
        } else if (!TerritoryMembershipService.isMember(
                serverLevel, serverPlayer, TerritoryOwnerId.of(raidBlock.getOwnerUUID()))) {
            serverPlayer.sendSystemMessage(Component.translatable(
                    "message.moveearth_addtional.territory_raid.not_member",
                    raidBlock.getOwnerName()
            ));
            return InteractionResult.FAIL;
        }

        raidBlock.setArmed(!raidBlock.isArmed());
        serverPlayer.sendSystemMessage(Component.translatable(
                raidBlock.isArmed()
                        ? "message.moveearth_addtional.territory_raid.armed"
                        : "message.moveearth_addtional.territory_raid.disarmed"
        ));
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof TerritoryRaidBlockEntity raidBlock) {
            TerritoryRaidService.remove(raidBlock);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}

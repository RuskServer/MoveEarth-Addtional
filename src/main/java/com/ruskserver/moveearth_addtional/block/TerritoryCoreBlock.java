package com.ruskserver.moveearth_addtional.block;

import com.ruskserver.moveearth_addtional.block.entity.TerritoryCoreBlockEntity;
import com.ruskserver.moveearth_addtional.block.entity.ModBlockEntities;
import com.ruskserver.moveearth_addtional.item.ModItems;
import com.ruskserver.moveearth_addtional.territory.data.TerritoryCoreSavedData;
import com.ruskserver.moveearth_addtional.territory.domain.TerritoryOwnerId;
import com.ruskserver.moveearth_addtional.territory.service.TerritoryMembershipService;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class TerritoryCoreBlock extends KineticBlock implements IBE<TerritoryCoreBlockEntity>, BlockSubLevelAssemblyListener {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    public TerritoryCoreBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Y));
    }

    @Override
    public Class<TerritoryCoreBlockEntity> getBlockEntityClass() {
        return TerritoryCoreBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends TerritoryCoreBlockEntity> getBlockEntityType() {
        return ModBlockEntities.TERRITORY_CORE.get();
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(AXIS);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (Sable.HELPER.getContaining(context.getLevel(), context.getClickedPos()) != null) {
            if (!context.getLevel().isClientSide() && context.getPlayer() instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable(
                        "message.moveearth_addtional.territory_core.sable_forbidden"), true);
            }
            return null;
        }
        return defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
    }

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                          BlockPos oldPos, BlockPos newPos) {
        if (Sable.HELPER.getContaining(resultingLevel, newPos) == null) {
            return;
        }

        Vec3 refundPos = Sable.HELPER.projectOutOfSubLevel(originLevel, Vec3.atCenterOf(oldPos));
        resultingLevel.removeBlock(newPos, false);

        ItemEntity refund = new ItemEntity(
                originLevel,
                refundPos.x(), refundPos.y(), refundPos.z(),
                new ItemStack(ModItems.TERRITORY_CORE.get())
        );
        refund.setDefaultPickUpDelay();
        originLevel.addFreshEntity(refund);
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
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
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

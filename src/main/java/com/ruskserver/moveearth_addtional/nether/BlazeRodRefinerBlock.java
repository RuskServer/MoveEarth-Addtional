package com.ruskserver.moveearth_addtional.nether;

import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Presses a stick and a Nether shard into a blaze rod, driven through the rod
 * that runs through it along a horizontal axis and heated by a lava fan.
 * Right-click with sticks or shards to load it, with an empty hand to take the
 * rods; belts, funnels and chutes work as well.
 */
public final class BlazeRodRefinerBlock extends HorizontalAxisKineticBlock implements IBE<BlazeRodRefinerBlockEntity> {
    public BlazeRodRefinerBlock(Properties properties) {
        super(properties);
    }

    /**
     * The model's parts, in pixels, for a rod along X: four corner posts, the
     * middle frame, the body, the press at rest and the rod (its 45° section
     * boxed). The model is raised so the rod meets Create shafts at the block's
     * centre, so the shape rises above the block like a fence's does.
     */
    private static final double[][] PARTS = {
            {0, 5.0858, 0, 2, 19.5858, 2}, {14, 5.0858, 0, 16, 19.5858, 2},
            {0, 5.0858, 14, 2, 19.5858, 16}, {14, 5.0858, 14, 16, 19.5858, 16},
            {0, 11.0858, 2, 2, 13.5858, 14}, {14, 11.0858, 2, 16, 13.5858, 14},
            {2, 11.0858, 0, 14, 13.5858, 2}, {2, 11.0858, 14, 14, 13.5858, 16},
            {2, 5.0858, 2, 14, 12.5858, 14},
            {3, 3.5858, 3, 13, 4.5858, 13}, {7, 4.5858, 7, 9, 23.5858, 9},
            {0, 5.1716, 5.1716, 16, 10.8284, 10.8284}};
    private static final VoxelShape SHAPE_X = shape(false);
    private static final VoxelShape SHAPE_Z = shape(true);

    private static VoxelShape shape(boolean alongZ) {
        VoxelShape shape = Shapes.empty();
        for (double[] p : PARTS) {
            shape = Shapes.or(shape, alongZ
                    ? Block.box(p[2], p[1], p[0], p[5], p[4], p[3])
                    : Block.box(p[0], p[1], p[2], p[3], p[4], p[5]));
        }
        return shape.optimize();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HORIZONTAL_AXIS) == net.minecraft.core.Direction.Axis.Z ? SHAPE_Z : SHAPE_X;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!BlazeRodRefinerBlockEntity.acceptsInput(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        withBlockEntityDo(level, pos, refiner -> player.setItemInHand(hand, refiner.insertByHand(stack)));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        withBlockEntityDo(level, pos, refiner -> refiner.giveOutput(player));
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) withBlockEntityDo(level, pos, BlazeRodRefinerBlockEntity::dropContents);
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public Class<BlazeRodRefinerBlockEntity> getBlockEntityClass() {
        return BlazeRodRefinerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BlazeRodRefinerBlockEntity> getBlockEntityType() {
        return NetherGateRegistry.BLAZE_ROD_REFINER_ENTITY.get();
    }
}

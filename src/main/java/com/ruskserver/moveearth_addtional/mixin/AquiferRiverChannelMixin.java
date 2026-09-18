package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.terrain.RiverChannelWater;
import com.ruskserver.moveearth_addtional.terrain.TerrainTile;
import com.ruskserver.moveearth_addtional.terrain.TerrainTileStore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fills our river channels directly, instead of asking the aquifer to.
 *
 * <p>Why the aquifer cannot do it is set out in {@link RiverChannelWater}: it
 * samples one point per cell and walls off cells that disagree about their
 * level, and a descending river makes them disagree everywhere. This takes the
 * channel out of its hands and leaves the rest of the world to it untouched.
 *
 * <p>The guard is the dimension's own water table. Our terrain tiles cover an
 * area of x/z that exists in every dimension, and the nether and the end run
 * through this same class, so being inside a tile proves nothing. Asking the
 * global picker what it has at our sea level does: only a dimension whose water
 * table is exactly there is the one the tiles were built for. The nether
 * answers lava below and air at that height, the end answers air.
 *
 * <p>What the channel says is a function of x and z alone, but this method is
 * called for every block of the chunk -- around a hundred thousand times, since
 * the whole column from the void to the build limit passes through it. Both
 * answers are therefore kept per column. One aquifer serves one chunk, so a
 * fixed table of 256 covers it, and the work drops by roughly the height of the
 * world.
 */
@Mixin(Aquifer.NoiseBasedAquifer.class)
public abstract class AquiferRiverChannelMixin {

    @Shadow @Final private Aquifer.FluidPicker globalFluidPicker;

    @Shadow protected boolean shouldScheduleFluidUpdate;

    @Unique private static final long MOVEEARTH$EMPTY = Long.MIN_VALUE;
    @Unique private long[] moveearth$columnKey;
    @Unique private double[] moveearth$columnInfluence;
    @Unique private int[] moveearth$columnLevel;
    /** Nought unknown, one ours, minus one not ours. Fixed for the whole chunk. */
    @Unique private int moveearth$dimension;

    @Inject(method = "computeSubstance", at = @At("HEAD"), cancellable = true)
    private void moveearth$riverChannel(DensityFunction.FunctionContext context, double substance,
                                        CallbackInfoReturnable<BlockState> callback) {
        if (substance > 0.0) {
            // solid ground; the channel is carved by the density function, not here
            return;
        }
        TerrainTileStore store = TerrainTileStore.active();
        if (store == null) {
            return;
        }
        int x = context.blockX();
        int y = context.blockY();
        int z = context.blockZ();
        if (moveearth$dimension == 0) {
            moveearth$dimension = moveearth$isOurDimension(store.seaY(), x, z) ? 1 : -1;
        }
        if (moveearth$dimension < 0) {
            return;
        }

        if (moveearth$columnKey == null) {
            moveearth$columnKey = new long[256];
            java.util.Arrays.fill(moveearth$columnKey, MOVEEARTH$EMPTY);
            moveearth$columnInfluence = new double[256];
            moveearth$columnLevel = new int[256];
        }
        int slot = ((x & 15) << 4) | (z & 15);
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        if (moveearth$columnKey[slot] != key) {
            TerrainTile tile = store.tileAt(x, z);
            moveearth$columnInfluence[slot] = tile == null ? 0.0 : tile.channelInfluence(x, z);
            moveearth$columnLevel[slot] = tile == null ? Integer.MIN_VALUE : tile.riverWaterLevel(x, z);
            moveearth$columnKey[slot] = key;
        }

        RiverChannelWater.Fill fill = RiverChannelWater.decide(
                moveearth$columnInfluence[slot], moveearth$columnLevel[slot], y);
        if (fill == RiverChannelWater.Fill.NONE) {
            return;
        }
        // Our water is already at the height the river runs at, so it has no
        // level to settle to and does not want a fluid tick.
        shouldScheduleFluidUpdate = false;
        callback.setReturnValue(fill == RiverChannelWater.Fill.WATER
                ? Blocks.WATER.defaultBlockState()
                : Blocks.AIR.defaultBlockState());
    }

    @Unique
    private boolean moveearth$isOurDimension(int seaY, int x, int z) {
        // asked at sea level, not at the position: vanilla's picker answers
        // lava far below any sea, and that is a depth, not a dimension
        Aquifer.FluidStatus status = globalFluidPicker.computeFluid(x, seaY - 1, z);
        return status.at(seaY - 1).is(Blocks.WATER) && status.at(seaY).isAir();
    }
}

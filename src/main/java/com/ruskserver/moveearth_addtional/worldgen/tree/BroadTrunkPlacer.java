package com.ruskserver.moveearth_addtional.worldgen.tree;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ruskserver.moveearth_addtional.worldgen.WorldgenRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import com.mojang.serialization.Codec;

/**
 * A thick, tapering, limbed trunk. The geometry is {@link TreeShape}.
 *
 * <p>Vanilla's widest trunk placer is two blocks square and its limbs are one
 * block thick lines drawn to the foliage, which is why even a large oak reads
 * as a prop next to real woodland. This one starts from a flared base, loses
 * girth with height, and throws limbs from the upper half, returning a foliage
 * attachment at every limb tip so the canopy is several overlapping masses
 * rather than one ball.
 *
 * <p>The trunk block comes from the configuration's own provider, so the
 * datapack chooses it; the intent is that it names {@code oak_wood} rather than
 * {@code oak_log}, because a limb built from logs shows its cut ends and a
 * bark-on-all-sides block does not.
 */
public class BroadTrunkPlacer extends TrunkPlacer {
    // One flat group rather than trunkPlacerParts().and(...): the products the
    // builder composes only go up to four extra fields, and there are six here.
    public static final MapCodec<BroadTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Codec.intRange(0, 32).fieldOf("base_height").forGetter(p -> p.baseHeight),
                    Codec.intRange(0, 24).fieldOf("height_rand_a").forGetter(p -> p.heightRandA),
                    Codec.intRange(0, 24).fieldOf("height_rand_b").forGetter(p -> p.heightRandB),
                    Codec.intRange(1, 4).fieldOf("girth").forGetter(p -> p.girth),
                    Codec.intRange(0, 4).fieldOf("flare").forGetter(p -> p.flare),
                    Codec.intRange(0, 12).fieldOf("limbs").forGetter(p -> p.limbs),
                    Codec.intRange(1, 12).fieldOf("limb_length").forGetter(p -> p.limbLength),
                    Codec.floatRange(0.0F, 1.0F).fieldOf("limb_start").forGetter(p -> p.limbStart),
                    Codec.floatRange(0.0F, 2.0F).fieldOf("limb_rise").forGetter(p -> p.limbRise))
                    .apply(instance, BroadTrunkPlacer::new));

    private final int girth;
    private final int flare;
    private final int limbs;
    private final int limbLength;
    private final float limbStart;
    private final float limbRise;

    public BroadTrunkPlacer(int baseHeight, int heightRandA, int heightRandB,
                            int girth, int flare, int limbs, int limbLength,
                            float limbStart, float limbRise) {
        super(baseHeight, heightRandA, heightRandB);
        this.girth = girth;
        this.flare = flare;
        this.limbs = limbs;
        this.limbLength = limbLength;
        this.limbStart = limbStart;
        this.limbRise = limbRise;
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return WorldgenRegistration.BROAD_TRUNK.get();
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(
            LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> blockSetter,
            RandomSource random, int height, BlockPos pos, TreeConfiguration config) {

        // Every column the trunk stands on, so a wide trunk is not left half in
        // the air on a slope.
        for (int[] offset : TreeShape.girthOffsets(TreeShape.trunkGirth(0, height, girth, flare))) {
            setDirtAt(level, blockSetter, random, pos.offset(offset[0], -1, offset[1]), config);
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = 0; y < height; y++) {
            int side = TreeShape.trunkGirth(y, height, girth, flare);
            for (int[] offset : TreeShape.girthOffsets(side)) {
                cursor.set(pos.getX() + offset[0], pos.getY() + y, pos.getZ() + offset[1]);
                placeLogIfFree(level, blockSetter, random, cursor, config);
            }
        }

        List<FoliagePlacer.FoliageAttachment> attachments = new ArrayList<>();
        attachments.add(new FoliagePlacer.FoliageAttachment(pos.above(height), 0, false));
        placeLimbs(level, blockSetter, random, height, pos, config, attachments);
        return attachments;
    }

    private void placeLimbs(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> blockSetter,
                            RandomSource random, int height, BlockPos pos, TreeConfiguration config,
                            List<FoliagePlacer.FoliageAttachment> attachments) {
        if (limbs <= 0) {
            return;
        }
        int lowest = Math.max(1, Mth.floor(height * limbStart));
        int span = Math.max(1, height - 1 - lowest);
        // A whole turn spread over the limbs, jittered. Evenly spaced limbs look
        // like a lamp post; unconstrained random ones clump on one side and the
        // tree leans.
        double turn = (Math.PI * 2.0) / limbs;
        double phase = random.nextDouble() * Math.PI * 2.0;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i < limbs; i++) {
            double yaw = phase + turn * i + (random.nextDouble() - 0.5) * turn * 0.6;
            int base = lowest + random.nextInt(span);
            int length = Math.max(1, limbLength - random.nextInt(Math.max(1, limbLength / 2)));
            double rise = limbRise * (0.6 + random.nextDouble() * 0.8);

            BlockPos from = pos.above(base);
            TreeShape.Step tip = null;
            for (TreeShape.Step step : TreeShape.limb(yaw, rise, length)) {
                cursor.set(from.getX() + step.x(), from.getY() + step.y(), from.getZ() + step.z());
                if (!isFree(level, cursor)) {
                    break;
                }
                placeLog(level, blockSetter, random, cursor, config, state -> axis(state, step));
                tip = step;
            }
            if (tip != null) {
                attachments.add(new FoliagePlacer.FoliageAttachment(
                        from.offset(tip.x(), tip.y(), tip.z()), 0, false));
            }
        }
    }

    /**
     * Point the log along the limb.
     *
     * <p>A limb of upright logs is a stack of stumps: the bark runs the wrong
     * way and the grain shows at every joint. Which axis dominates is decided
     * per block from its own offset, so a limb that climbs as it goes out turns
     * from horizontal to vertical where it actually does.
     */
    private static BlockState axis(BlockState state, TreeShape.Step step) {
        if (!state.hasProperty(RotatedPillarBlock.AXIS)) {
            return state;
        }
        int ax = Math.abs(step.x());
        int ay = Math.abs(step.y());
        int az = Math.abs(step.z());
        Direction.Axis chosen = Direction.Axis.Y;
        if (ax >= ay && ax >= az) {
            chosen = Direction.Axis.X;
        } else if (az >= ay && az >= ax) {
            chosen = Direction.Axis.Z;
        }
        return state.setValue(RotatedPillarBlock.AXIS, chosen);
    }
}

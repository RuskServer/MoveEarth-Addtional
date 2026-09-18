package com.ruskserver.moveearth_addtional.worldgen.tree;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ruskserver.moveearth_addtional.worldgen.WorldgenRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;

/**
 * A wide, ragged dome of leaves, one per limb tip.
 *
 * <p>Vanilla's blob is a squat cylinder with its corners rounded off, which is
 * fine at a radius of two and looks like a hedge at a radius of eight. This
 * carries a flattened ellipsoid instead, and roughens its surface rather than
 * only its corners, so the outline breaks up at any size.
 */
public class BroadFoliagePlacer extends FoliagePlacer {
    public static final MapCodec<BroadFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(
            instance -> foliagePlacerParts(instance)
                    .and(instance.group(
                            IntProvider.codec(1, 16).fieldOf("height").forGetter(p -> p.height),
                            Codec.floatRange(0.0F, 1.0F).fieldOf("ragged").forGetter(p -> p.ragged),
                            Codec.intRange(0, 8).fieldOf("lift").forGetter(p -> p.lift)))
                    .apply(instance, BroadFoliagePlacer::new));

    private final IntProvider height;
    private final float ragged;
    private final int lift;

    public BroadFoliagePlacer(IntProvider radius, IntProvider offset, IntProvider height,
                              float ragged, int lift) {
        super(radius, offset);
        this.height = height;
        this.ragged = ragged;
        this.lift = lift;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return WorldgenRegistration.BROAD_FOLIAGE.get();
    }

    @Override
    protected void createFoliage(LevelSimulatedReader level, FoliageSetter setter, RandomSource random,
                                 TreeConfiguration config, int maxFreeTreeHeight,
                                 FoliageAttachment attachment, int foliageHeight, int radius, int offset) {
        BlockPos origin = attachment.pos().above(offset);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        // Below the widest ring as well as above it, or the mass sits on the limb
        // like a hat instead of enclosing it.
        for (int dy = -foliageHeight; dy <= foliageHeight + lift; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (!TreeShape.inCanopy(dx, dy, dz, radius, foliageHeight, lift)) {
                        continue;
                    }
                    if (shouldSkipLocation(random, Math.abs(dx), dy, Math.abs(dz), radius, false)) {
                        continue;
                    }
                    cursor.setWithOffset(origin, dx, dy, dz);
                    tryPlaceLeaf(level, setter, random, config, cursor);
                }
            }
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int height, TreeConfiguration config) {
        return this.height.sample(random);
    }

    /**
     * Thin the outer shell at random, leave the inside solid.
     *
     * <p>Roughening throughout would put holes through the canopy that daylight
     * comes down; only the surface needs to be broken for the outline to stop
     * looking machined.
     */
    @Override
    protected boolean shouldSkipLocation(RandomSource random, int localX, int localY, int localZ,
                                         int range, boolean large) {
        if (range <= 0) {
            return true;
        }
        double edge = Math.sqrt((double) localX * localX + (double) localZ * localZ) / range;
        return edge > 0.62 && random.nextFloat() < ragged * edge;
    }
}

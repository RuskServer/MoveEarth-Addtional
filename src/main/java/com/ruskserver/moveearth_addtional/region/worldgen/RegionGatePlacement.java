package com.ruskserver.moveearth_addtional.region.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ruskserver.moveearth_addtional.region.RegionProfiles;
import com.ruskserver.moveearth_addtional.region.RegionResolver;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Stops an ore generating where its region does not have it.
 *
 * <p>Vanilla already has a place to decide this: a placement modifier is asked,
 * for every attempt, whether the position survives. So regional exclusivity
 * needs no new machinery in the generator, only one more modifier appended to
 * the ores that were going to generate anyway.
 *
 * <p>Only ever thins out. A multiplier above one would mean generating an ore
 * more often than the feature asks, which a modifier cannot do without
 * inventing positions the feature never chose. Instead the feature's own count
 * is read as the richest region's, and everywhere else is a fraction of it.
 * That keeps one number in the data to reason about rather than two.
 *
 * <p>Called from chunk generation worker threads, so it only reads. When the
 * region map is not built the multiplier comes back as one and everything
 * generates: a world missing its region map must not also be missing its ore.
 */
public class RegionGatePlacement extends PlacementModifier {

    public static final MapCodec<RegionGatePlacement> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(com.mojang.serialization.Codec.STRING.fieldOf("material")
                            .forGetter(gate -> gate.material))
                    .apply(instance, RegionGatePlacement::new));

    private final String material;

    public RegionGatePlacement(String material) {
        this.material = material;
    }

    public String material() {
        return material;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        int region = RegionResolver.regionAt(pos.getX(), pos.getZ());
        if (!RegionProfiles.allows(region, material)) {
            return Stream.of();
        }
        double density = RegionProfiles.densityFor(region, material);
        if (density >= 1.0) {
            return Stream.of(pos);
        }
        if (density <= 0.0) {
            return Stream.of();
        }
        // Thinning is per attempt rather than per chunk on purpose: the ore ends
        // up sparser without its veins changing shape or moving, so a region
        // poor in copper still looks like the same world, with less copper in it.
        return random.nextDouble() < density ? Stream.of(pos) : Stream.of();
    }

    @Override
    public PlacementModifierType<?> type() {
        return RegionWorldgen.REGION_GATE.get();
    }
}

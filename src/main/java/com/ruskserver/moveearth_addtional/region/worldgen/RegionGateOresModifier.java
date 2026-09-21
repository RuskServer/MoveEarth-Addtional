package com.ruskserver.moveearth_addtional.region.worldgen;

import com.mojang.serialization.MapCodec;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

/**
 * Appends a region test to every ore feature a biome generates.
 *
 * <p>Naming the features to change was the obvious approach and is the one to
 * avoid: vanilla alone has a dozen, Create adds zinc, and every ore mod or
 * worldgen change would mean editing the list. A list like that is only ever
 * correct on the day it is written. So nothing is named — the ore features are
 * found at runtime, by the same convention tags the rest of this system reads,
 * and whatever the pack happens to contain is handled.
 *
 * <p>Wrapping rather than replacing. The existing modifiers decide how many
 * veins, how deep and in what shape; this only adds one more question at the
 * end, so a feature that was going to place ore still does, in the same places,
 * minus what the region rules take away.
 *
 * <p>Confined to the underground ore step, so nothing touches structures,
 * vegetation or anything else a biome builds.
 */
public record RegionGateOresModifier() implements BiomeModifier {

    public static final MapCodec<RegionGateOresModifier> CODEC =
            MapCodec.unit(RegionGateOresModifier::new);

    /** Logged once, because a biome modifier runs for every biome there is. */
    private static volatile boolean reported = false;

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.MODIFY) {
            return;
        }
        List<Holder<PlacedFeature>> ores = builder.getGenerationSettings()
                .getFeatures(GenerationStep.Decoration.UNDERGROUND_ORES);
        int wrapped = 0;
        for (int index = 0; index < ores.size(); index++) {
            Holder<PlacedFeature> holder = ores.get(index);
            PlacedFeature feature = holder.value();
            Optional<String> material = OreFeatureMaterial.of(feature);
            if (material.isEmpty()) {
                // Left exactly as it was. An ore nothing can name must keep
                // generating everywhere rather than quietly vanish.
                continue;
            }
            if (feature.placement().stream().anyMatch(RegionGatePlacement.class::isInstance)) {
                continue;
            }
            List<PlacementModifier> placement = new ArrayList<>(feature.placement());
            placement.add(new RegionGatePlacement(material.get()));
            ores.set(index, Holder.direct(new PlacedFeature(feature.feature(), List.copyOf(placement))));
            wrapped++;
        }
        if (wrapped > 0 && !reported) {
            reported = true;
            Moveearth_addtional.LOGGER.info(
                    "Region gate applied to ore features; {} named in the first biome that had any.",
                    wrapped);
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return CODEC;
    }
}

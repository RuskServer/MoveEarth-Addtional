package com.ruskserver.moveearth_addtional.region.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

/**
 * Reports which ore features carry a region gate and which do not.
 *
 * <p>The gate is inserted by a biome modifier, which runs while registries load
 * and leaves nothing behind to say it worked. Without this there is no way to
 * tell a world where every ore is correctly gated from one where the modifier
 * never ran: both generate ore, and only one of them obeys the region rules.
 *
 * <p>Read back from the biomes the server is actually using, after modifiers
 * have applied, so it reports the state that will generate chunks rather than
 * what the code intended.
 */
public final class RegionFeatureAudit {

    /**
     * One ore feature as the loaded biomes hold it.
     *
     * @param suspectOre whether an unnamed feature still looks like ore, which
     *                   is what separates a resource escaping its region from a
     *                   feature that was never ore to begin with
     */
    public record Entry(String featureId, String material, boolean gated, String reason,
                        boolean suspectOre, List<String> biomes) { }

    private RegionFeatureAudit() { }

    /** Every ore feature the loaded biomes use, deduplicated. */
    public static List<Entry> run(MinecraftServer server, ResourceLocation onlyBiome) {
        var biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        record Found(String material, boolean gated, String reason, boolean suspectOre,
                     List<String> biomes) { }
        var byFeature = new TreeMap<String, Found>();

        for (var entry : biomes.entrySet()) {
            ResourceLocation biomeId = entry.getKey().location();
            if (onlyBiome != null && !onlyBiome.equals(biomeId)) {
                continue;
            }
            Biome biome = entry.getValue();
            var steps = biome.getGenerationSettings().features();
            int index = GenerationStep.Decoration.UNDERGROUND_ORES.ordinal();
            if (index >= steps.size()) {
                continue;
            }
            for (Holder<PlacedFeature> holder : steps.get(index)) {
                PlacedFeature feature = holder.value();
                // A gated feature was rebuilt with Holder.direct and has lost its
                // own key, but the configured feature inside is still a registry
                // reference -- and that is the name an operator recognises.
                String featureId = holder.unwrapKey().map(key -> key.location().toString())
                        .or(() -> feature.feature().unwrapKey().map(key -> key.location().toString()))
                        .orElse("(unnamed feature)");
                boolean gated = feature.placement().stream()
                        .anyMatch(RegionGatePlacement.class::isInstance);
                // One read, so the name and the reason it has none can never
                // disagree: they used to be two calls and could in principle
                // have answered differently about the same feature.
                OreFeatureMaterial.Reading reading = OreFeatureMaterial.read(feature);
                String material = gated ? gatedMaterial(feature) : reading.material().orElse(null);
                String reason = material == null ? reading.reason() : null;
                String key = featureId + "\u0000" + material;
                Found found = byFeature.get(key);
                List<String> where = found == null ? new ArrayList<>() : found.biomes();
                if (where.size() < 3) {
                    where.add(biomeId.toString());
                }
                byFeature.put(key, new Found(material, gated, reason, reading.suspectOre(), where));
            }
        }

        List<Entry> out = new ArrayList<>();
        byFeature.forEach((key, found) -> out.add(new Entry(
                key.substring(0, key.indexOf('\u0000')),
                found.material() == null ? "(unnamed)" : found.material(),
                found.gated(), found.reason(), found.suspectOre(),
                List.copyOf(found.biomes()))));
        return List.copyOf(out);
    }

    /** The material the gate itself was given, which is the one that counts. */
    private static String gatedMaterial(PlacedFeature feature) {
        for (PlacementModifier modifier : feature.placement()) {
            if (modifier instanceof RegionGatePlacement gate) {
                return gate.material();
            }
        }
        return null;
    }

    /** How many of the named ore features carry a gate. */
    public static String summarise(List<Entry> entries) {
        long named = entries.stream().filter(e -> !"(unnamed)".equals(e.material())).count();
        long gated = entries.stream().filter(Entry::gated).count();
        return gated + " of " + named + " named ore feature(s) gated, "
                + (entries.size() - named) + " left open";
    }

    /** Named features that should have been gated but were not. */
    public static Optional<String> missing(List<Entry> entries) {
        List<String> gaps = entries.stream()
                .filter(entry -> !entry.gated() && !"(unnamed)".equals(entry.material()))
                .map(Entry::featureId).toList();
        return gaps.isEmpty() ? Optional.empty() : Optional.of(String.join(", ", gaps));
    }
}

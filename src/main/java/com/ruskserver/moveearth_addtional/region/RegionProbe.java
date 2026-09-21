package com.ruskserver.moveearth_addtional.region;

import com.ruskserver.moveearth_addtional.region.worldgen.OreFeatureMaterial;
import com.ruskserver.moveearth_addtional.region.MaterialResolver.Resolution;
import com.ruskserver.moveearth_addtional.terrain.TerrainTile;
import com.ruskserver.moveearth_addtional.terrain.TerrainTileStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Reports what the running server actually contains, so the region system can
 * be built on measurements instead of on a table someone typed.
 *
 * <p>The plan for this system forbids a fixed list of ore ids, which only works
 * if the convention tags really do cover the modpack. That is a claim about a
 * specific set of mods, not something the design can assert, so this exists to
 * check it before any gating code is written: it walks every ore feature the
 * biomes actually use and says which ones {@link MaterialResolver} can name and
 * which it cannot. An ore that cannot be named is one the region gate has to
 * let through everywhere, and a long unresolved list means the generalisation
 * failed and needs widening — not that a mod name should be hardcoded.
 */
public final class RegionProbe {

    /**
     * One ore-generating feature and what it was resolved to.
     *
     * @param conventionTags every {@code c:} tag on the target blocks. Carried so
     *                       that an unresolved entry can say what it <em>does</em>
     *                       carry: the difference between "this mod tagged nothing"
     *                       and "this is a stone blob the resolver was never meant
     *                       to name" decides whether the rule needs widening, and
     *                       it is invisible without the tags.
     */
    public record OreFeature(String featureId, List<String> blocks,
                             List<String> conventionTags, Resolution resolution) { }

    /** One loaded tile, counted cell by cell. */
    public record TileFacts(String directory, int originX, int originZ, int sizeCells,
                            int blocksPerCell, Map<Integer, Integer> regionCells,
                            Map<Integer, Integer> continentCells) { }

    /** Everything the probe found, ready to be written out or summarised. */
    public record Report(List<OreFeature> oreFeatures, Map<String, List<String>> oreTags,
                         List<TileFacts> tiles) {

        public long resolvedCount() {
            return oreFeatures.stream().filter(f -> f.resolution().material().isPresent()).count();
        }

        public long unresolvedCount() {
            return oreFeatures.size() - resolvedCount();
        }

        public long ambiguousCount() {
            return oreFeatures.stream().filter(f -> f.resolution().ambiguous()).count();
        }
    }

    private RegionProbe() { }

    /**
     * Collects every distinct ore feature reachable from the loaded biomes.
     *
     * <p>Walks biomes rather than the feature registry because that is what the
     * region gate will later modify: a feature registered but used by no biome
     * cannot generate, and counting it would overstate the coverage this probe
     * is meant to establish.
     */
    public static Report run(MinecraftServer server) {
        var biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        Map<String, OreFeature> byFeature = new TreeMap<>();

        for (Biome biome : biomes) {
            List<HolderSet<PlacedFeature>> steps = biome.getGenerationSettings().features();
            int index = GenerationStep.Decoration.UNDERGROUND_ORES.ordinal();
            if (index >= steps.size()) {
                continue;
            }
            for (Holder<PlacedFeature> placed : steps.get(index)) {
                collect(placed, byFeature);
            }
        }
        return new Report(List.copyOf(byFeature.values()), oreTags(server), tileFacts());
    }

    private static void collect(Holder<PlacedFeature> placed, Map<String, OreFeature> out) {
        // Read by the same code the gate uses. This walked ore configs itself
        // once, and the two rules drifted the moment the gate learned to read a
        // mod's feature through its codec: the gate confined Mekanism's uranium
        // while the probe went on reporting that no such ore feature existed.
        // A probe that disagrees with the thing it is probing is worse than none.
        OreFeatureMaterial.Reading reading = OreFeatureMaterial.read(placed.value());
        if (reading.material().isEmpty() && !reading.suspectOre()) {
            // Disks of sand, lava flows and the like. Counting them would
            // inflate the unresolved total this probe exists to keep honest.
            return;
        }
        // Several biomes share the same feature; the id keeps one entry per
        // feature so the counts read as "distinct ore features", not
        // "biome-feature pairs", which would be dominated by common biomes.
        String featureId = idOf(placed);
        if (out.containsKey(featureId)) {
            return;
        }
        out.put(featureId, new OreFeature(featureId, reading.blocks(),
                reading.conventionTags(), reading.resolution()));
    }

    /** Counts every region and continent id present in each loaded tile. */
    private static List<TileFacts> tileFacts() {
        TerrainTileStore store = TerrainTileStore.active();
        if (store == null) {
            return List.of();
        }
        List<TileFacts> facts = new ArrayList<>();
        for (TerrainTile tile : store.tiles()) {
            Map<Integer, Integer> regions = new TreeMap<>();
            Map<Integer, Integer> continents = new TreeMap<>();
            for (int cellZ = 0; cellZ < tile.sizeCells(); cellZ++) {
                for (int cellX = 0; cellX < tile.sizeCells(); cellX++) {
                    regions.merge(tile.cellValue("region", cellX, cellZ), 1, Integer::sum);
                    continents.merge(tile.cellValue("continent", cellX, cellZ), 1, Integer::sum);
                }
            }
            facts.add(new TileFacts(String.valueOf(tile.directory().getFileName()),
                    tile.originX(), tile.originZ(), tile.sizeCells(), tile.blocksPerCell(),
                    Map.copyOf(regions), Map.copyOf(continents)));
        }
        return List.copyOf(facts);
    }

    /** Every {@code c:ores/*} tag and the blocks in it, for cross-checking. */
    private static Map<String, List<String>> oreTags(MinecraftServer server) {
        Map<String, List<String>> tags = new TreeMap<>();
        var registry = server.registryAccess().registryOrThrow(Registries.BLOCK);
        registry.getTagNames().forEach(tag -> {
            ResourceLocation id = tag.location();
            if (!MaterialResolver.CONVENTION.equals(id.getNamespace())
                    || !id.getPath().startsWith("ores/")) {
                return;
            }
            List<String> members = new ArrayList<>();
            registry.getTag(tag).ifPresent(holders ->
                    holders.forEach(h -> members.add(h.getKey() == null
                            ? h.value().toString()
                            : h.getKey().location().toString())));
            tags.put(id.toString(), List.copyOf(members));
        });
        return Map.copyOf(tags);
    }

    private static String idOf(Holder<PlacedFeature> placed) {
        Optional<String> key = placed.unwrapKey().map(k -> k.location().toString());
        return key.orElse("(inline placed feature)");
    }

    /** Indirection kept so the block id lookup is in one place. */
    private static final java.util.function.Function<Block, ResourceLocation> BLOCK_ID =
            block -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
}

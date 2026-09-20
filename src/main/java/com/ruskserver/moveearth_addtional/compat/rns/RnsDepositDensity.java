package com.ruskserver.moveearth_addtional.compat.rns;

import com.ruskserver.moveearth_addtional.region.RegionGrid;
import com.ruskserver.moveearth_addtional.region.RegionProfiles;
import com.ruskserver.moveearth_addtional.region.RegionResolver;
import com.ruskserver.moveearth_addtional.region.VeinDensity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * Counts the ore deposits each region would get, without generating a chunk.
 *
 * <p>Making a resource exclusive necessarily removes deposits: a grid position
 * whose weighted draw lands on a resource the region does not have yields
 * nothing at all. The total therefore falls, and it falls unevenly. That cannot
 * be reasoned about, only measured.
 *
 * <p>Two numbers, because Rock &amp; Stone decides in two steps. The grid says
 * <em>where</em> a deposit may sit, and that is exact arithmetic on the world
 * seed, so sites per region is an exact count. Which deposit lands there is a
 * weighted draw among the structure set's entries, so the survival rate is the
 * share of weight a region is allowed — exact in expectation rather than per
 * site. Reporting the expectation is honest and comparable between regions,
 * which is what a balance check needs; replaying the draw itself would mean
 * reimplementing vanilla's structure selection and trusting the copy.
 */
public final class RnsDepositDensity {

    private RnsDepositDensity() { }

    /**
     * Walks every deposit structure set's placement grid over a chunk range.
     *
     * @param minChunk lowest chunk coordinate on both axes, inclusive
     * @param maxChunk highest chunk coordinate on both axes, inclusive
     */
    public static List<VeinDensity> measure(MinecraftServer server, int minChunk, int maxChunk) {
        ServerLevel level = server.overworld();
        long seed = level.getSeed();
        List<StructureSet> sets = depositSets(server);

        Map<Integer, double[]> expected = new TreeMap<>();      // kept, refused
        Map<Integer, Map<String, Long>> materials = new HashMap<>();
        Map<Integer, Long> landChunks = countLandChunksPerRegion(minChunk, maxChunk);
        landChunks.keySet().forEach(region -> expected.put(region, new double[2]));

        for (StructureSet set : sets) {
            if (!(set.placement() instanceof RandomSpreadStructurePlacement placement)) {
                continue;
            }
            int spacing = Math.max(1, placement.spacing());
            int firstCell = Math.floorDiv(minChunk, spacing);
            int lastCell = Math.floorDiv(maxChunk, spacing);
            for (int cellZ = firstCell; cellZ <= lastCell; cellZ++) {
                for (int cellX = firstCell; cellX <= lastCell; cellX++) {
                    ChunkPos chunk = placement.getPotentialStructureChunk(
                            seed, cellX * spacing, cellZ * spacing);
                    if (chunk.x < minChunk || chunk.x > maxChunk
                            || chunk.z < minChunk || chunk.z > maxChunk
                            || !isLand(chunk.x, chunk.z)) {
                        continue;
                    }
                    int region = RegionResolver.regionAt(chunk.getMinBlockX(), chunk.getMinBlockZ());
                    if (region == RegionGrid.NONE) {
                        continue;
                    }
                    score(set, region, expected.computeIfAbsent(region, key -> new double[2]),
                            materials.computeIfAbsent(region, key -> new TreeMap<>()));
                }
            }
        }

        List<VeinDensity> out = new ArrayList<>();
        expected.forEach((region, counts) -> out.add(new VeinDensity(region,
                landChunks.getOrDefault(region, 0L), Math.round(counts[0]), Math.round(counts[1]),
                Map.copyOf(materials.getOrDefault(region, Map.of())))));
        return List.copyOf(out);
    }

    /**
     * Shares one grid position between the set's entries by weight.
     *
     * <p>A position is one deposit, not one per entry, so each entry counts for
     * its share of the weight. Summed over many positions this is the number of
     * deposits the region actually gets.
     */
    private static void score(StructureSet set, int region, double[] counts,
                              Map<String, Long> materials) {
        double totalWeight = set.structures().stream().mapToInt(StructureSet.StructureSelectionEntry::weight)
                .filter(weight -> weight > 0).sum();
        if (totalWeight <= 0) {
            return;
        }
        for (StructureSet.StructureSelectionEntry entry : set.structures()) {
            if (entry.weight() <= 0) {
                continue;
            }
            double share = entry.weight() / totalWeight;
            Structure structure = entry.structure().value();
            String material = RnsDepositGate.materialOf(structure);
            if (material == null || RegionProfiles.allows(region, material)) {
                counts[0] += share;
                materials.merge(material == null ? "(unnamed)" : material,
                        Math.max(1L, Math.round(share * 100)), Long::sum);
            } else {
                counts[1] += share;
            }
        }
    }

    /** Every structure set that holds at least one named deposit. */
    private static List<StructureSet> depositSets(MinecraftServer server) {
        List<StructureSet> sets = new ArrayList<>();
        for (StructureSet set : server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET)) {
            boolean holdsDeposits = set.structures().stream()
                    .anyMatch(entry -> RnsDepositGate.materialOf(entry.structure().value()) != null);
            if (holdsDeposits) {
                sets.add(set);
            }
        }
        return sets;
    }

    private static Map<Integer, Long> countLandChunksPerRegion(int minChunk, int maxChunk) {
        Map<Integer, Long> counts = new TreeMap<>();
        for (int chunkZ = minChunk; chunkZ <= maxChunk; chunkZ++) {
            for (int chunkX = minChunk; chunkX <= maxChunk; chunkX++) {
                if (!isLand(chunkX, chunkZ)) {
                    continue;
                }
                int region = RegionResolver.regionAt(chunkX << 4, chunkZ << 4);
                if (region != RegionGrid.NONE) {
                    counts.merge(region, 1L, Long::sum);
                }
            }
        }
        return counts;
    }

    /**
     * Whether a chunk is on land.
     *
     * <p>Read from the continent layer, which is not coast-filled: a position at
     * sea belongs to a region but to no continent, and a region's share of open
     * water says nothing about how far a player walks between deposits on it.
     */
    private static boolean isLand(int chunkX, int chunkZ) {
        return RegionResolver.continentAt(chunkX << 4, chunkZ << 4) != RegionGrid.NONE;
    }
}

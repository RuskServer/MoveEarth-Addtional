package com.ruskserver.moveearth_addtional.region;

import com.ruskserver.moveearth_addtional.region.RegionProfileAssigner.Region;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Measures the regions in a tile: how big each one is, what shape, and which
 * ones border which. No Minecraft in it so it can be tested.
 *
 * <p>The assigner needs these numbers and the tile does not store them — it
 * stores an id per cell and nothing else. Deriving them at startup rather than
 * writing them into the tile keeps the tile to raw layers, and means changing
 * how a region is judged never requires regenerating a world.
 */
public final class RegionSurvey {

    private RegionSurvey() { }

    /**
     * Walks the region and continent layers once and describes every region.
     *
     * <p>Compactness is cells over the square of the longer side of the
     * bounding box, so a solid square scores 1 and anything long and thin
     * scores low. Filling the bounding box was the obvious measure and is
     * wrong: a straight ribbon fills its box exactly and scores a perfect 1,
     * which is the one shape the measure exists to catch. The production tile
     * reads 0.20 for its coastal ribbon and 0.33 for a narrow strip, against
     * 0.42 to 0.68 for the provinces.
     */
    public static List<Region> survey(RegionGrid.Cells regions, RegionGrid.Cells continents,
                                      int worldSpawnCellX, int worldSpawnCellZ) {
        int size = regions.size();
        Map<Integer, int[]> bounds = new TreeMap<>();   // minX, minZ, maxX, maxZ
        Map<Integer, Integer> area = new TreeMap<>();
        Map<Integer, Integer> continent = new TreeMap<>();
        Map<Integer, Set<Integer>> neighbours = new TreeMap<>();

        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int id = regions.at(x, z);
                if (id <= 0) {
                    continue;
                }
                area.merge(id, 1, Integer::sum);
                continent.putIfAbsent(id, continents.at(x, z));
                int[] box = bounds.get(id);
                if (box == null) {
                    bounds.put(id, new int[] { x, z, x, z });
                } else {
                    box[0] = Math.min(box[0], x);
                    box[1] = Math.min(box[1], z);
                    box[2] = Math.max(box[2], x);
                    box[3] = Math.max(box[3], z);
                }
                // Only right and down: every shared border is seen once from
                // each side anyway, and recording it twice would be the same set.
                link(regions, neighbours, id, x + 1, z, size);
                link(regions, neighbours, id, x, z + 1, size);
            }
        }

        int spawnRegion = inside(worldSpawnCellX, worldSpawnCellZ, size)
                ? regions.at(worldSpawnCellX, worldSpawnCellZ)
                : RegionGrid.NONE;

        List<Region> out = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : area.entrySet()) {
            int id = entry.getKey();
            int[] box = bounds.get(id);
            long longest = Math.max(box[2] - box[0] + 1, box[3] - box[1] + 1);
            out.add(new Region(id, continent.getOrDefault(id, 0), entry.getValue(),
                    longest <= 0 ? 1.0 : entry.getValue() / (double) (longest * longest),
                    id == spawnRegion,
                    Set.copyOf(neighbours.getOrDefault(id, Set.of()))));
        }
        return List.copyOf(out);
    }

    private static void link(RegionGrid.Cells regions, Map<Integer, Set<Integer>> neighbours,
                             int id, int x, int z, int size) {
        if (!inside(x, z, size)) {
            return;
        }
        int other = regions.at(x, z);
        if (other <= 0 || other == id) {
            return;
        }
        neighbours.computeIfAbsent(id, key -> new HashSet<>()).add(other);
        neighbours.computeIfAbsent(other, key -> new HashSet<>()).add(id);
    }

    private static boolean inside(int x, int z, int size) {
        return x >= 0 && z >= 0 && x < size && z < size;
    }
}

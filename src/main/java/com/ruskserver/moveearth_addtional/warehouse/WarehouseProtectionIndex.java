package com.ruskserver.moveearth_addtional.warehouse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chunk buckets of warehouse protection footprints ({@link WarehouseSitePolicy#BUILD_MARGIN} around the
 * building). Explosions and pistons ask about every block they touch; this answers each with one hash
 * lookup and the exact {@link WarehouseSitePolicy#within} test. Free of Minecraft types.
 */
public final class WarehouseProtectionIndex {
    private final Map<Object, Map<Long, List<int[]>>> byChunk = new HashMap<>();

    public void add(Object dimension, int minX, int minZ) {
        int margin = WarehouseSitePolicy.BUILD_MARGIN;
        int fromChunkX = (int) (((long) minX - margin) >> 4);
        int toChunkX = (int) (((long) minX + WarehouseSitePolicy.WIDTH - 1 + margin) >> 4);
        int fromChunkZ = (int) (((long) minZ - margin) >> 4);
        int toChunkZ = (int) (((long) minZ + WarehouseSitePolicy.LENGTH - 1 + margin) >> 4);
        Map<Long, List<int[]>> chunks = byChunk.computeIfAbsent(dimension, ignored -> new HashMap<>());
        int[] site = {minX, minZ};
        for (int chunkX = fromChunkX; chunkX <= toChunkX; chunkX++) {
            for (int chunkZ = fromChunkZ; chunkZ <= toChunkZ; chunkZ++) {
                chunks.computeIfAbsent(key(chunkX, chunkZ), ignored -> new ArrayList<>(1)).add(site);
            }
        }
    }

    public boolean protects(Object dimension, int x, int z) {
        Map<Long, List<int[]>> chunks = byChunk.get(dimension);
        if (chunks == null) return false;
        List<int[]> sites = chunks.get(key(x >> 4, z >> 4));
        if (sites == null) return false;
        for (int index = 0; index < sites.size(); index++) {
            int[] site = sites.get(index);
            if (WarehouseSitePolicy.within(site[0], site[1], x, z, WarehouseSitePolicy.BUILD_MARGIN)) return true;
        }
        return false;
    }

    /** False when no site protects anything in this dimension, so a whole blast list can be skipped. */
    public boolean hasDimension(Object dimension) { return byChunk.containsKey(dimension); }

    private static long key(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | ((chunkZ & 0xFFFFFFFFL) << 32);
    }
}

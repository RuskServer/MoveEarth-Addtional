package com.ruskserver.moveearth_addtional.s2.siege;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Insertion-ordered square chunk areas ({@code |dx| <= radius && |dz| <= radius} around a centre chunk),
 * bucketed into coarse cells so a point lookup only visits the areas near it.
 *
 * <p>{@link #first} and {@link #last} return exactly what a forward or backward scan over all areas in
 * insertion order would return, so callers keep their "oldest wins" or "newest wins" semantics. Free of
 * Minecraft types: the dimension is any key with value equality.
 */
public final class ChunkSquareIndex<T> {
    /** 16 x 16 chunks per bucket: typical territory radii touch at most a 3 x 3 block of buckets. */
    static final int CELL_SHIFT = 4;
    /** Areas wider than this many buckets are kept in one shared list instead of being copied per bucket. */
    static final long MAX_CELLS_PER_AREA = 1024L;

    private final Map<Object, Map<Long, List<Area<T>>>> cells = new HashMap<>();
    private final List<Area<T>> wide = new ArrayList<>();
    private long sequence;
    private int size;

    public void add(Object dimension, int centerChunkX, int centerChunkZ, int radius, T value) {
        int safeRadius = Math.max(0, radius);
        Area<T> area = new Area<>(sequence++, dimension, centerChunkX, centerChunkZ, safeRadius, value);
        size++;
        long minCellX = ((long) centerChunkX - safeRadius) >> CELL_SHIFT;
        long maxCellX = ((long) centerChunkX + safeRadius) >> CELL_SHIFT;
        long minCellZ = ((long) centerChunkZ - safeRadius) >> CELL_SHIFT;
        long maxCellZ = ((long) centerChunkZ + safeRadius) >> CELL_SHIFT;
        if ((maxCellX - minCellX + 1L) * (maxCellZ - minCellZ + 1L) > MAX_CELLS_PER_AREA) {
            wide.add(area);
            return;
        }
        Map<Long, List<Area<T>>> dimensionCells = cells.computeIfAbsent(dimension, ignored -> new HashMap<>());
        for (long cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (long cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                dimensionCells.computeIfAbsent(cellKey(cellX, cellZ), ignored -> new ArrayList<>()).add(area);
            }
        }
    }

    /** The earliest added area containing the chunk, or null. */
    public T first(Object dimension, int chunkX, int chunkZ) {
        Area<T> best = null;
        List<Area<T>> bucket = bucket(dimension, chunkX, chunkZ);
        for (int index = 0; index < bucket.size(); index++) {
            Area<T> area = bucket.get(index);
            if (area.contains(dimension, chunkX, chunkZ)) {
                best = area;
                break;
            }
        }
        for (int index = 0; index < wide.size(); index++) {
            Area<T> area = wide.get(index);
            if (best != null && area.sequence > best.sequence) break;
            if (area.contains(dimension, chunkX, chunkZ)) {
                best = area;
                break;
            }
        }
        return best == null ? null : best.value;
    }

    /** The most recently added area containing the chunk, or null. */
    public T last(Object dimension, int chunkX, int chunkZ) {
        Area<T> best = null;
        List<Area<T>> bucket = bucket(dimension, chunkX, chunkZ);
        for (int index = bucket.size() - 1; index >= 0; index--) {
            Area<T> area = bucket.get(index);
            if (area.contains(dimension, chunkX, chunkZ)) {
                best = area;
                break;
            }
        }
        for (int index = wide.size() - 1; index >= 0; index--) {
            Area<T> area = wide.get(index);
            if (best != null && area.sequence < best.sequence) break;
            if (area.contains(dimension, chunkX, chunkZ)) {
                best = area;
                break;
            }
        }
        return best == null ? null : best.value;
    }

    public boolean isEmpty() { return size == 0; }

    public int size() { return size; }

    private List<Area<T>> bucket(Object dimension, int chunkX, int chunkZ) {
        Map<Long, List<Area<T>>> dimensionCells = cells.get(dimension);
        if (dimensionCells == null) return List.of();
        List<Area<T>> bucket = dimensionCells.get(cellKey(chunkX >> CELL_SHIFT, chunkZ >> CELL_SHIFT));
        return bucket == null ? List.of() : bucket;
    }

    private static long cellKey(long cellX, long cellZ) {
        return (cellX & 0xFFFFFFFFL) | ((cellZ & 0xFFFFFFFFL) << 32);
    }

    private record Area<T>(long sequence, Object dimension, int centerX, int centerZ, int radius, T value) {
        boolean contains(Object actualDimension, int chunkX, int chunkZ) {
            return dimension.equals(actualDimension)
                    && Math.abs(chunkX - centerX) <= radius && Math.abs(chunkZ - centerZ) <= radius;
        }
    }
}

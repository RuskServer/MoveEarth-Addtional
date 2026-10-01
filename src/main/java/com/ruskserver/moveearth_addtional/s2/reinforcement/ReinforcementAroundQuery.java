package com.ruskserver.moveearth_addtional.s2.reinforcement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Sphere selection behind {@link ReinforcementSavedData#around}. Free of Minecraft types so it can be
 * tested against the original copy-filter-sort implementation.
 *
 * <p>Small radii probe the sphere's cells directly instead of copying every entry of the touched chunk
 * columns (a fortified chunk can hold thousands); large radii walk the chunk index. Sorting is only done
 * when the caller needs nearest-first order, and the (possibly chunk-loading) presence check runs lazily
 * on the sorted candidates until the limit is reached.
 */
final class ReinforcementAroundQuery {
    /** A hash probe costs a few distance checks; prefer the chunk walk unless the sphere is clearly smaller. */
    private static final long CELL_COST = 4L;

    private ReinforcementAroundQuery() { }

    interface Source<T> {
        /** Entries indexed in the chunk range, counting may stop once {@code cap} is exceeded. */
        long countInChunks(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ, long cap);

        void forEachInChunks(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ, Consumer<T> consumer);

        /** The entry key at exactly this block, or null. */
        T at(int x, int y, int z);

        int x(T value);

        int y(T value);

        int z(T value);
    }

    static <T> List<T> select(Source<T> source, int centerX, int centerY, int centerZ, int radius,
                              Predicate<T> present, boolean sorted, int limit) {
        if (radius < 0 || limit <= 0) return List.of();
        List<T> candidates = new ArrayList<>();
        if (radius == 0) {
            T value = source.at(centerX, centerY, centerZ);
            if (value != null) candidates.add(value);
        } else {
            long radiusSquared = (long) radius * radius;
            int minChunkX = (centerX - radius) >> 4;
            int maxChunkX = (centerX + radius) >> 4;
            int minChunkZ = (centerZ - radius) >> 4;
            int maxChunkZ = (centerZ + radius) >> 4;
            long sphereCost = sphereCells(radius) * CELL_COST;
            long indexed = source.countInChunks(minChunkX, maxChunkX, minChunkZ, maxChunkZ, sphereCost);
            if (indexed == 0L) return List.of();
            if (indexed > sphereCost) {
                probeSphere(source, centerX, centerY, centerZ, radius, candidates);
            } else {
                source.forEachInChunks(minChunkX, maxChunkX, minChunkZ, maxChunkZ, value -> {
                    if (distanceSquared(source, value, centerX, centerY, centerZ) <= radiusSquared) {
                        candidates.add(value);
                    }
                });
            }
        }
        if (sorted && candidates.size() > 1) sortByDistance(source, candidates, centerX, centerY, centerZ);
        List<T> result = new ArrayList<>(Math.min(limit, candidates.size()));
        for (T value : candidates) {
            if (!present.test(value)) continue;
            result.add(value);
            if (result.size() >= limit) break;
        }
        return result;
    }

    static long distanceSquared(int x, int y, int z, int centerX, int centerY, int centerZ) {
        long dx = (long) x - centerX;
        long dy = (long) y - centerY;
        long dz = (long) z - centerZ;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Upper estimate of the integer cells inside the closed sphere (only steers the strategy). */
    static long sphereCells(int radius) {
        if (radius > 100_000) return Long.MAX_VALUE / 8;
        double padded = radius + 0.5D;
        return (long) Math.ceil(4.0D / 3.0D * Math.PI * padded * padded * padded);
    }

    private static <T> void probeSphere(Source<T> source, int centerX, int centerY, int centerZ, int radius,
                                        List<T> candidates) {
        long radiusSquared = (long) radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            long remainingX = radiusSquared - (long) dx * dx;
            int maxY = isqrt(remainingX);
            for (int dy = -maxY; dy <= maxY; dy++) {
                int maxZ = isqrt(remainingX - (long) dy * dy);
                for (int dz = -maxZ; dz <= maxZ; dz++) {
                    T value = source.at(centerX + dx, centerY + dy, centerZ + dz);
                    if (value != null) candidates.add(value);
                }
            }
        }
    }

    private static <T> void sortByDistance(Source<T> source, List<T> candidates,
                                           int centerX, int centerY, int centerZ) {
        int size = candidates.size();
        long[] keys = new long[size];
        long maxDistance = 0L;
        for (int index = 0; index < size; index++) {
            keys[index] = distanceSquared(source, candidates.get(index), centerX, centerY, centerZ);
            maxDistance = Math.max(maxDistance, keys[index]);
        }
        if (maxDistance < (1L << 32)) {
            // Distance in the high bits, original index in the low bits: one primitive sort, stable for ties.
            for (int index = 0; index < size; index++) keys[index] = keys[index] << 31 | index;
            Arrays.sort(keys);
            List<T> ordered = new ArrayList<>(size);
            for (long key : keys) ordered.add(candidates.get((int) (key & 0x7FFF_FFFFL)));
            candidates.clear();
            candidates.addAll(ordered);
            return;
        }
        Integer[] order = new Integer[size];
        for (int index = 0; index < size; index++) order[index] = index;
        long[] distances = keys;
        Arrays.sort(order, Comparator.comparingLong(index -> distances[index]));
        List<T> ordered = new ArrayList<>(size);
        for (Integer index : order) ordered.add(candidates.get(index));
        candidates.clear();
        candidates.addAll(ordered);
    }

    private static <T> long distanceSquared(Source<T> source, T value, int centerX, int centerY, int centerZ) {
        return distanceSquared(source.x(value), source.y(value), source.z(value), centerX, centerY, centerZ);
    }

    private static int isqrt(long value) {
        if (value <= 0L) return 0;
        long root = (long) Math.sqrt((double) value);
        while (root * root > value) root--;
        while ((root + 1) * (root + 1) <= value) root++;
        return (int) root;
    }
}

package com.ruskserver.moveearth_addtional.s2.reinforcement;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class ReinforcementAroundQueryTest {
    private record Pos(int x, int y, int z) { }

    /** Chunk-indexed positions mirroring ReinforcementSavedData's entriesByChunk. */
    private static final class Index implements ReinforcementAroundQuery.Source<Pos> {
        final Map<Long, Set<Pos>> byChunk = new HashMap<>();
        final Set<Pos> all = new HashSet<>();

        void add(Pos pos) {
            if (all.add(pos)) byChunk.computeIfAbsent(key(pos.x >> 4, pos.z >> 4), k -> new LinkedHashSet<>()).add(pos);
        }

        static long key(int x, int z) {
            return (x & 0xffffffffL) | ((long) z << 32);
        }

        @Override
        public long countInChunks(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ, long cap) {
            long count = 0;
            for (int x = minChunkX; x <= maxChunkX; x++) {
                for (int z = minChunkZ; z <= maxChunkZ; z++) {
                    Set<Pos> set = byChunk.get(key(x, z));
                    if (set != null) count += set.size();
                }
            }
            return count;
        }

        @Override
        public void forEachInChunks(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ, Consumer<Pos> consumer) {
            for (int x = minChunkX; x <= maxChunkX; x++) {
                for (int z = minChunkZ; z <= maxChunkZ; z++) {
                    Set<Pos> set = byChunk.get(key(x, z));
                    if (set != null) set.forEach(consumer);
                }
            }
        }

        @Override public Pos at(int x, int y, int z) {
            Pos pos = new Pos(x, y, z);
            return all.contains(pos) ? pos : null;
        }
        @Override public int x(Pos value) { return value.x; }
        @Override public int y(Pos value) { return value.y; }
        @Override public int z(Pos value) { return value.z; }

        /** The pre-v3.3 implementation: copy touched chunks, filter, sort, limit. */
        List<Pos> legacyAround(Pos center, int radius, Predicate<Pos> present, int limit) {
            long radiusSquared = (long) radius * radius;
            List<Pos> nearby = new ArrayList<>();
            forEachInChunks((center.x - radius) >> 4, (center.x + radius) >> 4,
                    (center.z - radius) >> 4, (center.z + radius) >> 4, nearby::add);
            return nearby.stream()
                    .filter(pos -> dist(pos, center) <= radiusSquared)
                    .filter(present)
                    .sorted(Comparator.comparingLong(pos -> dist(pos, center)))
                    .limit(limit)
                    .toList();
        }
    }

    private static long dist(Pos pos, Pos center) {
        return ReinforcementAroundQuery.distanceSquared(pos.x, pos.y, pos.z, center.x, center.y, center.z);
    }

    private static Index dense(Random random) {
        Index index = new Index();
        // A fortified chunk with walls (many entries in few chunks) plus scattered armor across chunk borders.
        for (int x = -20; x <= 20; x++) {
            for (int y = 60; y <= 75; y++) {
                index.add(new Pos(x, y, -3));
                index.add(new Pos(-3, y, x));
            }
        }
        for (int i = 0; i < 3000; i++) {
            index.add(new Pos(random.nextInt(200) - 100, 50 + random.nextInt(40), random.nextInt(200) - 100));
        }
        return index;
    }

    @Test
    void sortedSelectionMatchesLegacyAcrossStrategies() {
        Random random = new Random(1234L);
        Index index = dense(random);
        Predicate<Pos> present = pos -> Math.floorMod(pos.x * 31 + pos.y * 7 + pos.z, 11) != 0;
        for (int radius : new int[]{0, 1, 2, 3, 5, 8, 13, 24, 64}) {
            for (int trial = 0; trial < 25; trial++) {
                Pos center = trial % 3 == 0
                        ? new Pos(random.nextInt(40) - 20, 60 + random.nextInt(16), -3)
                        : new Pos(random.nextInt(220) - 110, 45 + random.nextInt(50), random.nextInt(220) - 110);
                if (trial % 5 == 0) center = new ArrayList<>(index.all).get(random.nextInt(index.all.size()));
                List<Pos> expected = index.legacyAround(center, radius, present, 8192);
                List<Pos> actual = ReinforcementAroundQuery.select(index, center.x, center.y, center.z,
                        radius, present, true, 8192);
                assertEquals(Set.copyOf(expected), Set.copyOf(actual), "radius " + radius + " at " + center);
                Pos c = center;
                assertEquals(expected.stream().map(pos -> dist(pos, c)).toList(),
                        actual.stream().map(pos -> dist(pos, c)).toList());
            }
        }
    }

    @Test
    void limitKeepsTheNearestEntries() {
        Index index = dense(new Random(99L));
        Pos center = new Pos(0, 66, -3);
        List<Pos> expected = index.legacyAround(center, 40, pos -> true, 100);
        List<Pos> actual = ReinforcementAroundQuery.select(index, 0, 66, -3, 40, pos -> true, true, 100);
        assertEquals(100, actual.size());
        assertEquals(expected.stream().map(pos -> dist(pos, center)).toList(),
                actual.stream().map(pos -> dist(pos, center)).toList());
    }

    @Test
    void unorderedSelectionReturnsTheSameSetWithoutCap() {
        Random random = new Random(7L);
        Index index = dense(random);
        Predicate<Pos> present = pos -> pos.y != 70;
        for (int radius : new int[]{0, 2, 4, 9, 30}) {
            for (int trial = 0; trial < 20; trial++) {
                Pos center = new ArrayList<>(index.all).get(random.nextInt(index.all.size()));
                List<Pos> actual = ReinforcementAroundQuery.select(index, center.x, center.y, center.z,
                        radius, present, false, Integer.MAX_VALUE);
                assertEquals(Set.copyOf(index.legacyAround(center, radius, present, Integer.MAX_VALUE)),
                        Set.copyOf(actual));
                assertEquals(actual.size(), Set.copyOf(actual).size(), "no duplicates");
            }
        }
    }

    @Test
    void radiusZeroIsAPointLookupAndNegativeRadiusIsEmpty() {
        Index index = new Index();
        index.add(new Pos(5, 64, 5));
        assertEquals(List.of(new Pos(5, 64, 5)),
                ReinforcementAroundQuery.select(index, 5, 64, 5, 0, pos -> true, true, 8192));
        assertTrue(ReinforcementAroundQuery.select(index, 5, 65, 5, 0, pos -> true, true, 8192).isEmpty());
        assertTrue(ReinforcementAroundQuery.select(index, 5, 64, 5, 0, pos -> false, true, 8192).isEmpty());
        assertTrue(ReinforcementAroundQuery.select(index, 5, 64, 5, -1, pos -> true, true, 8192).isEmpty());
    }

    @Test
    void presenceIsOnlyCheckedUntilTheLimitIsFilled() {
        Index index = dense(new Random(3L));
        int[] checks = {0};
        ReinforcementAroundQuery.select(index, 0, 66, -3, 64, pos -> {
            checks[0]++;
            return true;
        }, true, 10);
        assertEquals(10, checks[0]);
    }
}

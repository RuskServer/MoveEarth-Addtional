package com.ruskserver.moveearth_addtional.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReinforcementSnapshotDiffTest {
    @Test
    void identicalSnapshotChangesNothing() {
        assertTrue(changed(states(1L, 7L, 2L, 7L), states(1L, 7L, 2L, 7L)).isEmpty());
    }

    @Test
    void restyledAddedAndRemovedPositionsAreReported() {
        List<Long> changed = changed(states(1L, 7L, 2L, 7L, 3L, 7L), states(1L, 7L, 2L, 8L, 4L, 7L));

        assertEquals(Set.of(2L, 3L, 4L), Set.copyOf(changed));
        assertEquals(3, changed.size());
    }

    @Test
    void interiorBlockTouchesOnlyItsChunk() {
        assertEquals(List.of(chunk(0, 0)), chunks(5, 5));
        assertEquals(List.of(chunk(2, -3)), chunks(40, -40));
    }

    @Test
    void borderBlockAlsoTouchesTheNeighbourSharingTheFace() {
        assertEquals(List.of(chunk(1, 1), chunk(0, 1), chunk(1, 2)), chunks(16, 31));
        assertEquals(List.of(chunk(0, 0), chunk(1, 0), chunk(0, -1)), chunks(15, 0));
    }

    @Test
    void negativeCoordinatesUseFloorChunks() {
        assertEquals(List.of(chunk(-1, -2), chunk(0, -2), chunk(-1, -1)), chunks(-1, -17));
    }

    private static List<Long> changed(Map<Long, Long> previous, Map<Long, Long> next) {
        List<Long> changed = new ArrayList<>();
        ReinforcementSnapshotDiff.changedPositions(view(previous), view(next), changed::add);
        return changed;
    }

    private static List<String> chunks(int x, int z) {
        Set<String> out = new LinkedHashSet<>();
        ReinforcementSnapshotDiff.chunksTouching(x, z, (chunkX, chunkZ) -> out.add(chunk(chunkX, chunkZ)));
        return List.copyOf(out);
    }

    private static String chunk(int x, int z) {
        return x + "," + z;
    }

    private static Map<Long, Long> states(long... posState) {
        Map<Long, Long> map = new LinkedHashMap<>();
        for (int index = 0; index < posState.length; index += 2) map.put(posState[index], posState[index + 1]);
        return map;
    }

    private static ReinforcementSnapshotDiff.States view(Map<Long, Long> map) {
        return new ReinforcementSnapshotDiff.States() {
            @Override
            public boolean contains(long pos) {
                return map.containsKey(pos);
            }

            @Override
            public long get(long pos) {
                return map.get(pos);
            }

            @Override
            public void forEach(ReinforcementSnapshotDiff.PositionStateConsumer consumer) {
                map.forEach(consumer::accept);
            }
        };
    }
}

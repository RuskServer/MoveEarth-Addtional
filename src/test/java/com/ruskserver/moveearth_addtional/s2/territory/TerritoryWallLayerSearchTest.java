package com.ruskserver.moveearth_addtional.s2.territory;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerritoryWallLayerSearchTest {
    private static final TerritoryClosureSearch.Cell CORE = new TerritoryClosureSearch.Cell(0, 64, 0);
    private static final int RANGE = 10;

    /** A hollow box shell around the core at Chebyshev distance {@code distance}. */
    private static void shell(Set<TerritoryClosureSearch.Cell> barriers, int distance) {
        for (int x = -distance; x <= distance; x++)
            for (int y = -distance; y <= distance; y++)
                for (int z = -distance; z <= distance; z++)
                    if (Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z))) == distance)
                        barriers.add(CORE.offset(x, y, z));
    }

    private static TerritoryWallLayerSearch.Result count(Set<TerritoryClosureSearch.Cell> barriers) {
        return TerritoryWallLayerSearch.search(CORE, new TerritoryClosureSearch.WorldView() {
            @Override public boolean isLoaded(TerritoryClosureSearch.Cell cell) { return true; }
            @Override public boolean isBarrier(TerritoryClosureSearch.Cell cell) { return barriers.contains(cell); }
        }, RANGE, RANGE, TerritoryWallLayerSearch.CAP);
    }

    @Test
    void noWallIsZeroLayers() {
        var result = count(Set.of());
        assertEquals(TerritoryWallLayerSearch.Status.COUNTED, result.status());
        assertEquals(0, result.layers());
        assertFalse(result.atLeast());
    }

    @Test
    void singleShellIsOneLayerWithItsWeakPoint() {
        Set<TerritoryClosureSearch.Cell> barriers = new HashSet<>();
        shell(barriers, 3);
        var result = count(barriers);
        assertEquals(1, result.layers());
        assertEquals(1, result.weakPoints().size());
        assertTrue(barriers.contains(result.weakPoints().getFirst()));
    }

    @Test
    void separateAndThickWallsBothCountTwice() {
        Set<TerritoryClosureSearch.Cell> apart = new HashSet<>();
        shell(apart, 3);
        shell(apart, 6);
        assertEquals(2, count(apart).layers());
        Set<TerritoryClosureSearch.Cell> thick = new HashSet<>();
        shell(thick, 3);
        shell(thick, 4);
        var result = count(thick);
        assertEquals(2, result.layers());
        // Outside in: the outer skin first.
        assertEquals(4, chebyshev(result.weakPoints().getFirst()));
    }

    @Test
    void aHoleInTheOuterWallLeavesOneLayer() {
        Set<TerritoryClosureSearch.Cell> barriers = new HashSet<>();
        shell(barriers, 3);
        shell(barriers, 6);
        barriers.remove(CORE.offset(6, 0, 0));
        var result = count(barriers);
        assertEquals(1, result.layers());
        assertEquals(3, chebyshev(result.weakPoints().getFirst()));
    }

    @Test
    void deepFortressesStopCountingAtTheCap() {
        Set<TerritoryClosureSearch.Cell> barriers = new HashSet<>();
        for (int distance = 2; distance <= 7; distance++) shell(barriers, distance);
        var result = count(barriers);
        assertTrue(result.atLeast());
        assertEquals(TerritoryWallLayerSearch.CAP, result.layers());
    }

    @Test
    void unloadedGroundGivesNoCount() {
        var result = TerritoryWallLayerSearch.search(CORE, new TerritoryClosureSearch.WorldView() {
            @Override public boolean isLoaded(TerritoryClosureSearch.Cell cell) { return cell.x() < 5; }
            @Override public boolean isBarrier(TerritoryClosureSearch.Cell cell) { return false; }
        }, RANGE, RANGE, TerritoryWallLayerSearch.CAP);
        assertEquals(TerritoryWallLayerSearch.Status.UNLOADED, result.status());
    }

    @Test
    void anOuterWallBeyondTheClosureRangeCountsWhenTheBoxReachesIt() {
        Set<TerritoryClosureSearch.Cell> barriers = new HashSet<>();
        shell(barriers, 3);
        shell(barriers, 14);
        var view = new TerritoryClosureSearch.WorldView() {
            @Override public boolean isLoaded(TerritoryClosureSearch.Cell cell) { return true; }
            @Override public boolean isBarrier(TerritoryClosureSearch.Cell cell) { return barriers.contains(cell); }
        };
        assertEquals(1, TerritoryWallLayerSearch.search(CORE, view, 10, 10, 4).layers());
        assertEquals(2, TerritoryWallLayerSearch.search(CORE, view, 16, 16, 4).layers());
    }

    @Test
    void aLowCapStopsAtTheTarget() {
        Set<TerritoryClosureSearch.Cell> barriers = new HashSet<>();
        shell(barriers, 3);
        shell(barriers, 5);
        shell(barriers, 7);
        var view = new TerritoryClosureSearch.WorldView() {
            @Override public boolean isLoaded(TerritoryClosureSearch.Cell cell) { return true; }
            @Override public boolean isBarrier(TerritoryClosureSearch.Cell cell) { return barriers.contains(cell); }
        };
        var capped = TerritoryWallLayerSearch.search(CORE, view, RANGE, RANGE, 2);
        assertTrue(capped.atLeast());
        assertEquals(2, capped.layers());
        assertEquals(3, TerritoryWallLayerSearch.search(CORE, view, RANGE, RANGE, 4).layers());
    }

    @Test
    void goingOverAShortWallCountsAsGettingOut() {
        Set<TerritoryClosureSearch.Cell> barriers = new HashSet<>();
        shell(barriers, 3);
        var view = new TerritoryClosureSearch.WorldView() {
            @Override public boolean isLoaded(TerritoryClosureSearch.Cell cell) { return true; }
            @Override public boolean isBarrier(TerritoryClosureSearch.Cell cell) { return barriers.contains(cell); }
        };
        // A box only two blocks tall ends inside the shell: reaching its top is already out.
        assertEquals(0, TerritoryWallLayerSearch.search(CORE, view, RANGE, 2, 4).layers());
    }

    private static int chebyshev(TerritoryClosureSearch.Cell cell) {
        return Math.max(Math.abs(cell.x() - CORE.x()),
                Math.max(Math.abs(cell.y() - CORE.y()), Math.abs(cell.z() - CORE.z())));
    }
}

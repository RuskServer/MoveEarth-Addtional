package com.ruskserver.moveearth_addtional.s2.territory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerritoryClosureSearchTest {
    private static final TerritoryClosureSearch.Cell CORE = new TerritoryClosureSearch.Cell(100, 64, -40);

    @Test
    void acceptsFloorMountedCoreWithThreeByThreeByThreeInterior() {
        TerritoryClosureSearch.Result result = scan(shell(false), cell -> true, 8, 1_000);
        assertEquals(TerritoryClosureSearch.Status.SEALED, result.status());
        assertTrue(result.escapePath().isEmpty());
        assertEquals(27, result.visited());
    }

    @Test
    void returnsShortestEscapeRouteThroughShellHole() {
        TerritoryClosureSearch.Result result = scan(shell(true), cell -> true, 8, 5_000);
        assertEquals(TerritoryClosureSearch.Status.OPEN, result.status());
        assertTrue(result.escapePath().contains(CORE.offset(2, 0, 0)));
        TerritoryClosureSearch.Cell end = result.escapePath().getLast();
        assertEquals(8, Math.abs(end.x() - CORE.x()));
    }

    @Test
    void treatsMissingReinforcementBelowCoreAsEscapeRoute() {
        TerritoryClosureSearch.Result result = scan(chamber(cell -> cell.equals(CORE.offset(0, -1, 0))),
                cell -> true, 8, 5_000);

        assertEquals(TerritoryClosureSearch.Status.OPEN, result.status());
        assertTrue(result.escapePath().contains(CORE.offset(0, -1, 0)));
    }

    @Test
    void rejectsShellThatCrushesRequiredInteriorSpace() {
        TerritoryClosureSearch.Result result = scan(cell -> cell.equals(CORE.offset(1, 0, 0)),
                cell -> true, 8, 1_000);
        assertEquals(TerritoryClosureSearch.Status.TOO_SMALL, result.status());
        assertEquals(CORE.offset(1, 0, 0), result.escapePath().getFirst());
    }

    @Test
    void stopsAtUnloadedPosition() {
        TerritoryClosureSearch.Cell unloaded = CORE.offset(0, -1, 0);
        TerritoryClosureSearch.Result result = scan(cell -> false,
                cell -> !cell.equals(unloaded), 8, 1_000);
        assertEquals(TerritoryClosureSearch.Status.UNLOADED, result.status());
        assertEquals(unloaded, result.escapePath().getFirst());
    }

    @Test
    void capsLargeOpenInteriorScans() {
        TerritoryClosureSearch.Result result = scan(cell -> false, cell -> true, 8, 10);
        assertEquals(TerritoryClosureSearch.Status.LIMIT_EXCEEDED, result.status());
        assertEquals(11, result.visited());
        assertFalse(result.escapePath().isEmpty());
    }

    private static java.util.function.Predicate<TerritoryClosureSearch.Cell> shell(boolean eastHole) {
        return chamber(cell -> eastHole && cell.equals(CORE.offset(2, 0, 0)));
    }

    private static java.util.function.Predicate<TerritoryClosureSearch.Cell> chamber(
            java.util.function.Predicate<TerritoryClosureSearch.Cell> hole) {
        return cell -> {
            int dx = Math.abs(cell.x() - CORE.x());
            int dz = Math.abs(cell.z() - CORE.z());
            int dy = cell.y() - CORE.y();
            boolean floorOrCeiling = (dy == -1 || dy == 3) && dx <= 2 && dz <= 2;
            boolean wall = (dx == 2 || dz == 2) && dy >= -1 && dy <= 3;
            return (floorOrCeiling || wall) && !hole.test(cell);
        };
    }

    private static TerritoryClosureSearch.Result scan(
            java.util.function.Predicate<TerritoryClosureSearch.Cell> barrier,
            java.util.function.Predicate<TerritoryClosureSearch.Cell> loaded,
            int range, int limit) {
        return TerritoryClosureSearch.scan(CORE, new TerritoryClosureSearch.WorldView() {
            @Override
            public boolean isLoaded(TerritoryClosureSearch.Cell cell) {
                return loaded.test(cell);
            }

            @Override
            public boolean isBarrier(TerritoryClosureSearch.Cell cell) {
                return barrier.test(cell);
            }
        }, range, limit, 256);
    }
}

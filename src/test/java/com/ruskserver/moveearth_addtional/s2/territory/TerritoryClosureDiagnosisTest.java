package com.ruskserver.moveearth_addtional.s2.territory;

import com.ruskserver.moveearth_addtional.s2.territory.TerritoryClosureDiagnosis.Result;
import com.ruskserver.moveearth_addtional.s2.territory.TerritoryClosureDiagnosis.Status;
import com.ruskserver.moveearth_addtional.s2.territory.TerritoryClosureSearch.Cell;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerritoryClosureDiagnosisTest {
    private static final Cell CORE = new Cell(0, 0, 0);
    private static final int RANGE = 32;

    /** A world of reinforced walls, unreinforced blocks and air; ground below y = -6 is dirt. */
    private static class World implements TerritoryClosureDiagnosis.View {
        final Set<Cell> barriers = new HashSet<>();
        final Set<Cell> blocks = new HashSet<>();
        final Set<Cell> air = new HashSet<>();

        /** Reinforced hollow box whose inside spans -h..h on x and z and 0..height-1 on y. */
        void box(int h, int height) {
            for (int x = -h - 1; x <= h + 1; x++) {
                for (int y = -1; y <= height; y++) {
                    for (int z = -h - 1; z <= h + 1; z++) {
                        boolean shell = Math.abs(x) == h + 1 || Math.abs(z) == h + 1 || y == -1 || y == height;
                        if (shell) barriers.add(new Cell(x, y, z));
                    }
                }
            }
        }

        void hole(int x, int y, int z) {
            barriers.remove(new Cell(x, y, z));
            air.add(new Cell(x, y, z));
        }

        void block(int x, int y, int z) {
            barriers.remove(new Cell(x, y, z));
            blocks.add(new Cell(x, y, z));
        }

        @Override public boolean isLoaded(Cell cell) { return true; }
        @Override public boolean isBarrier(Cell cell) { return barriers.contains(cell); }
        @Override public boolean isEmpty(Cell cell) {
            if (blocks.contains(cell)) return false;
            if (air.contains(cell)) return true;
            return cell.y() > -6;
        }
    }

    private static Set<Cell> holes(Result result) {
        return new HashSet<>(result.holes());
    }

    @Test
    void aSealedCoreHasNoHoles() {
        World world = new World();
        world.box(3, 4);
        Result result = TerritoryClosureDiagnosis.diagnose(CORE, world, RANGE);
        assertEquals(Status.SEALED, result.status());
        assertTrue(result.holes().isEmpty());
    }

    @Test
    void aMissingBlockIsReportedWhereItIsNotPastIt() {
        World world = new World();
        world.box(3, 4);
        world.hole(4, 1, 0);
        Result result = TerritoryClosureDiagnosis.diagnose(CORE, world, RANGE);
        assertEquals(Status.OPEN, result.status());
        assertEquals(Set.of(new Cell(4, 1, 0)), holes(result));
        assertTrue(result.complete());
        List<Cell> path = result.paths().getFirst();
        assertEquals(new Cell(4, 1, 0), path.getLast(), "the way shown ends at the hole");
    }

    @Test
    void everyHoleIsReportedInOneDiagnosis() {
        World world = new World();
        world.box(3, 4);
        world.hole(4, 1, 0);          // a missing block
        world.block(-4, 2, 1);        // unreinforced stone in the wall
        world.block(0, 4, -2);        // the ceiling
        world.hole(2, -1, 2);         // the floor
        Result result = TerritoryClosureDiagnosis.diagnose(CORE, world, RANGE);
        assertEquals(Set.of(new Cell(4, 1, 0), new Cell(-4, 2, 1), new Cell(0, 4, -2), new Cell(2, -1, 2)),
                holes(result));
        assertTrue(result.complete());
    }

    @Test
    void anOpenOuterShellIsNotBlamedForAHoleInTheInnerOne() {
        World world = new World();
        world.box(3, 4);
        world.box(8, 9);
        world.hole(4, 1, 0);
        // The outer wall has a gate two blocks high and a missing block.
        world.hole(9, 0, 0);
        world.hole(9, 1, 0);
        world.hole(-9, 3, 3);
        Result result = TerritoryClosureDiagnosis.diagnose(CORE, world, RANGE);
        assertEquals(Set.of(new Cell(4, 1, 0)), holes(result));
    }

    @Test
    void aDoorAtTheEndOfACorridorIsBlamedRatherThanTheCorridor() {
        World world = new World();
        world.box(2, 3);
        // A corridor one wide and two high runs from the room's east wall to a door.
        for (int x = 3; x <= 8; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -1; z <= 1; z++) world.barriers.add(new Cell(x, y, z));
            }
            if (x < 8) {
                world.hole(x, 0, 0);
                world.hole(x, 1, 0);
            }
        }
        world.block(8, 0, 0);
        world.block(8, 1, 0);
        Result result = TerritoryClosureDiagnosis.diagnose(CORE, world, RANGE);
        assertEquals(Set.of(new Cell(8, 0, 0), new Cell(8, 1, 0)), holes(result));
    }

    @Test
    void aWideOpeningStopsAtTheCapAndSaysMoreMayRemain() {
        World world = new World();
        world.box(3, 4);
        for (int y = 0; y < 4; y++) for (int z = -3; z <= 3; z++) world.hole(4, y, z);
        Result result = TerritoryClosureDiagnosis.diagnose(CORE, world, RANGE);
        assertEquals(Status.OPEN, result.status());
        assertEquals(TerritoryClosureDiagnosis.MAX_HOLES, result.holes().size());
        assertFalse(result.complete());
        for (Cell hole : result.holes()) assertTrue(Math.abs(hole.x()) <= 5, "holes stay at the opening: " + hole);
    }

    @Test
    void exactlyAsManyHolesAsTheCapAreAllReportedAsComplete() {
        World world = new World();
        world.box(5, 4);
        Set<Cell> expected = new HashSet<>();
        for (int i = 0; i < TerritoryClosureDiagnosis.MAX_HOLES; i++) {
            Cell hole = new Cell(6, i % 4, -5 + (i / 4) * 2);
            world.hole(hole.x(), hole.y(), hole.z());
            expected.add(hole);
        }
        Result result = TerritoryClosureDiagnosis.diagnose(CORE, world, RANGE);
        assertEquals(expected, holes(result));
        assertTrue(result.complete());
    }

    @Test
    void anUnloadedChunkStopsTheDiagnosis() {
        World world = new World() {
            @Override public boolean isLoaded(Cell cell) { return cell.x() < 10; }
        };
        world.box(3, 4);
        world.hole(4, 1, 0);
        assertEquals(Status.UNLOADED, TerritoryClosureDiagnosis.diagnose(CORE, world, RANGE).status());
    }
}

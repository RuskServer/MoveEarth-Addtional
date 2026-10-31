package com.ruskserver.moveearth_addtional.pvp.map;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PvpMapGeometryTest {
    @Test
    void cornersInAnyOrderMakeTheSameBox() {
        PvpMapGeometry.Box box = PvpMapGeometry.Box.of(10, 90, -5, -10, 60, 5);
        assertEquals(new PvpMapGeometry.Box(-10, 60, -5, 10, 90, 5), box);
        assertEquals(21, box.width());
        assertEquals(11, box.depth());
    }

    @Test
    void containsIsInclusiveOnEveryAxis() {
        PvpMapGeometry.Box box = PvpMapGeometry.Box.of(0, 60, 0, 10, 80, 10);
        assertTrue(box.contains(0, 60, 0));
        assertTrue(box.contains(10, 80, 10));
        assertFalse(box.contains(11, 70, 5));
        assertFalse(box.contains(5, 59, 5));
        assertTrue(box.containsBox(PvpMapGeometry.Box.of(2, 62, 2, 8, 70, 8)));
        assertFalse(box.containsBox(PvpMapGeometry.Box.of(2, 62, 2, 12, 70, 8)));
    }

    @Test
    void aMapWithNoAreaGetsOneAroundItsSpawnsAndHill() {
        PvpMapGeometry.Box box = PvpMapGeometry.derived(List.of(
                new PvpMapGeometry.Point(-20, 80, 0), new PvpMapGeometry.Point(20, 80, 0),
                new PvpMapGeometry.Point(-4, 76, -4), new PvpMapGeometry.Point(4, 90, 4)));
        assertEquals(-20 - PvpMapGeometry.DERIVED_PADDING, box.minX());
        assertEquals(20 + PvpMapGeometry.DERIVED_PADDING, box.maxX());
        assertEquals(76 - PvpMapGeometry.DERIVED_BELOW, box.minY());
        assertEquals(90 + PvpMapGeometry.DERIVED_ABOVE, box.maxY());
        assertTrue(box.contains(-20, 80, 0));
    }

    @Test
    void anAreaWiderThanTheLimitIsTooLarge() {
        assertFalse(PvpMapGeometry.Box.of(0, 0, 0, PvpMapGeometry.MAX_SPAN - 1, 10, 10).tooLarge());
        assertTrue(PvpMapGeometry.Box.of(0, 0, 0, PvpMapGeometry.MAX_SPAN, 10, 10).tooLarge());
    }

    @Test
    void largerAreasUseBiggerCellsToStayWithinTheMapSize() {
        assertEquals(1, PvpMapGeometry.cellSize(PvpMapGeometry.Box.of(0, 0, 0, 255, 0, 100), 256));
        assertEquals(2, PvpMapGeometry.cellSize(PvpMapGeometry.Box.of(0, 0, 0, 256, 0, 100), 256));
        assertEquals(2, PvpMapGeometry.cellSize(PvpMapGeometry.Box.of(0, 0, 0, 511, 0, 100), 256));
        assertEquals(129, PvpMapGeometry.cells(257, 2));
    }

    @Test
    void anAdminsAreaIsAnAreaOnTheMapAtAnyHeight() {
        // Corners clicked on the ground: players standing and jumping above them are still inside.
        PvpMapGeometry.Box box = PvpMapGeometry.own(0, 64, 0, 100, 64, 100);
        assertTrue(box.containsXZ(50, 50));
        assertFalse(box.containsXZ(101, 50));
        assertEquals(64 - PvpMapGeometry.OWN_BELOW, box.minY());
        assertEquals(64 + PvpMapGeometry.OWN_ABOVE, box.maxY());
        assertTrue(box.containsXZ(PvpMapGeometry.Box.of(10, 200, 10, 20, 210, 20)));
        assertFalse(box.containsXZ(PvpMapGeometry.Box.of(90, 64, 90, 110, 64, 110)));
    }
}

package com.ruskserver.moveearth_addtional.compat.vehicle;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SableBodyDensityIndexTest {
    @Test
    void negativeCoordinatesAndCellBoundariesDoNotSplitTheBudget() {
        var index = new SableBodyDensityIndex<Integer>(128);
        index.put(1, new SableBodyDensityPolicy.Point(-128, 0));
        index.put(2, new SableBodyDensityPolicy.Point(0, 0));
        index.put(3, new SableBodyDensityPolicy.Point(128, 0));
        index.put(4, new SableBodyDensityPolicy.Point(100, 100));
        assertEquals(3, index.count(new SableBodyDensityPolicy.Point(0, 0), 100));
        index.remove(1);
        assertEquals(2, index.count(new SableBodyDensityPolicy.Point(0, 0), 100));
    }

    @Test
    void movementReplacesTheOldLocationInsteadOfCountingOneBodyTwice() {
        var index = new SableBodyDensityIndex<Integer>(128);
        index.put(1, new SableBodyDensityPolicy.Point(0, 0));
        index.put(1, new SableBodyDensityPolicy.Point(1000, 0));
        assertEquals(0, index.count(new SableBodyDensityPolicy.Point(0, 0), 100));
        assertEquals(1, index.count(new SableBodyDensityPolicy.Point(1000, 0), 100));
    }

    @Test
    void edgeDebrisCanRelieveACrowdedProtectedCenterWithoutOverDeleting() {
        var bodies = new SableBodyDensityIndex<Integer>(128);
        var hotspots = new SableBodyDensityIndex<Integer>(128);
        var left = new SableBodyDensityPolicy.Point(-100, 0);
        var center = new SableBodyDensityPolicy.Point(0, 0);
        var right = new SableBodyDensityPolicy.Point(100, 0);
        for (int id = 0; id < 63; id++) bodies.put(id, left);
        bodies.put(63, center);
        bodies.put(64, right);
        hotspots.put(63, center);
        assertEquals(2, bodies.count(right, 65));
        assertEquals(65, bodies.overcrowdedCountNear(right, hotspots, 64));
        bodies.remove(64);
        assertEquals(0, bodies.overcrowdedCountNear(left, hotspots, 64));
    }
}

package com.ruskserver.moveearth_addtional.compat.vehicle;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SableBodyDensityPolicyTest {
    private static SableBodyDensityPolicy.Point point(double x, double z) {
        return new SableBodyDensityPolicy.Point(x, z);
    }

    @Test
    void countsAcrossChunkBoundariesAndIncludesTheRadiusEdge() {
        assertEquals(3, SableBodyDensityPolicy.projectedCount(
                List.of(point(15, 0), point(144, 0), point(144.01, 0)), List.of(point(16, 0)), 128));
    }

    @Test
    void radiusIsCircularAndUsesBothWorldAxes() {
        assertEquals(2, SableBodyDensityPolicy.projectedCount(
                List.of(point(0, 128), point(-100, -100)), List.of(point(0, 0)), 128));
    }

    @Test
    void splitSiblingsConsumeBudgetTogetherRatherThanIndividually() {
        var existing = IntStream.range(0, 63).mapToObj(i -> point(i, 0)).toList();
        assertEquals(64, SableBodyDensityPolicy.projectedCount(existing, List.of(point(0, 0)), 128));
        assertEquals(65, SableBodyDensityPolicy.projectedCount(existing,
                List.of(point(0, 0), point(1, 0)), 128));
    }

    @Test
    void distantAreasDoNotShareBudgetAndAnEmptySplitAddsNothing() {
        assertEquals(1, SableBodyDensityPolicy.projectedCount(List.of(point(-1000, 0)),
                List.of(point(0, 0), point(1000, 0)), 128));
        assertEquals(0, SableBodyDensityPolicy.projectedCount(List.of(point(0, 0)), List.of(), 128));
    }

    @Test
    void hugeFragmentBurstsStopCountingAsSoonAsOverBudget() {
        var fragments = java.util.Collections.nCopies(100_000, point(0, 0));
        assertEquals(65, SableBodyDensityPolicy.projectedCount(List.of(), fragments, 128, 64));
    }

    @Test
    void addingOnTheEdgeCannotOvercrowdAnExistingAnchor() {
        var existing = new java.util.ArrayList<SableBodyDensityPolicy.Point>(
                java.util.Collections.nCopies(63, point(-100, 0)));
        existing.add(point(0, 0));
        // The new anchor sees only the middle anchor, but the middle would see 65 bodies.
        assertEquals(65, SableBodyDensityPolicy.projectedCount(existing, List.of(point(100, 0)), 128, 64));
    }
}

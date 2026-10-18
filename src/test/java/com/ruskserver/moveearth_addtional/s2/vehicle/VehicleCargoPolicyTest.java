package com.ruskserver.moveearth_addtional.s2.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VehicleCargoPolicyTest {
    @Test
    void stacksArePackedTwentySevenToABox() {
        assertEquals(0, VehicleCargoPolicy.boxes(0));
        assertEquals(1, VehicleCargoPolicy.boxes(27));
        assertEquals(2, VehicleCargoPolicy.boxes(28));
    }

    @Test
    void theOldestSnapshotGoesOnceTheLimitIsReached() {
        assertEquals(-1, VehicleCargoPolicy.dropIndex(VehicleCargoPolicy.KEPT_SNAPSHOTS - 1));
        assertEquals(VehicleCargoPolicy.KEPT_SNAPSHOTS - 1, VehicleCargoPolicy.dropIndex(VehicleCargoPolicy.KEPT_SNAPSHOTS));
    }

    @Test
    void generationOneIsTheNewest() {
        assertEquals(0, VehicleCargoPolicy.index(1, 3));
        assertEquals(2, VehicleCargoPolicy.index(3, 3));
        assertEquals(-1, VehicleCargoPolicy.index(4, 3));
        assertEquals(-1, VehicleCargoPolicy.index(0, 3));
    }

    @Test
    void theNewestSplitRecordIsNotPushedOut() {
        int full = VehicleCargoPolicy.KEPT_SNAPSHOTS;
        assertEquals(full - 2, VehicleCargoPolicy.dropIndex(full, full - 1));
        assertEquals(full - 1, VehicleCargoPolicy.dropIndex(full, 3));
        assertEquals(-1, VehicleCargoPolicy.dropIndex(full - 1, full - 2));
    }
}

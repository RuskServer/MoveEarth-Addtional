package com.ruskserver.moveearth_addtional.handler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RandomSpawnCapacityPolicyTest {
    @Test
    void currentTerrainLandAreaProducesAboutFiveHundredPoints() {
        assertEquals(504, RandomSpawnCapacityPolicy.targetForLandArea(20_132_352.0D));
    }

    @Test
    void smallAndInvalidAreasAreHandledSafely() {
        assertEquals(0, RandomSpawnCapacityPolicy.targetForLandArea(0.0D));
        assertEquals(0, RandomSpawnCapacityPolicy.targetForLandArea(Double.NaN));
        assertEquals(64, RandomSpawnCapacityPolicy.targetForLandArea(1.0D));
    }

    @Test
    void capacityNeverExceedsSavedDataLimit() {
        assertEquals(8_192, RandomSpawnCapacityPolicy.targetForLandArea(Double.MAX_VALUE));
    }
}

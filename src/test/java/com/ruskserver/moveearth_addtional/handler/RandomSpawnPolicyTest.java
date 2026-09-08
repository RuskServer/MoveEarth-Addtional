package com.ruskserver.moveearth_addtional.handler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomSpawnPolicyTest {
    @Test
    void acceptsDistancesAtTheirInclusiveLimits() {
        assertTrue(RandomSpawnPolicy.meetsDistanceRequirements(100.0D, 400.0D, 100.0D, 400.0D));
    }

    @Test
    void rejectsAPlayerOrPreviousSpawnThatIsTooClose() {
        assertFalse(RandomSpawnPolicy.meetsDistanceRequirements(99.0D, 400.0D, 100.0D, 400.0D));
        assertFalse(RandomSpawnPolicy.meetsDistanceRequirements(100.0D, 399.0D, 100.0D, 400.0D));
    }

    @Test
    void capsDistanceTermsSoExtremeCoordinatesDoNotDominate() {
        assertEquals(138.5D, RandomSpawnPolicy.score(500.0D, 800.0D, 3.5D, 100.0D));
    }
}

package com.ruskserver.moveearth_addtional.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How much jaggedness the bedrock at a point is entitled to.
 *
 * <p>The point of the mapping is that the two ends stay far apart -- soft ground
 * has to come out gentler than the single setting it replaced, or the world just
 * gets spikier everywhere -- and that the sharpest setting stays rare.
 */
class LithologyTest {
    private static final double SOFT = 0.20;

    @Test
    void softestRockKeepsOnlyTheFloor() {
        assertEquals(SOFT, TerrainTile.lithologyOf(0.0, SOFT), 1e-9);
    }

    @Test
    void hardestRockKeepsAllOfIt() {
        assertEquals(1.0, TerrainTile.lithologyOf(1.0, SOFT), 1e-9);
    }

    @Test
    void middlingRockIsNearerTheGentleEnd() {
        // squared, so half-hard ground gets a quarter of the range above the floor
        assertEquals(SOFT + 0.25 * (1.0 - SOFT), TerrainTile.lithologyOf(0.5, SOFT), 1e-9);
        assertTrue(TerrainTile.lithologyOf(0.5, SOFT) < 0.5);
    }

    @Test
    void risesWithHardnessAndStaysInRange() {
        double previous = -1.0;
        for (int i = 0; i <= 20; i++) {
            double value = TerrainTile.lithologyOf(i / 20.0, SOFT);
            assertTrue(value > previous, "not increasing at " + i);
            assertTrue(value >= SOFT && value <= 1.0, "out of range at " + i);
            previous = value;
        }
    }

    @Test
    void clampsHardnessOutsideZeroToOne() {
        assertEquals(SOFT, TerrainTile.lithologyOf(-3.0, SOFT), 1e-9);
        assertEquals(1.0, TerrainTile.lithologyOf(4.0, SOFT), 1e-9);
    }
}

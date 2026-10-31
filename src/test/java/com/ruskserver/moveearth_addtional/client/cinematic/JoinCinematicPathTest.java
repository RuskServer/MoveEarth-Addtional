package com.ruskserver.moveearth_addtional.client.cinematic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinCinematicPathTest {
    @Test
    void opensAtTheOpeningHeightLookingDownAndCutsLower() {
        var first = JoinCinematicPath.at(0.0, 400.0, -1.0);
        assertEquals(0, first.shot());
        assertEquals(400.0, first.height(), 1.0E-9);
        assertTrue(first.shotStart());
        var second = JoinCinematicPath.at(1.35, 400.0, 1.25);
        assertEquals(1, second.shot());
        assertTrue(second.shotStart());
        assertTrue(second.height() < 400.0 * 0.43);
        assertFalse(JoinCinematicPath.at(1.4, 400.0, 1.35).shotStart());
    }

    @Test
    void landsInThePlayersEyesAtTheEnd() {
        double total = JoinCinematicPath.totalSeconds();
        assertEquals(4.5, total, 1.0E-9);
        var start = JoinCinematicPath.at(3.11, 400.0, 3.09);
        assertEquals(3, start.shot());
        assertTrue(start.landing() < 0.05);
        var end = JoinCinematicPath.at(total, 400.0, total - 0.01);
        assertEquals(1.0, end.landing(), 1.0E-9);
        assertTrue(JoinCinematicPath.finished(total));
        assertFalse(JoinCinematicPath.finished(total - 0.1));
    }

    @Test
    void theLastShotIsNeverInsideTheGround() {
        for (double t = 0.0; t < JoinCinematicPath.totalSeconds(); t += 0.05) {
            var frame = JoinCinematicPath.at(t, 10.0, t - 0.05);
            assertTrue(frame.height() >= JoinCinematicPath.MIN_LAST_HEIGHT - 1.0E-9, "at " + t);
        }
    }

    @Test
    void distantHorizonsLetsTheCameraClimbHigher() {
        double vanilla = JoinCinematicPath.openingHeight(12 * 16, 0);
        assertEquals(12 * 16 * 0.7, vanilla, 1.0E-9);
        assertEquals(200.0, JoinCinematicPath.openingHeight(32 * 16, 0), 1.0E-9);
        assertEquals(600.0, JoinCinematicPath.openingHeight(12 * 16, 256 * 16), 1.0E-9);
        assertTrue(JoinCinematicPath.openingHeight(12 * 16, 64 * 16) > vanilla);
    }
}

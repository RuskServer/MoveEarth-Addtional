package com.ruskserver.moveearth_addtional.pvp.deploy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PvpDeployPolicyTest {
    @Test
    void aPointWithAnEnemyCloseByIsDangerous() {
        assertTrue(PvpDeployPolicy.dangerous(15.0 * 15.0));
        assertFalse(PvpDeployPolicy.dangerous(16.0 * 16.0));
        assertFalse(PvpDeployPolicy.dangerous(Double.MAX_VALUE));
    }

    @Test
    void theChosenPointIsUsedOnlyWhenItIsTheTeamsAndSafe() {
        assertTrue(PvpDeployPolicy.useChosen(true, false));
        assertFalse(PvpDeployPolicy.useChosen(true, true));
        assertFalse(PvpDeployPolicy.useChosen(false, false));
    }

    @Test
    void leavingTheAreaWarnsForFiveSecondsThenHurtsEverySecond() {
        assertEquals(5, PvpDeployPolicy.outOfBoundsSecondsLeft(1));
        assertEquals(1, PvpDeployPolicy.outOfBoundsSecondsLeft(99));
        assertEquals(0, PvpDeployPolicy.outOfBoundsSecondsLeft(100));
        assertFalse(PvpDeployPolicy.outOfBoundsDamage(99));
        assertTrue(PvpDeployPolicy.outOfBoundsDamage(100));
        assertFalse(PvpDeployPolicy.outOfBoundsDamage(110));
        assertTrue(PvpDeployPolicy.outOfBoundsDamage(120));
    }
}

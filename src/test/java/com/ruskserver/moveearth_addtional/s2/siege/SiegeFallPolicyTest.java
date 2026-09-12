package com.ruskserver.moveearth_addtional.s2.siege;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiegeFallPolicyTest {
    @Test
    void protectionFallsFromOuterRingsTowardCore() {
        assertFalse(SiegeFallPolicy.protectionDisabled(0, 4, 4));
        assertTrue(SiegeFallPolicy.protectionDisabled(1, 4, 4));
        assertTrue(SiegeFallPolicy.protectionDisabled(1, 4, 3));
        assertFalse(SiegeFallPolicy.protectionDisabled(1, 4, 2));
        assertTrue(SiegeFallPolicy.protectionDisabled(2, 4, 1));
        assertFalse(SiegeFallPolicy.protectionDisabled(2, 4, 0));
        assertTrue(SiegeFallPolicy.protectionDisabled(3, 4, 0));
    }

    @Test
    void uncontestedDefendersCaptureAndAttackersContest() {
        var captured = SiegeFallPolicy.advance(36000L, 0L,
                SiegeFallPolicy.Presence.DEFENDER_ONLY, 20L, 36000L, 12000L, 60L);
        assertEquals(20L, captured.captureTicks());
        var contested = SiegeFallPolicy.advance(captured.remainingTicks(), captured.captureTicks(),
                SiegeFallPolicy.Presence.CONTESTED, 20L, 36000L, 12000L, 60L);
        assertEquals(20L, contested.captureTicks());
        var recovered = SiegeFallPolicy.advance(contested.remainingTicks(), contested.captureTicks(),
                SiegeFallPolicy.Presence.DEFENDER_ONLY, 40L, 36000L, 12000L, 60L);
        assertTrue(recovered.recovered());
    }

    @Test
    void emptyCaptureAreaDecaysAtHalfSpeedAndTimerStillAdvances() {
        var result = SiegeFallPolicy.advance(100L, 50L,
                SiegeFallPolicy.Presence.EMPTY_OR_ATTACKER, 20L, 100L, 30L, 200L);
        assertEquals(80L, result.remainingTicks());
        assertEquals(40L, result.captureTicks());
    }
}

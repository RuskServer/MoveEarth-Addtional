package com.ruskserver.moveearth_addtional.s2.siege;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiegeTimerPolicyTest {
    @Test
    void ineffectiveAttemptStartsInitialLockWithoutExtendingIt() {
        var state = SiegeTimerPolicy.begin(false, 6000L, 36000L);
        var advanced = SiegeTimerPolicy.advance(state, 20L);
        assertEquals(SiegeTimerPolicy.Phase.INITIAL_LOCK, advanced.phase());
        assertEquals(5980L, SiegeTimerPolicy.attack(advanced, false, 36000L).remainingTicks());
    }

    @Test
    void effectiveDamageStartsAndResetsRollingTimer() {
        var initial = SiegeTimerPolicy.begin(false, 6000L, 36000L);
        var rolling = SiegeTimerPolicy.attack(initial, true, 36000L);
        assertEquals(SiegeTimerPolicy.Phase.ROLLING, rolling.phase());
        assertEquals(36000L, rolling.remainingTicks());
        assertEquals(36000L, SiegeTimerPolicy.attack(
                SiegeTimerPolicy.advance(rolling, 100L), true, 36000L).remainingTicks());
    }

    @Test
    void serverOpenTicksExpireTheTimer() {
        var active = new SiegeTimerPolicy.State(SiegeTimerPolicy.Phase.ROLLING, 20L);
        assertFalse(SiegeTimerPolicy.advance(active, 19L).expired());
        assertTrue(SiegeTimerPolicy.advance(active, 20L).expired());
    }
}

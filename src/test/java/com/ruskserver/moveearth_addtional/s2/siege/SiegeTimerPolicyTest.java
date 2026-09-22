package com.ruskserver.moveearth_addtional.s2.siege;

import org.junit.jupiter.api.DisplayName;
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

    @Test
    @DisplayName("the clock stops only while enemies stand in the territory")
    void holdsWhileContested() {
        assertTrue(SiegeTimerPolicy.holds(true, 0L, 100L));
        assertFalse(SiegeTimerPolicy.holds(false, 0L, 100L));
    }

    @Test
    @DisplayName("the budget runs out at the budget, not past it")
    void holdBudgetBoundary() {
        // One tick either side of the edge. A siege decided by an off-by-one
        // here would look like nothing at all from inside the game.
        assertTrue(SiegeTimerPolicy.holds(true, 99L, 100L));
        assertFalse(SiegeTimerPolicy.holds(true, 100L, 100L));
        assertFalse(SiegeTimerPolicy.holds(true, 101L, 100L));
    }

    @Test
    @DisplayName("a zero budget never stops the clock")
    void holdCanBeDisabled() {
        // How an operator turns the behaviour off: standing in the territory
        // stops counting for anything rather than counting a little.
        assertFalse(SiegeTimerPolicy.holds(true, 0L, 0L));
    }

    @Test
    @DisplayName("only recent work counts as pressing the siege")
    void pressingWindow() {
        assertTrue(SiegeTimerPolicy.pressing(0L, 2400L));
        assertTrue(SiegeTimerPolicy.pressing(2400L, 2400L));
        assertFalse(SiegeTimerPolicy.pressing(2401L, 2400L));
    }

    @Test
    @DisplayName("a player who has never acted is not pressing anything")
    void pressingNeedsEvidence() {
        // Silence ends the hold rather than extending it: the cost of counting
        // someone falls on the defender's nation, which stays locked in a war.
        assertFalse(SiegeTimerPolicy.pressing(-1L, 2400L));
    }

    @Test
    @DisplayName("a zero window still counts the instant of the act")
    void pressingZeroWindow() {
        // Set to zero the behaviour is nearly off, but not retroactively wrong:
        // the tick someone swung still counts, and the one after does not.
        assertTrue(SiegeTimerPolicy.pressing(0L, 0L));
        assertFalse(SiegeTimerPolicy.pressing(1L, 0L));
    }
}

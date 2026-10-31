package com.ruskserver.moveearth_addtional.compat.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SableVoidRescuePolicyTest {
    @Test
    void repeatFallsFiveSecondsApartCannotTriggerAnotherLift() {
        var state = SableVoidRescuePolicy.attempted(SableVoidRescuePolicy.State.EMPTY, 100);
        assertFalse(SableVoidRescuePolicy.mayAttempt(state, 200, 3, 1200));
        assertFalse(SableVoidRescuePolicy.mayAttempt(state, 1299, 3, 1200));
        assertTrue(SableVoidRescuePolicy.mayAttempt(state, 1300, 3, 1200));
    }

    @Test
    void threeFailedOrSuccessfulAttemptsExhaustTheBudgetEvenAfterCooldown() {
        var state = SableVoidRescuePolicy.State.EMPTY;
        for (int i = 0; i < 3; i++) {
            long now = i * 1200L;
            assertTrue(SableVoidRescuePolicy.mayAttempt(state, now, 3, 1200));
            state = SableVoidRescuePolicy.attempted(state, now);
        }
        assertFalse(SableVoidRescuePolicy.mayAttempt(state, 10000, 3, 1200));
        assertTrue(SableVoidRescuePolicy.reported(state).suspensionReported());
    }

    @Test
    void brieflyPassingAboveTheFloorDoesNotRearmTheRescueLoop() {
        var state = SableVoidRescuePolicy.attempted(SableVoidRescuePolicy.State.EMPTY, 0);
        state = SableVoidRescuePolicy.observe(state, 10, true, 600);
        state = SableVoidRescuePolicy.observe(state, 110, false, 600);
        state = SableVoidRescuePolicy.observe(state, 500, true, 600);
        state = SableVoidRescuePolicy.observe(state, 1099, true, 600);
        assertEquals(1, state.attempts());
        state = SableVoidRescuePolicy.observe(state, 1100, true, 600);
        assertEquals(SableVoidRescuePolicy.State.EMPTY, state);
        assertTrue(SableVoidRescuePolicy.mayAttempt(state, 1100, 3, 1200));
    }

    @Test
    void unloadedTimeDoesNotRearmAPreviouslyExhaustedCraft() {
        var restored = SableVoidRescuePolicy.restore(3, 2400, true);
        assertFalse(SableVoidRescuePolicy.mayAttempt(restored, 10000, 3, 1200));
        var observed = SableVoidRescuePolicy.observe(restored, 10000, true, 600);
        assertEquals(3, observed.attempts());
        assertEquals(10000, observed.safeSince());
        assertEquals(SableVoidRescuePolicy.State.EMPTY,
                SableVoidRescuePolicy.observe(observed, 10600, true, 600));
    }

    @Test
    void raisingTheAttemptLimitAllowsAnotherAttemptAfterCooldown() {
        var restored = SableVoidRescuePolicy.restore(3, 2400, true);
        assertFalse(SableVoidRescuePolicy.mayAttempt(restored, 3600, 3, 1200));
        assertTrue(SableVoidRescuePolicy.mayAttempt(restored, 3600, 4, 1200));
    }
}

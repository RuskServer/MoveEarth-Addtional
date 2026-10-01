package com.ruskserver.moveearth_addtional.s2.nation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinApplicationCooldownTest {
    private static final long COOLDOWN = JoinApplicationCooldownPolicy.COOLDOWN_MILLIS;

    @Test
    void aPlayerWithNoRecentChangeMayApply() {
        assertTrue(JoinApplicationCooldownPolicy.applyAllowed(null, 1_000L));
    }

    @Test
    void theNextApplicationWaitsForTheCooldown() {
        assertFalse(JoinApplicationCooldownPolicy.applyAllowed(1_000L, 1_000L));
        assertFalse(JoinApplicationCooldownPolicy.applyAllowed(1_000L, 1_000L + COOLDOWN - 1));
        assertTrue(JoinApplicationCooldownPolicy.applyAllowed(1_000L, 1_000L + COOLDOWN));
    }

    @Test
    void anApplyCancelLoopIsPacedToOneApplicationPerCooldown() {
        Long lastChange = null;
        int applications = 0;
        // A client alternating apply/cancel every 50 ms for one minute.
        for (long now = 0L; now < 60_000L; now += 100L) {
            if (JoinApplicationCooldownPolicy.applyAllowed(lastChange, now)) {
                applications++;
                lastChange = now + 50L; // the cancel that follows also counts as a change
            }
        }
        assertTrue(applications <= 60_000L / COOLDOWN + 1, "applications: " + applications);
    }

    @Test
    void aClockMovedBackDoesNotLockAPlayerOut() {
        assertTrue(JoinApplicationCooldownPolicy.applyAllowed(1_000L, 500L));
    }
}

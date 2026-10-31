package com.ruskserver.moveearth_addtional.s2.presence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReturnAfterBreakPolicyTest {
    private static final long HOUR = 60L * 60L * 1000L;
    private static final long NOW = 1_800_000_000_000L;

    @Test
    void twoDaysAwayCountsAndADayAndAHalfDoesNot() {
        assertTrue(ReturnAfterBreakPolicy.returned(NOW - 48 * HOUR, NOW));
        assertTrue(ReturnAfterBreakPolicy.returned(NOW - 10 * 24 * HOUR, NOW));
        assertFalse(ReturnAfterBreakPolicy.returned(NOW - 36 * HOUR, NOW));
    }

    @Test
    void aPlayerNeverSeenBeforeHasNotReturned() {
        assertFalse(ReturnAfterBreakPolicy.returned(0L, NOW));
    }
}

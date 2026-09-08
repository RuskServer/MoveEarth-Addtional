package com.ruskserver.moveearth_addtional.tpa;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TpaPolicyTest {
    @Test
    void grantsAllowanceOnlyBelowSixHoursAndBeforeThirdUse() {
        assertEquals(TpaPolicy.Mode.BEGINNER, TpaPolicy.mode(0, 0, 6, 3));
        assertEquals(TpaPolicy.Mode.BEGINNER,
                TpaPolicy.mode(6 * TpaPolicy.TICKS_PER_HOUR - 1, 2, 6, 3));
        assertEquals(TpaPolicy.Mode.REGULAR,
                TpaPolicy.mode(6 * TpaPolicy.TICKS_PER_HOUR, 2, 6, 3));
        assertEquals(TpaPolicy.Mode.REGULAR, TpaPolicy.mode(0, 3, 6, 3));
    }

    @Test
    void disabledAllowanceAlwaysUsesRegularRules() {
        assertEquals(TpaPolicy.Mode.REGULAR, TpaPolicy.mode(0, 0, 0, 3));
        assertEquals(TpaPolicy.Mode.REGULAR, TpaPolicy.mode(0, 0, 6, 0));
    }

    @Test
    void cooldownRemainingNeverBecomesNegative() {
        assertEquals(5_000L, TpaPolicy.remainingMillis(15_000L, 10_000L));
        assertEquals(0L, TpaPolicy.remainingMillis(10_000L, 15_000L));
    }
}

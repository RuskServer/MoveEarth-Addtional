package com.ruskserver.moveearth_addtional.s2.reinforcement;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerritoryIntrusionPolicyTest {
    @Test void firstBreakNotifiesThenAtMostEveryFiveMinutes() {
        assertTrue(TerritoryIntrusionPolicy.shouldNotify(0L, 1_000L));
        assertFalse(TerritoryIntrusionPolicy.shouldNotify(1_000L, 1_000L + 299_999L));
        assertTrue(TerritoryIntrusionPolicy.shouldNotify(1_000L, 1_000L + 300_000L));
    }

    @Test void breaksLeftAfterTheLastNoticeAreSentOnceTheWindowPasses() {
        assertEquals(TerritoryIntrusionPolicy.Sweep.KEEP, TerritoryIntrusionPolicy.sweep(1_000L, 7, 1_000L + 299_999L));
        assertEquals(TerritoryIntrusionPolicy.Sweep.FLUSH, TerritoryIntrusionPolicy.sweep(1_000L, 7, 1_000L + 300_000L));
    }

    @Test void entriesWithNothingPendingAreForgottenAfterTheWindow() {
        assertEquals(TerritoryIntrusionPolicy.Sweep.KEEP, TerritoryIntrusionPolicy.sweep(1_000L, 0, 1_000L + 1L));
        assertEquals(TerritoryIntrusionPolicy.Sweep.PRUNE, TerritoryIntrusionPolicy.sweep(1_000L, 0, 1_000L + 300_000L));
    }
}

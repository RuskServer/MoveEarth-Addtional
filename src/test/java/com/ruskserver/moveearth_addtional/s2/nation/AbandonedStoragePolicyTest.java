package com.ruskserver.moveearth_addtional.s2.nation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbandonedStoragePolicyTest {
    @Test
    void theOwnerKeepsTheChestForFourOpenHours() {
        long lost = 1_000L;
        assertFalse(AbandonedStoragePolicy.open(lost, lost + AbandonedStoragePolicy.GRACE_OPEN_TICKS - 1L));
        assertTrue(AbandonedStoragePolicy.open(lost, lost + AbandonedStoragePolicy.GRACE_OPEN_TICKS));
        assertEquals(1L, AbandonedStoragePolicy.remaining(lost, lost + AbandonedStoragePolicy.GRACE_OPEN_TICKS - 1L));
        assertEquals(4L * 72_000L, AbandonedStoragePolicy.GRACE_OPEN_TICKS);
    }

    @Test
    void aChestNeverSeenAbandonedStaysShut() {
        assertFalse(AbandonedStoragePolicy.open(0L, Long.MAX_VALUE));
        assertEquals(0L, AbandonedStoragePolicy.remaining(0L, 5L));
    }
}

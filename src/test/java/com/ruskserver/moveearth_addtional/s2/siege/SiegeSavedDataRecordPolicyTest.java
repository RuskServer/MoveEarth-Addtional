package com.ruskserver.moveearth_addtional.s2.siege;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiegeSavedDataRecordPolicyTest {
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();

    @Test
    void acceptsValidKnownPairWithPositiveRemainingTime() {
        assertTrue(SiegeSavedDataRecordPolicy.acceptsPair(true, true, first, second, 20L, true));
    }

    @Test
    void rejectsMissingOrInvalidUuid() {
        assertFalse(SiegeSavedDataRecordPolicy.acceptsPair(false, true, null, second, 20L, false));
        assertFalse(SiegeSavedDataRecordPolicy.acceptsPair(true, false, first, null, 20L, false));
    }

    @Test
    void rejectsExpiredAndSelfPeaceTruceRecords() {
        assertFalse(SiegeSavedDataRecordPolicy.acceptsPair(true, true, first, second, 0L, true));
        assertFalse(SiegeSavedDataRecordPolicy.acceptsPair(true, true, first, first, 20L, true));
    }
}

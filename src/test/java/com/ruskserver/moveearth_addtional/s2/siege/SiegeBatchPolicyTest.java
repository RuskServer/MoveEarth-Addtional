package com.ruskserver.moveearth_addtional.s2.siege;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiegeBatchPolicyTest {
    @Test
    void firstAttemptAndFirstHitOnACoreAreAlwaysRecorded() {
        assertFalse(SiegeBatchPolicy.repeats(false, false, false));
        assertFalse(SiegeBatchPolicy.repeats(false, false, true));
        // A hit after a plain attempt still moves the Siege to rolling.
        assertFalse(SiegeBatchPolicy.repeats(true, false, true));
    }

    @Test
    void laterAttemptsAndHitsOnTheSameCoreAreRepeats() {
        assertTrue(SiegeBatchPolicy.repeats(true, false, false));
        assertTrue(SiegeBatchPolicy.repeats(false, true, false));
        assertTrue(SiegeBatchPolicy.repeats(true, true, true));
        assertTrue(SiegeBatchPolicy.repeats(false, true, true));
    }
}

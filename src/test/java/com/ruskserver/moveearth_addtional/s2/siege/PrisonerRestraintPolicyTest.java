package com.ruskserver.moveearth_addtional.s2.siege;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrisonerRestraintPolicyTest {
    @Test
    void protectionDelaysCompletionInsteadOfRejectingStart() {
        assertEquals(200, PrisonerRestraintPolicy.protectionRemaining(200, 0));
        assertEquals(100, PrisonerRestraintPolicy.protectionRemaining(200, 100));
        assertEquals(0, PrisonerRestraintPolicy.protectionRemaining(200, 200));
        assertFalse(PrisonerRestraintPolicy.canComplete(60, 60, 100));
        assertTrue(PrisonerRestraintPolicy.canComplete(200, 60, 0));
    }

    @Test
    void unknownDownedTimeDoesNotDeadlockRestraint() {
        assertEquals(0, PrisonerRestraintPolicy.protectionRemaining(200, -1));
        assertFalse(PrisonerRestraintPolicy.canComplete(59, 60, 0));
        assertTrue(PrisonerRestraintPolicy.canComplete(60, 60, 0));
    }
}

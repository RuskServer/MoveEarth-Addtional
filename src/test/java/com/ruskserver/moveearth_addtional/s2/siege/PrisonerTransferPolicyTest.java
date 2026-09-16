package com.ruskserver.moveearth_addtional.s2.siege;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrisonerTransferPolicyTest {
    @Test
    void requiresExplicitConsentAndSameOperationalNation() {
        assertTrue(allowed(true, true));
        assertFalse(allowed(false, true));
        assertFalse(allowed(true, false));
    }

    @Test
    void rejectsDistanceRestrictionAndExistingEscort() {
        assertFalse(PrisonerTransferPolicy.allowed(true, true, true, true, 36.01D,
                true, true, true, false, false));
        assertFalse(PrisonerTransferPolicy.allowed(true, true, true, true, 4.0D,
                true, true, true, true, false));
        assertFalse(PrisonerTransferPolicy.allowed(true, true, true, true, 4.0D,
                true, true, true, false, true));
    }

    private static boolean allowed(boolean consent, boolean sameNation) {
        return PrisonerTransferPolicy.allowed(true, true, true, true, 16.0D,
                true, consent, sameNation, false, false);
    }
}

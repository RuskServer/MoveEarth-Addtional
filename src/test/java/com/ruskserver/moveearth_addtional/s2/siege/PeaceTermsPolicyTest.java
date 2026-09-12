package com.ruskserver.moveearth_addtional.s2.siege;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeaceTermsPolicyTest {
    @Test void acceptsZeroAndConfiguredMaximum() {
        assertTrue(PeaceTermsPolicy.validCompensation(0));
        assertTrue(PeaceTermsPolicy.validCompensation(PeaceTermsPolicy.MAX_GOLD_COMPENSATION));
    }

    @Test void rejectsNegativeAndExcessiveValues() {
        assertFalse(PeaceTermsPolicy.validCompensation(-1));
        assertFalse(PeaceTermsPolicy.validCompensation(PeaceTermsPolicy.MAX_GOLD_COMPENSATION + 1));
    }
}

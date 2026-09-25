package com.ruskserver.moveearth_addtional.economy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LedgerRulesTest {
    @Test void rejectsNegativeOverflowAndInsufficientFunds() {
        assertEquals(LedgerRules.Check.INVALID, LedgerRules.check(0, 0, 0, true, true));
        assertEquals(LedgerRules.Check.INSUFFICIENT_FUNDS, LedgerRules.check(4, 0, 5, true, true));
        assertEquals(LedgerRules.Check.OVERFLOW,
                LedgerRules.check(10, Long.MAX_VALUE, 1, true, true));
        assertEquals(LedgerRules.Check.ALLOWED, LedgerRules.check(10, 0, 5, true, true));
    }
}

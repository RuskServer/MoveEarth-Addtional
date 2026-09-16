package com.ruskserver.moveearth_addtional.s2.dispatch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchContractPolicyTest {
    @Test
    void escrowRoundsDurationUpButBillingRoundsOnlyOnceAtSettlement() {
        assertEquals(600L, DispatchContractPolicy.maximumCost(100L, 2_401L, 2));
        assertEquals(100L, DispatchContractPolicy.earned(100L, 1_200L));
        assertEquals(50L, DispatchContractPolicy.earned(100L, 600L));
        assertEquals(0L, DispatchContractPolicy.earned(100L, 11L));
    }

    @Test
    void requiresBothNationsAndEveryParticipant() {
        assertTrue(DispatchContractPolicy.mayFund(true, true, 3, 3));
        assertFalse(DispatchContractPolicy.mayFund(true, false, 3, 3));
        assertFalse(DispatchContractPolicy.mayFund(true, true, 3, 2));
        assertFalse(DispatchContractPolicy.mayFund(true, true, 0, 0));
    }

    @Test
    void arithmeticSaturatesInsteadOfWrapping() {
        assertEquals(Long.MAX_VALUE,
                DispatchContractPolicy.maximumCost(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE));
    }
}

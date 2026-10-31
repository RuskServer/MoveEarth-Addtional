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

    @Test
    void escrowHoldsTheFlatFeeOnTopOfTheTimedMaximum() {
        assertEquals(500L, DispatchContractPolicy.escrow(500L, 0L, 72_000L, 3));
        assertEquals(500L + DispatchContractPolicy.maximumCost(10L, 72_000L, 3),
                DispatchContractPolicy.escrow(500L, 10L, 72_000L, 3));
        assertEquals(Long.MAX_VALUE, DispatchContractPolicy.escrow(Long.MAX_VALUE, 10L, 72_000L, 3));
    }

    @Test
    void theFlatFeeIsEarnedOnlyByABattleTheProviderSawThrough() {
        assertTrue(DispatchContractPolicy.flatFeeEarned(true, "siege_ended"));
        assertTrue(DispatchContractPolicy.flatFeeEarned(true, "time_limit"));
        assertTrue(DispatchContractPolicy.flatFeeEarned(true, "cancelled:employer"));
        assertTrue(DispatchContractPolicy.flatFeeEarned(true, "employer_gone"));
        assertFalse(DispatchContractPolicy.flatFeeEarned(true, "cancelled:provider"));
        assertFalse(DispatchContractPolicy.flatFeeEarned(true, "provider_left"));
        assertFalse(DispatchContractPolicy.flatFeeEarned(true, "cancelled:admin"));
        assertFalse(DispatchContractPolicy.flatFeeEarned(false, "siege_ended"));
        assertFalse(DispatchContractPolicy.flatFeeEarned(false, "reservation_expired"));
    }
}

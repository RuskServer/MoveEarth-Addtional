package com.ruskserver.moveearth_addtional.s2.territory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerritoryFallSettlementPolicyTest {
    @Test
    void capitalAlwaysKeepsOnlyItsNonTransferableCenterChunk() {
        var decision = TerritoryFallSettlementPolicy.decide(true, 4, ignored -> true);
        assertEquals(TerritoryFallSettlementPolicy.Outcome.CAPITAL_REBUILDING, decision.outcome());
        assertEquals(0, decision.radius());
    }

    @Test
    void outpostKeepsLargestConflictFreeOccupationRadius() {
        var decision = TerritoryFallSettlementPolicy.decide(false, 4, radius -> radius > 2);
        assertEquals(TerritoryFallSettlementPolicy.Outcome.OUTPOST_OCCUPIED, decision.outcome());
        assertEquals(2, decision.radius());
    }

    @Test
    void outpostBecomesNeutralWhenEvenItsCoreChunkConflicts() {
        var decision = TerritoryFallSettlementPolicy.decide(false, 3, ignored -> true);
        assertEquals(TerritoryFallSettlementPolicy.Outcome.OUTPOST_NEUTRALIZED, decision.outcome());
        assertEquals(0, decision.radius());
    }

    @Test
    void individualAttackerNeverOccupiesAnOutpost() {
        assertEquals(TerritoryFallSettlementPolicy.Outcome.OUTPOST_NEUTRALIZED,
                TerritoryFallSettlementPolicy.decideIndividual(false).outcome());
        assertEquals(TerritoryFallSettlementPolicy.Outcome.CAPITAL_REBUILDING,
                TerritoryFallSettlementPolicy.decideIndividual(true).outcome());
    }
}

package com.ruskserver.moveearth_addtional.s2.territory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerritoryFallSettlementPolicyTest {
    @Test
    void capitalLosesTwoRadiusPerFallAndKeepsItsCenterChunk() {
        var decision = TerritoryFallSettlementPolicy.decide(true, 4, ignored -> true);
        assertEquals(TerritoryFallSettlementPolicy.Outcome.CAPITAL_REBUILDING, decision.outcome());
        assertEquals(2, decision.radius());
        assertEquals(0, TerritoryFallSettlementPolicy.decide(true, 2, ignored -> true).radius());
        assertEquals(0, TerritoryFallSettlementPolicy.decide(true, 1, ignored -> true).radius());
        assertEquals(0, TerritoryFallSettlementPolicy.decide(true, 0, ignored -> true).radius());
        assertEquals(1, TerritoryFallSettlementPolicy.decideIndividual(true, 3).radius());
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
                TerritoryFallSettlementPolicy.decideIndividual(false, 4).outcome());
        assertEquals(TerritoryFallSettlementPolicy.Outcome.CAPITAL_REBUILDING,
                TerritoryFallSettlementPolicy.decideIndividual(true, 4).outcome());
    }

    @Test
    void fallenCapitalCannotRegrowWhileTruceOrRecoveryRuns() {
        assertEquals(true, TerritoryFallSettlementPolicy.enlargementLocked(true, 2, 4, true, false));
        assertEquals(true, TerritoryFallSettlementPolicy.enlargementLocked(true, 2, 3, false, true));
        assertEquals(false, TerritoryFallSettlementPolicy.enlargementLocked(true, 2, 1, true, true));
        assertEquals(false, TerritoryFallSettlementPolicy.enlargementLocked(true, 2, 4, false, false));
        assertEquals(false, TerritoryFallSettlementPolicy.enlargementLocked(false, 1, 4, true, true));
    }
}

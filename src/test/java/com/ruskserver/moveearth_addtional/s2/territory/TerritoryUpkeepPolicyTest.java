package com.ruskserver.moveearth_addtional.s2.territory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerritoryUpkeepPolicyTest {
    @Test
    void roundsChunkUnitsUpAndAddsEscalatingOutposts() {
        assertEquals(0L, TerritoryUpkeepPolicy.calculate(0, 0));
        assertEquals(1L, TerritoryUpkeepPolicy.calculate(1, 0));
        assertEquals(2L, TerritoryUpkeepPolicy.calculate(9, 0));
        assertEquals(10L, TerritoryUpkeepPolicy.calculate(9, 1));
        assertEquals(26L, TerritoryUpkeepPolicy.calculate(9, 2));
    }

    @Test
    void addsAFlatCostForEveryRegisteredVehicleCore() {
        assertEquals(20L, TerritoryUpkeepPolicy.calculate(9, 1, 2, 8, 8L, 5L));
        assertEquals(10L, TerritoryUpkeepPolicy.calculate(9, 1, -3, 8, 8L, 5L));
    }

    @Test
    void payNowOnlyChargesAnUpkeepThatIsDue() {
        long due = 1_000_000L;
        org.junit.jupiter.api.Assertions.assertFalse(TerritoryUpkeepPolicy.canPayNow(due - 1L, due));
        org.junit.jupiter.api.Assertions.assertTrue(TerritoryUpkeepPolicy.canPayNow(due, due));
        org.junit.jupiter.api.Assertions.assertTrue(TerritoryUpkeepPolicy.canPayNow(due + 60_000L, due));
    }
}

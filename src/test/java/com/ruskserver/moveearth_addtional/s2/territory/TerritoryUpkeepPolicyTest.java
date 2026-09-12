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
}

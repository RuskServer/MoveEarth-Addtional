package com.ruskserver.moveearth_addtional.s2.territory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpkeepPenaltyPolicyTest {
    @Test
    void progressesFromGraceToWeakeningAndDisable() {
        long start = 10_000L;
        assertEquals(UpkeepPenalty.CURRENT, UpkeepPenaltyPolicy.evaluate(0L, start, 100L, 300L));
        assertEquals(UpkeepPenalty.GRACE, UpkeepPenaltyPolicy.evaluate(start, start + 99L, 100L, 300L));
        assertEquals(UpkeepPenalty.WEAKENED, UpkeepPenaltyPolicy.evaluate(start, start + 100L, 100L, 300L));
        assertEquals(UpkeepPenalty.DISABLED, UpkeepPenaltyPolicy.evaluate(start, start + 300L, 100L, 300L));
    }

    @Test
    void scalesOnlyWeakenedSiegeDamage() {
        assertEquals(10, UpkeepPenaltyPolicy.scaleSiegeDamage(10, UpkeepPenalty.GRACE, 2.0D));
        assertEquals(25, UpkeepPenaltyPolicy.scaleSiegeDamage(10, UpkeepPenalty.WEAKENED, 2.5D));
        assertEquals(Integer.MAX_VALUE,
                UpkeepPenaltyPolicy.scaleSiegeDamage(10, UpkeepPenalty.DISABLED, 2.0D));
    }

    @Test
    void shrinksOnlyDisabledTerritoryAndKeepsCoreChunk() {
        assertEquals(4, UpkeepPenaltyPolicy.effectiveTerritoryRadius(4, UpkeepPenalty.WEAKENED, 50));
        assertEquals(2, UpkeepPenaltyPolicy.effectiveTerritoryRadius(4, UpkeepPenalty.DISABLED, 50));
        assertEquals(1, UpkeepPenaltyPolicy.effectiveTerritoryRadius(3, UpkeepPenalty.DISABLED, 50));
        assertEquals(0, UpkeepPenaltyPolicy.effectiveTerritoryRadius(1, UpkeepPenalty.DISABLED, 0));
        assertEquals(0, UpkeepPenaltyPolicy.effectiveTerritoryRadius(0, UpkeepPenalty.DISABLED, 50));
        assertTrue(UpkeepPenalty.WEAKENED.coreRegenerationEnabled());
        assertFalse(UpkeepPenalty.DISABLED.coreRegenerationEnabled());
    }
}

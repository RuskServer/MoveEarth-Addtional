package com.ruskserver.moveearth_addtional.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HarvestFestivalRulesTest {
    @Test
    void scoringBonusStaysWithinPersonalCap() {
        assertEquals(10, HarvestFestivalRules.addHarvest(0, false));
        assertEquals(11, HarvestFestivalRules.addHarvest(0, true));
        assertEquals(2200, HarvestFestivalRules.addHarvest(2195, true));
    }

    @Test
    void topThreeRewardsAndDailyCurrencyCap() {
        assertEquals(5, HarvestFestivalRules.currency(0, 0));
        assertEquals(4, HarvestFestivalRules.currency(1, 0));
        assertEquals(2, HarvestFestivalRules.currency(3, 0));
        assertEquals(1, HarvestFestivalRules.currency(0, 9));
        assertEquals(0, HarvestFestivalRules.currency(0, 10));
        assertEquals(6, HarvestFestivalRules.diamonds(0));
        assertEquals(3, HarvestFestivalRules.diamonds(2));
        assertEquals(0, HarvestFestivalRules.diamonds(3));
    }

    @org.junit.jupiter.api.Test
    void equalCappedScoresRankByWhoReachedThemFirstNotByUuid() {
        String early = "ffffffff-0000-0000-0000-000000000000", late = "00000000-0000-0000-0000-000000000000";
        org.junit.jupiter.api.Assertions.assertTrue(HarvestFestivalRules.compare(
                HarvestFestivalRules.MAX_POINTS, 100L, early, HarvestFestivalRules.MAX_POINTS, 200L, late) < 0);
        org.junit.jupiter.api.Assertions.assertTrue(HarvestFestivalRules.compare(
                2000, 100L, early, 2100, 900L, late) > 0);
        org.junit.jupiter.api.Assertions.assertTrue(HarvestFestivalRules.compare(
                500, 100L, late, 500, 100L, early) < 0);
    }
}

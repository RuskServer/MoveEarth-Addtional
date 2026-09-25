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
}

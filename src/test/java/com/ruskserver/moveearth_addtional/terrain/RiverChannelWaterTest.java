package com.ruskserver.moveearth_addtional.terrain;

import org.junit.jupiter.api.Test;

import static com.ruskserver.moveearth_addtional.terrain.RiverChannelWater.Fill;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What the channel claims from the aquifer.
 *
 * <p>The two mistakes that matter are opposites: claiming too little leaves the
 * aquifer to miss a narrow channel and the surface rules to grass it over, and
 * claiming too much floods caves and hillsides that are nothing to do with the
 * river.
 */
class RiverChannelWaterTest {
    private static final int LEVEL = 100;

    @Test
    void leavesPositionsWithNoChannelToTheAquifer() {
        assertEquals(Fill.NONE, RiverChannelWater.decide(0.0, LEVEL, 90));
        assertEquals(Fill.NONE, RiverChannelWater.decide(1.0, Integer.MIN_VALUE, 90));
    }

    @Test
    void fillsTheChannelUpToButNotIncludingTheWaterLine() {
        assertEquals(Fill.WATER, RiverChannelWater.decide(1.0, LEVEL, LEVEL - 1));
        assertEquals(Fill.AIR, RiverChannelWater.decide(1.0, LEVEL, LEVEL));
    }

    @Test
    void claimsTheBanksTooNotJustTheChannelCentre() {
        // the carve is a bowl; its sides are below the water line as well
        assertEquals(Fill.WATER, RiverChannelWater.decide(0.05, LEVEL, LEVEL - 1));
    }

    @Test
    void stopsWellBelowTheBedSoCavesStayVanilla() {
        int depth = RiverChannelWater.FILL_DEPTH;
        assertEquals(Fill.WATER, RiverChannelWater.decide(1.0, LEVEL, LEVEL - depth));
        assertEquals(Fill.NONE, RiverChannelWater.decide(1.0, LEVEL, LEVEL - depth - 1));
    }

    @Test
    void keepsTheColumnAboveTheRiverOpen() {
        // so the aquifer cannot hang water or stone over the channel
        assertEquals(Fill.AIR, RiverChannelWater.decide(1.0, LEVEL, LEVEL + 40));
    }
}

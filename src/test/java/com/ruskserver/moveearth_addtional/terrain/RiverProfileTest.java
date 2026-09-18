package com.ruskserver.moveearth_addtional.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiverProfileTest {
    private static final RiverShape SHAPE = RiverShape.NONE;
    private static final int SEA_Y = 63;

    @Test
    void awayFromAnyChannelNothingIsCut() {
        assertEquals(0.0, RiverProfile.carve(0.0, 0.0, 90.0, SEA_Y, SHAPE));
        assertEquals(0.0, RiverProfile.carve(500.0, 40.0, 90.0, SEA_Y, SHAPE));
    }

    /**
     * The point of the distance field: a three block stream stays three blocks
     * wide even though the tile that carries it is far coarser.
     */
    @Test
    void aNarrowStreamStaysNarrow() {
        double width = SHAPE.minWidth();
        double centre = RiverProfile.carve(0.0, width, 70.0, SEA_Y, SHAPE);
        double edge = RiverProfile.carve(width * 0.5, width, 70.0, SEA_Y, SHAPE);
        double outside = RiverProfile.carve(width * 0.5 + RiverProfile.bank(width, SHAPE) + 0.1,
                width, 70.0, SEA_Y, SHAPE);
        assertTrue(centre > 0.0);
        assertEquals(centre, edge, 1.0E-9, "the channel floor is flat across its width");
        assertEquals(0.0, outside, "the cut ends once past the banks");
    }

    @Test
    void widerChannelsAreDeeperUpToTheCap() {
        double narrow = RiverProfile.depth(SHAPE.minWidth(), SHAPE);
        double middle = RiverProfile.depth(40.0, SHAPE);
        double widest = RiverProfile.depth(SHAPE.maxWidth(), SHAPE);
        assertEquals(SHAPE.depthBlocks(), narrow, 1.0E-9);
        assertTrue(middle > narrow);
        assertTrue(widest >= middle);
        assertTrue(widest <= SHAPE.maxDepth());
    }

    @Test
    void theCutTapersToNothingAcrossTheBanks() {
        double width = 30.0;
        double half = width * 0.5;
        double bank = RiverProfile.bank(width, SHAPE);
        double previous = Double.MAX_VALUE;
        for (double d = half; d <= half + bank; d += bank / 8.0) {
            double cut = RiverProfile.carve(d, width, 80.0, SEA_Y, SHAPE);
            assertTrue(cut <= previous + 1.0E-9, "the bank profile must not rise again at " + d);
            previous = cut;
        }
        assertEquals(0.0, RiverProfile.carve(half + bank, width, 80.0, SEA_Y, SHAPE), 1.0E-9);
    }

    /**
     * Water only exists at sea level, so a lowland channel has to reach below it
     * and an upland one must not be gouged down to it.
     */
    @Test
    void lowlandChannelsReachWaterAndUplandOnesDoNot() {
        double width = 20.0;
        double lowlandSurface = 68.0;
        double lowlandBed = lowlandSurface - RiverProfile.carve(0.0, width, lowlandSurface, SEA_Y, SHAPE);
        assertTrue(lowlandBed < SEA_Y, "a lowland channel bed must sit below sea level, was " + lowlandBed);

        double uplandSurface = 160.0;
        double cut = RiverProfile.carve(0.0, width, uplandSurface, SEA_Y, SHAPE);
        assertEquals(RiverProfile.depth(width, SHAPE), cut, 1.0E-9,
                "an upland channel is only incised, never dropped to sea level");
    }

    @Test
    void theLongProfileHasNoStepInIt() {
        double width = 20.0;
        double previous = -1.0;
        for (double surface = SEA_Y; surface <= 160.0; surface += 1.0) {
            double bed = surface - RiverProfile.carve(0.0, width, surface, SEA_Y, SHAPE);
            assertTrue(bed >= previous - 1.0E-9, "the bed dipped going upstream at surface " + surface);
            previous = bed;
        }
    }
}

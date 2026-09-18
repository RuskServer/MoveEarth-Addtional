package com.ruskserver.moveearth_addtional.terrain;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RiverNetworkTest {
    @Test
    void searchRadiusFollowsActualWidthBanksWaterAndBiomeRatherThanRasterClamp() {
        var shape = new RiverShape(3, 72, 200, 2, 1.15, 11, 5, 0.8, 92, 30, 2, 16, 56, 3.5, 16);
        var segments = List.of(new RiverNetwork.Segment(0, 0, 16, 16, 3, 72, 80, 75));
        // banks now scale with the channel, so a wide river reaches much further
        assertEquals(36 + 72 * shape.bankRatio(), RiverNetwork.requiredReach(segments, shape), 1.0E-9);
        // a narrow channel is bounded by the raised water table's radius; the
        // river biome corridor is only 14 blocks and no longer dominates
        var narrow = List.of(new RiverNetwork.Segment(0, 0, 16, 16, 3, 3, 80, 75));
        assertEquals(16, RiverNetwork.requiredReach(narrow, shape));
        // a generous water table can still dominate, but only while it is wider
        // than the channel's own banks
        var wideWater = new RiverShape(3, 72, 200, 2, 1.15, 11, 5, 0.8, 92, 30, 2, 160, 56, 3.5, 16);
        assertEquals(160, RiverNetwork.requiredReach(segments, wideWater));
    }

    @Test
    void smallerIndexPreservesAllConsumersAcrossCompetingChannelsAndBinBoundaries() {
        var shape = RiverShape.NONE;
        var segments = List.of(
                new RiverNetwork.Segment(-70, -70, 70, 70, 3, 3, 100, 90),
                new RiverNetwork.Segment(-70, 0, 70, 0, 72, 72, 80, 75));
        var old = new RiverNetwork(segments, 200);
        var optimized = new RiverNetwork(segments, shape);
        for (int x = -180; x <= 180; x += 3) {
            for (int z = -180; z <= 180; z += 3) {
                var a = old.sample(x, z);
                var b = optimized.sample(x, z);
                assertEquals(RiverProfile.carveAtWater(a.distance(), a.width(), 110, a.water(), shape),
                        RiverProfile.carveAtWater(b.distance(), b.width(), 110, b.water(), shape), 1e-9);
                assertEquals(TerrainField.RIVER_GATE.convert(a.distance(), 63, -56, 272),
                        TerrainField.RIVER_GATE.convert(b.distance(), 63, -56, 272), 1e-9);
                boolean wet = a.width() > 0 && a.distance() <= Math.max(shape.waterRadius(), a.width() / 2);
                boolean newWet = b.width() > 0 && b.distance() <= Math.max(shape.waterRadius(), b.width() / 2);
                assertEquals(wet, newWet);
                if (wet) assertEquals(a.water(), b.water(), 1e-9);
            }
        }
    }

    @Test
    void diagonalStreamRemainsConnectedBetweenCoarseCellsAndBins() {
        var network = new RiverNetwork(List.of(new RiverNetwork.Segment(
                56, 56, 72, 72, 3, 3, 100, 96)), 200);
        for (double p = 56; p <= 72; p += .5) {
            var sample = network.sample(p, p);
            assertEquals(0, sample.distance(), 1e-9);
            assertEquals(3, sample.width());
            double bed = 104 - RiverProfile.carveAtWater(
                    sample.distance(), sample.width(), 104, sample.water(), RiverShape.NONE);
            assertTrue(bed <= sample.water() - 2);
        }
        assertTrue(network.sample(64, 70).distance() > 3);
        assertEquals(0, network.sample(1000, 1000).width());
    }

    @Test
    void narrowHighlandBedHasWaterDepthInAdditionToFreeboard() {
        // stated against the shape rather than a literal, so changing the
        // narrowest channel's depth does not silently invalidate the intent
        var shape = RiverShape.NONE;
        double width = shape.minWidth();
        double surface = 160, water = 158;
        double bed = surface - RiverProfile.carveAtWater(0, width, surface, water, shape);
        assertEquals(water - RiverProfile.depth(width, shape), bed, 1.0E-9);
        assertTrue(bed < water, "the bed has to sit below its own water surface");
    }
}

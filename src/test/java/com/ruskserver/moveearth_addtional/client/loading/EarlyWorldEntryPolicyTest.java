package com.ruskserver.moveearth_addtional.client.loading;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class EarlyWorldEntryPolicyTest {
    @Test void onlyTheDestinationChunkIsNeededAwayFromAnEdge() {
        assertTrue(EarlyWorldEntryPolicy.footprintReady(8.2, 8.2, 8.8, 8.8, (x, z) -> x == 0 && z == 0));
        assertFalse(EarlyWorldEntryPolicy.footprintReady(24.2, 8.2, 24.8, 8.8, (x, z) -> x == 0 && z == 0));
    }

    @Test void doesNotAcceptAMissingChunkUnderEitherSideOfAnEdge() {
        assertFalse(EarlyWorldEntryPolicy.footprintReady(15.7, 8.2, 16.3, 8.8, (x, z) -> x == 0));
        assertTrue(EarlyWorldEntryPolicy.footprintReady(15.7, 8.2, 16.3, 8.8, (x, z) -> (x == 0 || x == 1) && z == 0));
    }

    @Test void negativeCornerRequiresAllFourFootprintChunks() {
        Set<String> requested = new HashSet<>();
        assertTrue(EarlyWorldEntryPolicy.footprintReady(-16.3, -0.3, -15.7, 0.3, (x, z) -> {
            requested.add(x + "," + z);
            return true;
        }));
        assertEquals(Set.of("-2,-1", "-2,0", "-1,-1", "-1,0"), requested);
        assertFalse(EarlyWorldEntryPolicy.footprintReady(-16.3, -0.3, -15.7, 0.3, (x, z) -> !(x == -2 && z == -1)));
    }

    @Test void exactMaximumEdgeDoesNotRequireAnUntouchedNeighbour() {
        assertTrue(EarlyWorldEntryPolicy.footprintReady(15.4, 0.1, 16.0, 0.7, (x, z) -> x == 0 && z == 0));
        assertTrue(EarlyWorldEntryPolicy.footprintReady(-16.6, -1, -16.0, -0.4, (x, z) -> x == -2 && z == -1));
        assertTrue(EarlyWorldEntryPolicy.footprintReady(-0.6, -1, 0.0, -0.4, (x, z) -> x == -1 && z == -1));
    }

    @Test void invalidOrOversizedBoundsFallBackWithoutScanning() {
        java.util.function.BiPredicate<Integer, Integer> neverScan = (x, z) -> { fail("must not scan"); return false; };
        assertFalse(EarlyWorldEntryPolicy.footprintReady(Double.NaN, 0, 1, 1, neverScan));
        assertFalse(EarlyWorldEntryPolicy.footprintReady(0, 0, Double.POSITIVE_INFINITY, 1, neverScan));
        assertFalse(EarlyWorldEntryPolicy.footprintReady(1, 0, 0, 1, neverScan));
        assertFalse(EarlyWorldEntryPolicy.footprintReady(0, 0, 1000, 1000, neverScan));
        assertFalse(EarlyWorldEntryPolicy.footprintReady(30_000_001, 0, 30_000_002, 1, neverScan));
    }
}

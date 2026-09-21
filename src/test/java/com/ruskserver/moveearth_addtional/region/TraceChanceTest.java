package com.ruskserver.moveearth_addtional.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TraceChanceTest {

    private static int countOver(long seed, String material, double rate, int side) {
        int hits = 0;
        for (int x = 0; x < side; x++) {
            for (int z = 0; z < side; z++) {
                if (TraceChance.occurs(seed, x, z, material, rate)) {
                    hits++;
                }
            }
        }
        return hits;
    }

    @Test
    @DisplayName("the same spot always answers the same way")
    void deterministic() {
        // The scanner and the thing being scanned ask at different moments.
        for (int i = 0; i < 200; i++) {
            boolean first = TraceChance.occurs(12345L, i, -i, "crude_oil", 0.08);
            assertEquals(first, TraceChance.occurs(12345L, i, -i, "crude_oil", 0.08));
        }
    }

    @Test
    @DisplayName("hits land near the requested share")
    void distribution() {
        for (double rate : new double[] { 0.02, 0.08, 0.25, 0.5 }) {
            int hits = countOver(99L, "crude_oil", rate, 200);
            double observed = hits / 40000.0;
            assertTrue(Math.abs(observed - rate) < 0.01,
                    "rate " + rate + " observed " + observed);
        }
    }

    @Test
    @DisplayName("nothing at zero, everything at one")
    void bounds() {
        assertFalse(TraceChance.occurs(1L, 4, 9, "crude_oil", 0.0));
        assertFalse(TraceChance.occurs(1L, 4, 9, "crude_oil", -1.0));
        assertTrue(TraceChance.occurs(1L, 4, 9, "crude_oil", 1.0));
        assertTrue(TraceChance.occurs(1L, 4, 9, "crude_oil", 2.0));
    }

    @Test
    @DisplayName("two materials do not share the same spots")
    void materialsDiffer() {
        // Sharing a sequence would put every exclusive resource in the same
        // chunks, so a region would have a little of everything or none of
        // anything. Overlap should be about rate*rate, not rate.
        int both = 0;
        int oil = 0;
        for (int x = 0; x < 200; x++) {
            for (int z = 0; z < 200; z++) {
                boolean a = TraceChance.occurs(7L, x, z, "crude_oil", 0.1);
                boolean b = TraceChance.occurs(7L, x, z, "uranium", 0.1);
                if (a) oil++;
                if (a && b) both++;
            }
        }
        assertTrue(oil > 3600 && oil < 4400, "oil hits " + oil);
        assertTrue(both < oil / 4, "overlap " + both + " of " + oil + " is too high");
    }

    @Test
    @DisplayName("two seeds do not produce the same map")
    void seedsDiffer() {
        int same = 0;
        for (int x = 0; x < 100; x++) {
            for (int z = 0; z < 100; z++) {
                if (TraceChance.occurs(1L, x, z, "crude_oil", 0.1)
                        == TraceChance.occurs(2L, x, z, "crude_oil", 0.1)) {
                    same++;
                }
            }
        }
        // Two independent draws at 0.1 agree about 82% of the time by chance.
        assertTrue(same < 9200, "seeds agree on " + same + " of 10000");
    }

    @Test
    @DisplayName("neighbouring positions are not correlated")
    void neighboursIndependent() {
        // A weak mix leaves stripes: whole rows or columns either all hit or
        // all miss, which reads in game as a wall rather than as scattered luck.
        for (int z = 0; z < 40; z++) {
            int rowHits = 0;
            for (int x = 0; x < 200; x++) {
                if (TraceChance.occurs(5L, x, z, "crude_oil", 0.1)) rowHits++;
            }
            assertTrue(rowHits > 4 && rowHits < 40, "row " + z + " had " + rowHits);
        }
    }
}

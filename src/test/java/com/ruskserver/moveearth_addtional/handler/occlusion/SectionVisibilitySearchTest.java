package com.ruskserver.moveearth_addtional.handler.occlusion;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectionVisibilitySearchTest {
    private static final long ALL_OPEN = (1L << 36) - 1L;
    /** Looking straight east from the middle of section (0, 0, 0). */
    private static final double EYE = 8.0D;

    private static int run(SectionVisibilitySearch search, int depth, SectionVisibilitySearch.MaskSource masks,
                           double minDot, Set<Long> visible) {
        Set<Long> visited = new HashSet<>();
        return search.run(0, 0, 0, EYE, EYE, EYE, 1.0D, 0.0D, 0.0D, minDot, depth, masks,
                visited::add, visible::add);
    }

    @Test
    void keyMatchesSectionPosPacking() {
        // SectionPos.asLong(1, 2, 3) = (1 << 42) | 2 | (3 << 20)
        assertEquals((1L << 42) | 2L | (3L << 20), SectionVisibilitySearch.key(1, 2, 3));
        assertEquals((0x3FFFFFL << 42) | 0xFFFFFL | (0x3FFFFFL << 20), SectionVisibilitySearch.key(-1, -1, -1));
    }

    @Test
    void openSpaceIsSeenAlongTheViewToTheDepthLimit() {
        Set<Long> visible = new HashSet<>();
        int truncated = run(new SectionVisibilitySearch(4096), 4, (x, y, z) -> ALL_OPEN, 0.342D, visible);
        assertEquals(SectionVisibilitySearch.COMPLETE, truncated);
        assertTrue(visible.contains(SectionVisibilitySearch.key(4, 0, 0)));
        assertFalse(visible.contains(SectionVisibilitySearch.key(5, 0, 0)));
        assertFalse(visible.contains(SectionVisibilitySearch.key(-3, 0, 0)), "behind the player");
    }

    @Test
    void closedSectionsStopTheSearch() {
        Set<Long> visible = new HashSet<>();
        run(new SectionVisibilitySearch(4096), 8,
                (x, y, z) -> x == 2 ? 0L : ALL_OPEN, -1.0D, visible);
        assertTrue(visible.contains(SectionVisibilitySearch.key(2, 0, 0)), "the wall itself is seen");
        assertFalse(visible.contains(SectionVisibilitySearch.key(3, 0, 0)));
    }

    @Test
    void budgetCapsTheWalkAndReportsWhereItStopped() {
        SectionVisibilitySearch search = new SectionVisibilitySearch(64);
        Set<Long> visible = new HashSet<>();
        int truncated = run(search, 12, (x, y, z) -> ALL_OPEN, -1.0D, visible);
        assertTrue(truncated < SectionVisibilitySearch.COMPLETE);
        assertTrue(search.queued() <= 65);
        assertTrue(visible.size() <= 65);
    }

    @Test
    void eachSectionIsQueuedOnce() {
        SectionVisibilitySearch search = new SectionVisibilitySearch(100_000);
        Set<Long> visible = new HashSet<>();
        run(search, 6, (x, y, z) -> ALL_OPEN, -1.0D, visible);
        // Every section within Manhattan distance 6 of the start: 1 + sum of 4d^2 + 2 for d = 1..6.
        assertEquals(377, search.queued());
        assertEquals(377, visible.size());
    }

    @Test
    void recomputeWaitsForTheMinimumInterval() {
        assertTrue(OcclusionRecomputePolicy.shouldRecompute(false, 0L, false, false, 5, 10));
        assertFalse(OcclusionRecomputePolicy.shouldRecompute(true, 4L, true, true, 5, 10));
        assertTrue(OcclusionRecomputePolicy.shouldRecompute(true, 5L, true, false, 5, 10));
        assertTrue(OcclusionRecomputePolicy.shouldRecompute(true, 5L, false, true, 5, 10));
        assertFalse(OcclusionRecomputePolicy.shouldRecompute(true, 9L, false, false, 5, 10));
        assertTrue(OcclusionRecomputePolicy.shouldRecompute(true, 10L, false, false, 5, 10));
        assertTrue(OcclusionRecomputePolicy.shouldRecompute(true, -3L, false, false, 5, 10));
    }

    @Test
    void movingEveryTickRecomputesAtMostEveryFifthTick() {
        long last = 0L;
        int searches = 1;
        for (long tick = 1L; tick <= 100L; tick++) {
            if (OcclusionRecomputePolicy.shouldRecompute(true, tick - last, true, true, 5, 10)) {
                last = tick;
                searches++;
            }
        }
        assertEquals(21, searches);
    }

    @Test
    void truncatedSearchFailsOpenFarAway() {
        assertFalse(OcclusionRecomputePolicy.beyondTruncation(SectionVisibilitySearch.COMPLETE, 0, 0, 0, 9, 9, 9));
        assertTrue(OcclusionRecomputePolicy.beyondTruncation(5, 0, 0, 0, 4, 0, 0));
        assertFalse(OcclusionRecomputePolicy.beyondTruncation(5, 0, 0, 0, 2, 0, 0));
    }
}

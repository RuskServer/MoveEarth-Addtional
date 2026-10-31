package com.ruskserver.moveearth_addtional.s2.presence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkMilestonePolicyTest {
    private static final long MINUTE = 60_000L;
    private static final long NOW = 1_800_000_000_000L;

    @Test
    void idleTimeRunsFromTheLastActivity() {
        assertEquals(31 * MINUTE, AfkMilestonePolicy.idleMillis(NOW - 31 * MINUTE, NOW));
        assertTrue(AfkMilestonePolicy.idleMillis(NOW - 31 * MINUTE, NOW) >= AfkMilestonePolicy.STRAYED_MILLIS);
        assertFalse(AfkMilestonePolicy.idleMillis(NOW - 90 * MINUTE, NOW) >= AfkMilestonePolicy.STATUE_MILLIS);
    }

    @Test
    void anUntrackedPlayerIsNeitherIdleNorAway() {
        assertEquals(0L, AfkMilestonePolicy.idleMillis(0L, NOW));
        assertFalse(AfkMilestonePolicy.away(0L, NOW, 5 * MINUTE));
    }

    @Test
    void awayFollowsTheAnalyticsThreshold() {
        assertTrue(AfkMilestonePolicy.away(NOW - 5 * MINUTE, NOW, 5 * MINUTE));
        assertFalse(AfkMilestonePolicy.away(NOW - 4 * MINUTE, NOW, 5 * MINUTE));
    }
}

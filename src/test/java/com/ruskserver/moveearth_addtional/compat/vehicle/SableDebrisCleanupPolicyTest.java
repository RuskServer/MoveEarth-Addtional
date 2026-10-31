package com.ruskserver.moveearth_addtional.compat.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SableDebrisCleanupPolicyTest {
    @Test
    void warnsThenWaitsAnEntireMinuteBeforeRemoving() {
        var warned = SableDebrisCleanupPolicy.review(false, 65, 64, 100, null, 1200);
        assertEquals(SableDebrisCleanupPolicy.Action.WARN, warned.action());
        assertEquals(SableDebrisCleanupPolicy.Action.WAIT,
                SableDebrisCleanupPolicy.review(false, 65, 64, 1299, warned.pendingSince(), 1200).action());
        assertEquals(SableDebrisCleanupPolicy.Action.REMOVE,
                SableDebrisCleanupPolicy.review(false, 65, 64, 1300, warned.pendingSince(), 1200).action());
    }

    @Test
    void coreConnectionPlayerOrRaidProtectionCancelsEvenAnExpiredDeletion() {
        var decision = SableDebrisCleanupPolicy.review(true, 200, 64, 2000, 100L, 1200);
        assertEquals(SableDebrisCleanupPolicy.Action.KEEP, decision.action());
        assertEquals(-1, decision.pendingSince());
    }

    @Test
    void returningToBudgetOrResettingWorldTimeCancelsTheOldDeadline() {
        assertEquals(SableDebrisCleanupPolicy.Action.KEEP,
                SableDebrisCleanupPolicy.review(false, 64, 64, 2000, 100L, 1200).action());
        assertEquals(SableDebrisCleanupPolicy.Action.WARN,
                SableDebrisCleanupPolicy.review(false, 65, 64, 50, 100L, 1200).action());
    }

    @Test
    void oneFreedSlotStopsDeletionOfOtherAlreadyWarnedBodies() {
        var index = new SableBodyDensityIndex<Integer>(128);
        var point = new SableBodyDensityPolicy.Point(0, 0);
        for (int id = 0; id < 65; id++) index.put(id, point);
        int removed = 0;
        for (int id = 0; id < 65; id++) {
            var decision = SableDebrisCleanupPolicy.review(false, index.count(point, 65), 64, 1200, 0L, 1200);
            if (decision.action() == SableDebrisCleanupPolicy.Action.REMOVE) { index.remove(id); removed++; }
        }
        assertEquals(1, removed);
        assertEquals(64, index.count(point, 65));
    }
}

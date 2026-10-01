package com.ruskserver.moveearth_addtional.network.common;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotCoalescerTest {
    private final UUID player = UUID.randomUUID();

    private static SnapshotCoalescer<Boolean> coalescer() {
        return new SnapshotCoalescer<>(5, Boolean::logicalOr);
    }

    @Test
    void theFirstRequestIsSentAtOnce() {
        assertEquals(Boolean.TRUE, coalescer().request(player, true, 100L));
    }

    @Test
    void requestsInsideTheCooldownFoldIntoOneDeferredSend() {
        SnapshotCoalescer<Boolean> snapshots = coalescer();
        assertEquals(Boolean.FALSE, snapshots.request(player, false, 100L));
        for (long tick = 100L; tick < 105L; tick++) assertNull(snapshots.request(player, false, tick));
        assertNull(snapshots.request(player, true, 104L));
        assertTrue(snapshots.due(104L).isEmpty());

        var due = snapshots.due(105L);
        assertEquals(1, due.size());
        assertEquals(Boolean.TRUE, due.get(player), "an open-screen request is kept when folded");
        assertTrue(snapshots.due(500L).isEmpty(), "a folded send goes out once");
    }

    @Test
    void aFloodYieldsAtMostOneSendPerCooldown() {
        SnapshotCoalescer<Boolean> snapshots = coalescer();
        int sent = 0;
        for (long tick = 0L; tick < 100L; tick++) {
            for (int burst = 0; burst < 20; burst++) if (snapshots.request(player, false, tick) != null) sent++;
            sent += snapshots.due(tick).size();
        }
        assertEquals(20, sent);
    }

    @Test
    void aRequestAfterTheCooldownCarriesThePendingOneWithIt() {
        SnapshotCoalescer<Boolean> snapshots = coalescer();
        snapshots.request(player, false, 100L);
        assertNull(snapshots.request(player, true, 101L));
        assertEquals(Boolean.TRUE, snapshots.request(player, false, 106L));
        assertFalse(snapshots.hasPending());
    }

    @Test
    void forgettingAndATickRestartDoNotBlockSends() {
        SnapshotCoalescer<Boolean> snapshots = coalescer();
        snapshots.request(player, false, 100L);
        assertNull(snapshots.request(player, false, 101L));
        snapshots.forget(player);
        assertFalse(snapshots.hasPending());
        assertEquals(Boolean.FALSE, snapshots.request(player, false, 102L));
        assertEquals(Boolean.FALSE, snapshots.request(player, false, 3L), "the tick counter restarted");
    }
}

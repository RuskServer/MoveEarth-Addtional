package com.ruskserver.moveearth_addtional.s2.territory;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerritoryTransitionTrackerTest {
    @Test
    void initialObservationDoesNotPretendLoginIsABorderCrossing() {
        TerritoryTransitionTracker tracker = new TerritoryTransitionTracker();
        assertFalse(tracker.observe(UUID.randomUUID(), null));
    }

    @Test
    void reportsOnlyActualTerritoryChanges() {
        TerritoryTransitionTracker tracker = new TerritoryTransitionTracker();
        UUID player = UUID.randomUUID();
        UUID firstNation = UUID.randomUUID();
        UUID secondNation = UUID.randomUUID();

        assertFalse(tracker.observe(player, null));
        assertTrue(tracker.observe(player, firstNation));
        assertFalse(tracker.observe(player, firstNation));
        assertTrue(tracker.observe(player, secondNation));
        assertTrue(tracker.observe(player, null));
        assertFalse(tracker.observe(player, null));
    }

    @Test
    void forgottenPlayerStartsFreshOnNextLogin() {
        TerritoryTransitionTracker tracker = new TerritoryTransitionTracker();
        UUID player = UUID.randomUUID();
        tracker.observe(player, UUID.randomUUID());
        tracker.forget(player);
        assertFalse(tracker.observe(player, null));
    }
}

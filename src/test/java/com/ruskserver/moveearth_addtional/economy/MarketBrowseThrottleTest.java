package com.ruskserver.moveearth_addtional.economy;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketBrowseThrottleTest {
    private final UUID player = UUID.randomUUID();
    private final UUID first = UUID.randomUUID();
    private final UUID latest = UUID.randomUUID();

    @Test
    void theFirstRefreshGoesOutAtOnce() {
        assertTrue(new MarketBrowseThrottle().request(player, first, 100L));
    }

    @Test
    void refreshesInsideTheCooldownFoldIntoOneWithTheLatestSelection() {
        MarketBrowseThrottle throttle = new MarketBrowseThrottle();
        throttle.request(player, first, 100L);
        assertFalse(throttle.request(player, first, 102L));
        assertFalse(throttle.request(player, latest, 105L));
        assertTrue(throttle.due(109L).isEmpty());

        var due = throttle.due(110L);
        assertEquals(1, due.size());
        assertEquals(latest, due.getFirst().getValue());
        assertTrue(throttle.due(200L).isEmpty(), "a deferred refresh is sent once");
    }

    @Test
    void aRefreshAfterTheCooldownIsNotDelayed() {
        MarketBrowseThrottle throttle = new MarketBrowseThrottle();
        throttle.request(player, first, 100L);
        assertTrue(throttle.request(player, latest, 100L + MarketBrowseThrottle.COOLDOWN_TICKS));
    }

    @Test
    void forgettingAPlayerDropsTheirPendingRefresh() {
        MarketBrowseThrottle throttle = new MarketBrowseThrottle();
        throttle.request(player, first, 100L);
        throttle.request(player, latest, 101L);
        throttle.forget(player);
        assertTrue(throttle.due(500L).isEmpty());
        assertTrue(throttle.request(player, first, 102L));
    }
}

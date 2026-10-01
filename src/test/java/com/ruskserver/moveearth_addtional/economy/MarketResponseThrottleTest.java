package com.ruskserver.moveearth_addtional.economy;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketResponseThrottleTest {
    private final UUID player = UUID.randomUUID();
    private final UUID first = UUID.randomUUID();
    private final UUID latest = UUID.randomUUID();

    @Test
    void theFirstReplyGoesOutAtOnce() {
        var reply = new MarketResponseThrottle().respond(player, first, "", 100L);
        assertNotNull(reply);
        assertEquals(first, reply.selection());
    }

    @Test
    void repliesInsideTheCooldownFoldIntoOneWithTheLatestSelection() {
        MarketResponseThrottle throttle = new MarketResponseThrottle();
        throttle.respond(player, first, "", 100L);
        assertNull(throttle.respond(player, first, "", 102L));
        assertNull(throttle.respond(player, latest, "", 105L));
        assertTrue(throttle.due(109L).isEmpty());

        var due = throttle.due(110L);
        assertEquals(1, due.size());
        assertEquals(latest, due.getFirst().selection());
        assertTrue(throttle.due(200L).isEmpty(), "a deferred reply is sent once");
    }

    @Test
    void aFoldedReplyKeepsTheLatestActionResult() {
        MarketResponseThrottle throttle = new MarketResponseThrottle();
        throttle.respond(player, first, "", 100L);
        throttle.respond(player, first, "取引を更新しました", 101L);
        throttle.respond(player, latest, "", 102L);
        var due = throttle.due(110L);
        assertEquals("取引を更新しました", due.getFirst().result(), "a later browse does not erase a trade result");
        assertEquals(latest, due.getFirst().selection());
    }

    @Test
    void aReplyAfterTheCooldownIsNotDelayed() {
        MarketResponseThrottle throttle = new MarketResponseThrottle();
        throttle.respond(player, first, "", 100L);
        assertNotNull(throttle.respond(player, latest, "", 100L + MarketResponseThrottle.COOLDOWN_TICKS));
    }

    @Test
    void aRefusalWithoutASelectionKeepsTheLastStation() {
        MarketResponseThrottle throttle = new MarketResponseThrottle();
        throttle.respond(player, first, "", 100L);
        var reply = throttle.respond(player, null, "too fast", 200L);
        assertEquals(first, reply.selection());
    }

    @Test
    void ordinaryClickingAndShortBurstsPassButAFloodIsRefused() {
        MarketResponseThrottle throttle = new MarketResponseThrottle();
        for (int i = 0; i < MarketResponseThrottle.ACTION_BURST; i++)
            assertTrue(throttle.allowAction(player, 100L), "burst click " + i);
        assertFalse(throttle.allowAction(player, 100L));
        assertFalse(throttle.allowAction(player, 101L));
        assertTrue(throttle.allowAction(player, 100L + MarketResponseThrottle.ACTION_REFILL_TICKS));

        MarketResponseThrottle steady = new MarketResponseThrottle();
        for (long tick = 0; tick < 2_000L; tick += MarketResponseThrottle.ACTION_REFILL_TICKS)
            assertTrue(steady.allowAction(player, tick), "five clicks a second never run dry");

        MarketResponseThrottle flood = new MarketResponseThrottle();
        int allowed = 0;
        for (long tick = 0; tick < 200L; tick++)
            for (int packet = 0; packet < 20; packet++) if (flood.allowAction(player, tick)) allowed++;
        assertTrue(allowed <= MarketResponseThrottle.ACTION_BURST + 200 / MarketResponseThrottle.ACTION_REFILL_TICKS,
                "4000 packets in 10 s run " + allowed + " actions");
    }

    @Test
    void anIdleBucketRefillsOnlyToTheBurst() {
        MarketResponseThrottle throttle = new MarketResponseThrottle();
        throttle.allowAction(player, 0L);
        int allowed = 0;
        for (int i = 0; i < 100; i++) if (throttle.allowAction(player, 100_000L)) allowed++;
        assertEquals(MarketResponseThrottle.ACTION_BURST, allowed);
    }

    @Test
    void aRestartedTickCounterDoesNotLockThePlayerOut() {
        MarketResponseThrottle throttle = new MarketResponseThrottle();
        throttle.respond(player, first, "", 50_000L);
        for (int i = 0; i < 20; i++) throttle.allowAction(player, 50_000L);
        assertNotNull(throttle.respond(player, first, "", 5L));
        assertTrue(throttle.allowAction(player, 5L));
    }

    @Test
    void forgettingAPlayerDropsTheirPendingReply() {
        MarketResponseThrottle throttle = new MarketResponseThrottle();
        throttle.respond(player, first, "", 100L);
        throttle.respond(player, latest, "", 101L);
        throttle.forget(player);
        assertTrue(throttle.due(500L).isEmpty());
        assertNotNull(throttle.respond(player, first, "", 102L));
    }
}

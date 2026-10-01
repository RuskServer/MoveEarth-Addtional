package com.ruskserver.moveearth_addtional.network.common;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class C2SRateLimiterTest {
    private static final C2SRateLimiter.Budget WIDE = new C2SRateLimiter.Budget("wide", 1_000, 1L);
    private final AtomicLong clock = new AtomicLong(10_000L);
    private final UUID player = UUID.randomUUID();

    private C2SRateLimiter limiter(C2SRateLimiter.Budget global) {
        return new C2SRateLimiter(global, clock::get);
    }

    private static int admitted(C2SRateLimiter limiter, UUID player, Object key, C2SRateLimiter.Budget budget, int tries) {
        int passed = 0;
        for (int i = 0; i < tries; i++) if (limiter.tryAcquire(player, key, budget)) passed++;
        return passed;
    }

    @Test
    void aBurstPassesAndTheRestIsRefused() {
        C2SRateLimiter limiter = limiter(WIDE);
        assertEquals(C2SRateLimiter.ACTION.burst(), admitted(limiter, player, "a", C2SRateLimiter.ACTION, 100));
    }

    @Test
    void tokensComeBackAtTheRefillRateAndNeverAboveTheBurst() {
        C2SRateLimiter limiter = limiter(WIDE);
        C2SRateLimiter.Budget budget = C2SRateLimiter.REQUEST;
        admitted(limiter, player, "r", budget, 100);
        clock.addAndGet(budget.refillMillis() - 1);
        assertFalse(limiter.tryAcquire(player, "r", budget));
        clock.addAndGet(1);
        assertTrue(limiter.tryAcquire(player, "r", budget));
        assertFalse(limiter.tryAcquire(player, "r", budget));

        clock.addAndGet(budget.refillMillis() * 1_000);
        assertEquals(budget.burst(), admitted(limiter, player, "r", budget, 100));
    }

    @Test
    void aSustainedFloodIsCutToTheSteadyRate() {
        C2SRateLimiter limiter = limiter(WIDE);
        C2SRateLimiter.Budget budget = C2SRateLimiter.ACTION;
        int passed = 0;
        // One packet per millisecond for ten seconds.
        for (int ms = 0; ms < 10_000; ms++) {
            if (limiter.tryAcquire(player, "a", budget)) passed++;
            clock.incrementAndGet();
        }
        assertEquals(budget.burst() + 10_000 / budget.refillMillis(), passed, 1);
    }

    @Test
    void keysAndPlayersHaveTheirOwnBuckets() {
        C2SRateLimiter limiter = limiter(WIDE);
        UUID other = UUID.randomUUID();
        admitted(limiter, player, "a", C2SRateLimiter.ACTION, 100);
        assertFalse(limiter.tryAcquire(player, "a", C2SRateLimiter.ACTION));
        assertTrue(limiter.tryAcquire(player, "b", C2SRateLimiter.ACTION), "another packet type is unaffected");
        assertTrue(limiter.tryAcquire(other, "a", C2SRateLimiter.ACTION), "another player is unaffected");
    }

    @Test
    void theGlobalBucketCapsAPlayerAcrossAllPacketTypes() {
        C2SRateLimiter limiter = limiter(new C2SRateLimiter.Budget("global", 5, 1_000L));
        int passed = 0;
        for (int key = 0; key < 20; key++) if (limiter.tryAcquire(player, key, C2SRateLimiter.ACTION)) passed++;
        assertEquals(5, passed);
        assertFalse(limiter.tryAcquire(player, "fresh", C2SRateLimiter.INPUT));
    }

    @Test
    void aRefusedPacketDoesNotSpendTheOtherBucket() {
        C2SRateLimiter limiter = limiter(new C2SRateLimiter.Budget("global", 4, 1_000_000L));
        C2SRateLimiter.Budget tiny = new C2SRateLimiter.Budget("tiny", 1, 1_000_000L);
        assertTrue(limiter.tryAcquire(player, "t", tiny));
        for (int i = 0; i < 10; i++) assertFalse(limiter.tryAcquire(player, "t", tiny));
        assertEquals(3, admitted(limiter, player, "other", C2SRateLimiter.ACTION, 10),
                "refused packets did not drain the global bucket");
    }

    @Test
    void subActionChecksDoNotChargeTheGlobalBucket() {
        C2SRateLimiter limiter = limiter(new C2SRateLimiter.Budget("global", 1, 1_000_000L));
        assertTrue(limiter.tryAcquire(player, "packet", C2SRateLimiter.ACTION));
        assertTrue(limiter.tryAcquire(player, "unlink", C2SRateLimiter.AUDIT, false));
        assertTrue(limiter.tryAcquire(player, "unlink", C2SRateLimiter.AUDIT, false));
        assertFalse(limiter.tryAcquire(player, "unlink", C2SRateLimiter.AUDIT, false), "audit burst is 2");
    }

    @Test
    void forgettingAPlayerOnLogoutDropsTheirState() {
        C2SRateLimiter limiter = limiter(WIDE);
        admitted(limiter, player, "a", C2SRateLimiter.ACTION, 100);
        assertEquals(1, limiter.trackedPlayers());
        limiter.forget(player);
        assertEquals(0, limiter.trackedPlayers());
        assertTrue(limiter.tryAcquire(player, "a", C2SRateLimiter.ACTION));
    }

    @Test
    void aClockThatGoesBackwardsDoesNotLockAPlayerOut() {
        C2SRateLimiter limiter = limiter(WIDE);
        admitted(limiter, player, "a", C2SRateLimiter.ACTION, 100);
        clock.addAndGet(-5_000L);
        assertTrue(limiter.tryAcquire(player, "a", C2SRateLimiter.ACTION));
    }

    @Test
    void snapshotRequestsSettleWithinTwoToFourPerSecond() {
        assertTrue(C2SRateLimiter.REQUEST.perSecond() >= 2.0D && C2SRateLimiter.REQUEST.perSecond() <= 4.0D);
        assertTrue(C2SRateLimiter.HEAVY.perSecond() >= 2.0D && C2SRateLimiter.HEAVY.perSecond() <= 4.0D);
    }
}

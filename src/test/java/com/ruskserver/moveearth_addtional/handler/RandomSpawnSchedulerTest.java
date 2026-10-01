package com.ruskserver.moveearth_addtional.handler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomSpawnSchedulerTest {
    @Test
    void servedKeysMoveBehindEveryoneElse() {
        RandomSpawnScheduler<String> scheduler = new RandomSpawnScheduler<>();
        scheduler.add("a");
        scheduler.add("b");
        scheduler.add("c");
        scheduler.served("a");
        assertEquals(List.of("b", "c", "a"), scheduler.serviceOrder());
    }

    @Test
    void addingTwiceKeepsTheOriginalPlace() {
        RandomSpawnScheduler<String> scheduler = new RandomSpawnScheduler<>();
        scheduler.add("a");
        scheduler.add("b");
        scheduler.add("a");
        assertEquals(List.of("a", "b"), scheduler.serviceOrder());
        scheduler.served("missing");
        assertEquals(2, scheduler.size());
    }

    @Test
    void fortySearchesWithSixSlotsAreAllServedWithinSevenRounds() {
        RandomSpawnScheduler<Integer> scheduler = new RandomSpawnScheduler<>();
        for (int player = 0; player < 40; player++) scheduler.add(player);
        Map<Integer, Integer> firstServedRound = new HashMap<>();
        for (int round = 0; round < 7; round++) {
            int slots = 6;
            for (Integer player : scheduler.serviceOrder()) {
                if (slots == 0) break;
                firstServedRound.putIfAbsent(player, round);
                scheduler.served(player);
                slots--;
            }
        }
        assertEquals(40, firstServedRound.size());
    }

    @Test
    void oneLongSearchCannotStarveTheOthers() {
        RandomSpawnScheduler<String> scheduler = new RandomSpawnScheduler<>();
        scheduler.add("long");
        scheduler.add("x");
        scheduler.add("y");
        List<String> served = new ArrayList<>();
        for (int round = 0; round < 3; round++) {
            String next = scheduler.serviceOrder().get(0);
            served.add(next);
            scheduler.served(next);
        }
        assertEquals(List.of("long", "x", "y"), served);
    }

    @Test
    void walkingTheOrderWhileRemovingIsSafe() {
        RandomSpawnScheduler<String> scheduler = new RandomSpawnScheduler<>();
        scheduler.add("a");
        scheduler.add("b");
        for (String key : scheduler.serviceOrder()) scheduler.remove(key);
        assertEquals(0, scheduler.size());
    }

    @Test
    void slotsNeverGoNegative() {
        assertEquals(6, RandomSpawnScheduler.availableSlots(6, 0));
        assertEquals(0, RandomSpawnScheduler.availableSlots(6, 9));
        assertEquals(6, RandomSpawnScheduler.availableSlots(6, -1));
    }

    @Test
    void queueTimeoutIsInclusive() {
        assertFalse(RandomSpawnScheduler.queueTimedOut(100L, 699L, 600));
        assertTrue(RandomSpawnScheduler.queueTimedOut(100L, 700L, 600));
    }

    @Test
    void fallbackRelaxesThePlayerDistanceButNeverDropsIt() {
        assertEquals(192, RandomSpawnScheduler.fallbackPlayerDistance(384));
        assertEquals(1, RandomSpawnScheduler.fallbackPlayerDistance(1));
        assertEquals(1, RandomSpawnScheduler.fallbackPlayerDistance(0));
        // A spot 150 blocks from an enemy is refused even by the fallback.
        double minimum = Math.pow(RandomSpawnScheduler.fallbackPlayerDistance(384), 2);
        assertFalse(RandomSpawnPolicy.fallbackAllowed(150.0D * 150.0D, minimum));
        assertTrue(RandomSpawnPolicy.fallbackAllowed(200.0D * 200.0D, minimum));
        assertTrue(RandomSpawnPolicy.fallbackAllowed(Double.POSITIVE_INFINITY, minimum));
    }

    @Test
    void failedSearchesRetryABoundedNumberOfTimes() {
        assertTrue(RandomSpawnScheduler.mayRetry(0, 2));
        assertTrue(RandomSpawnScheduler.mayRetry(1, 2));
        assertFalse(RandomSpawnScheduler.mayRetry(2, 2));
        assertFalse(RandomSpawnScheduler.mayRetry(0, 0));
        assertFalse(RandomSpawnScheduler.mayRetry(-1, 2));
    }

    @Test
    void retryStartsOnItsDueTick() {
        assertFalse(RandomSpawnScheduler.retryDue(300L, 299L));
        assertTrue(RandomSpawnScheduler.retryDue(300L, 300L));
        assertTrue(RandomSpawnScheduler.retryDue(300L, 1_000L));
    }
}

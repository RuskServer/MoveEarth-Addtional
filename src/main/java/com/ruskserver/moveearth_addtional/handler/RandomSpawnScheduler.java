package com.ruskserver.moveearth_addtional.handler;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Fair order for random-spawn searches competing for chunk-load slots. A search that
 * has just been served goes to the back, so one search with many rejected candidates
 * cannot hold a slot while everyone who joined at the same moment waits at world spawn.
 */
final class RandomSpawnScheduler<K> {
    private final LinkedHashSet<K> order = new LinkedHashSet<>();

    void add(K key) {
        order.add(key);
    }

    void remove(K key) {
        order.remove(key);
    }

    void clear() {
        order.clear();
    }

    int size() {
        return order.size();
    }

    /** Keys from the least recently served; a copy, safe to change the scheduler while walking it. */
    List<K> serviceOrder() {
        return new ArrayList<>(order);
    }

    /** Moves {@code key} behind every other waiting key. */
    void served(K key) {
        if (order.remove(key)) order.add(key);
    }

    static int availableSlots(int maximum, int active) {
        return Math.max(0, maximum - Math.max(0, active));
    }

    /** A search that never got a slot gives up after {@code timeoutTicks}, inclusive. */
    static boolean queueTimedOut(long queuedTick, long currentTick, int timeoutTicks) {
        return currentTick - queuedTick >= timeoutTicks;
    }

    /**
     * Distance from hostile players a timed-out search may settle for: half of the normal
     * rule, never zero, so the fallback cannot drop a player next to an enemy.
     */
    static int fallbackPlayerDistance(int normalDistance) {
        return Math.max(1, normalDistance / 2);
    }

    /** {@code attempt} counts automatic retries already made; 0 is the first search. */
    static boolean mayRetry(int attempt, int maxRetries) {
        return attempt >= 0 && attempt < maxRetries;
    }

    static boolean retryDue(long dueTick, long currentTick) {
        return currentTick >= dueTick;
    }
}

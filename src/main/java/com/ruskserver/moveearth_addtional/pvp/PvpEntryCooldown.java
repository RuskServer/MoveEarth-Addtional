package com.ruskserver.moveearth_addtional.pvp;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player spacing between PvP join/leave transitions. A transition is expensive
 * (inventory stash written to disk, cross-dimension teleport, entry-state sync), so a
 * player may make at most one every {@link #cooldownTicks()} server ticks.
 * Times are server tick counts; the instance is cleared when the server stops.
 */
final class PvpEntryCooldown {
    private static final int PRUNE_THRESHOLD = 64;

    private final long cooldownTicks;
    private final Map<UUID, Long> lastTransition = new HashMap<>();

    PvpEntryCooldown(long cooldownTicks) {
        if (cooldownTicks < 0) throw new IllegalArgumentException("cooldownTicks < 0");
        this.cooldownTicks = cooldownTicks;
    }

    long cooldownTicks() {
        return cooldownTicks;
    }

    /** Ticks the player still has to wait; 0 when a transition is allowed now. */
    long remainingTicks(UUID playerId, long now) {
        Long last = lastTransition.get(playerId);
        if (last == null || now < last) return 0L;
        return Math.max(0L, last + cooldownTicks - now);
    }

    void record(UUID playerId, long now) {
        if (lastTransition.size() >= PRUNE_THRESHOLD) {
            lastTransition.values().removeIf(last -> now < last || now - last >= cooldownTicks);
        }
        lastTransition.put(playerId, now);
    }

    void clear() {
        lastTransition.clear();
    }

    /** Whole seconds shown to the player, rounded up so a positive wait never reads as 0. */
    static long secondsCeil(long ticks) {
        return ticks <= 0 ? 0L : (ticks + 19L) / 20L;
    }
}

package com.ruskserver.moveearth_addtional.network.common;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Per-player token buckets for client-to-server packets.
 *
 * <p>Every player has one bucket shared by all MoveEarth packets ({@link #GLOBAL}) plus one bucket per
 * key (normally the packet type, or a packet type and sub-action). A packet passes only when both buckets
 * hold a token; it then takes one from each. Buckets refill one token per {@link Budget#refillMillis()}
 * up to {@link Budget#burst()}, so ordinary clicking and short bursts pass while a flood is cut to the
 * steady refill rate.
 *
 * <p>Thread-safe: packets are checked on Netty threads before any work reaches the server thread, while
 * logout and the coalescing flush run on the server thread. All state is in memory and is dropped per
 * player on logout.
 */
public final class C2SRateLimiter {
    /** Up to {@code burst} packets at once, then one more every {@code refillMillis}. */
    public record Budget(String name, int burst, long refillMillis) {
        public Budget {
            if (burst < 1 || refillMillis < 1L) throw new IllegalArgumentException("Invalid budget " + name);
        }

        /** Steady-state packets per second once the burst is spent. */
        public double perSecond() {
            return 1000.0D / refillMillis;
        }
    }

    /** Everything one player sends to MoveEarth, combined: 60 at once, then 40/s. */
    public static final Budget GLOBAL = new Budget("global", 60, 25L);
    /** Cheap continuous input such as mouse-wheel adjustments: 30 at once, then 20/s. */
    public static final Budget INPUT = new Budget("input", 30, 50L);
    /** Ordinary UI buttons that change state: 10 at once, then 5/s. */
    public static final Budget ACTION = new Budget("action", 10, 200L);
    /** Actions whose reply rebuilds a large snapshot: 5 at once, then 2.5/s. */
    public static final Budget HEAVY = new Budget("heavy", 5, 400L);
    /** Read-only snapshot requests; extra requests are folded into one: 3 at once, then 2.5/s. */
    public static final Budget REQUEST = new Budget("request", 3, 400L);
    /** Actions with server-wide side effects (founding, Discord linking): 3 at once, then one per 3 s. */
    public static final Budget SENSITIVE = new Budget("sensitive", 3, 3_000L);
    /** Sub-actions that write the shared audit log: 2 at once, then one per 10 s. */
    public static final Budget AUDIT = new Budget("audit", 2, 10_000L);

    private static final class Bucket {
        private int tokens;
        private long refilledAt;

        private Bucket(Budget budget, long now) {
            tokens = budget.burst();
            refilledAt = now;
        }

        private void refill(Budget budget, long now) {
            if (now < refilledAt) {
                // The clock went backwards (tests, or a restarted source): start over full.
                tokens = budget.burst();
                refilledAt = now;
                return;
            }
            long refills = (now - refilledAt) / budget.refillMillis();
            if (refills <= 0L) return;
            tokens = (int) Math.min(budget.burst(), tokens + refills);
            refilledAt = tokens == budget.burst() ? now : refilledAt + refills * budget.refillMillis();
        }
    }

    private static final class PlayerBuckets {
        private final Bucket global;
        private final Map<Object, Bucket> keyed = new HashMap<>();

        private PlayerBuckets(Budget budget, long now) {
            global = new Bucket(budget, now);
        }
    }

    private final Budget global;
    private final LongSupplier clockMillis;
    private final Map<UUID, PlayerBuckets> players = new HashMap<>();

    public C2SRateLimiter(Budget global, LongSupplier clockMillis) {
        this.global = global;
        this.clockMillis = clockMillis;
    }

    /** A limiter on the monotonic system clock with the default {@link #GLOBAL} budget. */
    public static C2SRateLimiter systemClock() {
        return new C2SRateLimiter(GLOBAL, () -> System.nanoTime() / 1_000_000L);
    }

    /** Takes a token from the player's global bucket and the keyed bucket; false refuses the packet. */
    public boolean tryAcquire(UUID player, Object key, Budget budget) {
        return tryAcquire(player, key, budget, true);
    }

    /**
     * @param countGlobal false for a sub-action check made after the packet already passed the gate,
     *                    so one packet is not charged to the global bucket twice
     */
    public synchronized boolean tryAcquire(UUID player, Object key, Budget budget, boolean countGlobal) {
        if (player == null || key == null || budget == null) return false;
        long now = clockMillis.getAsLong();
        PlayerBuckets buckets = players.computeIfAbsent(player, ignored -> new PlayerBuckets(global, now));
        Bucket own = buckets.keyed.computeIfAbsent(key, ignored -> new Bucket(budget, now));
        own.refill(budget, now);
        if (countGlobal) buckets.global.refill(global, now);
        if (own.tokens <= 0 || countGlobal && buckets.global.tokens <= 0) return false;
        own.tokens--;
        if (countGlobal) buckets.global.tokens--;
        return true;
    }

    public synchronized void forget(UUID player) {
        players.remove(player);
    }

    public synchronized void clear() {
        players.clear();
    }

    synchronized int trackedPlayers() {
        return players.size();
    }
}

package com.ruskserver.moveearth_addtional.economy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player limits on the market screen. Each snapshot expires orders and walks up to 256
 * stations, so snapshots go out at most once per {@link #COOLDOWN_TICKS}; replies inside the
 * cooldown are not dropped but folded into one send, carrying the latest selection and the latest
 * non-blank result, once it ends. Actions (trade, cancel, claim, waypoint) additionally draw on a
 * small token bucket, so ordinary clicking and short bursts pass while a flood of packets, even
 * with bogus ids, is refused before it touches the ledger.
 */
final class MarketResponseThrottle {
    static final int COOLDOWN_TICKS = 10;
    static final int ACTION_BURST = 6;
    static final int ACTION_REFILL_TICKS = 4;

    record Reply(UUID player, UUID selection, String result) { }

    private record Pending(UUID selection, String result) { }

    private static final class Bucket {
        private int tokens;
        private long refilledAt;
    }

    private final Map<UUID, Long> lastSent = new HashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, Bucket> buckets = new HashMap<>();
    private final Map<UUID, UUID> lastSelection = new HashMap<>();

    /**
     * The reply to send now, or null when it waits for {@link #due}. A null selection keeps the
     * station the player last had selected (used when refusing a flood, which names no station).
     */
    Reply respond(UUID player, UUID selection, String result, long tick) {
        Pending earlier = pending.get(player);
        UUID chosen = selection != null ? selection
                : earlier != null ? earlier.selection() : lastSelection.get(player);
        String kept = result == null || result.isBlank()
                ? earlier == null ? "" : earlier.result() : result;
        if (elapsed(lastSent.get(player), tick)) {
            lastSent.put(player, tick);
            pending.remove(player);
            remember(player, chosen);
            return new Reply(player, chosen, kept);
        }
        pending.put(player, new Pending(chosen, kept));
        return null;
    }

    /** Takes one action token; false means the action must be refused without running. */
    boolean allowAction(UUID player, long tick) {
        Bucket bucket = buckets.get(player);
        if (bucket == null || tick < bucket.refilledAt) {
            // New player, or the tick counter restarted with the server.
            bucket = new Bucket();
            bucket.tokens = ACTION_BURST;
            bucket.refilledAt = tick;
            buckets.put(player, bucket);
        }
        long refills = (tick - bucket.refilledAt) / ACTION_REFILL_TICKS;
        if (refills > 0) {
            bucket.tokens = (int) Math.min(ACTION_BURST, bucket.tokens + refills);
            bucket.refilledAt = bucket.tokens == ACTION_BURST ? tick
                    : bucket.refilledAt + refills * ACTION_REFILL_TICKS;
        }
        if (bucket.tokens <= 0) return false;
        bucket.tokens--;
        return true;
    }

    /** Deferred replies whose cooldown has ended; they count as sent. */
    List<Reply> due(long tick) {
        List<Reply> ready = new ArrayList<>();
        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!elapsed(lastSent.get(entry.getKey()), tick)) continue;
            ready.add(new Reply(entry.getKey(), entry.getValue().selection(), entry.getValue().result()));
            lastSent.put(entry.getKey(), tick);
            remember(entry.getKey(), entry.getValue().selection());
            iterator.remove();
        }
        return ready;
    }

    private void remember(UUID player, UUID selection) {
        if (selection != null) lastSelection.put(player, selection);
    }

    /** A tick before the last send means the server's tick counter restarted. */
    private static boolean elapsed(Long last, long tick) {
        return last == null || tick - last >= COOLDOWN_TICKS || tick < last;
    }

    void forget(UUID player) {
        lastSent.remove(player);
        pending.remove(player);
        buckets.remove(player);
        lastSelection.remove(player);
    }

    void clear() {
        lastSent.clear();
        pending.clear();
        buckets.clear();
        lastSelection.clear();
    }
}

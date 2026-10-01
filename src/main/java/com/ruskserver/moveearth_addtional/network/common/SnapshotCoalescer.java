package com.ruskserver.moveearth_addtional.network.common;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BinaryOperator;

/**
 * Sends a per-player snapshot at most once per {@code cooldownTicks}. A request inside the cooldown is
 * not dropped: it is folded into a single pending send (merged with {@link #merge}) that goes out from
 * {@link #due} once the cooldown ends. Server-thread only.
 *
 * @param <P> what a send needs, e.g. whether the screen should open
 */
public final class SnapshotCoalescer<P> {
    private final int cooldownTicks;
    private final BinaryOperator<P> merge;
    private final Map<UUID, Long> lastSent = new HashMap<>();
    private final Map<UUID, P> pending = new LinkedHashMap<>();

    /** @param merge combines an older pending request with a newer one */
    public SnapshotCoalescer(int cooldownTicks, BinaryOperator<P> merge) {
        if (cooldownTicks < 1) throw new IllegalArgumentException("cooldownTicks");
        this.cooldownTicks = cooldownTicks;
        this.merge = merge;
    }

    /** The request to send now, or null when it waits (merged) for {@link #due}. */
    public P request(UUID player, P request, long tick) {
        P earlier = pending.get(player);
        P merged = earlier == null ? request : merge.apply(earlier, request);
        if (elapsed(lastSent.get(player), tick)) {
            lastSent.put(player, tick);
            pending.remove(player);
            return merged;
        }
        pending.put(player, merged);
        return null;
    }

    /** Pending requests whose cooldown has ended, in request order; they count as sent. */
    public Map<UUID, P> due(long tick) {
        Map<UUID, P> ready = new LinkedHashMap<>();
        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!elapsed(lastSent.get(entry.getKey()), tick)) continue;
            ready.put(entry.getKey(), entry.getValue());
            lastSent.put(entry.getKey(), tick);
            iterator.remove();
        }
        return ready;
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    public void forget(UUID player) {
        lastSent.remove(player);
        pending.remove(player);
    }

    public void clear() {
        lastSent.clear();
        pending.clear();
    }

    /** A tick before the last send means the server's tick counter restarted. */
    private boolean elapsed(Long last, long tick) {
        return last == null || tick - last >= cooldownTicks || tick < last;
    }
}

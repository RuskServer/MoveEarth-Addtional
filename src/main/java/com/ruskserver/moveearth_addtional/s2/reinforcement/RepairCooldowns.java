package com.ruskserver.moveearth_addtional.s2.reinforcement;

import java.util.HashMap;
import java.util.Map;

/** Position-owned battle damage history, independent of replacement block identities. */
public final class RepairCooldowns {
    private final Map<Long, Long> deadlines = new HashMap<>();

    public long until(long position, long now) { return Math.max(now, deadlines.getOrDefault(position, 0L)); }

    public void hit(long position, long now, long delay) {
        if (delay > 0) deadlines.merge(position, now + delay, Math::max);
    }

    public boolean expire(long now) { return deadlines.values().removeIf(until -> until <= now); }

    public Map<Long, Long> snapshot() { return Map.copyOf(deadlines); }

    public void restore(long position, long deadline) { deadlines.merge(position, deadline, Math::max); }
}

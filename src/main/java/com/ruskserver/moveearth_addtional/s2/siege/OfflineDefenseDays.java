package com.ruskserver.moveearth_addtional.s2.siege;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Per core, whether offline defense applied at the first attack of an open day. Pure data for tests. */
final class OfflineDefenseDays {
    record Snapshot(long day, boolean allowed) { }

    private final Map<UUID, Snapshot> snapshots = new HashMap<>();

    /** Records the day's answer unless an earlier attack that day already did; returns whether anything changed. */
    boolean observe(UUID coreId, long day, boolean allowed) {
        Snapshot current = snapshots.get(coreId);
        if (current != null && current.day() == day) return false;
        snapshots.put(coreId, new Snapshot(day, allowed));
        snapshots.values().removeIf(snapshot -> snapshot.day() < day - 1);
        return true;
    }

    /** The day's answer, or null before the core's first attack that day. */
    Boolean allowedOn(UUID coreId, long day) {
        Snapshot current = snapshots.get(coreId);
        return current == null || current.day() != day ? null : current.allowed();
    }

    void put(UUID coreId, Snapshot snapshot) {
        snapshots.put(coreId, snapshot);
    }

    void forEach(BiConsumer<UUID, Snapshot> action) {
        snapshots.forEach(action);
    }
}

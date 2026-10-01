package com.ruskserver.moveearth_addtional.s2.siege;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Per core, whether offline defense applied when the day's current fight on it started. Pure data for tests. */
final class OfflineDefenseDays {
    record Snapshot(long day, boolean allowed) { }

    private final Map<UUID, Snapshot> snapshots = new HashMap<>();

    /**
     * Records the answer taken when a fight started on the core, replacing any earlier answer.
     * {@link OfflineDefenseDayPolicy#decides} chooses when; returns whether anything changed.
     */
    boolean record(UUID coreId, long day, boolean allowed) {
        Snapshot next = new Snapshot(day, allowed);
        Snapshot previous = snapshots.put(coreId, next);
        boolean pruned = snapshots.values().removeIf(snapshot -> snapshot.day() < day - 1);
        return pruned || !next.equals(previous);
    }

    /** The day's answer, or null before a fight started on the core that day. */
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

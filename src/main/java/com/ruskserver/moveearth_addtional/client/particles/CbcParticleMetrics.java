package com.ruskserver.moveearth_addtional.client.particles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class CbcParticleMetrics {
    record Summary(String type, long created, long dropped, long updates, long tickNanos, long collisionLodUpdates) { }
    private static final class Entry {
        long created;
        long dropped;
        long updates;
        long tickNanos;
        long collisionLodUpdates;
    }

    private final Map<String, Entry> entries = new HashMap<>();

    void created(String type, boolean dropped) {
        Entry entry = entries.computeIfAbsent(type, ignored -> new Entry());
        entry.created++;
        if (dropped) entry.dropped++;
    }

    void updated(String type, long nanos) {
        updated(type, nanos, false);
    }

    void updated(String type, long nanos, boolean collisionLod) {
        Entry entry = entries.computeIfAbsent(type, ignored -> new Entry());
        entry.updates++;
        entry.tickNanos += Math.max(0, nanos);
        if (collisionLod) entry.collisionLodUpdates++;
    }

    List<Summary> summary() {
        return entries.entrySet().stream()
                .sorted((first, second) -> Long.compare(second.getValue().tickNanos, first.getValue().tickNanos))
                .limit(8)
                .map(value -> {
                    Entry entry = value.getValue();
                    String type = value.getKey();
                    return new Summary(type.substring(type.lastIndexOf('.') + 1), entry.created,
                            entry.dropped, entry.updates, entry.tickNanos, entry.collisionLodUpdates);
                }).toList();
    }
}

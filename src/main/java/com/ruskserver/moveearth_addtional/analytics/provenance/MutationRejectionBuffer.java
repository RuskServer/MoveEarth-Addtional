package com.ruskserver.moveearth_addtional.analytics.provenance;

import java.util.*;

/** Bounded minute buckets. Counts guard refusals, not inferred attacks or destroyed blocks. */
public final class MutationRejectionBuffer {
    public static final int MAX_KEYS = 256;
    public record Key(String route, String dimension) { }
    public record Batch(long occurredAt, String route, String dimension, int x, int y, int z, long count) { }
    private static final class Counter {
        final long time;
        final int x, y, z;
        long count;
        Counter(long time, int x, int y, int z) { this.time = time; this.x = x; this.y = y; this.z = z; }
    }
    private final Map<Key, Counter> counters = new LinkedHashMap<>();

    public synchronized void add(long now, String route, String dimension, int x, int y, int z) {
        Key key = new Key(route, dimension);
        // Keep totals even under pathological dimension cardinality; no unbounded location map.
        if (!counters.containsKey(key) && counters.size() >= MAX_KEYS) key = new Key("overflow", "multiple");
        Counter counter = counters.computeIfAbsent(key, ignored -> new Counter(now, x, y, z));
        counter.count++;
    }

    /** Caller drains at minute boundaries and shutdown, including when analytics is disabled. */
    public synchronized List<Batch> drain() {
        List<Batch> result = new ArrayList<>(counters.size());
        counters.forEach((key, counter) -> result.add(new Batch(counter.time, key.route, key.dimension,
                counter.x, counter.y, counter.z, counter.count)));
        counters.clear();
        return result;
    }
}

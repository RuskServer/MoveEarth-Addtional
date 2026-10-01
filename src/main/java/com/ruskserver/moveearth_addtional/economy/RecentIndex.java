package com.ruskserver.moveearth_addtional.economy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The newest entries per key, capped per key, so a history lookup costs at most the cap instead
 * of a scan of the whole journal. Entries are kept newest first in insertion order.
 */
public final class RecentIndex<K, V> {
    private final int limit;
    private final Map<K, ArrayDeque<V>> byKey = new HashMap<>();

    public RecentIndex(int limit) {
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        this.limit = limit;
    }

    public int limit() { return limit; }

    public void add(K key, V value) {
        if (key == null || value == null) return;
        ArrayDeque<V> recent = byKey.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        recent.addFirst(value);
        if (recent.size() > limit) recent.removeLast();
    }

    /** Up to {@code max} entries for the key, newest first; never more than the cap. */
    public List<V> recent(K key, int max) {
        ArrayDeque<V> recent = key == null ? null : byKey.get(key);
        if (recent == null || max <= 0) return List.of();
        List<V> out = new ArrayList<>(Math.min(max, recent.size()));
        for (V value : recent) {
            if (out.size() >= max) break;
            out.add(value);
        }
        return List.copyOf(out);
    }

    public void remove(K key) { byKey.remove(key); }

    public int keys() { return byKey.size(); }
}

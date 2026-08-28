package com.ruskserver.moveearth_addtional.analytics.query.cache;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 有効期限（TTL）付きのスレッドセーフなインメモリクエリキャッシュ
 */
public class AnalyticsQueryCache<K, V> {

    private record CacheEntry<V>(V value, long expiresAtMs) {
        boolean isExpired(long nowMs) {
            return nowMs >= expiresAtMs;
        }
    }

    private final long ttlMs;
    private final ConcurrentHashMap<K, CacheEntry<V>> map = new ConcurrentHashMap<>();

    public AnalyticsQueryCache(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    public void put(K key, V value) {
        if (key == null || value == null) return;
        long expiresAt = System.currentTimeMillis() + ttlMs;
        map.put(key, new CacheEntry<>(value, expiresAt));
    }

    public Optional<V> get(K key) {
        if (key == null) return Optional.empty();
        CacheEntry<V> entry = map.get(key);
        if (entry == null) return Optional.empty();

        if (entry.isExpired(System.currentTimeMillis())) {
            map.remove(key, entry);
            return Optional.empty();
        }

        return Optional.ofNullable(entry.value());
    }

    public void invalidate(K key) {
        if (key != null) {
            map.remove(key);
        }
    }

    public void clear() {
        map.clear();
    }

    public int size() {
        return map.size();
    }
}

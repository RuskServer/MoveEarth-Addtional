package com.ruskserver.moveearth_addtional.analytics;

import com.ruskserver.moveearth_addtional.analytics.query.cache.AnalyticsQueryCache;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class AnalyticsQueryCacheTest {

    @Test
    public void testPutAndGet() {
        AnalyticsQueryCache<String, String> cache = new AnalyticsQueryCache<>(1000L);

        cache.put("key1", "value1");
        Optional<String> val = cache.get("key1");

        assertTrue(val.isPresent());
        assertEquals("value1", val.get());
        assertEquals(1, cache.size());
    }

    @Test
    public void testExpiration() throws InterruptedException {
        // 50ms TTLの短いキャッシュ
        AnalyticsQueryCache<String, String> cache = new AnalyticsQueryCache<>(50L);

        cache.put("key1", "value1");
        assertTrue(cache.get("key1").isPresent());

        Thread.sleep(70L);

        // 期限切れでemptyが返り、マップからも削除される
        assertFalse(cache.get("key1").isPresent());
    }

    @Test
    public void testInvalidateAndClear() {
        AnalyticsQueryCache<String, String> cache = new AnalyticsQueryCache<>(1000L);

        cache.put("k1", "v1");
        cache.put("k2", "v2");
        assertEquals(2, cache.size());

        cache.invalidate("k1");
        assertFalse(cache.get("k1").isPresent());
        assertTrue(cache.get("k2").isPresent());

        cache.clear();
        assertEquals(0, cache.size());
    }
}

package com.ruskserver.moveearth_addtional.handler.dcc;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongConsumer;

/**
 * Minecraft-independent per-player state for the delayed chunk cache.
 */
final class DelayedChunkCacheState {
    private final LinkedHashMap<Long, Long> cachedAtByChunk = new LinkedHashMap<>();

    boolean contains(long packedPos) {
        return this.cachedAtByChunk.containsKey(packedPos);
    }

    boolean remove(long packedPos) {
        return this.cachedAtByChunk.remove(packedPos) != null;
    }

    void put(long packedPos, long gameTime, int sizeLimit, LongConsumer onEvict) {
        this.cachedAtByChunk.remove(packedPos);
        this.cachedAtByChunk.put(packedPos, gameTime);
        enforceCapacity(sizeLimit, onEvict);
    }

    void enforceCapacity(int sizeLimit, LongConsumer onEvict) {
        Iterator<Long> iterator = this.cachedAtByChunk.keySet().iterator();
        while (this.cachedAtByChunk.size() > sizeLimit && iterator.hasNext()) {
            long packedPos = iterator.next();
            iterator.remove();
            onEvict.accept(packedPos);
        }
    }

    void evictTooFar(int centerX, int centerZ, int maximumDistance, LongConsumer onEvict) {
        Iterator<Map.Entry<Long, Long>> iterator = this.cachedAtByChunk.entrySet().iterator();
        while (iterator.hasNext()) {
            long packedPos = iterator.next().getKey();
            int distance = Math.max(
                    Math.abs(centerX - unpackX(packedPos)),
                    Math.abs(centerZ - unpackZ(packedPos))
            );
            if (distance > maximumDistance) {
                iterator.remove();
                onEvict.accept(packedPos);
            }
        }
    }

    void evictTimedOut(long gameTime, long timeoutTicks, LongConsumer onEvict) {
        Iterator<Map.Entry<Long, Long>> iterator = this.cachedAtByChunk.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Long> entry = iterator.next();
            if (gameTime - entry.getValue() < timeoutTicks) {
                return;
            }
            iterator.remove();
            onEvict.accept(entry.getKey());
        }
    }

    void flush(LongConsumer onEvict) {
        this.cachedAtByChunk.keySet().forEach(onEvict::accept);
        this.cachedAtByChunk.clear();
    }

    void forEach(LongConsumer action) {
        this.cachedAtByChunk.keySet().forEach(action::accept);
    }

    int size() {
        return this.cachedAtByChunk.size();
    }

    static long pack(int x, int z) {
        return (long)x & 0xFFFFFFFFL | ((long)z & 0xFFFFFFFFL) << 32;
    }

    static int unpackX(long packedPos) {
        return (int)(packedPos & 0xFFFFFFFFL);
    }

    static int unpackZ(long packedPos) {
        return (int)(packedPos >>> 32 & 0xFFFFFFFFL);
    }
}

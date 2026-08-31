package com.ruskserver.moveearth_addtional.handler.dcc;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelayedChunkCacheStateTest {
    @Test
    void consumesCacheHitWithoutEviction() {
        DelayedChunkCacheState state = new DelayedChunkCacheState();
        List<Long> evicted = new ArrayList<>();
        long chunk = DelayedChunkCacheState.pack(-3, 0);

        state.put(chunk, 10, 64, evicted::add);

        assertTrue(state.contains(chunk));
        assertTrue(state.remove(chunk));
        assertFalse(state.contains(chunk));
        assertTrue(evicted.isEmpty());
    }

    @Test
    void expiresOldestEntriesWhilePlayerRemainsStill() {
        DelayedChunkCacheState state = new DelayedChunkCacheState();
        List<Long> evicted = new ArrayList<>();
        long oldChunk = DelayedChunkCacheState.pack(-3, 0);
        long freshChunk = DelayedChunkCacheState.pack(-3, 1);

        state.put(oldChunk, 1, 64, evicted::add);
        state.put(freshChunk, 15, 64, evicted::add);
        state.evictTimedOut(21, 20, evicted::add);

        assertEquals(List.of(oldChunk), evicted);
        assertFalse(state.contains(oldChunk));
        assertTrue(state.contains(freshChunk));
    }

    @Test
    void enforcesExactCapacityInOldestFirstOrder() {
        DelayedChunkCacheState state = new DelayedChunkCacheState();
        List<Long> evicted = new ArrayList<>();
        long oldest = DelayedChunkCacheState.pack(-3, 0);
        long newest = DelayedChunkCacheState.pack(-3, 1);

        state.put(oldest, 1, 1, evicted::add);
        state.put(newest, 2, 1, evicted::add);

        assertEquals(1, state.size());
        assertEquals(List.of(oldest), evicted);
        assertTrue(state.contains(newest));
    }

    @Test
    void evictsChunksBeyondConfiguredChessboardDistance() {
        DelayedChunkCacheState state = new DelayedChunkCacheState();
        List<Long> evicted = new ArrayList<>();
        long near = DelayedChunkCacheState.pack(4, -4);
        long far = DelayedChunkCacheState.pack(5, 0);

        state.put(near, 1, 64, evicted::add);
        state.put(far, 2, 64, evicted::add);
        state.evictTooFar(0, 0, 4, evicted::add);

        assertTrue(state.contains(near));
        assertFalse(state.contains(far));
        assertEquals(List.of(far), evicted);
    }
}

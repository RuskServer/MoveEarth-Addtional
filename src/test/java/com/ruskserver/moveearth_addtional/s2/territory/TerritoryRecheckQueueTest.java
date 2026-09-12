package com.ruskserver.moveearth_addtional.s2.territory;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerritoryRecheckQueueTest {
    @Test
    void keepsEarliestDeadlineWhenChangesAreDebounced() {
        TerritoryRecheckQueue<String> queue = new TerritoryRecheckQueue<>();
        queue.schedule("core", 120L);
        queue.schedule("core", 160L);
        queue.schedule("core", 100L);

        assertTrue(queue.pollDue(99L, 2).isEmpty());
        assertEquals(List.of("core"), queue.pollDue(100L, 2));
        assertEquals(0, queue.size());
    }

    @Test
    void limitsWorkPerTickAndLeavesRemainingEntriesQueued() {
        TerritoryRecheckQueue<String> queue = new TerritoryRecheckQueue<>();
        queue.schedule("one", 10L);
        queue.schedule("two", 10L);
        queue.schedule("three", 10L);

        assertEquals(List.of("one", "two"), queue.pollDue(10L, 2));
        assertEquals(1, queue.size());
        assertEquals(List.of("three"), queue.pollDue(10L, 2));
    }

    @Test
    void clearDropsPendingServerState() {
        TerritoryRecheckQueue<String> queue = new TerritoryRecheckQueue<>();
        queue.schedule("core", 1L);
        queue.clear();
        assertTrue(queue.pollDue(10L, 2).isEmpty());
    }
}

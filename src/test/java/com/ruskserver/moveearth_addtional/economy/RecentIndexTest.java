package com.ruskserver.moveearth_addtional.economy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecentIndexTest {
    @Test
    void keepsTheNewestEntriesPerKeyUpToTheCap() {
        RecentIndex<String, Integer> index = new RecentIndex<>(3);
        for (int i = 1; i <= 10; i++) index.add("nation", i);
        index.add("player", 99);
        assertEquals(List.of(10, 9, 8), index.recent("nation", 100), "newest first, never beyond the cap");
        assertEquals(List.of(10, 9), index.recent("nation", 2));
        assertEquals(List.of(99), index.recent("player", 5));
    }

    @Test
    void unknownKeysAndNonPositiveLimitsAreEmpty() {
        RecentIndex<String, Integer> index = new RecentIndex<>(5);
        index.add("a", 1);
        assertTrue(index.recent("b", 5).isEmpty());
        assertTrue(index.recent("a", 0).isEmpty());
        assertTrue(index.recent(null, 5).isEmpty());
    }

    @Test
    void removingAKeyDropsItsHistory() {
        RecentIndex<String, Integer> index = new RecentIndex<>(5);
        index.add("disbanded", 1);
        index.remove("disbanded");
        assertTrue(index.recent("disbanded", 5).isEmpty());
        assertEquals(0, index.keys());
    }

    @Test
    void returnedListsAreSnapshots() {
        RecentIndex<String, Integer> index = new RecentIndex<>(5);
        index.add("a", 1);
        List<Integer> before = index.recent("a", 5);
        index.add("a", 2);
        assertEquals(List.of(1), before);
        assertThrows(UnsupportedOperationException.class, () -> before.add(3));
    }

    @Test
    void capMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new RecentIndex<String, Integer>(0));
    }
}

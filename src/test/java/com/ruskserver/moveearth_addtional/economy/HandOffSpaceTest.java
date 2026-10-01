package com.ruskserver.moveearth_addtional.economy;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HandOffSpaceTest {
    private static HandOffSpace<String> space(String[] keys, int[] counts, boolean[] takesNew) {
        return new HandOffSpace<>(Arrays.asList(keys), counts, takesNew, Objects::equals, 99);
    }

    @Test
    void partialStacksAndEmptySlotsTakeWhatFits() {
        HandOffSpace<String> space = space(new String[]{"iron", null, "dirt"}, new int[]{60, 0, 64},
                new boolean[]{true, true, true});
        assertEquals(68, space.book("iron", 100, 64), "4 top up the partial stack, 64 fill the empty slot");
        assertEquals(0, space.book("quartz", 4, 64), "booked space is gone for the next item");
    }

    @Test
    void theOffhandOnlyTopsUpAMatchingStack() {
        HandOffSpace<String> space = space(new String[]{"stone", "diamond"}, new int[]{64, 10},
                new boolean[]{true, false});
        assertEquals(54, space.book("diamond", 60, 64));
        HandOffSpace<String> emptyOffhand = space(new String[]{"stone", null}, new int[]{64, 0},
                new boolean[]{true, false});
        assertEquals(0, emptyOffhand.book("diamond", 5, 64));
    }

    @Test
    void unstackableItemsTakeOneSlotEach() {
        HandOffSpace<String> space = space(new String[]{null, null, "gun"}, new int[]{0, 0, 1},
                new boolean[]{true, true, true});
        assertEquals(2, space.book("gun", 5, 1));
    }

    @Test
    void aFullInventoryBooksNothing() {
        HandOffSpace<String> space = space(new String[]{"stone"}, new int[]{64}, new boolean[]{true});
        assertEquals(0, space.book("stone", 1, 64));
        assertEquals(0, space.book("iron", 0, 64));
    }
}

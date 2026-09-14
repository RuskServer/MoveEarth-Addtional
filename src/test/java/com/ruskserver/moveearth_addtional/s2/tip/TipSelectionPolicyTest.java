package com.ruskserver.moveearth_addtional.s2.tip;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TipSelectionPolicyTest {
    @Test
    void selectsOnlyUnreadTipsWhileAnyRemain() {
        String selected = TipSelectionPolicy.select(List.of("a", "b", "c"), Set.of("a", "c"), "a", 4L);
        assertEquals("b", selected);
    }

    @Test
    void avoidsImmediateRepeatAfterCompletingACycle() {
        String selected = TipSelectionPolicy.select(List.of("a", "b", "c"), Set.of("a", "b", "c"), "b", 0L);
        assertNotEquals("b", selected);
    }

    @Test
    void supportsASingleTipCatalog() {
        assertEquals("a", TipSelectionPolicy.select(List.of("a"), Set.of("a"), "a", 2L));
    }

    @Test
    void rejectsAnEmptyCatalog() {
        assertNull(TipSelectionPolicy.select(List.of(), Set.of(), null, 0L));
    }
}

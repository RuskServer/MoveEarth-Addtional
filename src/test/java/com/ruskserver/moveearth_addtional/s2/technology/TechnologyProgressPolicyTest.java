package com.ruskserver.moveearth_addtional.s2.technology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TechnologyProgressPolicyTest {
    @Test void incrementIsBoundedAndIgnoresNegativeAmounts() {
        assertEquals(5L, TechnologyProgressPolicy.increment(3L, 8L, 5L));
        assertEquals(3L, TechnologyProgressPolicy.increment(3L, -2L, 5L));
        assertEquals(0L, TechnologyProgressPolicy.increment(-10L, 0L, 5L));
    }
}

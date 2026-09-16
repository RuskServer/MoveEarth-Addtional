package com.ruskserver.moveearth_addtional.s2.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenTimeSavedDataTest {
    @Test
    void clockIsMonotonicAndSaturating() {
        assertEquals(0L, OpenTimePolicy.advance(0L, -20L));
        assertEquals(60L, OpenTimePolicy.advance(20L, 40L));
        assertEquals(Long.MAX_VALUE, OpenTimePolicy.advance(60L, Long.MAX_VALUE));
    }
}

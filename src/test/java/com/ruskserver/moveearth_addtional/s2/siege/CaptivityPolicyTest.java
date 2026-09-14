package com.ruskserver.moveearth_addtional.s2.siege;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CaptivityPolicyTest {
    @Test
    void advancesOnlyByExplicitOpenTicks() {
        assertEquals(180L, CaptivityPolicy.advance(200L, 20L));
        assertEquals(200L, CaptivityPolicy.advance(200L, 0L));
        assertEquals(0L, CaptivityPolicy.advance(10L, 20L));
    }
}

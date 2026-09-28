package com.ruskserver.moveearth_addtional.s2.territory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguringReservationTest {
    @Test
    void aReservationLastsOneHourOfOpenTime() {
        long start = 1_000L;
        assertTrue(ConfiguringReservationPolicy.live(start, start));
        assertTrue(ConfiguringReservationPolicy.live(start, start + ConfiguringReservationPolicy.LIMIT_OPEN_TICKS - 1));
        assertFalse(ConfiguringReservationPolicy.live(start, start + ConfiguringReservationPolicy.LIMIT_OPEN_TICKS));
    }
}

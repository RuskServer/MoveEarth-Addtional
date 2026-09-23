package com.ruskserver.moveearth_addtional.pvp;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerOpenDayCycleTest {
    @Test
    void switchesOpeningDayAtNineteenJst() {
        assertEquals("2026-09-02", ServerOpenDayCycle.currentId(clockAt(2026, 9, 3, 18, 59)));
        assertEquals("2026-09-03", ServerOpenDayCycle.currentId(clockAt(2026, 9, 3, 19, 0)));
    }

    @Test
    void reportsNextNineteenOClockReset() {
        assertEquals(
                ZonedDateTime.of(2026, 9, 3, 19, 0, 0, 0, ServerOpenDayCycle.JST),
                ServerOpenDayCycle.nextReset(clockAt(2026, 9, 3, 18, 59)));
        assertEquals(
                ZonedDateTime.of(2026, 9, 4, 19, 0, 0, 0, ServerOpenDayCycle.JST),
                ServerOpenDayCycle.nextReset(clockAt(2026, 9, 3, 19, 0)));
    }

    private static Clock clockAt(int year, int month, int day, int hour, int minute) {
        ZonedDateTime time = ZonedDateTime.of(
                year, month, day, hour, minute, 0, 0, ServerOpenDayCycle.JST);
        return Clock.fixed(time.toInstant(), ServerOpenDayCycle.JST);
    }
}

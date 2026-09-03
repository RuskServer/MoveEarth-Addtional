package com.ruskserver.moveearth_addtional.tpa;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenDayCycleTest {
    @Test
    void switchesOpeningDayAtEighteenJst() {
        assertEquals("2026-09-02", OpenDayCycle.currentId(clockAt(2026, 9, 3, 17, 59)));
        assertEquals("2026-09-03", OpenDayCycle.currentId(clockAt(2026, 9, 3, 18, 0)));
    }

    @Test
    void reportsNextEighteenOClockReset() {
        assertEquals(
                ZonedDateTime.of(2026, 9, 3, 18, 0, 0, 0, OpenDayCycle.JST),
                OpenDayCycle.nextReset(clockAt(2026, 9, 3, 17, 59)));
        assertEquals(
                ZonedDateTime.of(2026, 9, 4, 18, 0, 0, 0, OpenDayCycle.JST),
                OpenDayCycle.nextReset(clockAt(2026, 9, 3, 18, 0)));
    }

    private static Clock clockAt(int year, int month, int day, int hour, int minute) {
        ZonedDateTime time = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, OpenDayCycle.JST);
        return Clock.fixed(time.toInstant(), OpenDayCycle.JST);
    }
}

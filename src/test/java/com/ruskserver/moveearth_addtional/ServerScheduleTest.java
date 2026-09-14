package com.ruskserver.moveearth_addtional;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerScheduleTest {
    @Test
    void opensAtEighteen() {
        assertFalse(ServerSchedule.isOpenHour(17));
        assertTrue(ServerSchedule.isOpenHour(18));
    }

    @Test
    void closesAtMidnight() {
        assertTrue(ServerSchedule.isOpenHour(23));
        assertFalse(ServerSchedule.isOpenHour(0));
    }

    @Test
    void openNowUsesTheJstWindow() {
        assertTrue(ServerSchedule.isOpenNow(Clock.fixed(
                Instant.parse("2026-09-14T09:00:00Z"), ZoneOffset.UTC)));
        assertTrue(ServerSchedule.isOpenNow(Clock.fixed(
                Instant.parse("2026-09-14T14:59:59Z"), ZoneOffset.UTC)));
        assertFalse(ServerSchedule.isOpenNow(Clock.fixed(
                Instant.parse("2026-09-14T15:00:00Z"), ZoneOffset.UTC)));
    }
}

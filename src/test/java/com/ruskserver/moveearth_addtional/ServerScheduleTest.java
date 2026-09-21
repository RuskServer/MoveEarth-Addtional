package com.ruskserver.moveearth_addtional;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerScheduleTest {
    @Test
    void opensAtNineteen() {
        assertFalse(ServerSchedule.isOpenHour(18));
        assertTrue(ServerSchedule.isOpenHour(19));
    }

    @Test
    void closesAtTwentyThree() {
        assertTrue(ServerSchedule.isOpenHour(22));
        assertFalse(ServerSchedule.isOpenHour(23));
    }

    @Test
    void openNowUsesTheJstWindow() {
        assertTrue(ServerSchedule.isOpenNow(Clock.fixed(
                Instant.parse("2026-09-14T10:00:00Z"), ZoneOffset.UTC)));
        assertTrue(ServerSchedule.isOpenNow(Clock.fixed(
                Instant.parse("2026-09-14T13:59:59Z"), ZoneOffset.UTC)));
        assertFalse(ServerSchedule.isOpenNow(Clock.fixed(
                Instant.parse("2026-09-14T14:00:00Z"), ZoneOffset.UTC)));
    }
}

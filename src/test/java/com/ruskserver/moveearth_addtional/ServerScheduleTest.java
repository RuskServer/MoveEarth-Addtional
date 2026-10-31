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
    @Test
    void newSiegesStopOneHourBeforeClosing() {
        for (int hour = 0; hour < 24; hour++) {
            org.junit.jupiter.api.Assertions.assertEquals(hour >= 19 && hour < 22,
                    ServerSchedule.isSiegeStartHour(hour), "hour=" + hour);
        }
        org.junit.jupiter.api.Assertions.assertEquals(ServerSchedule.CLOSE_HOUR - 1,
                ServerSchedule.SIEGE_START_CUTOFF_HOUR);
    }

    @Test
    void siegeWindowUsesJstAndExactCutoffAcrossDays() {
        assertFalse(ServerSchedule.canStartSiegeNow(Clock.fixed(
                Instant.parse("2026-10-27T09:59:59Z"), ZoneOffset.UTC)));
        assertTrue(ServerSchedule.canStartSiegeNow(Clock.fixed(
                Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC)));
        assertTrue(ServerSchedule.canStartSiegeNow(Clock.fixed(
                Instant.parse("2026-10-27T12:59:59.999Z"), ZoneOffset.UTC)));
        assertFalse(ServerSchedule.canStartSiegeNow(Clock.fixed(
                Instant.parse("2026-10-27T13:00:00Z"), ZoneOffset.UTC)));
        assertFalse(ServerSchedule.canStartSiegeNow(Clock.fixed(
                Instant.parse("2026-10-27T15:00:00Z"), ZoneOffset.UTC)));
        assertTrue(ServerSchedule.canStartSiegeNow(Clock.fixed(
                Instant.parse("2026-10-28T10:00:00Z"), ZoneOffset.UTC)));
    }
}

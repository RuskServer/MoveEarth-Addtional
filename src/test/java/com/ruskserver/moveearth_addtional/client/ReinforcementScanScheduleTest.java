package com.ruskserver.moveearth_addtional.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReinforcementScanScheduleTest {
    @Test
    void movementRequestsImmediately() {
        ReinforcementScanSchedule schedule = new ReinforcementScanSchedule();
        schedule.requested(0L);

        assertTrue(schedule.tick(true, 0L));
    }

    @Test
    void idleScansBackOffUpToTheCap() {
        ReinforcementScanSchedule schedule = new ReinforcementScanSchedule();
        schedule.requested(5L);

        assertEquals(ReinforcementScanSchedule.BASE_INTERVAL_TICKS, ticksUntilRequest(schedule, 5L));
        schedule.requested(5L);
        assertEquals(400, ticksUntilRequest(schedule, 5L));
        schedule.requested(5L);
        assertEquals(ReinforcementScanSchedule.MAX_INTERVAL_TICKS, ticksUntilRequest(schedule, 5L));
        schedule.requested(5L);
        assertEquals(ReinforcementScanSchedule.MAX_INTERVAL_TICKS, ticksUntilRequest(schedule, 5L));
    }

    @Test
    void anyChangeReturnsToTheBaseInterval() {
        ReinforcementScanSchedule schedule = new ReinforcementScanSchedule();
        schedule.requested(1L);
        ticksUntilRequest(schedule, 1L);
        schedule.requested(1L);
        ticksUntilRequest(schedule, 1L);
        schedule.requested(1L);
        assertEquals(ReinforcementScanSchedule.MAX_INTERVAL_TICKS, ticksUntilRequest(schedule, 2L));

        schedule.requested(2L);
        assertEquals(ReinforcementScanSchedule.BASE_INTERVAL_TICKS, ticksUntilRequest(schedule, 2L));
    }

    @Test
    void resetRestoresTheBaseInterval() {
        ReinforcementScanSchedule schedule = new ReinforcementScanSchedule();
        schedule.requested(0L);
        ticksUntilRequest(schedule, 0L);
        schedule.requested(0L);
        ticksUntilRequest(schedule, 0L);
        schedule.reset();

        assertEquals(ReinforcementScanSchedule.BASE_INTERVAL_TICKS, schedule.interval());
        assertFalse(schedule.tick(false, 0L));
    }

    private static int ticksUntilRequest(ReinforcementScanSchedule schedule, long changeCount) {
        for (int tick = 1; tick <= 10_000; tick++) {
            if (schedule.tick(false, changeCount)) return tick;
        }
        throw new AssertionError("never requested");
    }
}

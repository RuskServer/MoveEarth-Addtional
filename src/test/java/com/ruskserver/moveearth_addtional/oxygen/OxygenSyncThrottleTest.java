package com.ruskserver.moveearth_addtional.oxygen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OxygenSyncThrottleTest {
    private static OxygenSyncThrottle.Snapshot snapshot(int oxygen, int filter, boolean mask, boolean danger) {
        return new OxygenSyncThrottle.Snapshot(oxygen, filter, mask, danger, false, 1.0F, false, false, false);
    }

    @Test
    void sendsFirstStateThenNothingWhileIdle() {
        OxygenSyncThrottle throttle = new OxygenSyncThrottle(10);
        assertTrue(throttle.shouldSend(snapshot(1000, 0, false, false), 0L));
        int sent = 0;
        for (long tick = 1L; tick <= 200L; tick++) {
            if (throttle.shouldSend(snapshot(1000, 0, false, false), tick)) sent++;
        }
        assertEquals(0, sent);
    }

    @Test
    void movingGaugesAreSentAtMostOncePerInterval() {
        OxygenSyncThrottle throttle = new OxygenSyncThrottle(10);
        throttle.shouldSend(snapshot(1000, 1000, true, true), 0L);
        int sent = 0;
        for (long tick = 1L; tick <= 100L; tick++) {
            if (throttle.shouldSend(snapshot(1000, (int) (1000 - tick), true, true), tick)) sent++;
        }
        assertEquals(10, sent);
    }

    @Test
    void zoneOrMaskChangesAreSentAtOnce() {
        OxygenSyncThrottle throttle = new OxygenSyncThrottle(10);
        throttle.shouldSend(snapshot(1000, 0, false, false), 0L);
        assertTrue(throttle.shouldSend(snapshot(1000, 0, false, true), 1L));
        assertTrue(throttle.shouldSend(snapshot(1000, 900, true, true), 2L));
    }

    @Test
    void runningOutIsSentAtOnce() {
        OxygenSyncThrottle throttle = new OxygenSyncThrottle(10);
        throttle.shouldSend(snapshot(5, 0, false, true), 0L);
        assertFalse(throttle.shouldSend(snapshot(3, 0, false, true), 1L));
        assertTrue(throttle.shouldSend(snapshot(0, 0, false, true), 2L));
    }

    @Test
    void gaugeIsClampedToItsRange() {
        assertEquals(0, OxygenSyncThrottle.gauge(-1.0F));
        assertEquals(1000, OxygenSyncThrottle.gauge(2.0F));
        assertEquals(500, OxygenSyncThrottle.gauge(0.5F));
    }
}

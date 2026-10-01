package com.ruskserver.moveearth_addtional.s2.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestCampfireProbeTest {
    @Test
    void scansAtMostOncePerIntervalWhenAskedEveryTick() {
        RestCampfireProbe probe = new RestCampfireProbe(20);
        int scans = 0;
        for (long tick = 1000L; tick < 1100L; tick++) {
            if (probe.tryAcquire(tick)) scans++;
        }
        assertEquals(5, scans);
    }

    @Test
    void firstRequestScansImmediately() {
        assertTrue(new RestCampfireProbe(20).tryAcquire(0L));
    }

    @Test
    void scanAllowedExactlyWhenTheIntervalElapsed() {
        RestCampfireProbe probe = new RestCampfireProbe(20);
        assertTrue(probe.tryAcquire(100L));
        assertFalse(probe.tryAcquire(119L));
        assertTrue(probe.tryAcquire(120L));
    }

    @Test
    void clockGoingBackwardsDoesNotBlockScans() {
        RestCampfireProbe probe = new RestCampfireProbe(20);
        assertTrue(probe.tryAcquire(5_000L));
        assertTrue(probe.tryAcquire(10L));
        assertFalse(probe.tryAcquire(11L));
    }
}

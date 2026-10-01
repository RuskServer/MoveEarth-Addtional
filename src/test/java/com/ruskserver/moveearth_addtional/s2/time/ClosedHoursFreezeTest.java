package com.ruskserver.moveearth_addtional.s2.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClosedHoursFreezeTest {
    @Test
    void pausedCycleDoesNotBecomeTheOperatorRuleAtRestartOrOpening() {
        for (boolean savedOperatorRule : new boolean[]{true, false}) {
            boolean closed = ClosedHoursFreeze.shouldFreeze(true, true, false);
            assertFalse(ClosedHoursFreeze.allowCycle(savedOperatorRule, closed));
            boolean reopened = ClosedHoursFreeze.shouldFreeze(true, true, true);
            assertEquals(savedOperatorRule, ClosedHoursFreeze.allowCycle(savedOperatorRule, reopened));
        }
    }

    @Test
    void integratedServersNeverFreeze() {
        assertFalse(ClosedHoursFreeze.shouldFreeze(true, false, false));
        assertFalse(ClosedHoursFreeze.shouldFreeze(true, false, true));
    }

    @Test
    void disablingWhileClosedReleasesCycles() {
        assertFalse(ClosedHoursFreeze.shouldFreeze(false, true, false));
        assertFalse(ClosedHoursFreeze.shouldFreeze(false, true, true));
    }
}

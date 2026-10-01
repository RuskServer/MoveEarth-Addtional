package com.ruskserver.moveearth_addtional.compat.aeronautics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JointWindupLimitTest {
    private static final double MIN_LEAD = 10.0;

    @Test
    void freeJointFollowsItsTarget() {
        assertEquals(33.0, JointWindupLimit.limitAdvance(30.0, 33.0, 29.0, MIN_LEAD));
        assertEquals(-33.0, JointWindupLimit.limitAdvance(-30.0, -33.0, -29.0, MIN_LEAD));
    }

    @Test
    void blockedHingeStopsWindingUpAtTheLead() {
        // Held at 0 degrees while turning 3 degrees a tick: the target stops at 10.
        double target = 0.0;
        for (int tick = 0; tick < 60; tick++) {
            target = JointWindupLimit.limitAdvance(target, Math.min(90.0, target + 3.0), 0.0, MIN_LEAD);
        }
        assertEquals(10.0, target);
        double reverse = 0.0;
        for (int tick = 0; tick < 60; tick++) {
            reverse = JointWindupLimit.limitAdvance(reverse, Math.max(-90.0, reverse - 3.0), 0.0, MIN_LEAD);
        }
        assertEquals(-10.0, reverse);
    }

    @Test
    void targetIsNeverPulledBackTowardTheLeaf() {
        assertEquals(20.0, JointWindupLimit.limitAdvance(20.0, 23.0, -40.0, MIN_LEAD));
        assertEquals(45.0, JointWindupLimit.limitAdvance(45.0, 45.0, -60.0, MIN_LEAD));
    }

    @Test
    void fastJointMayLeadByTwoTicksOfMotion() {
        // 19.2 degrees a tick (64 RPM): the lead grows to 38.4 degrees.
        assertEquals(38.4, JointWindupLimit.limitAdvance(30.0, 49.2, 0.0, MIN_LEAD), 1.0E-9);
    }

    @Test
    void unknownAngleLeavesTheTargetAlone() {
        assertEquals(12.0, JointWindupLimit.limitAdvance(9.0, 12.0, Double.NaN, MIN_LEAD));
        assertEquals(12.0, JointWindupLimit.limitWrappedAdvance(9.0, 12.0, Double.NaN, MIN_LEAD));
    }

    @Test
    void blockedSwivelStopsWindingUpAcrossTheWrap() {
        // Held at 355 degrees while turning 4 degrees a tick, with the target kept in (-360, 360)
        // the way the swivel bearing does it: the target stops 10 degrees ahead, at 5.
        double target = 355.0;
        for (int tick = 0; tick < 200; tick++) {
            target = JointWindupLimit.limitWrappedAdvance(target, (target + 4.0) % 360.0, -5.0, MIN_LEAD);
        }
        assertEquals(0.0, JointWindupLimit.wrap(target - 5.0), 1.0E-9);
    }

    @Test
    void blockedSwivelTurningBackwardsStopsToo() {
        double target = -170.0;
        for (int tick = 0; tick < 200; tick++) {
            target = JointWindupLimit.limitWrappedAdvance(target, (target - 4.0) % 360.0, -175.0, MIN_LEAD);
        }
        // 10 degrees past -175 the short way round is 175.
        assertEquals(0.0, JointWindupLimit.wrap(target - 175.0), 1.0E-9);
    }

    @Test
    void freeSwivelIsUntouchedEvenWhenItsTargetWraps() {
        assertEquals(2.0, JointWindupLimit.limitWrappedAdvance(358.0, 2.0 % 360.0, 357.0, MIN_LEAD));
        assertEquals(-2.0, JointWindupLimit.limitWrappedAdvance(-358.0, -2.0, -357.0, MIN_LEAD));
        assertEquals(90.0, JointWindupLimit.limitWrappedAdvance(90.0, 90.0, -45.0, MIN_LEAD));
    }

    @Test
    void wrapsIntoHalfOpenRange() {
        assertEquals(-180.0, JointWindupLimit.wrap(180.0));
        assertEquals(170.0, JointWindupLimit.wrap(-190.0));
        assertEquals(0.0, JointWindupLimit.wrap(720.0));
    }
}

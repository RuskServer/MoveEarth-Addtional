package com.ruskserver.moveearth_addtional.compat.aeronautics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BalloonLeakPolicyTest {
    private static final double NORMAL = 9.0D * 20.0D;

    @Test
    void aHoleThatLeavesTooLittleRoomLeaksSlowly() {
        assertEquals(600.0D, BalloonLeakPolicy.emptyingTicks(true, NORMAL, false, 40, 100.0D, 30.0D));
        assertEquals(600.0D, BalloonLeakPolicy.emptyingTicks(true, NORMAL, true, 0, 0.0D, 30.0D));
    }

    @Test
    void turningABurnerDownKeepsAeronauticsOwnTime() {
        assertEquals(NORMAL, BalloonLeakPolicy.emptyingTicks(true, NORMAL, false, 200, 50.0D, 30.0D));
    }

    @Test
    void disabledOrFasterLeaksNeverSpeedItUp() {
        assertEquals(NORMAL, BalloonLeakPolicy.emptyingTicks(false, NORMAL, true, 0, 100.0D, 30.0D));
        assertEquals(NORMAL, BalloonLeakPolicy.emptyingTicks(true, NORMAL, true, 0, 100.0D, 5.0D));
    }
}

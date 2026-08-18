package com.ruskserver.moveearth_addtional.territory.raid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RaidPowerBalanceTest {
    @Test
    void scalesWithSquareRootOfValidStress() {
        assertEquals(64.0D, RaidPowerBalance.strength(1024.0D, 2.0D, 128.0D));
    }

    @Test
    void capsSingleEmitterStrength() {
        assertEquals(128.0D, RaidPowerBalance.strength(16384.0D, 2.0D, 128.0D));
    }

    @Test
    void rejectsInvalidOrUnpoweredInput() {
        assertEquals(0.0D, RaidPowerBalance.strength(0.0D, 2.0D, 128.0D));
        assertEquals(0.0D, RaidPowerBalance.strength(Double.NaN, 2.0D, 128.0D));
        assertEquals(0.0D, RaidPowerBalance.strength(1024.0D, 0.0D, 128.0D));
    }
}

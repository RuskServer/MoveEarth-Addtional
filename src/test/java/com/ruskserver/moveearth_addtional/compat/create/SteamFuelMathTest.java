package com.ruskserver.moveearth_addtional.compat.create;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SteamFuelMathTest {
    @Test
    void loadIsTheShareOfCapacityInUse() {
        assertEquals(0.25D, SteamFuelMath.load(256.0F, 1024.0F), 1.0E-9D);
        assertEquals(0.0D, SteamFuelMath.load(0.0F, 1024.0F), 1.0E-9D);
    }

    @Test
    void overstressedNetworksCountAsFullyLoaded() {
        assertEquals(1.0D, SteamFuelMath.load(4096.0F, 1024.0F), 1.0E-9D);
        assertEquals(1.0D, SteamFuelMath.load(64.0F, 0.0F), 1.0E-9D);
    }

    @Test
    void networksWithNoCapacityAndNoDemandAreIdle() {
        assertEquals(0.0D, SteamFuelMath.load(0.0F, 0.0F), 1.0E-9D);
    }

    @Test
    void burnRateScalesLinearlyFromIdleToFullLoad() {
        assertEquals(0.2D, SteamFuelMath.burnRate(0.0D, 0.2D, 2.5D), 1.0E-9D);
        assertEquals(1.35D, SteamFuelMath.burnRate(0.5D, 0.2D, 2.5D), 1.0E-9D);
        assertEquals(2.5D, SteamFuelMath.burnRate(1.0D, 0.2D, 2.5D), 1.0E-9D);
    }

    @Test
    void burnRateNeverFallsBelowIdleOrZero() {
        assertEquals(0.0D, SteamFuelMath.burnRate(0.5D, -1.0D, 0.0D), 1.0E-9D);
        assertEquals(0.8D, SteamFuelMath.burnRate(1.0D, 0.8D, 0.5D), 1.0E-9D);
    }
}

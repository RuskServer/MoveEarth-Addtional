package com.ruskserver.moveearth_addtional.compat.create;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaterWheelBalanceTest {
    @Test
    void localBudgetStaysFullUntilItsAllowanceIsExceeded() {
        assertEquals(1.0D, WaterWheelBalanceMath.densityMultiplier(1.0D, 6.0D), 1.0E-9D);
        assertEquals(1.0D, WaterWheelBalanceMath.densityMultiplier(6.0D, 6.0D), 1.0E-9D);
        assertEquals(0.5D, WaterWheelBalanceMath.densityMultiplier(12.0D, 6.0D), 1.0E-9D);
    }

    @Test
    void densitySharingCapsLinearScaling() {
        double sixWheels = 6.0D * WaterWheelBalanceMath.densityMultiplier(6.0D, 6.0D);
        double twelveWheels = 12.0D * WaterWheelBalanceMath.densityMultiplier(12.0D, 6.0D);
        assertEquals(sixWheels, twelveWheels, 1.0E-9D);
    }

    @Test
    void sourceAndDensityMultipliersCompose() {
        assertEquals(0.24375D,
                WaterWheelBalanceMath.combinedMultiplier(0.65D, 0.50D, 0.50D, 0.50D), 1.0E-9D);
        assertEquals(0.65D,
                WaterWheelBalanceMath.combinedMultiplier(0.65D, 1.0D, 1.0D, 0.50D), 1.0E-9D);
    }

    @Test
    void sourceQualityIsOnlyAHalfStrengthPenaltyByDefault() {
        assertEquals(0.50625D,
                WaterWheelBalanceMath.combinedMultiplier(0.75D, 0.35D, 1.0D, 0.50D), 1.0E-9D);
        assertEquals(0.5625D,
                WaterWheelBalanceMath.combinedMultiplier(0.75D, 0.50D, 1.0D, 0.50D), 1.0E-9D);
    }
}

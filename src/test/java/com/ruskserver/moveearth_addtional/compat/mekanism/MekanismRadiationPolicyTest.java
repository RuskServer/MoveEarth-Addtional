package com.ruskserver.moveearth_addtional.compat.mekanism;

import com.ruskserver.moveearth_addtional.compat.mekanism.MekanismRadiationPolicy.Zone;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MekanismRadiationPolicyTest {
    private static final double MEKANISM = 0.9995D;

    @Test
    void eachZoneUsesItsOwnRate() {
        assertEquals(0.99D, MekanismRadiationPolicy.decayRate(MEKANISM, Zone.NEAR_CORE, 0.995D, 0.99D), 1.0E-12D);
        assertEquals(0.995D, MekanismRadiationPolicy.decayRate(MEKANISM, Zone.WILDERNESS, 0.995D, 0.99D), 1.0E-12D);
        assertEquals(MEKANISM, MekanismRadiationPolicy.decayRate(MEKANISM, Zone.TERRITORY, 0.995D, 0.99D), 1.0E-12D);
    }

    @Test
    void noZoneDecaysSlowerThanMekanism() {
        assertEquals(0.98D, MekanismRadiationPolicy.decayRate(0.98D, Zone.NEAR_CORE, 0.995D, 0.99D), 1.0E-12D);
    }

    @Test
    void crossBorderExposureIsScaledByTheMultiplier() {
        // Exposure is magnitude / distanceSqr, so a four-times distance is a quarter of the exposure.
        assertEquals(400.0D, MekanismRadiationPolicy.exposureDistanceSqr(100.0D, true, 0.25D), 1.0E-9D);
        assertEquals(100.0D, MekanismRadiationPolicy.exposureDistanceSqr(100.0D, false, 0.25D), 1.0E-9D);
        assertEquals(100.0D, MekanismRadiationPolicy.exposureDistanceSqr(100.0D, true, 1.0D), 1.0E-9D);
    }

    @Test
    void crossBorderScalingKeepsMekanismsOneBlockFloor() {
        assertEquals(4.0D, MekanismRadiationPolicy.exposureDistanceSqr(0.0D, true, 0.25D), 1.0E-9D);
        assertEquals(Double.POSITIVE_INFINITY, MekanismRadiationPolicy.exposureDistanceSqr(9.0D, true, 0.0D));
    }

    @Test
    void halfLivesMatchTheConfigComments() {
        assertEquals(23.1D, MekanismRadiationPolicy.halfLifeSteps(0.9995D) / 60.0D, 0.05D);
        assertEquals(5.8D, MekanismRadiationPolicy.halfLifeSteps(0.998D) / 60.0D, 0.05D);
        assertEquals(2.3D, MekanismRadiationPolicy.halfLifeSteps(0.995D) / 60.0D, 0.05D);
        assertEquals(69.0D, MekanismRadiationPolicy.halfLifeSteps(0.99D), 0.5D);
    }
}

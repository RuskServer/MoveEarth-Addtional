package com.ruskserver.moveearth_addtional.s2.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VehicleCoreHealthPolicyTest {
    @Test
    void damageClampsAtZeroAndIgnoresInvalidAmounts() {
        assertEquals(70, VehicleCoreHealthPolicy.damage(100, 30));
        assertEquals(0, VehicleCoreHealthPolicy.damage(20, 30));
        assertEquals(100, VehicleCoreHealthPolicy.damage(100, -5));
        assertEquals(0, VehicleCoreHealthPolicy.damage(0, 10));
    }
}

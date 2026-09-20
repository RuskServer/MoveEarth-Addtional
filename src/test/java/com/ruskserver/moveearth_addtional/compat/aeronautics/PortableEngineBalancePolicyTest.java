package com.ruskserver.moveearth_addtional.compat.aeronautics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortableEngineBalancePolicyTest {
    @Test
    void identifiesEveryColoredSimulatedPortableEngine() {
        assertTrue(PortableEngineBalancePolicy.isPortableEngine("simulated", "red_portable_engine"));
        assertTrue(PortableEngineBalancePolicy.isPortableEngine("simulated", "light_blue_portable_engine"));
        assertFalse(PortableEngineBalancePolicy.isPortableEngine("other", "red_portable_engine"));
        assertFalse(PortableEngineBalancePolicy.isPortableEngine("simulated", "portable_engine_part"));
    }

    @Test
    void capsDefaultCapacityAtThirtyTwoPerRpm() {
        float capacity = PortableEngineBalancePolicy.cappedCapacity(
                true, "simulated", "red_portable_engine", 64.0F, 32.0D);
        assertEquals(32.0F, capacity);
        assertEquals(1024.0F, capacity * 32.0F);
        assertEquals(2048.0F, capacity * 64.0F);
    }

    @Test
    void preservesLowerOperatorConfiguredCapacity() {
        assertEquals(24.0F, PortableEngineBalancePolicy.cappedCapacity(
                true, "simulated", "white_portable_engine", 24.0F, 32.0D));
    }

    @Test
    void leavesOtherBlocksAndDisabledBalanceUntouched() {
        assertEquals(64.0F, PortableEngineBalancePolicy.cappedCapacity(
                false, "simulated", "red_portable_engine", 64.0F, 32.0D));
        assertEquals(64.0F, PortableEngineBalancePolicy.cappedCapacity(
                true, "create", "steam_engine", 64.0F, 32.0D));
    }
}

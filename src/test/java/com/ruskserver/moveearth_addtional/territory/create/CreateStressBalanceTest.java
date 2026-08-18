package com.ruskserver.moveearth_addtional.territory.create;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CreateStressBalanceTest {
    @Test
    void unloadedAndOverstressedNetworksDoNotCount() {
        assertEquals(0.0D, CreateStressBalance.utilization(0.0D, 16_384.0D));
        assertEquals(0.0D, CreateStressBalance.utilization(20_000.0D, 16_384.0D));
    }

    @Test
    void loadIsAllocatedByNetworkUtilization() {
        assertEquals(0.5D, CreateStressBalance.utilization(8_192.0D, 16_384.0D));
        assertEquals(1.0D, CreateStressBalance.utilization(16_384.0D, 16_384.0D));
    }

    @Test
    void scoreUsesSquareRootDiminishingReturnsAndCap() {
        assertEquals(8.0D, CreateStressBalance.industrialScore(256.0D));
        assertEquals(64.0D, CreateStressBalance.industrialScore(16_384.0D));
        assertEquals(256.0D, CreateStressBalance.industrialScore(262_144.0D));
        assertEquals(256.0D, CreateStressBalance.industrialScore(1_000_000.0D));
    }
}

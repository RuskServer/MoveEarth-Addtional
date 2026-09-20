package com.ruskserver.moveearth_addtional.compat.mekanism;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MekanismCableUpgradePolicyTest {
    @Test
    void blocksEveryValidUniversalCableAlloyShortcut() {
        assertTrue(blocked("infused_alloy", "basic_universal_cable"));
        assertTrue(blocked("reinforced_alloy", "advanced_universal_cable"));
        assertTrue(blocked("atomic_alloy", "elite_universal_cable"));
    }

    @Test
    void leavesOtherTransmittersAndInvalidTierCombinationsAlone() {
        assertFalse(blocked("atomic_alloy", "basic_universal_cable"));
        assertFalse(blocked("infused_alloy", "basic_mechanical_pipe"));
        assertFalse(MekanismCableUpgradePolicy.shouldBlock(
                "other", "infused_alloy", "mekanism", "basic_universal_cable"));
        assertFalse(MekanismCableUpgradePolicy.shouldBlock(
                "mekanism", "infused_alloy", "other", "basic_universal_cable"));
    }

    private static boolean blocked(String itemPath, String blockPath) {
        return MekanismCableUpgradePolicy.shouldBlock(
                "mekanism", itemPath, "mekanism", blockPath);
    }
}

package com.ruskserver.moveearth_addtional.s2.reinforcement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReinforcementWrenchPolicyTest {
    @Test
    void recognizesItemsFromTheCommonWrenchTag() {
        assertTrue(ReinforcementWrenchPolicy.isWrench(true, "example", "maintenance_tool"));
    }

    @Test
    void recognizesCreateWrenchEvenWhenADataPackReplacesTheTag() {
        assertTrue(ReinforcementWrenchPolicy.isWrench(false, "create", "wrench"));
    }

    @Test
    void ignoresOrdinaryItems() {
        assertFalse(ReinforcementWrenchPolicy.isWrench(false, "minecraft", "stick"));
    }

    @Test
    void onlyBlocksWrenchesOnReinforcedPositions() {
        assertTrue(ReinforcementWrenchPolicy.shouldBlock(true, true));
        assertFalse(ReinforcementWrenchPolicy.shouldBlock(false, true));
        assertFalse(ReinforcementWrenchPolicy.shouldBlock(true, false));
    }
}

package com.ruskserver.moveearth_addtional.s2.siege;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerritoryBreakPolicyTest {
    @Test
    void solidBlocksCount() {
        assertTrue(TerritoryBreakPolicy.counts(1.5F, false));
        assertTrue(TerritoryBreakPolicy.counts(0.2F, false));
    }

    @Test
    void grassCropsAndReplaceableBlocksDoNot() {
        assertFalse(TerritoryBreakPolicy.counts(0.0F, false));
        assertFalse(TerritoryBreakPolicy.counts(0.1F, true));
    }

    @Test
    void unbreakableBlocksDoNot() {
        assertFalse(TerritoryBreakPolicy.counts(-1.0F, false));
    }
}

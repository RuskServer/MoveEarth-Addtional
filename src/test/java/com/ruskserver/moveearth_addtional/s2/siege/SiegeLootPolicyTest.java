package com.ruskserver.moveearth_addtional.s2.siege;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiegeLootPolicyTest {
    @Test void fallenLootStartsAtStageTwoOutsideCoreAndVault() {
        assertFalse(SiegeLootPolicy.fallenAccess(1, true, false, false));
        assertTrue(SiegeLootPolicy.fallenAccess(2, true, false, false));
        assertFalse(SiegeLootPolicy.fallenAccess(2, false, false, false));
        assertFalse(SiegeLootPolicy.fallenAccess(2, true, true, false));
        assertFalse(SiegeLootPolicy.fallenAccess(3, true, false, true));
    }

    @Test void finalizedWindowUsesOpenTimeAndStillProtectsVault() {
        assertTrue(SiegeLootPolicy.finalizedAccess(99, 100, true, true, false));
        assertFalse(SiegeLootPolicy.finalizedAccess(100, 100, true, true, false));
        assertFalse(SiegeLootPolicy.finalizedAccess(99, 100, false, true, false));
        assertFalse(SiegeLootPolicy.finalizedAccess(99, 100, true, false, false));
        assertFalse(SiegeLootPolicy.finalizedAccess(99, 100, true, true, true));
    }
}

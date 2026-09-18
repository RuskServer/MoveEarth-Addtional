package com.ruskserver.moveearth_addtional.compat.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SableVoidFailsafePolicyTest {
    @Test
    void onlyTriggersAfterBoundsPassConfiguredFloorDepth() {
        assertFalse(SableVoidFailsafePolicy.shouldRescue(-68.0D, -64, 4));
        assertTrue(SableVoidFailsafePolicy.shouldRescue(-68.01D, -64, 4));
        assertFalse(SableVoidFailsafePolicy.shouldRescue(Double.NaN, -64, 4));
    }

    @Test
    void liftsBottomAboveTerrainWithClearance() {
        assertEquals(143.0D,
                SableVoidFailsafePolicy.verticalDisplacement(-70.0D, 70, 3), 0.0001D);
    }
}

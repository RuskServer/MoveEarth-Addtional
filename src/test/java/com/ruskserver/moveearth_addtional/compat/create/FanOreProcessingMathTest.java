package com.ruskserver.moveearth_addtional.compat.create;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FanOreProcessingMathTest {
    @Test
    void scalesCreatesConfiguredTime() {
        assertEquals(1200, FanOreProcessingMath.scaledTicks(150, 8.0D));
        assertEquals(225, FanOreProcessingMath.scaledTicks(150, 1.5D));
    }

    @Test
    void neverShortensOrTouchesDisabledProcessing() {
        assertEquals(150, FanOreProcessingMath.scaledTicks(150, 1.0D));
        assertEquals(150, FanOreProcessingMath.scaledTicks(150, 0.5D));
        assertEquals(0, FanOreProcessingMath.scaledTicks(0, 8.0D));
    }

    @Test
    void leavesRoomForCreatesStackSizeFactor() {
        // Create multiplies by up to 4 for a 64-stack and adds one; that must not overflow.
        int scaled = FanOreProcessingMath.scaledTicks(Integer.MAX_VALUE / 2, 64.0D);
        assertEquals(Integer.MAX_VALUE / 16, scaled);
    }
}

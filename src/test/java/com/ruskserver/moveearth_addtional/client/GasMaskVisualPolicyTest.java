package com.ruskserver.moveearth_addtional.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GasMaskVisualPolicyTest {
    @Test
    void fadeApproachesItsTargetWithoutOvershooting() {
        float fadedIn = GasMaskVisualPolicy.approach(0.0F, 1.0F, 1.0F / 60.0F);
        assertTrue(fadedIn > 0.0F && fadedIn < 1.0F);
        float fadedOut = GasMaskVisualPolicy.approach(1.0F, 0.0F, 1.0F / 60.0F);
        assertTrue(fadedOut > 0.0F && fadedOut < 1.0F);
    }

    @Test
    void fogAndPanicStrengthsAreClamped() {
        assertEquals(0.0F, GasMaskVisualPolicy.fogStrength(1.0F));
        assertEquals(1.0F, GasMaskVisualPolicy.fogStrength(0.0F));
        assertEquals(0.0F, GasMaskVisualPolicy.panicStrength(0.8F));
        assertEquals(1.0F, GasMaskVisualPolicy.panicStrength(0.0F));
    }
}

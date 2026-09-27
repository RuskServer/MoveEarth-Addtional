package com.ruskserver.moveearth_addtional.client.scope;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScopePipMathTest {
    @Test
    void magnifiedScopesWithLensMasksAreEnabledRegardlessOfPack() {
        assertTrue(ScopePipMath.eligibleOptic(true, 4.25, 2, 1, 1));
        assertTrue(ScopePipMath.eligibleOptic(true, 2.5, 2, 1, 1));
        assertTrue(ScopePipMath.eligibleOptic(true, 8, 2, 1, 2));
        assertFalse(ScopePipMath.eligibleOptic(true, 1.25, 2, 1, 1));
        assertFalse(ScopePipMath.eligibleOptic(false, 4, 2, 1, 1));
        assertTrue(ScopePipMath.eligibleOptic(true, 4.25, 2, 0.5F, 1));
        assertFalse(ScopePipMath.eligibleOptic(true, 4.25, 2, 0, 1));
        assertFalse(ScopePipMath.eligibleOptic(true, Double.NaN, 2, 1, 1));
        assertFalse(ScopePipMath.eligibleOptic(true, 4.25, 2, Float.NaN, 1));
        assertFalse(ScopePipMath.eligibleOptic(true, 8, 2, 1, 0));
        assertFalse(ScopePipMath.eligibleOptic(true, 8, 2, 1, 128));
    }

    @Test
    void lensUsesAngularMagnificationRatherThanDividingDegrees() {
        double lens = ScopePipMath.lensFov(90, 4);
        assertEquals(28.072486935852957, lens, 1e-9);
        assertEquals(4, Math.tan(Math.toRadians(90) / 2) / Math.tan(Math.toRadians(lens) / 2), 1e-9);
        assertEquals(70, ScopePipMath.lensFov(70, 1), 1e-9);
    }

    @Test
    void invalidMagnificationKeepsOriginalFov() {
        assertEquals(70, ScopePipMath.lensFov(70, 0));
        assertEquals(70, ScopePipMath.lensFov(70, Double.NaN));
    }
}

package com.ruskserver.moveearth_addtional.compat.aeronautics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwivelConstraintPolicyTest {
    @Test
    void horizontalModeAcceptsBothVerticalDirections() {
        assertTrue(SwivelConstraintPolicy.shouldReplace(true, true, 0, 1, 0, 0, 1, 0));
        assertTrue(SwivelConstraintPolicy.shouldReplace(true, true, 0, -1, 0, 0, -1, 0));
    }

    @Test
    void horizontalModeRejectsSideFacingAndInvalidAxes() {
        assertFalse(SwivelConstraintPolicy.shouldReplace(true, true, 1, 0, 0, 1, 0, 0));
        assertFalse(SwivelConstraintPolicy.shouldReplace(true, true, 0, 0, 0, 0, 1, 0));
    }

    @Test
    void allOrientationModeAcceptsAnyUsablePair() {
        assertTrue(SwivelConstraintPolicy.shouldReplace(true, false, 1, 0, 0, 0, 0, -1));
    }

    @Test
    void disabledModeAlwaysKeepsOriginalConstraint() {
        assertFalse(SwivelConstraintPolicy.shouldReplace(false, false, 0, 1, 0, 0, 1, 0));
    }

    @Test
    void compatibilityAcceptsOnlyTestedVersionFamilies() {
        assertTrue(SwivelConstraintCompatibility.testedVersionStrings("1.3.2", "2.0.4-37"));
        assertFalse(SwivelConstraintCompatibility.testedVersionStrings("1.4.0", "2.0.4"));
        assertFalse(SwivelConstraintCompatibility.testedVersionStrings("1.3.2", "2.1.0"));
        assertFalse(SwivelConstraintCompatibility.testedVersionStrings("missing", "2.0.4"));
    }

}

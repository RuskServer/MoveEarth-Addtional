package com.ruskserver.moveearth_addtional.client.scope;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopeShadowMathTest {
    private static final double CX = 960;
    private static final double CY = 540;
    private static final double R = 200;

    @Test
    void aCenteredSteadyScopeKeepsNearlyTheWholeLensClear() {
        var shadow = ScopeShadowMath.shadow(CX, CY, R, CX, CY, 0, 0, 4, 1, 1);
        assertEquals(CX, shadow.clearX(), 1e-3);
        assertEquals(CY, shadow.clearY(), 1e-3);
        assertTrue(shadow.clearRadius() > R * 0.9, "only a thin rim shadow when aligned");
    }

    @Test
    void anOffCentreEyepieceShiftsTheClearDiscAwayAndCutsTheFarSide() {
        // Eyepiece 40 px to the right of the eye: the clear disc moves left of it.
        var shadow = ScopeShadowMath.shadow(CX + 40, CY, R, CX, CY, 0, 0, 4, 1, 1);
        assertTrue(shadow.clearX() < CX + 40);
        // The lens's right edge (CX + 40 + R) now lies outside the clear disc: a crescent there.
        assertTrue(CX + 40 + R - shadow.clearX() > shadow.clearRadius());
    }

    @Test
    void higherMagnificationIsMoreSensitiveToTheSameMisalignment() {
        var low = ScopeShadowMath.shadow(CX + 30, CY, R, CX, CY, 0, 0, 4, 1, 1);
        var high = ScopeShadowMath.shadow(CX + 30, CY, R, CX, CY, 0, 0, 12, 1, 1);
        assertTrue(Math.abs(high.clearX() - (CX + 30)) > Math.abs(low.clearX() - (CX + 30)));
    }

    @Test
    void theClearDiscGrowsInAsTheScopeComesUp() {
        var rising = ScopeShadowMath.shadow(CX, CY, R, CX, CY, 0, 0, 4, 0.2, 1);
        var up = ScopeShadowMath.shadow(CX, CY, R, CX, CY, 0, 0, 4, 1, 1);
        assertTrue(rising.clearRadius() < up.clearRadius());
    }

    @Test
    void zeroStrengthLeavesNoShadow() {
        var off = ScopeShadowMath.shadow(CX + 80, CY, R, CX, CY, 0, 0, 8, 1, 0);
        assertEquals(0.0, off.darkness(), 1e-6);
    }

    @Test
    void turningLagsBehindThenFadesAndIsCapped() {
        double[] lag = ScopeShadowMath.lag(0, 0, 10, 0, 1 / 60.0, 4);
        assertTrue(lag[0] < 0, "turning right lags the image left");
        double[] later = ScopeShadowMath.lag(lag[0], lag[1], 0, 0, 0.5, 4);
        assertTrue(Math.abs(later[0]) < Math.abs(lag[0]) * 0.1, "fades within a fraction of a second");
        double[] flick = ScopeShadowMath.lag(0, 0, 180, 0, 1 / 60.0, 12);
        assertEquals(ScopeShadowMath.MAX_LAG, Math.hypot(flick[0], flick[1]), 1e-9);
    }
}

package com.ruskserver.moveearth_addtional.jobs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JobProgressionTest {
    @Test
    void calculatesConfiguredQuadraticCurve() {
        assertEquals(100, JobProgression.xpNeededForNextLevel(1, 50, 100, 25, 10));
        assertEquals(135, JobProgression.xpNeededForNextLevel(2, 50, 100, 25, 10));
        assertEquals(190, JobProgression.xpNeededForNextLevel(3, 50, 100, 25, 10));
        assertEquals(1_135, JobProgression.xpNeededForNextLevel(10, 50, 100, 25, 10));
    }

    @Test
    void returnsZeroAtLevelCap() {
        assertEquals(0, JobProgression.xpNeededForNextLevel(50, 50, 100, 25, 10));
        assertEquals(0, JobProgression.xpNeededForNextLevel(51, 50, 100, 25, 10));
    }

    @Test
    void clampsInvalidNegativeCurveToOne() {
        assertEquals(1, JobProgression.xpNeededForNextLevel(1, 50, -10, 0, 0));
    }
}

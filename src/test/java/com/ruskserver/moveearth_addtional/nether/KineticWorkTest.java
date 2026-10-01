package com.ruskserver.moveearth_addtional.nether;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KineticWorkTest {
    @Test
    void refinerTakesThreeMinutesAtFullSpeedAndSixteenAtTheMinimum() {
        long rod = KineticWork.required(3.0D);
        assertEquals(3.0D, KineticWork.minutesAt(256.0F, rod), 1.0E-9);
        assertEquals(16.0D, KineticWork.minutesAt(48.0F, rod), 1.0E-9);
        assertEquals(12.0D, KineticWork.minutesAt(64.0F, rod), 1.0E-9);
    }

    @Test
    void nothingBelowTheMinimumAndDirectionDoesNotMatter() {
        assertEquals(0L, KineticWork.step(32.0F, 48.0F));
        assertEquals(48L, KineticWork.step(48.0F, 48.0F));
        assertEquals(64L, KineticWork.step(-64.0F, 48.0F));
        assertEquals(0L, KineticWork.step(0.0F, 0.0F));
    }

    @Test
    void speedAboveCreatesMaximumIsCapped() {
        assertEquals(256L, KineticWork.step(1024.0F, 48.0F));
    }

    @Test
    void stressTimesTimeIsTheSameAtEverySpeed() {
        long rod = KineticWork.required(3.0D);
        double stressPerRpm = 64.0D;
        double atFull = 256 * stressPerRpm * KineticWork.minutesAt(256.0F, rod);
        double atMinimum = 48 * stressPerRpm * KineticWork.minutesAt(48.0F, rod);
        assertEquals(atFull, atMinimum, 1.0E-6);
    }
}

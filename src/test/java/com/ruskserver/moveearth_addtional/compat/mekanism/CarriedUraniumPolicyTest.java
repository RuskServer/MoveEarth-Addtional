package com.ruskserver.moveearth_addtional.compat.mekanism;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarriedUraniumPolicyTest {
    private static final double RATE = 2.0E-6D;
    /** Mekanism's default targetDecayRate, applied about once a second. */
    private static final double DECAY = 0.9995D;
    /** Dose above which Mekanism's sickness can start: severity 0.1 on its 10 nSv to 100 Sv log scale. */
    private static final double SICKNESS = Math.pow(10.0D, -5.0D + 0.1D * 7.0D);

    @Test
    void blocksAndNuggetsScaleWithTheIngotsInThem() {
        assertEquals(1.0D, CarriedUraniumPolicy.ingotsPerItem("mekanism:ingot_uranium"));
        assertEquals(9.0D, CarriedUraniumPolicy.ingotsPerItem("mekanism:block_uranium"));
        assertEquals(1.0D / 9.0D, CarriedUraniumPolicy.ingotsPerItem("mekanism:nugget_uranium"), 1e-12);
        assertEquals(0.0D, CarriedUraniumPolicy.ingotsPerItem("mekanism:ingot_osmium"));
        assertEquals(0.0D, CarriedUraniumPolicy.dosePerSecond(64, 0.0D));
    }

    @Test
    void oneStackIsHarmlessForAWhileThenMildlySickening() {
        double perSecond = CarriedUraniumPolicy.dosePerSecond(64, RATE);
        assertTrue(carry(perSecond, 30 * 60) < SICKNESS, "half an hour with one stack is still fine");
        assertTrue(carry(perSecond, 60 * 60) > SICKNESS, "an hour with one stack brings on sickness");
        double steady = CarriedUraniumPolicy.steadyDose(perSecond, DECAY);
        assertEquals(71.1e-6, steady, 0.5e-6);
        assertTrue(steady < SICKNESS * 1.5, "one stack never gets far past the threshold");
    }

    @Test
    void moreUraniumSickensSooner() {
        assertTrue(carry(CarriedUraniumPolicy.dosePerSecond(128, RATE), 20 * 60) > SICKNESS);
        assertTrue(carry(CarriedUraniumPolicy.dosePerSecond(64 * 9, RATE), 4 * 60) > SICKNESS);
    }

    @Test
    void cleanWaterHalvesADoseInAboutHalfAMinute() {
        double dose = 1.0E-3D;
        for (int second = 0; second < 35; second++) dose = CarriedUraniumPolicy.washed(dose, 0.98D);
        assertEquals(0.5E-3D, dose, 0.03E-3D);
        assertEquals(0.0D, CarriedUraniumPolicy.washed(1.0E-6D, 0.98D), "a trace is washed off entirely");
        assertEquals(1.0E-3D, CarriedUraniumPolicy.washed(1.0E-3D, 1.0D), "1.0 turns washing off");
    }

    @Test
    void dosesReadInFittingUnits() {
        assertEquals("71 µSv", CarriedUraniumPolicy.formatDose(71.1e-6));
        assertEquals("1.2 mSv", CarriedUraniumPolicy.formatDose(1.2e-3));
        assertEquals("2.00 Sv", CarriedUraniumPolicy.formatDose(2.0));
    }

    private static double carry(double perSecond, int seconds) {
        double dose = 0.0D;
        for (int second = 0; second < seconds; second++) dose = (dose + perSecond) * DECAY;
        return dose;
    }
}

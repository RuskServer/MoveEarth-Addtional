package com.ruskserver.moveearth_addtional.s2.nation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NationTaxPolicyTest {
    @Test
    void singleCurrencyAwardsPayTheTaxOnceTheFractionAddsUp() {
        // Jobs pay one currency at a time: at 10 % every tenth one goes to the treasury.
        int carry = 0;
        int taxed = 0;
        int kept = 0;
        for (int i = 0; i < 10; i++) {
            NationTaxPolicy.Split split = NationTaxPolicy.split(1, 10, carry);
            taxed += split.tax();
            kept += split.net();
            carry = split.carry();
        }
        assertEquals(1, taxed);
        assertEquals(9, kept);
        assertEquals(0, carry);
    }

    @Test
    void theTaxNeverTakesMoreThanTheAwardNorCreatesCurrency() {
        for (int award = 1; award <= 5; award++) {
            for (int carry = 0; carry < 100; carry++) {
                NationTaxPolicy.Split split = NationTaxPolicy.split(award, NationTaxPolicy.MAX_PERCENT, carry);
                assertEquals(award, split.tax() + split.net());
                assertTrue(split.tax() <= award && split.carry() >= 0 && split.carry() < 100);
            }
        }
    }

    @Test
    void noTaxLeavesTheAwardWhole() {
        NationTaxPolicy.Split split = NationTaxPolicy.split(3, 0, 40);
        assertEquals(0, split.tax());
        assertEquals(3, split.net());
        assertEquals(40, split.carry());
        assertEquals(new NationTaxPolicy.Split(0, 0, 0), NationTaxPolicy.split(0, 20, 0));
    }

    @Test
    void ratesAreClampedToTheAllowedRange() {
        assertEquals(0, NationTaxPolicy.clamp(-3));
        assertEquals(NationTaxPolicy.MAX_PERCENT, NationTaxPolicy.clamp(80));
        assertEquals(NationTaxPolicy.split(10, NationTaxPolicy.MAX_PERCENT, 0), NationTaxPolicy.split(10, 90, 0));
    }

    @Test
    void raisesWaitADayButCutsNeverWait() {
        long day = NationTaxPolicy.RAISE_COOLDOWN_MILLIS;
        assertTrue(NationTaxPolicy.mayChange(0, 10, 0L, 1_000L));
        assertFalse(NationTaxPolicy.mayChange(10, 15, 5_000L, 5_000L + day - 1L));
        assertTrue(NationTaxPolicy.mayChange(10, 15, 5_000L, 5_000L + day));
        assertTrue(NationTaxPolicy.mayChange(15, 5, 5_000L, 5_001L));
        assertFalse(NationTaxPolicy.mayChange(10, 10, 0L, 1_000L));
        assertFalse(NationTaxPolicy.mayChange(10, NationTaxPolicy.MAX_PERCENT + 1, 0L, 1_000L));
        assertEquals(1L, NationTaxPolicy.raiseWaitMinutes(5_000L, 5_000L + day - 1L));
        assertEquals(0L, NationTaxPolicy.raiseWaitMinutes(0L, 1_000L));
    }
}

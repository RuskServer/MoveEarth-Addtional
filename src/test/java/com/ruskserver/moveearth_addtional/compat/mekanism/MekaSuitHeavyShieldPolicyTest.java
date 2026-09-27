package com.ruskserver.moveearth_addtional.compat.mekanism;

import com.ruskserver.moveearth_addtional.compat.mekanism.MekaSuitHeavyShieldPolicy.Outcome;
import com.ruskserver.moveearth_addtional.compat.mekanism.MekaSuitHeavyShieldPolicy.Settings;
import com.ruskserver.moveearth_addtional.compat.mekanism.MekaSuitHeavyShieldPolicy.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MekaSuitHeavyShieldPolicyTest {
    private static final Settings SETTINGS = new Settings(16.0D, 12.0D, 2, 400L);

    @Test
    void lightHitsPassUntouchedAndKeepCharges() {
        Outcome outcome = MekaSuitHeavyShieldPolicy.onHit(15.9F, State.full(2), 100L, SETTINGS);
        assertFalse(outcome.blocked());
        assertEquals(15.9F, outcome.damage(), 1.0E-6F);
        assertEquals(2, outcome.state().charges());
    }

    @Test
    void heavyHitLosesAFlatAmountAndSpendsACharge() {
        Outcome outcome = MekaSuitHeavyShieldPolicy.onHit(30.0F, State.full(2), 100L, SETTINGS);
        assertTrue(outcome.blocked());
        assertEquals(18.0F, outcome.damage(), 1.0E-6F);
        assertEquals(1, outcome.state().charges());
        assertEquals(500L, outcome.state().readyAt());
        assertFalse(outcome.depleted());
    }

    @Test
    void reductionNeverGoesBelowZero() {
        Settings strong = new Settings(1.0D, 50.0D, 2, 400L);
        assertEquals(0.0F, MekaSuitHeavyShieldPolicy.onHit(20.0F, State.full(2), 0L, strong).damage(), 1.0E-6F);
    }

    @Test
    void lastChargeReportsDepletionAndFurtherHeavyHitsGoThrough() {
        Outcome first = MekaSuitHeavyShieldPolicy.onHit(20.0F, State.full(2), 100L, SETTINGS);
        Outcome second = MekaSuitHeavyShieldPolicy.onHit(20.0F, first.state(), 150L, SETTINGS);
        assertTrue(second.depleted());
        assertEquals(0, second.state().charges());
        Outcome third = MekaSuitHeavyShieldPolicy.onHit(20.0F, second.state(), 200L, SETTINGS);
        assertFalse(third.blocked());
        assertEquals(20.0F, third.damage(), 1.0E-6F);
    }

    @Test
    void everyStoppedHitRestartsTheCooldown() {
        Outcome first = MekaSuitHeavyShieldPolicy.onHit(20.0F, State.full(2), 100L, SETTINGS);
        Outcome second = MekaSuitHeavyShieldPolicy.onHit(20.0F, first.state(), 300L, SETTINGS);
        assertEquals(700L, second.state().readyAt());
    }

    @Test
    void chargesComeBackOnceTheCooldownRunsOut() {
        State empty = new State(0, 550L);
        assertEquals(0, MekaSuitHeavyShieldPolicy.current(empty, 549L, 2).charges());
        assertEquals(2, MekaSuitHeavyShieldPolicy.current(empty, 550L, 2).charges());
        assertTrue(MekaSuitHeavyShieldPolicy.justRecharged(empty, 550L, 2));
        assertFalse(MekaSuitHeavyShieldPolicy.justRecharged(empty, 549L, 2));
        assertFalse(MekaSuitHeavyShieldPolicy.justRecharged(State.full(2), 550L, 2));
    }

    @Test
    void savedChargesAboveANewLowerMaximumAreClamped() {
        assertEquals(2, MekaSuitHeavyShieldPolicy.current(new State(5, 1_000L), 10L, 2).charges());
    }
}

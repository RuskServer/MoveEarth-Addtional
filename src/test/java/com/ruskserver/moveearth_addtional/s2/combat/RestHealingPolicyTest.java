package com.ruskserver.moveearth_addtional.s2.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RestHealingPolicyTest {
    @Test
    void advancesAcrossOneOrMoreAllowancePeriods() {
        assertEquals(new RestHealingPolicy.Clock(0L, 80L), RestHealingPolicy.advance(100L, 60L, 20L));
        assertEquals(new RestHealingPolicy.Clock(1L, 10L), RestHealingPolicy.advance(100L, 90L, 20L));
        assertEquals(new RestHealingPolicy.Clock(3L, 5L), RestHealingPolicy.advance(100L, 5L, 300L));
    }

    @Test
    void clampsRemainingAllowance() {
        assertEquals(20.0F, RestHealingPolicy.remaining(20.0F, -1.0F));
        assertEquals(7.5F, RestHealingPolicy.remaining(20.0F, 12.5F));
        assertEquals(0.0F, RestHealingPolicy.remaining(20.0F, 30.0F));
    }

    @Test
    void healingCannotExceedMissingHealthAllowanceOrStep() {
        assertEquals(1.0F, RestHealingPolicy.healAmount(12.0F, 20.0F, 1.0F));
        assertEquals(0.5F, RestHealingPolicy.healAmount(0.5F, 20.0F, 1.0F));
        assertEquals(0.25F, RestHealingPolicy.healAmount(12.0F, 0.25F, 1.0F));
        assertEquals(0.0F, RestHealingPolicy.healAmount(12.0F, 0.0F, 1.0F));
    }
}

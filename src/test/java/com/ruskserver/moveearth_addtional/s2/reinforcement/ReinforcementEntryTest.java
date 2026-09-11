package com.ruskserver.moveearth_addtional.s2.reinforcement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReinforcementEntryTest {
    @Test
    void materialDurabilityMatchesPlan() {
        assertEquals(32, ReinforcementMaterial.COBBLESTONE.maxDurability());
        assertEquals(64, ReinforcementMaterial.COPPER.maxDurability());
        assertEquals(128, ReinforcementMaterial.IRON.maxDurability());
        assertEquals(192, ReinforcementMaterial.GOLD.maxDurability());
        assertEquals(320, ReinforcementMaterial.DIAMOND.maxDurability());
    }

    @Test
    void repairAddsQuarterAndNeverExceedsMaximum() {
        ReinforcementEntry damaged = new ReinforcementEntry(ReinforcementMaterial.IRON, 100, true);
        assertEquals(128, damaged.repair().durability());
        assertFalse(damaged.repair().damaged());
    }

    @Test
    void malformedDurabilityIsClamped() {
        assertEquals(0, new ReinforcementEntry(ReinforcementMaterial.GOLD, -1, true).durability());
        assertEquals(192, new ReinforcementEntry(ReinforcementMaterial.GOLD, 999, true).durability());
    }

    @Test
    void damageCannotDropBelowZero() {
        ReinforcementEntry entry = new ReinforcementEntry(ReinforcementMaterial.COBBLESTONE, 1, true);
        assertEquals(0, entry.damage(5).durability());
    }

    @Test
    void newReinforcementWaitsThirtySecondsBeforeActivation() {
        ReinforcementEntry pending = ReinforcementEntry.pending(ReinforcementMaterial.IRON, 100L);
        assertFalse(pending.enabled());
        assertEquals(600L, pending.activationTicksRemaining(100L));
        assertEquals(pending, pending.advance(699L));

        ReinforcementEntry activated = pending.advance(700L);
        assertTrue(activated.enabled());
        assertEquals(1, activated.durability());
    }

    @Test
    void durabilityFillsGraduallyAndCompletesAfterSixtySeconds() {
        ReinforcementEntry pending = ReinforcementEntry.pending(ReinforcementMaterial.IRON, 0L);
        ReinforcementEntry halfway = pending.advance(ReinforcementEntry.ACTIVATION_DELAY_TICKS
                + ReinforcementEntry.HP_FILL_TICKS / 2L);
        assertTrue(halfway.enabled());
        assertTrue(halfway.durability() >= 64 && halfway.durability() < 128);
        assertTrue(halfway.activatesAt() > 0L);

        ReinforcementEntry completed = halfway.advance(ReinforcementEntry.ACTIVATION_DELAY_TICKS
                + ReinforcementEntry.HP_FILL_TICKS);
        assertEquals(128, completed.durability());
        assertEquals(0L, completed.activatesAt());
    }

    @Test
    void legacyCompletedEntryDoesNotEnterConstructionAgain() {
        ReinforcementEntry legacy = ReinforcementEntry.full(ReinforcementMaterial.DIAMOND);
        assertSame(legacy, legacy.advance(999_999L));
    }
}

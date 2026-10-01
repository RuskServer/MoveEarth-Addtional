package com.ruskserver.moveearth_addtional.s2.reinforcement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReinforcementChangeDetectionTest {
    private static final long PLACED = 1_000L;
    private static final long ACTIVATES = PLACED + ReinforcementEntry.ACTIVATION_DELAY_TICKS;

    @Test
    void activationCountdownIsNotAChange() {
        ReinforcementEntry pending = ReinforcementEntry.pending(ReinforcementMaterial.IRON, PLACED);
        for (long tick = PLACED; tick < ACTIVATES; tick += 20L) {
            ReinforcementEntry.Step step = pending.step(tick);
            assertFalse(step.changed(), "tick " + tick);
            assertSame(pending, step.after());
            assertTrue(step.after().constructing());
        }
    }

    @Test
    void activationAndFillAreChanges() {
        ReinforcementEntry entry = ReinforcementEntry.pending(ReinforcementMaterial.IRON, PLACED);
        ReinforcementEntry.Step activated = entry.step(ACTIVATES);
        assertTrue(activated.changed());
        assertTrue(activated.activated());
        assertFalse(activated.completed());
        assertTrue(activated.after().enabled());

        ReinforcementEntry.Step filling = activated.after().step(ACTIVATES + 20L);
        assertTrue(filling.changed());
        assertFalse(filling.activated());
        assertTrue(filling.after().durability() > activated.after().durability());

        ReinforcementEntry.Step completed = filling.after().step(ACTIVATES + ReinforcementEntry.HP_FILL_TICKS);
        assertTrue(completed.changed());
        assertTrue(completed.completed());
        assertFalse(completed.after().constructing());
        assertFalse(completed.after().damaged());
    }

    @Test
    void finishedArmorNeverChanges() {
        ReinforcementEntry full = ReinforcementEntry.full(ReinforcementMaterial.GOLD);
        assertFalse(full.step(123_456L).changed());
        ReinforcementEntry damaged = new ReinforcementEntry(ReinforcementMaterial.GOLD, 10, true);
        assertFalse(damaged.step(123_456L).changed());
    }

    @Test
    void scanSignatureIgnoresTheRunningCountdownButNotRealChanges() {
        ReinforcementEntry pending = ReinforcementEntry.pending(ReinforcementMaterial.COPPER, PLACED);
        long base = ReinforcementScanSignature.add(ReinforcementScanSignature.EMPTY, 9L, pending, false);
        // Nothing time-dependent enters the hash: the same entry hashes identically on every scan.
        assertEquals(base, ReinforcementScanSignature.add(ReinforcementScanSignature.EMPTY, 9L,
                pending.step(PLACED + 200L).after(), false));
        assertNotEquals(base, ReinforcementScanSignature.add(ReinforcementScanSignature.EMPTY, 9L,
                ReinforcementEntry.pending(ReinforcementMaterial.COPPER, PLACED + 20L), false));
        assertNotEquals(base, ReinforcementScanSignature.add(ReinforcementScanSignature.EMPTY, 9L,
                pending.step(ACTIVATES).after(), false));
        assertNotEquals(base, ReinforcementScanSignature.add(ReinforcementScanSignature.EMPTY, 9L, pending, true));
        assertNotEquals(base, ReinforcementScanSignature.add(ReinforcementScanSignature.EMPTY, 10L, pending, false));
    }
}

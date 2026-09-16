package com.ruskserver.moveearth_addtional.s2.recovery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryObjectivePolicyTest {
    @Test
    void unlocksAidOnlyFromCompletedObjectives() {
        var initial = RecoveryObjectivePolicy.evaluate(false, 10, 0, false);
        assertEquals(30, initial.supportPercent());
        assertFalse(initial.resealed());
        assertFalse(initial.wallsRestored());

        var complete = RecoveryObjectivePolicy.evaluate(true, 10, 10, true);
        assertEquals(100, complete.supportPercent());
        assertTrue(complete.resealed());
        assertTrue(complete.wallsRestored());
        assertTrue(complete.upkeepPaid());
    }

    @Test
    void missingWallSnapshotUsesTheResealFallback() {
        var progress = RecoveryObjectivePolicy.evaluate(true, 0, 0, false);
        assertTrue(progress.wallsRestored());
        assertEquals(80, progress.supportPercent());
    }
}

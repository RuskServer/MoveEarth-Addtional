package com.ruskserver.moveearth_addtional.s2.recovery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryObjectivePolicyTest {
    @Test
    void cheapBlockSpamCannotReplaceAnEqualNumberOfDiamondWalls() {
        int target = RecoveryObjectivePolicy.wallHealth(java.util.stream.IntStream.of(320, 320), 2);
        int repaired = RecoveryObjectivePolicy.wallHealth(java.util.stream.IntStream.generate(() -> 32).limit(10000), 2);
        assertEquals(640, target);
        assertEquals(64, repaired);
        assertFalse(RecoveryObjectivePolicy.evaluate(true, target, repaired, true).wallsRestored());
    }

    @Test
    void onlyActualHealthCountsAndStrongestWallsAreOrderIndependent() {
        assertEquals(448, RecoveryObjectivePolicy.wallHealth(java.util.stream.IntStream.of(32, 128, 320), 2));
        assertEquals(448, RecoveryObjectivePolicy.wallHealth(java.util.stream.IntStream.of(320, 128, 32), 2));
        assertFalse(RecoveryObjectivePolicy.evaluate(true, 640,
                RecoveryObjectivePolicy.wallHealth(java.util.stream.IntStream.of(319, 320), 2), true).wallsRestored());
        assertTrue(RecoveryObjectivePolicy.evaluate(true, 640,
                RecoveryObjectivePolicy.wallHealth(java.util.stream.IntStream.of(320, 320), 2), true).wallsRestored());
    }

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

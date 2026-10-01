package com.ruskserver.moveearth_addtional.s2.tutorial;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TutorialStepLogTest {
    private static int indexOf(String id) {
        for (int index = 0; index < TutorialCatalog.STEPS.size(); index++) {
            if (TutorialCatalog.STEPS.get(index).id().equals(id)) return index;
        }
        throw new AssertionError(id);
    }

    private static boolean[] done(String... ids) {
        boolean[] done = new boolean[TutorialCatalog.STEPS.size()];
        for (String id : ids) done[indexOf(id)] = true;
        return done;
    }

    @Test
    void aPassedOverNationStepIsNotReported() {
        // Wilderness start: the goal list moves past the nation step, which stays undone.
        List<Integer> fresh = TutorialStepLog.newlyDone(done("open_hub", "region"), Set.of());
        assertEquals(List.of(indexOf("open_hub"), indexOf("region")), fresh);
    }

    @Test
    void theNationStepIsReportedWhenTheyJoinLater() {
        List<Integer> fresh = TutorialStepLog.newlyDone(done("open_hub", "region", "rest", "nation"),
                Set.of("open_hub", "region", "rest"));
        assertEquals(List.of(indexOf("nation")), fresh);
    }

    @Test
    void stepsAreReportedOnce() {
        assertTrue(TutorialStepLog.newlyDone(done("open_hub"), Set.of("open_hub")).isEmpty());
    }

    @Test
    void migrationKeepsOnlyStepsThatAreReallyDone() {
        // The old log had passed index 3 (open_hub, nation, region) without a nation.
        List<String> seeded = TutorialStepLog.migrated(indexOf("region") + 1, done("open_hub", "region"));
        assertEquals(List.of("open_hub", "region"), seeded);
        assertFalse(seeded.contains("nation"));
    }
}

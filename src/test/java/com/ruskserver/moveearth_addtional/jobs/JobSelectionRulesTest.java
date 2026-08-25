package com.ruskserver.moveearth_addtional.jobs;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobSelectionRulesTest {
    @Test
    void removesMissingActiveJobs() {
        Set<String> active = new LinkedHashSet<>(List.of("miner", "removed"));

        assertTrue(JobSelectionRules.removeMissing(active, Set.of("miner", "farmer")));
        assertEquals(Set.of("miner"), active);
    }

    @Test
    void preservesSelectionsWhenAllDefinitionsFailedToLoad() {
        Set<String> active = new LinkedHashSet<>(List.of("miner", "farmer"));

        assertFalse(JobSelectionRules.removeMissing(active, Set.of()));
        assertEquals(Set.of("miner", "farmer"), active);
    }

    @Test
    void restoresAProductByIdAfterTheVisibleListChanges() {
        assertEquals(1, JobSelectionRules.indexOfId(List.of("disabled", "selected"), "selected"));
        assertEquals(0, JobSelectionRules.indexOfId(List.of("other"), "selected"));
    }
}

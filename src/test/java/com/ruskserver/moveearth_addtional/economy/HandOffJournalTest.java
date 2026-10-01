package com.ruskserver.moveearth_addtional.economy;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandOffJournalTest {
    @Test
    void itemsMoveOnlyAfterTheStoreIsPersisted() {
        HandOffJournal journal = new HandOffJournal();
        List<String> log = new ArrayList<>();
        journal.begin();
        journal.record(() -> log.add("undo claim"));
        boolean moved = journal.commitThenGive(() -> { log.add("persist"); return true; },
                List.of("iron", "quartz"), item -> log.add("give " + item));
        assertTrue(moved);
        assertEquals(List.of("persist", "give iron", "give quartz"), log, "persist strictly before any give");
        assertFalse(journal.open());
    }

    @Test
    void aFailedPersistRollsBackNewestFirstAndGivesNothing() {
        HandOffJournal journal = new HandOffJournal();
        List<String> log = new ArrayList<>();
        journal.begin();
        journal.record(() -> log.add("undo first"));
        journal.record(() -> log.add("undo second"));
        boolean moved = journal.commitThenGive(() -> { log.add("persist"); return false; },
                List.of("iron"), item -> log.add("give " + item));
        assertFalse(moved);
        assertEquals(List.of("persist", "undo second", "undo first"), log);
        assertFalse(journal.open(), "the hand-off is over either way");
    }

    @Test
    void nothingTakenNeedsNoWrite() {
        HandOffJournal journal = new HandOffJournal();
        journal.begin();
        assertTrue(journal.commit(() -> { throw new AssertionError("no change, no write"); }));
    }

    @Test
    void anAbandonedHandOffIsUndone() {
        HandOffJournal journal = new HandOffJournal();
        List<String> log = new ArrayList<>();
        journal.begin();
        journal.record(() -> log.add("undo"));
        journal.abort();
        assertEquals(List.of("undo"), log);
        journal.abort();
        assertEquals(List.of("undo"), log, "aborting twice is harmless");
    }

    @Test
    void changesOutsideAHandOffAreNotRecorded() {
        HandOffJournal journal = new HandOffJournal();
        List<String> log = new ArrayList<>();
        journal.record(() -> log.add("ordinary purchase"));
        journal.begin();
        assertTrue(journal.commit(() -> false), "only changes made inside the hand-off count");
        assertTrue(log.isEmpty());
    }

    @Test
    void rollbackStepsDoNotRecordThemselves() {
        HandOffJournal journal = new HandOffJournal();
        List<String> log = new ArrayList<>();
        journal.begin();
        // Like the goods store, an undo step is itself a store change that calls record().
        journal.record(() -> { log.add("undo"); journal.record(() -> log.add("re-recorded")); });
        assertFalse(journal.commit(() -> false));
        journal.begin();
        assertTrue(journal.commit(() -> false));
        assertEquals(List.of("undo"), log);
    }

    @Test
    void handOffsDoNotNest() {
        HandOffJournal journal = new HandOffJournal();
        journal.begin();
        assertThrows(IllegalStateException.class, journal::begin);
        assertThrows(IllegalStateException.class, () -> new HandOffJournal().commit(() -> true));
    }
}

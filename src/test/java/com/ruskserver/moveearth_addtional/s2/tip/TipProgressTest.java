package com.ruskserver.moveearth_addtional.s2.tip;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TipProgressTest {
    @Test
    void tracksCountdownSeenStateAndBoundedHistory() {
        TipProgress progress = new TipProgress(true, 300);
        progress.advanceSecond();
        assertEquals(299, progress.remainingSeconds());

        progress.markShown("nation_hub", 1800, 2);
        progress.markShown("territory_core", 1800, 2);
        progress.markShown("upkeep", 1800, 2);
        assertEquals(1800, progress.remainingSeconds());
        assertTrue(progress.seen().containsAll(List.of("nation_hub", "territory_core", "upkeep")));
        assertEquals(List.of("territory_core", "upkeep"), progress.history());
        assertEquals("upkeep", progress.previous());
    }

    @Test
    void disablingPausesAndEnablingRestartsWithTheInitialDelay() {
        TipProgress progress = new TipProgress(true, 300);
        progress.setEnabled(false, 120);
        progress.advanceSecond();
        assertFalse(progress.enabled());
        assertEquals(300, progress.remainingSeconds());

        progress.setEnabled(true, 120);
        assertTrue(progress.enabled());
        assertEquals(120, progress.remainingSeconds());
        progress.advanceSecond();
        assertEquals(119, progress.remainingSeconds());
    }
}

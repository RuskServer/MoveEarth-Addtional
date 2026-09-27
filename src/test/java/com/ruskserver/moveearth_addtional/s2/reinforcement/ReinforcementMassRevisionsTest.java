package com.ruskserver.moveearth_addtional.s2.reinforcement;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReinforcementMassRevisionsTest {
    @Test void unrelatedChunksDoNotInvalidateBody() {
        var revisions = new ReinforcementMassRevisions();
        revisions.changed(-1, -1);
        assertEquals(0, revisions.inside(0, 0, 15, 15));
        assertEquals(1, revisions.inside(-16, -16, -1, -1));
        revisions.changed(32, 32);
        assertEquals(1, revisions.inside(-16, -16, -1, -1));
        assertEquals(2, revisions.revision());
    }

    @Test void deletingLastEntryRetainsNewEpoch() {
        var revisions = new ReinforcementMassRevisions();
        revisions.changed(1, 1); // insertion
        long inserted = revisions.inside(0, 0, 15, 15);
        revisions.changed(1, 1); // removal, including bulk removal/cleanup
        assertTrue(revisions.inside(0, 0, 15, 15) > inserted);
    }

    @Test void negativeAndPositiveChunkCoordinatesDoNotCollide() {
        var revisions = new ReinforcementMassRevisions();
        revisions.changed(-16, 16);
        revisions.changed(16, -16);
        assertEquals(1, revisions.inside(-16, 16, -1, 31));
        assertEquals(2, revisions.inside(16, -16, 31, -1));
    }
}

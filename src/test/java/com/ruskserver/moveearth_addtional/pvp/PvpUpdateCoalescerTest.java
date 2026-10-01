package com.ruskserver.moveearth_addtional.pvp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PvpUpdateCoalescerTest {
    @Test void cleanStateIsNeverDue() {
        PvpUpdateCoalescer coalescer = new PvpUpdateCoalescer(5);
        assertFalse(coalescer.due(0));
        assertFalse(coalescer.due(1000));
    }

    @Test void manyChangesInOneWindowProduceOneSend() {
        PvpUpdateCoalescer coalescer = new PvpUpdateCoalescer(5);
        int sends = 0;
        for (int tick = 0; tick < 20; tick++) {
            // a burst of votes every tick
            for (int vote = 0; vote < 10; vote++) coalescer.markDirty();
            if (coalescer.due(tick)) {
                coalescer.sent(tick);
                sends++;
            }
        }
        assertEquals(4, sends, "200 votes over one second -> 4 broadcasts");
    }

    @Test void pendingChangeIsSentOnceWindowOpens() {
        PvpUpdateCoalescer coalescer = new PvpUpdateCoalescer(5);
        coalescer.markDirty();
        assertTrue(coalescer.due(10));
        coalescer.sent(10);
        coalescer.markDirty();
        assertFalse(coalescer.due(14));
        assertTrue(coalescer.isDirty());
        assertTrue(coalescer.due(15));
    }

    @Test void unrelatedSendAlsoSatisfiesPendingChange() {
        PvpUpdateCoalescer coalescer = new PvpUpdateCoalescer(5);
        coalescer.markDirty();
        coalescer.sent(20); // e.g. the per-second countdown broadcast
        assertFalse(coalescer.due(100));
    }

    @Test void resetDropsPendingChange() {
        PvpUpdateCoalescer coalescer = new PvpUpdateCoalescer(5);
        coalescer.markDirty();
        coalescer.reset();
        assertFalse(coalescer.due(0));
    }
}

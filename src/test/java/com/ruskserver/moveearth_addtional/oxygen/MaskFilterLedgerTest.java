package com.ruskserver.moveearth_addtional.oxygen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaskFilterLedgerTest {
    @Test
    void writesOncePerIntervalWhileFilterIsConsumedEveryTick() {
        MaskFilterLedger ledger = new MaskFilterLedger(100);
        ledger.track(3600);
        int writes = 0;
        for (int tick = 0; tick < 1000; tick++) {
            ledger.consume(1);
            ledger.tick();
            if (ledger.flushDue()) {
                ledger.markFlushed();
                writes++;
            }
        }
        assertEquals(10, writes);
        assertEquals(2600, ledger.current());
        assertEquals(2600, ledger.written());
    }

    @Test
    void unflushedConsumptionIsReportedSoItCanBeSettled() {
        MaskFilterLedger ledger = new MaskFilterLedger(100);
        ledger.track(500);
        for (int tick = 0; tick < 40; tick++) {
            ledger.consume(2);
            ledger.tick();
        }
        assertFalse(ledger.flushDue());
        assertEquals(80, ledger.unflushed());
        assertEquals(420, ledger.current());
        assertEquals(500, ledger.written());
    }

    @Test
    void runningOutIsWrittenImmediately() {
        MaskFilterLedger ledger = new MaskFilterLedger(100);
        ledger.track(3);
        ledger.consume(5);
        ledger.tick();
        assertEquals(0, ledger.current());
        assertTrue(ledger.flushDue());
        assertEquals(0, ledger.markFlushed());
        assertFalse(ledger.flushDue());
    }

    @Test
    void nothingToWriteWhenIdle() {
        MaskFilterLedger ledger = new MaskFilterLedger(10);
        ledger.track(100);
        for (int tick = 0; tick < 50; tick++) ledger.tick();
        assertFalse(ledger.flushDue());
        assertEquals(0, ledger.unflushed());
    }

    @Test
    void retrackingAdoptsAnOutsideWrite() {
        MaskFilterLedger ledger = new MaskFilterLedger(100);
        ledger.track(100);
        ledger.consume(30);
        ledger.track(3600); // a filter replacement
        assertEquals(3600, ledger.current());
        assertEquals(0, ledger.unflushed());
    }

    @Test
    void untrackedLedgerOwesNothing() {
        MaskFilterLedger ledger = new MaskFilterLedger(100);
        ledger.consume(10);
        assertEquals(0, ledger.unflushed());
        ledger.track(50);
        ledger.consume(10);
        ledger.untrack();
        assertEquals(0, ledger.unflushed());
        assertFalse(ledger.flushDue());
    }
}

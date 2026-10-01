package com.ruskserver.moveearth_addtional.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyGoodsMigrationTest {
    @Test
    void importsOnlyWhenNoGoodsFileExistsYet() {
        assertEquals(LegacyGoodsMigration.IMPORT, LegacyGoodsMigration.decide(true, false));
    }

    @Test
    void anExistingGoodsFileWinsOverTheLedgerCopy() {
        // The goods file is written at the import and at every hand-off, so it is never older
        // than a leftover copy in a ledger that had not been saved again before a crash.
        assertEquals(LegacyGoodsMigration.DISCARD, LegacyGoodsMigration.decide(true, true));
    }

    @Test
    void newWorldsHaveNothingToMove() {
        assertEquals(LegacyGoodsMigration.NONE, LegacyGoodsMigration.decide(false, false));
        assertEquals(LegacyGoodsMigration.NONE, LegacyGoodsMigration.decide(false, true));
    }
}

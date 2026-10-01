package com.ruskserver.moveearth_addtional.oxygen;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PendingFilterDebtsTest {
    private static final UUID A = new UUID(0L, 1L);
    private static final UUID B = new UUID(0L, 2L);

    @Test
    void debtIsChargedOnceAndAccumulates() {
        PendingFilterDebts debts = new PendingFilterDebts(16, 60_000L);
        debts.add(A, 40, 0L);
        debts.add(A, 25, 10L);
        assertEquals(65, debts.take(A, 20L));
        assertEquals(0, debts.take(A, 21L));
    }

    @Test
    void nonPositiveDebtsAndMissingIdsAreIgnored() {
        PendingFilterDebts debts = new PendingFilterDebts(16, 60_000L);
        debts.add(A, 0, 0L);
        debts.add(null, 10, 0L);
        assertEquals(0, debts.size());
        assertEquals(0, debts.take(null, 0L));
    }

    @Test
    void oldDebtsExpire() {
        PendingFilterDebts debts = new PendingFilterDebts(16, 1_000L);
        debts.add(A, 10, 0L);
        debts.add(B, 10, 900L);
        assertEquals(0, debts.take(A, 1_000L));
        assertEquals(10, debts.take(B, 1_000L));
    }

    @Test
    void capacityDropsTheOldestDebt() {
        PendingFilterDebts debts = new PendingFilterDebts(1, 60_000L);
        debts.add(A, 10, 0L);
        debts.add(B, 20, 1L);
        assertEquals(1, debts.size());
        assertEquals(0, debts.take(A, 2L));
        assertEquals(20, debts.take(B, 2L));
    }
}

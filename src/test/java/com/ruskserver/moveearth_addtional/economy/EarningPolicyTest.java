package com.ruskserver.moveearth_addtional.economy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EarningPolicyTest {
    @Test void onlyADeliberateTurnCountsAsInput() {
        assertFalse(EarningPolicy.turned(90F, 10F, 90.2F, 10.1F));
        assertTrue(EarningPolicy.turned(90F, 10F, 91F, 10F));
        assertTrue(EarningPolicy.turned(90F, 10F, 90F, 9F));
    }

    @Test void yawWrapIsASmallTurnNotAFullCircle() {
        assertFalse(EarningPolicy.turned(179.9F, 0F, -179.9F, 0F));
        assertTrue(EarningPolicy.turned(179.9F, 0F, -179F, 0F));
        assertFalse(EarningPolicy.turned(0F, 0F, 720F, 0F));
    }

    @Test void neverTurnedOrQuietForTheWindowIsIdle() {
        long window = 300_000L;
        assertTrue(EarningPolicy.idle(0L, 1_000_000L, window));
        assertFalse(EarningPolicy.idle(1_000_000L, 1_000_000L + window - 1, window));
        assertTrue(EarningPolicy.idle(1_000_000L, 1_000_000L + window, window));
    }

    @Test void nationMembershipIsCheckedBeforeIdleness() {
        assertEquals(EarningPolicy.Refusal.NO_NATION, EarningPolicy.refusal(false, false));
        assertEquals(EarningPolicy.Refusal.NO_NATION, EarningPolicy.refusal(false, true));
        assertEquals(EarningPolicy.Refusal.IDLE, EarningPolicy.refusal(true, true));
        assertEquals(EarningPolicy.Refusal.NONE, EarningPolicy.refusal(true, false));
    }

    @Test void newAccountsShareOneDailyTransferAllowance() {
        int eightHours = 8 * 72_000;
        assertEquals(20L, EarningPolicy.remainingTransfer(0, eightHours, 0L, 20L));
        assertEquals(5L, EarningPolicy.remainingTransfer(eightHours - 1, eightHours, 15L, 20L));
        assertEquals(0L, EarningPolicy.remainingTransfer(0, eightHours, 30L, 20L));
        assertEquals(Long.MAX_VALUE, EarningPolicy.remainingTransfer(eightHours, eightHours, 999L, 20L));
        assertEquals(Long.MAX_VALUE, EarningPolicy.remainingTransfer(0, 0, 999L, 20L));
    }

    @Test void dayMatchesTheJobsIncomeWindow() {
        assertEquals(EarningPolicy.day(86_400_000L - 1) + 1, EarningPolicy.day(86_400_000L));
    }
}

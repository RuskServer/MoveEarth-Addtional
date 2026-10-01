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

    @Test void activeTimeGrowsOnlyWhileNotIdle() {
        assertEquals(20L, EarningPolicy.activeTicksAfter(0L, false));
        assertEquals(0L, EarningPolicy.activeTicksAfter(0L, true));
        assertEquals(1_020L, EarningPolicy.activeTicksAfter(1_000L, false));
        assertEquals(1_000L, EarningPolicy.activeTicksAfter(1_000L, true));
        assertEquals(Long.MAX_VALUE, EarningPolicy.activeTicksAfter(Long.MAX_VALUE - 5L, false));
        assertEquals(0L, EarningPolicy.activeTicksAfter(-40L, true));
        // An hour online at the keyboard is an hour of active time.
        long ticks = 0L;
        for (int second = 0; second < 3_600; second++) ticks = EarningPolicy.activeTicksAfter(ticks, false);
        assertEquals(72_000L, ticks);
    }

    @Test void newAccountPeriodEndsAfterTheActiveThreshold() {
        long fourHours = 4L * 72_000L;
        assertEquals(fourHours, EarningPolicy.newAccountTicksLeft(0L, fourHours));
        assertEquals(1L, EarningPolicy.newAccountTicksLeft(fourHours - 1L, fourHours));
        assertEquals(0L, EarningPolicy.newAccountTicksLeft(fourHours, fourHours));
        assertEquals(0L, EarningPolicy.newAccountTicksLeft(0L, 0L));
        assertEquals(20L, EarningPolicy.remainingTransfer(fourHours - 1L, fourHours, 0L, 20L));
        assertEquals(Long.MAX_VALUE, EarningPolicy.remainingTransfer(fourHours, fourHours, 0L, 20L));
    }

    @Test void newAccountsShareOneDailyTransferAllowance() {
        int eightHours = 8 * 72_000;
        assertEquals(20L, EarningPolicy.remainingTransfer(0, eightHours, 0L, 20L));
        assertEquals(5L, EarningPolicy.remainingTransfer(eightHours - 1, eightHours, 15L, 20L));
        assertEquals(0L, EarningPolicy.remainingTransfer(0, eightHours, 30L, 20L));
        assertEquals(Long.MAX_VALUE, EarningPolicy.remainingTransfer(eightHours, eightHours, 999L, 20L));
        assertEquals(Long.MAX_VALUE, EarningPolicy.remainingTransfer(0, 0, 999L, 20L));
    }

    @Test void deliberateGuiWorkCountsAsInputButFarmingActionsDoNot() {
        assertTrue(EarningPolicy.countsAsInput(EarningPolicy.Activity.TURN_VIEW));
        assertTrue(EarningPolicy.countsAsInput(EarningPolicy.Activity.CRAFT));
        assertTrue(EarningPolicy.countsAsInput(EarningPolicy.Activity.SMELT));
        assertTrue(EarningPolicy.countsAsInput(EarningPolicy.Activity.GUN_SMITH_CRAFT));
        assertFalse(EarningPolicy.countsAsInput(EarningPolicy.Activity.BREAK_BLOCK));
        assertFalse(EarningPolicy.countsAsInput(EarningPolicy.Activity.ATTACK));
    }

    @Test void aLongCraftingSessionWithoutTurningStaysActive() {
        long window = 300_000L, start = 1_000_000L;
        long lastInput = EarningPolicy.lastInputAfter(0L, EarningPolicy.Activity.TURN_VIEW, start);
        // Ten minutes at a crafting table, a craft every minute, the view never turned.
        for (long minute = 1; minute <= 10; minute++)
            lastInput = EarningPolicy.lastInputAfter(lastInput, EarningPolicy.Activity.CRAFT, start + minute * 60_000L);
        assertFalse(EarningPolicy.idle(lastInput, start + 10 * 60_000L + 1, window));
    }

    @Test void anAutoclickerFarmStillGoesIdle() {
        long window = 300_000L, start = 1_000_000L;
        long lastInput = EarningPolicy.lastInputAfter(0L, EarningPolicy.Activity.TURN_VIEW, start);
        for (long second = 1; second <= 600; second++) {
            lastInput = EarningPolicy.lastInputAfter(lastInput, EarningPolicy.Activity.ATTACK, start + second * 1000L);
            lastInput = EarningPolicy.lastInputAfter(lastInput, EarningPolicy.Activity.BREAK_BLOCK, start + second * 1000L);
        }
        assertEquals(start, lastInput);
        assertTrue(EarningPolicy.idle(lastInput, start + window, window));
        assertTrue(EarningPolicy.idle(EarningPolicy.lastInputAfter(0L, EarningPolicy.Activity.ATTACK, start),
                start, window), "attacking right after login is not input either");
    }

    @Test void marketSpendingSharesTheNewAccountAllowance() {
        int eightHours = 8 * 72_000;
        long remaining = EarningPolicy.remainingTransfer(0, eightHours, 15L, 20L);
        assertTrue(EarningPolicy.allowsTransfer(5L, remaining));
        assertFalse(EarningPolicy.allowsTransfer(6L, remaining), "a 1,000,000 TC alt purchase is refused");
        assertFalse(EarningPolicy.allowsTransfer(1_000_000L, EarningPolicy.remainingTransfer(0, eightHours, 0L, 20L)));
        assertTrue(EarningPolicy.allowsTransfer(1_000_000L,
                EarningPolicy.remainingTransfer(eightHours, eightHours, 0L, 20L)), "established accounts are unlimited");
    }

    @Test void dayMatchesTheJobsIncomeWindow() {
        assertEquals(EarningPolicy.day(86_400_000L - 1) + 1, EarningPolicy.day(86_400_000L));
    }
}

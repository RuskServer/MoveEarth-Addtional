package com.ruskserver.moveearth_addtional.s2.siege;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class SiegeReattackPolicyTest {
    private static long at(String iso) { return Instant.parse(iso).getEpochSecond(); }

    @Test void failuresAtOpeningAndDuringSessionWaitForTomorrowNineteenJst() {
        long tomorrow = at("2026-10-28T10:00:00Z");
        assertEquals(tomorrow, SiegeReattackPolicy.nextOpening(at("2026-10-27T10:00:00Z")));
        assertEquals(tomorrow, SiegeReattackPolicy.nextOpening(at("2026-10-27T11:00:00Z")));
        assertEquals(tomorrow, SiegeReattackPolicy.nextOpening(at("2026-10-27T13:59:59Z")));
    }

    @Test void closedHoursResolveToTheUpcomingOpeningWithoutAnotherDayAdded() {
        long tomorrow = at("2026-10-28T10:00:00Z");
        assertEquals(tomorrow, SiegeReattackPolicy.nextOpening(at("2026-10-27T14:00:00Z")));
        assertEquals(tomorrow, SiegeReattackPolicy.nextOpening(at("2026-10-27T15:00:00Z")));
        assertEquals(tomorrow, SiegeReattackPolicy.nextOpening(at("2026-10-28T09:59:59Z")));
    }

    @Test void expiresExactlyAtOpeningEvenIfNoTicksRanOvernight() {
        long opening = at("2026-10-28T10:00:00Z");
        assertTrue(SiegeReattackPolicy.blocked(opening, opening - 1));
        assertFalse(SiegeReattackPolicy.blocked(opening, opening));
        assertFalse(SiegeReattackPolicy.blocked(opening, opening + 3600));
        assertEquals(0, SiegeReattackPolicy.remainingOpenTicks(opening, opening));
    }

    @Test void oldGuiCountsOnlyTheRemainingPartOfThisOpening() {
        long opening = at("2026-10-28T10:00:00Z");
        assertEquals(3 * 3600 * 20L, SiegeReattackPolicy.remainingOpenTicks(opening, at("2026-10-27T11:00:00Z")));
        assertEquals(60 * 20L, SiegeReattackPolicy.remainingOpenTicks(opening, at("2026-10-27T13:59:00Z")));
        assertTrue(SiegeReattackPolicy.remainingOpenTicks(opening, at("2026-10-27T15:00:00Z")) > 0);
    }

    @Test void monthAndYearBoundariesUseTheJstCalendar() {
        assertEquals(at("2027-01-01T10:00:00Z"), SiegeReattackPolicy.nextOpening(at("2026-12-31T12:00:00Z")));
        assertEquals(at("2026-11-01T10:00:00Z"), SiegeReattackPolicy.nextOpening(at("2026-10-31T12:00:00Z")));
    }
}

package com.ruskserver.moveearth_addtional.s2.nation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllianceTerminationPolicyTest {
    @Test void noticeLastsTwoHoursOfOpenTime() {
        assertEquals(144_000L, AllianceTerminationPolicy.NOTICE_OPEN_TICKS);
        long endsAt = AllianceTerminationPolicy.endsAt(5_000L);
        assertEquals(149_000L, endsAt);
        assertEquals(144_000L, AllianceTerminationPolicy.remaining(endsAt, 5_000L));
        assertEquals(1L, AllianceTerminationPolicy.remaining(endsAt, endsAt - 1L));
    }

    @Test void allianceStaysUntilTheNoticeRunsOut() {
        long endsAt = AllianceTerminationPolicy.endsAt(0L);
        assertFalse(AllianceTerminationPolicy.expired(endsAt, endsAt - 1L));
        assertTrue(AllianceTerminationPolicy.expired(endsAt, endsAt));
        assertEquals(0L, AllianceTerminationPolicy.remaining(endsAt, endsAt + 20L));
    }

    @Test void noPendingNoticeNeverExpires() {
        assertFalse(AllianceTerminationPolicy.pending(0L));
        assertFalse(AllianceTerminationPolicy.expired(0L, Long.MAX_VALUE));
        assertEquals(0L, AllianceTerminationPolicy.remaining(0L, 100L));
    }

    @Test void clockSaturatesInsteadOfOverflowing() {
        assertEquals(Long.MAX_VALUE, AllianceTerminationPolicy.endsAt(Long.MAX_VALUE - 10L));
    }

    @Test void declaringNeedsAnAllianceWithoutPendingNotice() {
        assertEquals(AllianceTerminationPolicy.Decision.NOT_ALLIED, AllianceTerminationPolicy.declare(false, false));
        assertEquals(AllianceTerminationPolicy.Decision.ALREADY_PENDING, AllianceTerminationPolicy.declare(true, true));
        assertEquals(AllianceTerminationPolicy.Decision.ALLOWED, AllianceTerminationPolicy.declare(true, false));
    }

    @Test void onlyTheDeclaringNationWithdrawsNotice() {
        assertEquals(AllianceTerminationPolicy.Decision.NOT_ALLIED, AllianceTerminationPolicy.cancel(false, true, true));
        assertEquals(AllianceTerminationPolicy.Decision.NOT_PENDING, AllianceTerminationPolicy.cancel(true, false, true));
        assertEquals(AllianceTerminationPolicy.Decision.NOT_DECLARER, AllianceTerminationPolicy.cancel(true, true, false));
        assertEquals(AllianceTerminationPolicy.Decision.ALLOWED, AllianceTerminationPolicy.cancel(true, true, true));
    }
}

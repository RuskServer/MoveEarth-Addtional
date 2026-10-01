package com.ruskserver.moveearth_addtional.s2.siege;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineDefenseDayTest {
    private final UUID core = UUID.randomUUID();

    @Test
    void aFallenCoreNeverGetsOfflineDefense() {
        assertTrue(OfflineDefenseDayPolicy.suppressed(true, false, null));
        assertTrue(OfflineDefenseDayPolicy.suppressed(true, true, true));
    }

    @Test
    void aRollingSiegeFollowsTheAnswerTakenWhenTheFightStarted() {
        assertTrue(OfflineDefenseDayPolicy.suppressed(false, true, false), "defenders were on when the fight started");
        assertFalse(OfflineDefenseDayPolicy.suppressed(false, true, true), "nobody was on when the fight started");
        assertFalse(OfflineDefenseDayPolicy.suppressed(false, true, null), "no fight started yet today");
        assertFalse(OfflineDefenseDayPolicy.suppressed(false, false, false), "no rolling siege");
    }

    @Test
    void onlyAFightStartingOnAQuietCoreDecides() {
        assertTrue(OfflineDefenseDayPolicy.decides(true, false, false, false), "first rolling siege today");
        assertTrue(OfflineDefenseDayPolicy.decides(true, false, false, true),
                "a separate fight later in the evening asks again");
        assertFalse(OfflineDefenseDayPolicy.decides(true, false, true, true),
                "joining a fight already underway keeps its answer");
        assertFalse(OfflineDefenseDayPolicy.decides(false, true, false, true),
                "a defender logging off mid-fight does not change it");
        assertFalse(OfflineDefenseDayPolicy.decides(false, false, false, false),
                "a non-effective poke never decides");
    }

    @Test
    void aFightCarriedIntoANewDayAsksAtItsFirstEffectiveHit() {
        assertTrue(OfflineDefenseDayPolicy.decides(false, true, false, false));
        assertTrue(OfflineDefenseDayPolicy.decides(true, false, true, false));
    }

    @Test
    void aLaterFightReplacesTheEarlierAnswer() {
        OfflineDefenseDays days = new OfflineDefenseDays();
        assertTrue(days.record(core, 100L, false));
        assertTrue(days.record(core, 100L, true), "defenders offline when the second fight started");
        assertEquals(Boolean.TRUE, days.allowedOn(core, 100L));
        assertFalse(days.record(core, 100L, true), "same answer: nothing to save");
    }

    @Test
    void aNewDayAsksAgain() {
        OfflineDefenseDays days = new OfflineDefenseDays();
        days.record(core, 100L, false);
        assertNull(days.allowedOn(core, 101L), "yesterday's answer does not carry over");
        days.record(core, 101L, true);
        assertEquals(Boolean.TRUE, days.allowedOn(core, 101L));
    }
}

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
    void aRollingSiegeFollowsTodaysFirstAttack() {
        assertTrue(OfflineDefenseDayPolicy.suppressed(false, true, false), "defenders were on at today's first attack");
        assertFalse(OfflineDefenseDayPolicy.suppressed(false, true, true), "nobody was on at today's first attack");
        assertFalse(OfflineDefenseDayPolicy.suppressed(false, true, null), "not attacked yet today");
        assertFalse(OfflineDefenseDayPolicy.suppressed(false, false, false), "no rolling siege");
    }

    @Test
    void theFirstAttackOfTheDayDecidesAndLaterOnesDoNot() {
        OfflineDefenseDays days = new OfflineDefenseDays();
        days.observe(core, 100L, false);
        days.observe(core, 100L, true); // defenders logged off mid-fight: no change today
        assertEquals(Boolean.FALSE, days.allowedOn(core, 100L));
    }

    @Test
    void aNewDayAsksAgain() {
        OfflineDefenseDays days = new OfflineDefenseDays();
        days.observe(core, 100L, false);
        assertNull(days.allowedOn(core, 101L), "yesterday's answer does not carry over");
        days.observe(core, 101L, true);
        assertEquals(Boolean.TRUE, days.allowedOn(core, 101L));
    }
}

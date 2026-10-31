package com.ruskserver.moveearth_addtional.s2.presence;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiddenAdvancementPolicyTest {
    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final long MINUTE = 60_000L;

    private static ZonedDateTime jst(int hour, int minute, int second) {
        return ZonedDateTime.of(2026, 10, 10, hour, minute, second, 0, JST);
    }

    @Test
    void openingRushIsOnlyTheFirstMinute() {
        assertTrue(HiddenAdvancementPolicy.openingRush(jst(19, 0, 0)));
        assertTrue(HiddenAdvancementPolicy.openingRush(jst(19, 0, 59)));
        assertFalse(HiddenAdvancementPolicy.openingRush(jst(19, 1, 0)));
        assertFalse(HiddenAdvancementPolicy.openingRush(jst(18, 59, 59)));
    }

    @Test
    void openingRushReadsJapanTimeWhateverTheServerZone() {
        assertTrue(HiddenAdvancementPolicy.openingRush(jst(19, 0, 10).withZoneSameInstant(ZoneId.of("UTC"))));
    }

    @Test
    void lastOrdersIsTheFinalFiveMinutes() {
        assertTrue(HiddenAdvancementPolicy.lastOrders(jst(22, 55, 0)));
        assertTrue(HiddenAdvancementPolicy.lastOrders(jst(22, 59, 59)));
        assertFalse(HiddenAdvancementPolicy.lastOrders(jst(22, 54, 59)));
        assertFalse(HiddenAdvancementPolicy.lastOrders(jst(23, 0, 0)));
    }

    @Test
    void shortReignIsUnderAnHour() {
        long founded = 1_800_000_000_000L;
        assertTrue(HiddenAdvancementPolicy.shortReign(founded, founded + 59 * MINUTE));
        assertFalse(HiddenAdvancementPolicy.shortReign(founded, founded + 60 * MINUTE));
        assertFalse(HiddenAdvancementPolicy.shortReign(0L, 30 * MINUTE));
        assertFalse(HiddenAdvancementPolicy.shortReign(founded, founded - MINUTE));
    }

    @Test
    void capturesCountPerJapanDay() {
        long evening = jst(20, 0, 0).toInstant().toEpochMilli();
        long lateNight = jst(23, 59, 0).toInstant().toEpochMilli();
        long nextDay = jst(23, 59, 0).plusMinutes(2).toInstant().toEpochMilli();
        long today = HiddenAdvancementPolicy.day(evening);
        assertEquals(today, HiddenAdvancementPolicy.day(lateNight));
        assertEquals(today + 1, HiddenAdvancementPolicy.day(nextDay));

        assertEquals(1, HiddenAdvancementPolicy.countToday(0L, 0, today));
        assertEquals(2, HiddenAdvancementPolicy.countToday(today, 1, today));
        assertEquals(1, HiddenAdvancementPolicy.countToday(today - 1, 5, today));
    }

    @Test
    void wrongTurnOnlyCountsSomeoneElsesLand() {
        UUID own = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertTrue(HiddenAdvancementPolicy.foreignLand(other, own));
        assertTrue(HiddenAdvancementPolicy.foreignLand(other, null));
        assertFalse(HiddenAdvancementPolicy.foreignLand(own, own));
        assertFalse(HiddenAdvancementPolicy.foreignLand(null, own));
    }
}

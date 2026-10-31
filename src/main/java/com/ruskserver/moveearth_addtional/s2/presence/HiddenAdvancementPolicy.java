package com.ruskserver.moveearth_addtional.s2.presence;

import com.ruskserver.moveearth_addtional.ServerSchedule;

import java.time.ZonedDateTime;

/**
 * When the light-hearted hidden advancements of {@link HiddenAdvancementService} are earned. Pure, so it is
 * tested without a server.
 */
public final class HiddenAdvancementPolicy {
    /** 「独り言」: local chat lines nobody was in range to hear. */
    public static final int MONOLOGUE_LINES = 10;
    /** 「まだ迷ってる？」: times the in-game wiki was opened. */
    public static final int WIKI_OPENS = 50;
    /** 「方向音痴」: deaths in another nation's land, not at a player's hand. */
    public static final int WRONG_TURN_DEATHS = 5;
    /** 「お勤めご苦労様です」: times imprisoned in one Japan day. */
    public static final int INMATE_CAPTURES_PER_DAY = 2;
    /** 「三日天下」: a nation disbanded within this long of its founding. */
    public static final long SHORT_REIGN_MILLIS = 60L * 60L * 1000L;
    /** 「光ってない？」: a dose of 1 mSv, twenty times where Mekanism's sickness starts. */
    public static final double GLOWING_DOSE = 1.0E-3D;
    /** 「風呂上がり」: the wash only counts once the dose was enough to make a player sick. */
    public static final double BATH_FROM_DOSE = 5.0E-5D;
    /** 「閉店間際の客」: minutes before closing. */
    static final int LAST_ORDERS_MINUTES = 5;

    private HiddenAdvancementPolicy() { }

    /** 「開店ダッシュ」: in the very first minute the server is open. */
    public static boolean openingRush(ZonedDateTime time) {
        ZonedDateTime local = time.withZoneSameInstant(ServerSchedule.ZONE);
        return local.getHour() == ServerSchedule.OPEN_HOUR && local.getMinute() == 0;
    }

    /** 「閉店間際の客」: in the last few minutes before the server closes. */
    public static boolean lastOrders(ZonedDateTime time) {
        ZonedDateTime local = time.withZoneSameInstant(ServerSchedule.ZONE);
        return local.getHour() == ServerSchedule.CLOSE_HOUR - 1 && local.getMinute() >= 60 - LAST_ORDERS_MINUTES;
    }

    /** 「三日天下」: a nation founded at {@code foundedAt} and disbanded at {@code now}. */
    public static boolean shortReign(long foundedAt, long now) {
        return foundedAt > 0L && now >= foundedAt && now - foundedAt < SHORT_REIGN_MILLIS;
    }

    /** The Japan calendar day of {@code millis}, for counting things per day. */
    public static long day(long millis) {
        return java.time.Instant.ofEpochMilli(millis).atZone(ServerSchedule.ZONE).toLocalDate().toEpochDay();
    }

    /** The count for {@code today} after one more, given the day and count stored before it. */
    public static int countToday(long storedDay, int storedCount, long today) {
        return storedDay == today ? Math.max(0, storedCount) + 1 : 1;
    }

    /** 「方向音痴」: whether a death in land held by {@code landNation} counts against a player of {@code ownNation}. */
    public static boolean foreignLand(java.util.UUID landNation, java.util.UUID ownNation) {
        return landNation != null && !landNation.equals(ownNation);
    }
}

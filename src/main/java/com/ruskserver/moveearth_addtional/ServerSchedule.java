package com.ruskserver.moveearth_addtional;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class ServerSchedule {
    public static final int OPEN_HOUR = 19;
    public static final int CLOSE_HOUR = 23;
    public static final int CLOSING_WARNING_HOUR = 22;
    public static final int SIEGE_START_CUTOFF_HOUR = CLOSE_HOUR - 1;
    public static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

    private ServerSchedule() {
    }

    public static boolean isOpenHour(int hour) {
        return hour >= OPEN_HOUR && hour < CLOSE_HOUR;
    }

    /** New Sieges stop one hour before closing; ongoing conflicts use their existing timers. */
    public static boolean isSiegeStartHour(int hour) {
        return isOpenHour(hour) && hour < SIEGE_START_CUTOFF_HOUR;
    }

    public static boolean canStartSiegeNow() {
        return canStartSiegeNow(Clock.system(ZONE));
    }

    static boolean canStartSiegeNow(Clock clock) {
        return isSiegeStartHour(ZonedDateTime.now(clock).withZoneSameInstant(ZONE).getHour());
    }

    public static boolean isOpenNow() {
        return isOpenNow(Clock.system(ZONE));
    }

    static boolean isOpenNow(Clock clock) {
        return isOpenHour(ZonedDateTime.now(clock).withZoneSameInstant(ZONE).getHour());
    }
}

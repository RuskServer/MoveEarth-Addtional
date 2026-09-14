package com.ruskserver.moveearth_addtional;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class ServerSchedule {
    public static final int OPEN_HOUR = 18;
    public static final int CLOSE_HOUR = 0;
    public static final int CLOSING_WARNING_HOUR = 23;
    public static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

    private ServerSchedule() {
    }

    public static boolean isOpenHour(int hour) {
        return hour >= OPEN_HOUR && hour <= 23;
    }

    public static boolean isOpenNow() {
        return isOpenNow(Clock.system(ZONE));
    }

    static boolean isOpenNow(Clock clock) {
        return isOpenHour(ZonedDateTime.now(clock).withZoneSameInstant(ZONE).getHour());
    }
}

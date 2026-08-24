package com.ruskserver.moveearth_addtional.tpa;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Identifies the server opening day. A new cycle starts at 19:00 JST. */
public final class OpenDayCycle {
    public static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final int RESET_HOUR = 19;

    private OpenDayCycle() {
    }

    public static String currentId() {
        return currentId(Clock.system(JST));
    }

    static String currentId(Clock clock) {
        LocalDate date = ZonedDateTime.now(clock).withZoneSameInstant(JST)
                .minusHours(RESET_HOUR)
                .toLocalDate();
        return date.toString();
    }

    public static ZonedDateTime nextReset() {
        ZonedDateTime now = ZonedDateTime.now(JST);
        ZonedDateTime reset = now.toLocalDate().atTime(RESET_HOUR, 0).atZone(JST);
        return now.isBefore(reset) ? reset : reset.plusDays(1);
    }
}

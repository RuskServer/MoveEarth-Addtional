package com.ruskserver.moveearth_addtional.event;

public final class EventScheduleRules {
    public static final long INTERVAL_TICKS = 3L * 60L * 60L * 20L;

    private EventScheduleRules() { }

    public static long nextStart(long startedAt) {
        return startedAt > Long.MAX_VALUE - INTERVAL_TICKS
                ? Long.MAX_VALUE : startedAt + INTERVAL_TICKS;
    }

    public static boolean due(long now, long nextStart, boolean settled, boolean serverOpen) {
        return serverOpen && settled && now >= nextStart;
    }
}

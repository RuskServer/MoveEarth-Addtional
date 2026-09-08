package com.ruskserver.moveearth_addtional;

public final class ServerSchedule {
    public static final int OPEN_HOUR = 18;
    public static final int CLOSE_HOUR = 0;
    public static final int CLOSING_WARNING_HOUR = 23;

    private ServerSchedule() {
    }

    static boolean isOpenHour(int hour) {
        return hour >= OPEN_HOUR && hour <= 23;
    }
}

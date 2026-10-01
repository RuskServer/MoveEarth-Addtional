package com.ruskserver.moveearth_addtional.s2.time;

/**
 * Decides when to pause cycles without changing the operators' gamerules.
 */
public final class ClosedHoursFreeze {
    private ClosedHoursFreeze() { }

    public static boolean shouldFreeze(boolean enabled, boolean dedicated, boolean open) {
        return enabled && dedicated && !open;
    }

    public static boolean allowCycle(boolean operatorRule, boolean frozen) {
        return operatorRule && !frozen;
    }

    public static boolean restorationSaved(boolean daylight, boolean weather,
                                            String savedDaylight, String savedWeather) {
        return Boolean.toString(daylight).equals(savedDaylight)
                && Boolean.toString(weather).equals(savedWeather);
    }
}

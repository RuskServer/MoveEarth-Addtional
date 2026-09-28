package com.ruskserver.moveearth_addtional.s2.siege;

/**
 * Whether a rolling siege withholds offline defense. The choice is taken at the
 * first attack of each server-open day: defenders who were online then cannot
 * log off mid-fight to gain protection, but a siege carried past closing time
 * no longer keeps yesterday's answer when nobody is on to defend today.
 */
public final class OfflineDefenseDayPolicy {
    private OfflineDefenseDayPolicy() { }

    /**
     * @param allowedToday whether offline defense applied at today's first attack on this core,
     *                     or null when it has not been attacked yet today
     */
    public static boolean suppressed(boolean fallen, boolean rolling, Boolean allowedToday) {
        return fallen || rolling && allowedToday != null && !allowedToday;
    }
}

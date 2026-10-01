package com.ruskserver.moveearth_addtional.s2.siege;

/**
 * Whether a rolling siege withholds offline defense. The choice is taken when real fighting starts
 * on a core: when a siege turns rolling on a core that had no other rolling siege. Defenders who
 * were online then cannot log off mid-fight to gain protection, a non-effective poke earlier in the
 * evening no longer decides the whole night, and a separate siege started later while nobody is on
 * gets the protection. A siege carried past closing time no longer keeps yesterday's answer: its
 * first effective hit of the new open day asks again.
 */
public final class OfflineDefenseDayPolicy {
    private OfflineDefenseDayPolicy() { }

    /**
     * @param allowedToday whether offline defense applied when today's current fight on this core started,
     *                     or null when no fight has started on it today
     */
    public static boolean suppressed(boolean fallen, boolean rolling, Boolean allowedToday) {
        return fallen || rolling && allowedToday != null && !allowedToday;
    }

    /**
     * Whether this attempt takes (or retakes) today's answer for the core.
     *
     * @param rollingStarted     the attempt turned a siege rolling (first effective damage)
     * @param rollingExtended    the attempt extended a siege that was already rolling
     * @param otherRollingOnCore another siege was already rolling on the core before this attempt
     * @param decidedToday       the core already has an answer for today
     */
    public static boolean decides(boolean rollingStarted, boolean rollingExtended,
                                  boolean otherRollingOnCore, boolean decidedToday) {
        if (rollingStarted && !otherRollingOnCore) return true;
        return (rollingStarted || rollingExtended) && !decidedToday;
    }
}

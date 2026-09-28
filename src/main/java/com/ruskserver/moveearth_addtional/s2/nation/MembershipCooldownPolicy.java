package com.ruskserver.moveearth_addtional.s2.nation;

import com.ruskserver.moveearth_addtional.s2.time.OpenTimePolicy;

import java.util.UUID;

/**
 * Cooldown after a player leaves, is kicked from, or loses a nation to disbandment. Durations are in
 * server-opening ticks. While it runs the player can neither join nor found a nation, and Siege
 * checks still bind them to their former nation's peace and alliances.
 */
public final class MembershipCooldownPolicy {
    /** Four hours of server-opening time. */
    public static final long COOLDOWN_OPEN_TICKS = 4L * 60L * 60L * 20L;

    private MembershipCooldownPolicy() { }

    public static long endsAt(long openNow) {
        return OpenTimePolicy.advance(openNow, COOLDOWN_OPEN_TICKS);
    }

    public static long remaining(long endsAt, long openNow) {
        return endsAt <= 0L ? 0L : Math.max(0L, endsAt - Math.max(0L, openNow));
    }

    public static boolean active(long endsAt, long openNow) {
        return remaining(endsAt, openNow) > 0L;
    }

    /**
     * The nation whose ceasefires and alliances bind an attacker: their current (or contracted)
     * nation, else the nation they left while its cooldown still runs and it still exists.
     */
    public static UUID ceasefireNation(UUID currentNation, UUID formerNation, boolean cooldownActive,
                                       boolean formerNationExists) {
        if (currentNation != null) return currentNation;
        return cooldownActive && formerNationExists ? formerNation : null;
    }

    /**
     * Whether a player still bound to {@code formerNation} may not attack {@code defenderNation}.
     * Attacking the former nation itself is not covered: that is not a ceasefire or alliance.
     */
    public static boolean formerNationBlocks(UUID formerNation, UUID defenderNation,
                                             boolean allied, boolean peaceTruce) {
        return formerNation != null && defenderNation != null && !formerNation.equals(defenderNation)
                && (allied || peaceTruce);
    }

    /** Whole minutes left, rounded up so "0" is never shown while a cooldown still runs. */
    public static long remainingMinutes(long ticks) {
        return ticks <= 0L ? 0L : (ticks + 1199L) / 1200L;
    }
}

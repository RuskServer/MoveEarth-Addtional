package com.ruskserver.moveearth_addtional.s2.siege;

import java.util.List;
import java.util.UUID;

/**
 * Who a core's fall belongs to when several sieges are open on it, whom the defender may
 * surrender to, and which withdrawals count as a failed siege.
 *
 * <p>A fall used to belong to whichever siege landed the last point, and it erased every other
 * siege on the core. An unaffiliated alt could therefore take (or be surrendered) a nation's fall
 * and withdraw it: the core came back at the recovery percentage and the real attacker's siege was
 * gone. A nation siege now always outranks an individual one, and the displaced sieges come back
 * when the fall is withdrawn instead of being settled.
 */
public final class SiegeFallAttributionPolicy {
    private SiegeFallAttributionPolicy() { }

    /**
     * @param remainingTicks the siege's timer; a rolling timer restarts on every effective hit, so the
     *                       larger value is the siege that hit the core most recently
     */
    public record Candidate(UUID siegeId, boolean individual, boolean rolling, long remainingTicks) { }

    /**
     * The siege credited with the fall. The finisher keeps it unless it is an individual attacker
     * while a nation siege is open on the same core; then the nation siege that is rolling and hit
     * most recently takes it.
     */
    public static UUID credited(Candidate finisher, List<Candidate> onCore) {
        if (finisher == null) return null;
        if (!finisher.individual() || onCore == null) return finisher.siegeId();
        Candidate best = null;
        for (Candidate candidate : onCore) {
            if (candidate == null || candidate.individual()) continue;
            if (best == null || outranks(candidate, best)) best = candidate;
        }
        return best == null ? finisher.siegeId() : best.siegeId();
    }

    /** A defender may not hand a fall to an individual attacker while a nation is besieging the same core. */
    public static boolean surrenderAllowed(boolean targetIndividual, boolean nationSiegeOnCore) {
        return !targetIndividual || !nationSiegeOnCore;
    }

    /**
     * Withdrawing after real damage (a rolling siege, or a fall) is a failed siege and locks the
     * attacker out of the core like a siege that ran out. Leaving during the initial lock is not.
     */
    public static boolean withdrawalLocksCore(boolean rolling, boolean fallen) {
        return rolling || fallen;
    }

    private static boolean outranks(Candidate candidate, Candidate best) {
        if (candidate.rolling() != best.rolling()) return candidate.rolling();
        return candidate.remainingTicks() > best.remainingTicks();
    }
}

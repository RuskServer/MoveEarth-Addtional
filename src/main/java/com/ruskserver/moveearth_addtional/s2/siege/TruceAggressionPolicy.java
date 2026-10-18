package com.ruskserver.moveearth_addtional.s2.siege;

/**
 * When a nation under a rebuilding truce gives it up by attacking. The truce shields its land from Siege; a
 * nation that strikes first at another nation's players or vehicles while shielded could fight with no risk,
 * so the first such strike ends the truce. Striking back is not a first strike: a hit within
 * {@link #SELF_DEFENSE_MILLIS} of the other nation hitting this one is defence. Pure, so it is tested without
 * a server.
 */
public final class TruceAggressionPolicy {
    /** How long after being hit by a nation a counterattack on it still counts as defence. */
    public static final long SELF_DEFENSE_MILLIS = 30_000L;

    private TruceAggressionPolicy() {
    }

    /**
     * @param attackerShielded    the attacker's nation is under a rebuilding truce
     * @param attackerNation      the attacker's nation, or null
     * @param victimNation        the nation hit, or null for a player with none
     * @param allied              the two nations are allies
     * @param lastHitByVictimAt   when the victim's nation last hit the attacker's, in millis, or 0
     */
    public static boolean endsTruce(boolean attackerShielded, java.util.UUID attackerNation,
                                    java.util.UUID victimNation, boolean allied, long lastHitByVictimAt, long now) {
        if (!attackerShielded || attackerNation == null || victimNation == null) return false;
        if (attackerNation.equals(victimNation) || allied) return false;
        return !defending(lastHitByVictimAt, now);
    }

    public static boolean defending(long lastHitByVictimAt, long now) {
        return lastHitByVictimAt > 0L && now - lastHitByVictimAt <= SELF_DEFENSE_MILLIS;
    }
}

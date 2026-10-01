package com.ruskserver.moveearth_addtional.s2.vehicle;

import java.util.UUID;

/**
 * Who may break a vehicle core by hand, and when.
 *
 * <p>Breaking a core removes its record and drops the block; placing it again
 * registers a fresh record at full HP. Left unchecked, an owner could skip the
 * in-combat repair cap and the repair intervals by re-coring mid-fight, and
 * breaking a destroyed core would erase the attacker's salvage rights, which
 * hang off that record. So the owner may dismantle only a healthy vehicle that
 * is out of combat; a parked, undamaged vehicle can still be taken apart.
 */
public final class VehicleCoreDismantlePolicy {
    private VehicleCoreDismantlePolicy() { }

    public enum Decision {
        ALLOWED,
        /** Own nation, but without the reinforcement-management permission. */
        NO_PERMISSION,
        /** Own nation, inside the combat window opened by the last hit. */
        IN_COMBAT,
        /** Own nation, below full HP: repairs must be earned, not reset. */
        DAMAGED,
        /** Another nation, and the core still has HP: weapons, not pickaxes. */
        OPERATIONAL,
        /** Destroyed core whose salvage rights belong to someone else. */
        LOOT_PROTECTED
    }

    public static Decision owner(boolean manager, int health, int maximumHealth, long now, long combatUntil) {
        if (!manager) return Decision.NO_PERMISSION;
        if (now < combatUntil) return Decision.IN_COMBAT;
        if (health < maximumHealth) return Decision.DAMAGED;
        return Decision.ALLOWED;
    }

    public static Decision outsider(int health, boolean salvageHeldByOthers) {
        if (health > 0) return Decision.OPERATIONAL;
        return salvageHeldByOthers ? Decision.LOOT_PROTECTED : Decision.ALLOWED;
    }

    /**
     * Whether a live salvage grant keeps this player from breaking the wreck.
     * Only the attacker side it was granted to may break it before it expires.
     */
    public static boolean salvageHeldByOthers(boolean grantExists, long expiresOpenTick, long nowOpenTick,
                                              boolean individualAttacker, UUID attackerId,
                                              UUID playerId, UUID playerNation) {
        if (!grantExists || nowOpenTick >= expiresOpenTick || attackerId == null) return false;
        UUID self = individualAttacker ? playerId : playerNation;
        return !attackerId.equals(self);
    }

    /** Whole seconds, rounded up, until the combat window closes. */
    public static long secondsUntil(long now, long until) {
        return Math.max(0L, until - now + 19L) / 20L;
    }
}

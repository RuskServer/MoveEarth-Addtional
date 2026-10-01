package com.ruskserver.moveearth_addtional.compat.cbc;

/** Which CBC entities near an impact may be taken as the munition that caused it. */
public final class CbcMunitionSelectionPolicy {
    private CbcMunitionSelectionPolicy() { }

    /**
     * Cannon projectiles (shot, shells, autocannon rounds, mortar stones) and fragment bursts report
     * terrain impacts; mounts, carriages and contraption entities never do and must not be picked, or
     * the impact would be classified and attributed from the cannon instead of the round.
     *
     * @param projectile      the entity is a vanilla {@code Projectile} (every CBC cannon projectile is)
     * @param fragmentBurst   the entity is a projectile-burst cloud (shrapnel, grapeshot, flak)
     * @param knownMunitionId the registry name classifies as a non-utility munition
     */
    public static boolean isMunition(boolean projectile, boolean fragmentBurst, boolean knownMunitionId) {
        return projectile || fragmentBurst || knownMunitionId;
    }
}

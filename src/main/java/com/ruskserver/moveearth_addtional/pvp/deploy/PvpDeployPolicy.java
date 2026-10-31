package com.ruskserver.moveearth_addtional.pvp.deploy;

/**
 * Timing and safety rules of the deploy screen and the battle area's edge. Pure, so it is tested without a
 * server.
 */
public final class PvpDeployPolicy {
    /** A player left on the deploy screen this long is deployed anyway, so nobody hides in it. */
    public static final int AUTO_DEPLOY_TICKS = 15 * 20;
    /** A deploy point with an enemy this close cannot be chosen. */
    public static final double DANGER_RADIUS = 16.0D;
    /** Seconds outside the battle area before it starts to hurt. */
    public static final int OUT_OF_BOUNDS_GRACE_TICKS = 5 * 20;
    public static final int OUT_OF_BOUNDS_DAMAGE_INTERVAL = 20;
    public static final float OUT_OF_BOUNDS_DAMAGE = 2.0F;

    private PvpDeployPolicy() {
    }

    public static boolean dangerous(double nearestEnemyDistanceSq) {
        return nearestEnemyDistanceSq < DANGER_RADIUS * DANGER_RADIUS;
    }

    /** Whether the player's chosen point is used: it must be their team's and not under threat. */
    public static boolean useChosen(boolean chosenIsTeamPoint, boolean chosenIsDangerous) {
        return chosenIsTeamPoint && !chosenIsDangerous;
    }

    /** Whether this tick outside the area deals damage: once the grace runs out, then every second. */
    public static boolean outOfBoundsDamage(int ticksOutside) {
        return ticksOutside >= OUT_OF_BOUNDS_GRACE_TICKS
                && (ticksOutside - OUT_OF_BOUNDS_GRACE_TICKS) % OUT_OF_BOUNDS_DAMAGE_INTERVAL == 0;
    }

    /** Whole seconds of grace left, rounded up; 0 once it has run out. */
    public static int outOfBoundsSecondsLeft(int ticksOutside) {
        int left = OUT_OF_BOUNDS_GRACE_TICKS - ticksOutside;
        return left <= 0 ? 0 : (left + 19) / 20;
    }
}

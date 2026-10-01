package com.ruskserver.moveearth_addtional.compat.sentry;

/**
 * What a Create: Sentry Mechanical Arm turret may aim at and carry. Pure, so the
 * limits are tested without the mod or Minecraft.
 *
 * <p>A turret on a wall could aim straight down and cover the wall's own foot,
 * leaving attackers no approach; a depression limit leaves the ground right
 * below it unguarded. Carried guns are limited to single-shot sidearms so a
 * turret holds an approach rather than out-firing a squad.
 */
public final class SentryTurretRules {
    private SentryTurretRules() { }

    /** Degrees below horizontal from {@code from} to {@code to}; negative when {@code to} is higher. */
    public static double depressionDegrees(double fromX, double fromY, double fromZ,
                                           double toX, double toY, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        return Math.toDegrees(Math.atan2(fromY - toY, Math.sqrt(dx * dx + dz * dz)));
    }

    public static boolean tooSteep(double depressionDegrees, double maxDepressionDegrees) {
        return depressionDegrees > maxDepressionDegrees;
    }

    /**
     * A gun a turret may fire: semi-automatic only, one projectile per shot, no
     * explosive rounds, and at most {@code maxDamage} per hit.
     */
    public static boolean gunAllowed(boolean semiAutoOnly, int projectilesPerShot, float damage,
                                     boolean explosive, double maxDamage) {
        return semiAutoOnly && projectilesPerShot <= 1 && !explosive && damage <= maxDamage;
    }
}

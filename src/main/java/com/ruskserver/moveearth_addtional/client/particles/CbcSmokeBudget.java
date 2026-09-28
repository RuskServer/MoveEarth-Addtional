package com.ruskserver.moveearth_addtional.client.particles;

final class CbcSmokeBudget {
    private long tick = Long.MIN_VALUE;
    private int nearCount;
    private int farCount;

    boolean accept(long currentTick, boolean near, int nearLimit, int farLimit) {
        if (currentTick != tick) {
            tick = currentTick;
            nearCount = 0;
            farCount = 0;
        }
        if (near) {
            if (nearCount >= nearLimit) return false;
            nearCount++;
        } else {
            if (farCount >= farLimit) return false;
            farCount++;
        }
        return true;
    }

    static boolean decorativeSmoke(String className) {
        return className.equals("rbasamoyai.createbigcannons.effects.particles.smoke.CannonSmokeParticle")
                || className.equals("rbasamoyai.createbigcannons.effects.particles.smoke.FallbackCannonSmokeParticle")
                || className.equals("rbasamoyai.createbigcannons.effects.particles.smoke.QuickFiringBreechSmokeParticle");
    }

    static boolean explosionSmoke(String className) {
        return className.equals("rbasamoyai.createbigcannons.effects.particles.smoke.ShellExplosionSmokeParticle");
    }

    static boolean skipCollision(String className, int age, double distanceSquared, double radius) {
        boolean supported = explosionSmoke(className)
                || className.equals("rbasamoyai.createbigcannons.effects.particles.smoke.TrailSmokeParticle");
        return supported && age >= 8 && Double.isFinite(distanceSquared) && Double.isFinite(radius)
                && radius > 0 && distanceSquared > radius * radius;
    }
}

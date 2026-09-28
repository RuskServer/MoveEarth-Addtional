package com.ruskserver.moveearth_addtional.client.particles;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CbcSmokeBudgetTest {
    @Test
    void capsEachZoneWithoutFarSmokeConsumingNearReserve() {
        CbcSmokeBudget budget = new CbcSmokeBudget();
        assertTrue(budget.accept(10, false, 2, 1));
        assertFalse(budget.accept(10, false, 2, 1));
        assertTrue(budget.accept(10, true, 2, 1));
        assertTrue(budget.accept(10, true, 2, 1));
        assertFalse(budget.accept(10, true, 2, 1));
    }

    @Test
    void budgetResetsOnTickChangeIncludingTimeMovingBackwards() {
        CbcSmokeBudget budget = new CbcSmokeBudget();
        assertTrue(budget.accept(10, true, 1, 1));
        assertFalse(budget.accept(10, true, 1, 1));
        assertTrue(budget.accept(11, true, 1, 1));
        assertTrue(budget.accept(0, true, 1, 1));
    }

    @Test
    void onlyExactDecorativeTypesAreLimited() {
        String prefix = "rbasamoyai.createbigcannons.effects.particles.smoke.";
        assertTrue(CbcSmokeBudget.decorativeSmoke(prefix + "CannonSmokeParticle"));
        assertTrue(CbcSmokeBudget.decorativeSmoke(prefix + "FallbackCannonSmokeParticle"));
        assertTrue(CbcSmokeBudget.decorativeSmoke(prefix + "QuickFiringBreechSmokeParticle"));
        assertFalse(CbcSmokeBudget.decorativeSmoke(prefix + "SmokeShellSmokeParticle"));
        assertFalse(CbcSmokeBudget.decorativeSmoke(prefix + "GasCloudParticle"));
        assertFalse(CbcSmokeBudget.decorativeSmoke(prefix + "TrailSmokeParticle"));
        assertFalse(CbcSmokeBudget.decorativeSmoke("other.CannonSmokeParticle"));
    }

    @Test
    void explosionBudgetDoesNotConsumeCannonReserve() {
        CbcSmokeBudget cannon = new CbcSmokeBudget();
        CbcSmokeBudget explosion = new CbcSmokeBudget();
        assertTrue(explosion.accept(10, true, 1, 1));
        assertFalse(explosion.accept(10, true, 1, 1));
        assertTrue(cannon.accept(10, true, 1, 1));
    }

    @Test
    void explosionClassificationPreservesTacticalSmokeAndTrails() {
        String prefix = "rbasamoyai.createbigcannons.effects.particles.smoke.";
        assertTrue(CbcSmokeBudget.explosionSmoke(prefix + "ShellExplosionSmokeParticle"));
        assertFalse(CbcSmokeBudget.explosionSmoke(prefix + "SmokeShellSmokeParticle"));
        assertFalse(CbcSmokeBudget.explosionSmoke(prefix + "TrailSmokeParticle"));
        assertFalse(CbcSmokeBudget.explosionSmoke(prefix + "GasCloudParticle"));
    }

    @Test
    void collisionLodPreservesNearAndFreshSmoke() {
        String type = "rbasamoyai.createbigcannons.effects.particles.smoke.ShellExplosionSmokeParticle";
        assertFalse(CbcSmokeBudget.skipCollision(type, 7, 10000, 64));
        assertFalse(CbcSmokeBudget.skipCollision(type, 8, 4096, 64));
        assertTrue(CbcSmokeBudget.skipCollision(type, 8, 4097, 64));
        assertFalse(CbcSmokeBudget.skipCollision(type, 8, Double.NaN, 64));
        assertFalse(CbcSmokeBudget.skipCollision(type, 8, Double.POSITIVE_INFINITY, 64));
        assertFalse(CbcSmokeBudget.skipCollision(type, 8, 10000, 0));
    }

    @Test
    void collisionLodOnlyAppliesToExplosionAndTrailSmoke() {
        String prefix = "rbasamoyai.createbigcannons.effects.particles.smoke.";
        assertTrue(CbcSmokeBudget.skipCollision(prefix + "TrailSmokeParticle", 8, 10000, 64));
        assertFalse(CbcSmokeBudget.skipCollision(prefix + "SmokeShellSmokeParticle", 8, 10000, 64));
        assertFalse(CbcSmokeBudget.skipCollision(prefix + "GasCloudParticle", 8, 10000, 64));
        assertFalse(CbcSmokeBudget.skipCollision("other.TrailSmokeParticle", 8, 10000, 64));
    }
}

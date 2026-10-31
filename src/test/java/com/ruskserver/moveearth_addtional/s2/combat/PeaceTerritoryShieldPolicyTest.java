package com.ruskserver.moveearth_addtional.s2.combat;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PeaceTerritoryShieldPolicyTest {
    private final UUID citizen = UUID.randomUUID(), foreign = UUID.randomUUID();
    private final UUID home = UUID.randomUUID(), other = UUID.randomUUID();

    @Test void foreignRangedDamageIsBlockedRegardlessOfShootersPosition() {
        assertTrue(PeaceTerritoryShieldPolicy.blocks(citizen, home, home, foreign, other, true, false, false, false));
        assertTrue(PeaceTerritoryShieldPolicy.blocks(citizen, home, home, foreign, null, true, false, false, false));
        assertTrue(PeaceTerritoryShieldPolicy.blocks(citizen, home, home, null, other, true, false, false, false));
        assertTrue(PeaceTerritoryShieldPolicy.blocks(citizen, home, home, null, null, true, false, false, false));
    }
    @Test void defenderMayFireAtVisitorsWithoutLosingProtection() {
        // The visitor is outside their own home territory, so the defender's shot can land.
        assertFalse(PeaceTerritoryShieldPolicy.blocks(foreign, other, home, citizen, home, true, false, false, false));
        // No retaliation clock or first-shot exception exists: the visitor's reply is still blocked.
        for (int hit = 0; hit < 20; hit++) {
            assertTrue(PeaceTerritoryShieldPolicy.blocks(citizen, home, home, foreign, other, true, false, false, false));
        }
    }
    @Test void siegeAreaDisablesOnlyThisShieldInBothDirections() {
        assertFalse(PeaceTerritoryShieldPolicy.blocks(citizen, home, home, foreign, other, true, false, true, false));
        assertFalse(PeaceTerritoryShieldPolicy.blocks(foreign, other, home, citizen, home, true, false, true, false));
    }
    @Test void wildernessAndForeignLandDoNotProtectCitizens() {
        assertFalse(PeaceTerritoryShieldPolicy.blocks(citizen, home, null, foreign, other, true, false, false, false));
        assertFalse(PeaceTerritoryShieldPolicy.blocks(citizen, home, other, foreign, other, true, false, false, false));
        assertFalse(PeaceTerritoryShieldPolicy.blocks(citizen, null, null, foreign, other, true, false, false, false));
    }
    @Test void sameNationSelfMobsMeleeAndArenaKeepTheirExistingRules() {
        assertFalse(PeaceTerritoryShieldPolicy.blocks(citizen, home, home, foreign, home, true, false, false, false));
        assertFalse(PeaceTerritoryShieldPolicy.blocks(citizen, home, home, citizen, null, true, false, false, false));
        assertFalse(PeaceTerritoryShieldPolicy.blocks(citizen, home, home, foreign, other, true, true, false, false));
        assertFalse(PeaceTerritoryShieldPolicy.blocks(citizen, home, home, foreign, other, false, false, false, false));
        assertFalse(PeaceTerritoryShieldPolicy.blocks(citizen, home, home, foreign, other, true, false, false, true));
    }
    @Test void allSiegeChunksIncludingCornersCountButUnrelatedAreasDoNot() {
        assertTrue(PeaceTerritoryShieldPolicy.insideChunks(-2, 2, 0, 0, 2));
        assertFalse(PeaceTerritoryShieldPolicy.insideChunks(-3, 2, 0, 0, 2));
        assertFalse(PeaceTerritoryShieldPolicy.insideChunks(0, 0, 0, 0, -1));
        assertFalse(PeaceTerritoryShieldPolicy.insideChunks(Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 0, 2));
    }
    @Test void modRangedDamageWithoutAnOwnerEntityStillCountsAsRanged() {
        for (String path : new String[]{"cannon_projectile", "big_cannon_projectile", "machine_gun_fire",
                "machine_gun_fire_in_water", "shrapnel", "grapeshot", "flak"}) {
            assertTrue(PeaceTerritoryShieldPolicy.rangedType(false, false, false, false, "createbigcannons", path));
        }
        assertTrue(PeaceTerritoryShieldPolicy.rangedType(false, false, false, true, "tacz", "bullet"));
        assertTrue(PeaceTerritoryShieldPolicy.rangedType(false, true, false, false, "minecraft", "explosion"));
        assertTrue(PeaceTerritoryShieldPolicy.rangedType(false, false, true, false, "minecraft", "magic"));
        assertFalse(PeaceTerritoryShieldPolicy.rangedType(false, false, false, false, "createbigcannons", "molten_metal"));
        assertFalse(PeaceTerritoryShieldPolicy.rangedType(false, false, false, false, "minecraft", "player_attack"));
        assertFalse(PeaceTerritoryShieldPolicy.rangedType(false, false, false, false, "minecraft", "fall"));
        assertTrue(PeaceTerritoryShieldPolicy.rangedType(false, false, false, false, "mekanism", "laser"));
        assertTrue(PeaceTerritoryShieldPolicy.rangedType(false, false, false, false, "mekanism", "flamethrower"));
        assertFalse(PeaceTerritoryShieldPolicy.rangedType(false, false, false, false, "mekanism", "radiation"));
    }
}

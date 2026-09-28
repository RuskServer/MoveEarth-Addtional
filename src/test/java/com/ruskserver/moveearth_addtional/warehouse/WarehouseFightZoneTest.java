package com.ruskserver.moveearth_addtional.warehouse;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarehouseFightZoneTest {
    @Test void attackersMustStandInsideTheRaidersFiringRange() {
        int x = 1000, y = 70, z = -500;
        assertTrue(WarehouseSitePolicy.insideFightZone(x, y, z, x + 10, y + 1, z + 5));
        assertTrue(WarehouseSitePolicy.insideFightZone(x, y, z, x - 24, y, z));
        assertFalse(WarehouseSitePolicy.insideFightZone(x, y, z, x - 25, y, z));
        assertTrue(WarehouseSitePolicy.insideFightZone(x, y, z,
                x + WarehouseSitePolicy.WIDTH - 1 + 24, y, z + WarehouseSitePolicy.LENGTH - 1 + 24));
        // The old 48-block leash let a player at ~80 blocks shoot freely; that is now refused.
        assertFalse(WarehouseSitePolicy.insideFightZone(x, y, z, x - 80, y, z));
        assertFalse(WarehouseSitePolicy.insideFightZone(x, y, z, x + 10, y + WarehouseSitePolicy.HEIGHT + 16, z));
        assertTrue(WarehouseSitePolicy.FIGHT_MARGIN < 32);
    }
}

package com.ruskserver.moveearth_addtional.warehouse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarehouseSitePolicyTest {
    @Test
    void buildingProtectionIncludesBothEdgesAndFullHeight() {
        assertTrue(WarehouseSitePolicy.within(100, -50, 36, -114, 64));
        assertTrue(WarehouseSitePolicy.within(100, -50, 200, 30, 64));
        assertFalse(WarehouseSitePolicy.within(100, -50, 35, -114, 64));
        assertFalse(WarehouseSitePolicy.within(100, -50, 200, 31, 64));
    }

    @Test
    void controlsAreLimitedToTheActualBuildingVolume() {
        assertTrue(WarehouseSitePolicy.insideStructure(100, 70, -50, 136, 80, -34));
        assertFalse(WarehouseSitePolicy.insideStructure(100, 70, -50, 137, 80, -34));
        assertFalse(WarehouseSitePolicy.insideStructure(100, 70, -50, 136, 81, -34));
        assertFalse(WarehouseSitePolicy.insideStructure(100, 70, -50, 136, 69, -34));
    }

    @Test
    void claimIntersectionUsesExclusiveChunkEndAndLongMath() {
        assertTrue(WarehouseSitePolicy.intersectsClaim(100, -50, -64, 16, -64, 16));
        assertFalse(WarehouseSitePolicy.intersectsClaim(100, -50, -160, -144, -160, -144));
        assertFalse(WarehouseSitePolicy.intersectsClaim(Integer.MAX_VALUE - 100,
                Integer.MIN_VALUE + 100, 0, 16, 0, 16));
    }

    @Test
    void nearbyWarehousesCannotOverlapProtectionMargins() {
        assertTrue(WarehouseSitePolicy.overlaps(100, -50, 264, -50));
        assertFalse(WarehouseSitePolicy.overlaps(100, -50, 265, -50));
    }
}

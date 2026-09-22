package com.ruskserver.moveearth_addtional.warehouse;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WarehouseFixedSitesTest {
    @Test
    void oneNonOverlappingSitePerProductionRegion() {
        assertEquals(8, WarehouseFixedSites.SITES.size());
        var regions = new HashSet<Integer>();
        for (var site : WarehouseFixedSites.SITES) {
            regions.add(site.region());
            for (var other : WarehouseFixedSites.SITES) {
                if (site == other) continue;
                assertFalse(WarehouseSitePolicy.overlaps(site.minX(), site.minZ(),
                        other.minX(), other.minZ()));
            }
        }
        assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7, 8), regions);
    }
}

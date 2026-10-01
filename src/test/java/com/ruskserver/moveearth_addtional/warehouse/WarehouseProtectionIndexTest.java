package com.ruskserver.moveearth_addtional.warehouse;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarehouseProtectionIndexTest {
    @Test
    void agreesWithTheLinearFootprintTest() {
        int[][] sites = {{-4_000, 2_500}, {7, -19}, {1_000, 1_000}, {-33, -400}};
        WarehouseProtectionIndex index = new WarehouseProtectionIndex();
        for (int[] site : sites) index.add("overworld", site[0], site[1]);
        Random random = new Random(7L);
        for (int probe = 0; probe < 50_000; probe++) {
            int[] near = sites[random.nextInt(sites.length)];
            int x = near[0] + random.nextInt(400) - 200;
            int z = near[1] + random.nextInt(400) - 200;
            boolean expected = false;
            for (int[] site : sites) {
                expected |= WarehouseSitePolicy.within(site[0], site[1], x, z, WarehouseSitePolicy.BUILD_MARGIN);
            }
            assertEquals(expected, index.protects("overworld", x, z), x + "," + z);
            assertFalse(index.protects("the_nether", x, z));
        }
    }

    @Test
    void edgesOfTheMarginAreExact() {
        WarehouseProtectionIndex index = new WarehouseProtectionIndex();
        index.add("overworld", 0, 0);
        int margin = WarehouseSitePolicy.BUILD_MARGIN;
        assertTrue(index.protects("overworld", -margin, -margin));
        assertFalse(index.protects("overworld", -margin - 1, 0));
        assertTrue(index.protects("overworld", WarehouseSitePolicy.WIDTH - 1 + margin, 0));
        assertFalse(index.protects("overworld", WarehouseSitePolicy.WIDTH + margin, 0));
        assertTrue(index.hasDimension("overworld"));
        assertFalse(index.hasDimension("the_end"));
    }
}

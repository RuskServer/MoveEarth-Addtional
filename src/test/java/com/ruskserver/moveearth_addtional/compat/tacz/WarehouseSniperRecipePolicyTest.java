package com.ruskserver.moveearth_addtional.compat.tacz;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WarehouseSniperRecipePolicyTest {
    @Test
    void onlySniperCategoryIsGated() {
        assertTrue(WarehouseSniperRecipePolicy.requiresAssembly("Sniper"));
        assertTrue(WarehouseSniperRecipePolicy.requiresAssembly("sniper rifle"));
        assertFalse(WarehouseSniperRecipePolicy.requiresAssembly("marksman"));
        assertFalse(WarehouseSniperRecipePolicy.requiresAssembly("DMR"));
        assertFalse(WarehouseSniperRecipePolicy.requiresAssembly("rifle"));
        assertFalse(WarehouseSniperRecipePolicy.requiresAssembly(null));
    }
}

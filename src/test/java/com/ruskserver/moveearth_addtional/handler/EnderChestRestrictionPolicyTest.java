package com.ruskserver.moveearth_addtional.handler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnderChestRestrictionPolicyTest {
    @Test
    void identifiesOnlyTheVanillaEnderChestId() {
        assertTrue(EnderChestRestrictionPolicy.isRestrictedId("minecraft", "ender_chest"));
        assertFalse(EnderChestRestrictionPolicy.isRestrictedId("minecraft", "chest"));
        assertFalse(EnderChestRestrictionPolicy.isRestrictedId("example", "ender_chest"));
    }
}

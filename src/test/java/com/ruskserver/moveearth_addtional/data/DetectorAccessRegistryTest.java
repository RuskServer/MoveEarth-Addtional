package com.ruskserver.moveearth_addtional.data;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetectorAccessRegistryTest {
    @Test
    void managerCanEditOnlyTheGrantingOwnersWhitelist() {
        DetectorAccessRegistry registry = new DetectorAccessRegistry();
        UUID owner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        UUID manager = UUID.randomUUID();

        assertTrue(registry.addManager(owner, manager, "Manager"));
        assertTrue(registry.canEditWhitelist(owner, manager));
        assertFalse(registry.canEditWhitelist(otherOwner, manager));
        assertTrue(registry.canEditWhitelist(owner, owner));
    }

    @Test
    void ownerCannotBeAddedAsOwnManagerAndOnlyOwnerCanDelegate() {
        DetectorAccessRegistry registry = new DetectorAccessRegistry();
        UUID owner = UUID.randomUUID();

        assertFalse(registry.addManager(owner, owner, "Owner"));
        assertFalse(registry.isManager(owner, owner));
    }

    @Test
    void managerCanBeRemovedCaseInsensitivelyByName() {
        DetectorAccessRegistry registry = new DetectorAccessRegistry();
        UUID owner = UUID.randomUUID();
        UUID manager = UUID.randomUUID();

        registry.addManager(owner, manager, "BaseAdmin");
        assertTrue(registry.removeManagerByName(owner, "baseadmin"));
        assertFalse(registry.isManager(owner, manager));
    }

}

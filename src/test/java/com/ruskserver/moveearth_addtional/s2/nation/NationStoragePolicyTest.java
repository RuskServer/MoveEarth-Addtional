package com.ruskserver.moveearth_addtional.s2.nation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NationStoragePolicyTest {
    @Test void nationlessPlayersAreDenied() {
        assertFalse(NationStoragePolicy.canUseStorage(false, false, false));
    }

    @Test void membersNeedOwnTerritoryWhileOperatorsBypass() {
        assertFalse(NationStoragePolicy.canUseStorage(true, false, false));
        assertTrue(NationStoragePolicy.canUseStorage(true, true, false));
        assertTrue(NationStoragePolicy.canUseStorage(false, false, true));
    }

    @Test void onlyKnownStorageMenusAreRestricted() {
        assertTrue(NationStoragePolicy.isRestrictedMenuId("minecraft", "generic_9x6"));
        assertTrue(NationStoragePolicy.isRestrictedMenuId("minecraft", "hopper"));
        assertTrue(NationStoragePolicy.isRestrictedMenuId("create", "toolbox"));
        assertFalse(NationStoragePolicy.isRestrictedMenuId("minecraft", "furnace"));
        assertFalse(NationStoragePolicy.isRestrictedMenuId("create", "mechanical_crafter"));
    }
}

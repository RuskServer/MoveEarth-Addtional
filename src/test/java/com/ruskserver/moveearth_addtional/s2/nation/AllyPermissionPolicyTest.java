package com.ruskserver.moveearth_addtional.s2.nation;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllyPermissionPolicyTest {
    private static final UUID HOST = UUID.randomUUID();
    private static final UUID ALLY = UUID.randomUUID();

    @Test void nothingIsGrantedByDefault() {
        for (AllyPermission permission : AllyPermission.values()) {
            assertFalse(AllyPermissionPolicy.granted(true, 0, 0, permission));
        }
    }

    @Test void nationWideAndPersonalGrantsAddUp() {
        int build = AllyPermissionPolicy.with(0, AllyPermission.BUILD, true);
        int storage = AllyPermissionPolicy.with(0, AllyPermission.STORAGE, true);
        assertTrue(AllyPermissionPolicy.granted(true, build, 0, AllyPermission.BUILD));
        assertTrue(AllyPermissionPolicy.granted(true, 0, storage, AllyPermission.STORAGE));
        assertTrue(AllyPermissionPolicy.granted(true, build, storage, AllyPermission.STORAGE));
        assertFalse(AllyPermissionPolicy.granted(true, build, storage, AllyPermission.REINFORCE));
    }

    @Test void grantsNeverApplyOutsideAnAlliance() {
        int all = AllyPermission.ALL_MASK;
        assertFalse(AllyPermissionPolicy.granted(false, all, all, AllyPermission.BUILD));
        assertFalse(AllyPermissionPolicy.foreignAccess(HOST, ALLY, false, all, all, true, AllyPermission.BUILD));
    }

    @Test void foreignAccessStillNeedsTheOwnNationPermission() {
        int all = AllyPermission.ALL_MASK;
        assertTrue(AllyPermissionPolicy.foreignAccess(HOST, ALLY, true, all, 0, true, AllyPermission.REINFORCE));
        assertFalse(AllyPermissionPolicy.foreignAccess(HOST, ALLY, true, all, 0, false, AllyPermission.REINFORCE));
    }

    @Test void ownTerritoryAndNationlessActorsAreNotForeignAccess() {
        int all = AllyPermission.ALL_MASK;
        assertFalse(AllyPermissionPolicy.foreignAccess(HOST, HOST, true, all, all, true, AllyPermission.BUILD));
        assertFalse(AllyPermissionPolicy.foreignAccess(HOST, null, true, all, all, true, AllyPermission.BUILD));
        assertFalse(AllyPermissionPolicy.foreignAccess(null, ALLY, true, all, all, true, AllyPermission.BUILD));
    }

    @Test void togglingOnlyTouchesOneBitAndUnknownBitsAreDropped() {
        int mask = AllyPermissionPolicy.with(AllyPermission.ALL_MASK, AllyPermission.STORAGE, false);
        assertTrue(AllyPermissionPolicy.has(mask, AllyPermission.BUILD));
        assertFalse(AllyPermissionPolicy.has(mask, AllyPermission.STORAGE));
        assertTrue(AllyPermissionPolicy.has(mask, AllyPermission.REINFORCE));
        assertEquals(AllyPermission.ALL_MASK, AllyPermissionPolicy.sanitize(0xFF));
        assertFalse(AllyPermissionPolicy.granted(true, 1 << 7, 1 << 7, AllyPermission.BUILD));
    }

    @Test void networkIdsAreStable() {
        assertEquals(AllyPermission.BUILD, AllyPermission.fromNetworkId(0));
        assertEquals(AllyPermission.STORAGE, AllyPermission.fromNetworkId(1));
        assertEquals(AllyPermission.REINFORCE, AllyPermission.fromNetworkId(2));
        assertNull(AllyPermission.fromNetworkId(3));
    }

    @Test void onlyDiplomacyManagersEditGrantsForCurrentAllies() {
        assertEquals(AllyPermissionPolicy.EditDecision.NO_PERMISSION,
                AllyPermissionPolicy.edit(false, true, false, false));
        assertEquals(AllyPermissionPolicy.EditDecision.NOT_ALLIED,
                AllyPermissionPolicy.edit(true, false, false, false));
        assertEquals(AllyPermissionPolicy.EditDecision.TARGET_NOT_MEMBER,
                AllyPermissionPolicy.edit(true, true, true, false));
        assertEquals(AllyPermissionPolicy.EditDecision.ALLOWED,
                AllyPermissionPolicy.edit(true, true, true, true));
        assertEquals(AllyPermissionPolicy.EditDecision.ALLOWED,
                AllyPermissionPolicy.edit(true, true, false, false));
    }
}

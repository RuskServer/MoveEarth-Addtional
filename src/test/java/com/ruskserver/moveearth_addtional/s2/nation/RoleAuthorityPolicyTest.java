package com.ruskserver.moveearth_addtional.s2.nation;

import com.ruskserver.moveearth_addtional.s2.S2Permission;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleAuthorityPolicyTest {
    private static final long ROLES = S2Permission.MANAGE_ROLES.mask();
    private static final long TREASURY = S2Permission.MANAGE_TREASURY.mask();
    private static final long EVERYTHING = ~S2Permission.OWNER.mask();

    @Test
    void roleManagerCannotCreateOrWidenRolesBeyondTheirOwnPermissions() {
        assertFalse(RoleAuthorityPolicy.maySave(false, ROLES, 0L, TREASURY));
        assertFalse(RoleAuthorityPolicy.maySave(false, ROLES, ROLES, EVERYTHING));
        assertTrue(RoleAuthorityPolicy.maySave(false, ROLES, 0L, ROLES));
    }

    @Test
    void roleManagerCannotStripPermissionsTheyLackFromAnotherRole() {
        assertFalse(RoleAuthorityPolicy.maySave(false, ROLES, TREASURY, 0L));
        // Renaming a role without touching its bits needs no authority over them.
        assertTrue(RoleAuthorityPolicy.maySave(false, ROLES, TREASURY, TREASURY));
    }

    @Test
    void roleManagerCannotSelfAssignOrHandOutHigherRoles() {
        assertFalse(RoleAuthorityPolicy.mayAssign(false, true, ROLES, ROLES, ROLES));
        assertFalse(RoleAuthorityPolicy.mayAssign(false, false, ROLES, 0L, TREASURY));
        // Nor demote someone who holds more than they do.
        assertFalse(RoleAuthorityPolicy.mayAssign(false, false, ROLES, TREASURY, 0L));
        assertTrue(RoleAuthorityPolicy.mayAssign(false, false, ROLES, 0L, ROLES));
    }

    @Test
    void ownerIsUnrestricted() {
        assertTrue(RoleAuthorityPolicy.maySave(true, 0L, 0L, EVERYTHING));
        assertTrue(RoleAuthorityPolicy.mayAssign(true, false, 0L, TREASURY, EVERYTHING));
        assertTrue(RoleAuthorityPolicy.withinAuthority(true, 0L, TREASURY));
    }
}

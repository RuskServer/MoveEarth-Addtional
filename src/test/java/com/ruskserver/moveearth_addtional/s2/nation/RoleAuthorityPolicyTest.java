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

    @Test
    void membersKickOnlyStrictlyLowerAuthority() {
        long members = S2Permission.MANAGE_MEMBERS.mask();
        long bastion = S2Permission.BASTION_ACCESS.mask();
        long officer = members | bastion;
        long seniorOfficer = officer | TREASURY;
        // A plain member (bastion access only) sits below an officer who also holds it.
        assertTrue(RoleAuthorityPolicy.mayKick(false, false, officer, bastion));
        assertTrue(RoleAuthorityPolicy.mayKick(false, false, seniorOfficer, officer));
        // Equal rank and superiors are out of reach.
        assertFalse(RoleAuthorityPolicy.mayKick(false, false, officer, officer));
        assertFalse(RoleAuthorityPolicy.mayKick(false, false, officer, seniorOfficer));
        // A target holding anything the actor lacks is not lower, even with fewer bits overall.
        assertFalse(RoleAuthorityPolicy.mayKick(false, false, seniorOfficer, members | ROLES));
        assertFalse(RoleAuthorityPolicy.mayKick(false, true, seniorOfficer, 0L));
    }

    @Test
    void ownerKicksAnyoneButThemselves() {
        assertTrue(RoleAuthorityPolicy.mayKick(true, false, 0L, EVERYTHING));
        assertFalse(RoleAuthorityPolicy.mayKick(true, true, EVERYTHING, EVERYTHING));
    }
}

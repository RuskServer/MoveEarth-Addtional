package com.ruskserver.moveearth_addtional.s2.nation;

/**
 * Role managers may grant, revoke and hand out only permissions they hold themselves, so
 * MANAGE_ROLES alone cannot escalate to the treasury or anything else. The owner is unrestricted.
 */
public final class RoleAuthorityPolicy {
    private RoleAuthorityPolicy() { }

    /** Whether every bit in {@code mask} is one the actor holds. */
    public static boolean withinAuthority(boolean actorIsOwner, long actorMask, long mask) {
        return actorIsOwner || (mask & ~actorMask) == 0L;
    }

    /** Saving a role may only add or remove bits the actor holds. */
    public static boolean maySave(boolean actorIsOwner, long actorMask, long previousMask, long requestedMask) {
        return withinAuthority(actorIsOwner, actorMask, previousMask ^ requestedMask);
    }

    /**
     * Assigning needs authority over both the role being given and the role being replaced,
     * and a non-owner may not reassign their own role.
     */
    public static boolean mayAssign(boolean actorIsOwner, boolean self, long actorMask,
                                    long targetCurrentMask, long newRoleMask) {
        if (actorIsOwner) return true;
        return !self && withinAuthority(false, actorMask, targetCurrentMask)
                && withinAuthority(false, actorMask, newRoleMask);
    }
}

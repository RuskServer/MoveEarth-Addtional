package com.ruskserver.moveearth_addtional.s2.nation;

import java.util.UUID;

/** Pure rules for the per-ally grants a host nation gives inside its own territory. */
public final class AllyPermissionPolicy {
    private AllyPermissionPolicy() { }

    /** Drops unknown bits, so a corrupt save or packet can never grant a future permission. */
    public static int sanitize(int mask) {
        return mask & AllyPermission.ALL_MASK;
    }

    public static int with(int mask, AllyPermission permission, boolean enabled) {
        int safe = sanitize(mask);
        return enabled ? safe | permission.mask() : safe & ~permission.mask();
    }

    public static boolean has(int mask, AllyPermission permission) {
        return permission != null && (mask & permission.mask()) != 0;
    }

    /**
     * Whether a member of an allied nation holds {@code permission} in the host's territory.
     * The nation-wide grant and the member's personal grant add up; neither applies outside an
     * alliance, including after it has ended.
     */
    public static boolean granted(boolean allied, int nationMask, int personalMask, AllyPermission permission) {
        return allied && has(sanitize(nationMask) | sanitize(personalMask), permission);
    }

    /**
     * Foreign-territory access for an actor standing in land controlled by {@code hostNation}. Only
     * a member of a different, allied nation can qualify, and they still need the matching
     * permission in their own nation.
     */
    public static boolean foreignAccess(UUID hostNation, UUID actorNation, boolean allied,
                                        int nationMask, int personalMask, boolean ownRolePermission,
                                        AllyPermission permission) {
        if (hostNation == null || actorNation == null || hostNation.equals(actorNation)) return false;
        return ownRolePermission && granted(allied, nationMask, personalMask, permission);
    }

    public enum EditDecision { ALLOWED, NO_PERMISSION, NOT_ALLIED, TARGET_NOT_MEMBER }

    /** Only a manager of the host's diplomacy edits its grants, and only for a current ally. */
    public static EditDecision edit(boolean canManageDiplomacy, boolean allied,
                                    boolean personal, boolean targetInAlly) {
        if (!canManageDiplomacy) return EditDecision.NO_PERMISSION;
        if (!allied) return EditDecision.NOT_ALLIED;
        if (personal && !targetInAlly) return EditDecision.TARGET_NOT_MEMBER;
        return EditDecision.ALLOWED;
    }
}

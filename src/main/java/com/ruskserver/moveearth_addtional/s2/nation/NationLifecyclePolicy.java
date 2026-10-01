package com.ruskserver.moveearth_addtional.s2.nation;

/** Pure validation for owner-only lifecycle operations. */
public final class NationLifecyclePolicy {
    private NationLifecyclePolicy() { }

    public static Decision transfer(boolean actorOwner, boolean targetMember,
                                    boolean targetOwner, boolean siegeLocked) {
        if (!actorOwner) return Decision.OWNER_ONLY;
        if (siegeLocked) return Decision.SIEGE_LOCKED;
        if (!targetMember) return Decision.TARGET_NOT_MEMBER;
        if (targetOwner) return Decision.TARGET_IS_OWNER;
        return Decision.ALLOWED;
    }

    /**
     * Disbanding deletes every diplomatic record at once, so a nation bound by an alliance (including
     * one under termination notice) or a peace truce must first let it run out: otherwise disbanding
     * would be an instant way out of the notice period and the truce.
     */
    public static Decision disband(boolean actorOwner, boolean siegeLocked, boolean hasPrisoners,
                                   boolean hasAlliance, boolean hasPeaceTruce) {
        if (!actorOwner) return Decision.OWNER_ONLY;
        if (siegeLocked) return Decision.SIEGE_LOCKED;
        if (hasPrisoners) return Decision.PRISONERS_EXIST;
        if (hasAlliance) return Decision.ALLIANCE_ACTIVE;
        if (hasPeaceTruce) return Decision.PEACE_TRUCE_ACTIVE;
        return Decision.ALLOWED;
    }

    public enum Decision {
        ALLOWED, OWNER_ONLY, TARGET_NOT_MEMBER, TARGET_IS_OWNER, SIEGE_LOCKED, PRISONERS_EXIST,
        ALLIANCE_ACTIVE, PEACE_TRUCE_ACTIVE
    }
}

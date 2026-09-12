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

    public static Decision disband(boolean actorOwner, boolean siegeLocked, boolean hasPrisoners) {
        if (!actorOwner) return Decision.OWNER_ONLY;
        if (siegeLocked) return Decision.SIEGE_LOCKED;
        if (hasPrisoners) return Decision.PRISONERS_EXIST;
        return Decision.ALLOWED;
    }

    public enum Decision {
        ALLOWED, OWNER_ONLY, TARGET_NOT_MEMBER, TARGET_IS_OWNER, SIEGE_LOCKED, PRISONERS_EXIST
    }
}

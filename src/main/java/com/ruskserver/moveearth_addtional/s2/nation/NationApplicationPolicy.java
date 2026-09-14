package com.ruskserver.moveearth_addtional.s2.nation;

/** Pure validation shared by the persistent, server-authoritative join application flow. */
final class NationApplicationPolicy {
    private NationApplicationPolicy() { }

    static Decision apply(boolean alreadyMember, boolean nationExists, boolean alreadyApplied) {
        if (alreadyMember) return Decision.ALREADY_MEMBER;
        if (!nationExists) return Decision.NATION_NOT_FOUND;
        if (alreadyApplied) return Decision.ALREADY_APPLIED;
        return Decision.ALLOW_APPLY;
    }

    static Decision decide(boolean canManage, boolean applicationExists,
                           boolean alreadyMember, boolean approve) {
        if (!canManage) return Decision.NO_PERMISSION;
        if (!applicationExists) return Decision.APPLICATION_NOT_FOUND;
        if (alreadyMember) return Decision.ALREADY_MEMBER;
        return approve ? Decision.ALLOW_APPROVE : Decision.ALLOW_REJECT;
    }

    enum Decision {
        ALLOW_APPLY, ALLOW_APPROVE, ALLOW_REJECT,
        NO_PERMISSION, ALREADY_MEMBER, ALREADY_APPLIED, NATION_NOT_FOUND, APPLICATION_NOT_FOUND
    }
}

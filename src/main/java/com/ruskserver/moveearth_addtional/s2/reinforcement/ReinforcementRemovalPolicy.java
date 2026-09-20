package com.ruskserver.moveearth_addtional.s2.reinforcement;

/** Pure authorization boundary for deliberate reinforcement removal. */
final class ReinforcementRemovalPolicy {
    private ReinforcementRemovalPolicy() {
    }

    static boolean canStrip(boolean nationMember, boolean hasPermission,
                            boolean ownsLocation, boolean withinReach) {
        return nationMember && hasPermission && ownsLocation && withinReach;
    }
}

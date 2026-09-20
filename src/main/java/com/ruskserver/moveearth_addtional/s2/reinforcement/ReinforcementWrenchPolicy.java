package com.ruskserver.moveearth_addtional.s2.reinforcement;

/** Pure classification used by the server-side reinforced-block wrench guard. */
final class ReinforcementWrenchPolicy {
    private ReinforcementWrenchPolicy() {
    }

    static boolean isWrench(boolean commonWrenchTag, String namespace, String path) {
        return commonWrenchTag || ("create".equals(namespace) && "wrench".equals(path));
    }

    static boolean shouldBlock(boolean reinforced, boolean wrench) {
        return reinforced && wrench;
    }
}

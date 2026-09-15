package com.ruskserver.moveearth_addtional.s2.nation;

/** Pure access rule shared by the server event handlers and tests. */
public final class NationStoragePolicy {
    private NationStoragePolicy() { }

    public static boolean canUseStorage(boolean nationMember, boolean withinOwnTerritory,
                                        boolean operatorBypass) {
        return operatorBypass || nationMember && withinOwnTerritory;
    }

    public static boolean isRestrictedMenuId(String namespace, String path) {
        if ("minecraft".equals(namespace)) {
            return path != null && (path.startsWith("generic_9x")
                    || path.equals("generic_3x3") || path.equals("hopper")
                    || path.equals("shulker_box"));
        }
        return "create".equals(namespace) && "toolbox".equals(path);
    }
}

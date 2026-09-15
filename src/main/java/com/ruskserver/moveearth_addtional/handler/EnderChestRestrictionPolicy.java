package com.ruskserver.moveearth_addtional.handler;

/** Dependency-free identity policy shared by runtime restrictions and tests. */
final class EnderChestRestrictionPolicy {
    private static final String MINECRAFT = "minecraft";
    private static final String ENDER_CHEST = "ender_chest";

    private EnderChestRestrictionPolicy() {
    }

    static boolean isRestrictedId(String namespace, String path) {
        return MINECRAFT.equals(namespace) && ENDER_CHEST.equals(path);
    }
}

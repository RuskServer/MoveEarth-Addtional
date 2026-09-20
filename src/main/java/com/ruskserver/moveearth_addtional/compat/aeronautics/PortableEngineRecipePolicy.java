package com.ruskserver.moveearth_addtional.compat.aeronautics;

/** Pure identifiers used while replacing Create: Simulated's inexpensive engine recipe. */
public final class PortableEngineRecipePolicy {
    public static final String LEGACY_NAMESPACE = "simulated";
    public static final String LEGACY_PATH = "red_portable_engine";
    public static final String REPLACEMENT_NAMESPACE = "moveearth_addtional";
    public static final String REPLACEMENT_PATH = "red_portable_engine";

    private PortableEngineRecipePolicy() {
    }

    public static boolean isLegacyRecipe(String namespace, String path) {
        return LEGACY_NAMESPACE.equals(namespace) && LEGACY_PATH.equals(path);
    }

    public static boolean isReplacementRecipe(String namespace, String path) {
        return REPLACEMENT_NAMESPACE.equals(namespace) && REPLACEMENT_PATH.equals(path);
    }
}

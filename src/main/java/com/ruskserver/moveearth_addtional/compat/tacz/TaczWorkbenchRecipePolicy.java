package com.ruskserver.moveearth_addtional.compat.tacz;

import java.util.Map;

/** Pure mapping between TaCZ's original workbench recipes and MoveEarth replacements. */
public final class TaczWorkbenchRecipePolicy {
    private static final String TACZ = "tacz";
    private static final String MOVE_EARTH = "moveearth_addtional";
    private static final Map<String, String> REPLACEMENTS = Map.of(
            "gun_smith_table", "gun_smith_table",
            "ammo_workbench", "ammo_workbench",
            "attachment_workbench", "attachment_workbench"
    );

    private TaczWorkbenchRecipePolicy() {
    }

    public static boolean isLegacyRecipe(String namespace, String path) {
        return TACZ.equals(namespace) && REPLACEMENTS.containsKey(path);
    }

    public static boolean isReplacementRecipe(String legacyPath, String namespace, String path) {
        String replacementPath = REPLACEMENTS.get(legacyPath);
        return replacementPath != null
                && MOVE_EARTH.equals(namespace)
                && replacementPath.equals(path);
    }
}

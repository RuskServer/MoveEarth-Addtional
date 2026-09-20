package com.ruskserver.moveearth_addtional.compat.mekanism;

import java.util.Map;

/** Prevents placed cables from bypassing MoveEarth's curated tier recipes. */
public final class MekanismCableUpgradePolicy {
    private static final String MEKANISM = "mekanism";
    private static final Map<String, String> ALLOY_FOR_CABLE = Map.of(
            "basic_universal_cable", "infused_alloy",
            "advanced_universal_cable", "reinforced_alloy",
            "elite_universal_cable", "atomic_alloy"
    );

    private MekanismCableUpgradePolicy() {
    }

    public static boolean shouldBlock(String itemNamespace, String itemPath,
                                      String blockNamespace, String blockPath) {
        if (!MEKANISM.equals(itemNamespace) || !MEKANISM.equals(blockNamespace)) {
            return false;
        }
        return itemPath.equals(ALLOY_FOR_CABLE.get(blockPath));
    }
}

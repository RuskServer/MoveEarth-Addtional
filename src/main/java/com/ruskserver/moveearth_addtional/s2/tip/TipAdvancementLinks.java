package com.ruskserver.moveearth_addtional.s2.tip;

import java.util.Map;

/**
 * The tip for the next step of the industry path, shown the moment a player
 * earns the advancement before it. Periodic tips stay random; these make sure
 * the milestones themselves point onward.
 */
public final class TipAdvancementLinks {
    /** MoveEarth advancement path to the tip that explains what comes after it. */
    static final Map<String, String> NEXT_TIP = Map.of(
            "industry/andesite_alloy", "industry_water_wheel",
            "industry/rotation", "industry_brass",
            "industry/iron_sheet", "industry_steam",
            "industry/brass", "industry_electricity",
            "industry/electricity", "industry_mekanism");

    private TipAdvancementLinks() { }

    /** The tip id to show after {@code advancementPath}, or null. */
    public static String nextTip(String advancementPath) {
        return NEXT_TIP.get(advancementPath);
    }
}

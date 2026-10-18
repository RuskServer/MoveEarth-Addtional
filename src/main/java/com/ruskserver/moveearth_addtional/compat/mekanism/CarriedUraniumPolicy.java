package com.ruskserver.moveearth_addtional.compat.mekanism;

import java.util.Map;

/**
 * How much radiation carried uranium gives off, and how clean water washes a dose
 * away. Pure, so it is tested without Minecraft or Mekanism.
 *
 * <p>Mekanism has no exposure from uranium held in an inventory. Here every
 * uranium item counts as some number of ingots and each ingot adds a fixed
 * dose rate. Mekanism decays a player's dose by its targetDecayRate about once
 * a second, so a steady load settles at {@code rate / (1 - decay)}: with the
 * defaults, 2 µSv/h per ingot and 0.9995, one stack of ingots settles near
 * 71 µSv. Mekanism's sickness starts above about 50 µSv, so one stack carried
 * for about 40 minutes brings on mild sickness, two stacks within about
 * 15 minutes, and a stack of blocks within about three.
 */
public final class CarriedUraniumPolicy {
    /** Ingots each uranium item stands for; ores and raw pieces count as one ingot. */
    private static final Map<String, Double> INGOTS = Map.of(
            "mekanism:uranium_ore", 1.0D,
            "mekanism:deepslate_uranium_ore", 1.0D,
            "mekanism:raw_uranium", 1.0D,
            "mekanism:block_raw_uranium", 9.0D,
            "mekanism:ingot_uranium", 1.0D,
            "mekanism:nugget_uranium", 1.0D / 9.0D,
            "mekanism:block_uranium", 9.0D,
            "mekanism:dust_uranium", 1.0D,
            "mekanism:yellow_cake_uranium", 1.0D);

    /** Below this a dose is not worth washing or reporting, in Sv. */
    public static final double NEGLIGIBLE_DOSE = 1.0E-6D;

    private CarriedUraniumPolicy() {
    }

    /** Ingots one item of {@code itemId} stands for; 0 for anything that is not uranium. */
    public static double ingotsPerItem(String itemId) {
        return INGOTS.getOrDefault(itemId, 0.0D);
    }

    /** Dose, in Sv, carried uranium adds in one second. */
    public static double dosePerSecond(double ingots, double svPerHourPerIngot) {
        if (!(ingots > 0.0D) || !(svPerHourPerIngot > 0.0D)) return 0.0D;
        return ingots * svPerHourPerIngot / 3600.0D;
    }

    /** The dose a constant load settles at, given the share of the dose kept each second. */
    public static double steadyDose(double dosePerSecond, double decayPerSecond) {
        if (decayPerSecond >= 1.0D) return Double.POSITIVE_INFINITY;
        return dosePerSecond / (1.0D - Math.max(0.0D, decayPerSecond));
    }

    /** Dose left after one second in clean water. */
    public static double washed(double dose, double keptPerSecond) {
        if (!(dose > 0.0D)) return 0.0D;
        double kept = dose * Math.max(0.0D, Math.min(1.0D, keptPerSecond));
        return kept < NEGLIGIBLE_DOSE ? 0.0D : kept;
    }

    /** A dose for a player to read, such as {@code 71 µSv} or {@code 1.2 mSv}. */
    public static String formatDose(double sv) {
        if (sv >= 1.0D) return String.format(java.util.Locale.ROOT, "%.2f Sv", sv);
        if (sv >= 1.0E-3D) return String.format(java.util.Locale.ROOT, "%.1f mSv", sv * 1.0E3D);
        return String.format(java.util.Locale.ROOT, "%.0f µSv", sv * 1.0E6D);
    }
}

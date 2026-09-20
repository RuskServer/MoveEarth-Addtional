package com.ruskserver.moveearth_addtional.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side balance controls for Create water wheels. */
public final class WaterWheelBalanceConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.DoubleValue BASE_CAPACITY_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue ARTIFICIAL_FLOW_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue NATURAL_RIVER_MINIMUM_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue SOURCE_QUALITY_PENALTY_STRENGTH;
    private static final ModConfigSpec.IntValue SHARED_RADIUS_BLOCKS;
    private static final ModConfigSpec.DoubleValue FULL_POWER_UNITS;
    private static final ModConfigSpec.DoubleValue LARGE_WHEEL_UNITS;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("waterWheelBalance");

        ENABLED = BUILDER
                .comment("Apply MoveEarth's water-wheel capacity and local-density balance rules.")
                .define("enabled", true);

        BASE_CAPACITY_MULTIPLIER = BUILDER
                .comment("Global multiplier applied to every small and large water wheel's stress capacity.")
                .defineInRange("baseCapacityMultiplier", 0.75D, 0.0D, 2.0D);

        ARTIFICIAL_FLOW_MULTIPLIER = BUILDER
                .comment("Source-quality multiplier for vanilla/player-made flowing water.")
                .defineInRange("artificialFlowMultiplier", 0.50D, 0.0D, 1.0D);

        NATURAL_RIVER_MINIMUM_MULTIPLIER = BUILDER
                .comment(
                        "Minimum source-quality multiplier for generated rivers.",
                        "Wider and steeper rivers rise above this value up to 1.0."
                )
                .defineInRange("naturalRiverMinimumMultiplier", 0.35D, 0.0D, 1.0D);

        SOURCE_QUALITY_PENALTY_STRENGTH = BUILDER
                .comment(
                        "How strongly source quality reduces output: 0 ignores it, 1 applies it directly.",
                        "The default half-strength penalty keeps standalone wheels useful while rewarding good rivers."
                )
                .defineInRange("sourceQualityPenaltyStrength", 0.50D, 0.0D, 1.0D);

        SHARED_RADIUS_BLOCKS = BUILDER
                .comment("Radius in which active water wheels share the same hydraulic power budget.")
                .defineInRange("sharedRadiusBlocks", 32, 0, 128);

        FULL_POWER_UNITS = BUILDER
                .comment(
                        "Small-wheel-equivalent units that can run at full output in one hydraulic area.",
                        "Additional wheels share this budget instead of increasing output linearly."
                )
                .defineInRange("fullPowerUnits", 6.0D, 0.25D, 64.0D);

        LARGE_WHEEL_UNITS = BUILDER
                .comment("How many small-wheel-equivalent units one large water wheel consumes.")
                .defineInRange("largeWheelUnits", 4.0D, 1.0D, 16.0D);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private WaterWheelBalanceConfig() {
    }

    public static boolean enabled() { return ENABLED.getAsBoolean(); }
    public static double baseCapacityMultiplier() { return BASE_CAPACITY_MULTIPLIER.getAsDouble(); }
    public static double artificialFlowMultiplier() { return ARTIFICIAL_FLOW_MULTIPLIER.getAsDouble(); }
    public static double naturalRiverMinimumMultiplier() { return NATURAL_RIVER_MINIMUM_MULTIPLIER.getAsDouble(); }
    public static double sourceQualityPenaltyStrength() { return SOURCE_QUALITY_PENALTY_STRENGTH.getAsDouble(); }
    public static int sharedRadiusBlocks() { return SHARED_RADIUS_BLOCKS.getAsInt(); }
    public static double fullPowerUnits() { return FULL_POWER_UNITS.getAsDouble(); }
    public static double largeWheelUnits() { return LARGE_WHEEL_UNITS.getAsDouble(); }
}

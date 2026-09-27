package com.ruskserver.moveearth_addtional.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side balance controls for Create industry: steam boiler fuel and fan ore processing. */
public final class CreateIndustryConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue STEAM_FUEL_ENABLED;
    private static final ModConfigSpec.DoubleValue IDLE_BURN_RATE;
    private static final ModConfigSpec.DoubleValue FULL_LOAD_BURN_RATE;
    private static final ModConfigSpec.BooleanValue FAN_ORE_ENABLED;
    private static final ModConfigSpec.DoubleValue FAN_ORE_TIME_MULTIPLIER;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("steamEngineFuel");

        STEAM_FUEL_ENABLED = BUILDER
                .comment(
                        "Make blaze burners under a steam boiler burn fuel in proportion to how much",
                        "of their engines' stress capacity is actually being used."
                )
                .define("enabled", true);

        IDLE_BURN_RATE = BUILDER
                .comment(
                        "Fuel burn speed when the engines drive no load, relative to Create's normal rate.",
                        "1.0 is Create's constant rate; lower values let an idle boiler stay lit cheaply."
                )
                .defineInRange("idleBurnRate", 0.2D, 0.0D, 1.0D);

        FULL_LOAD_BURN_RATE = BUILDER
                .comment(
                        "Fuel burn speed when the engines' network uses all of its stress capacity.",
                        "The rate scales linearly between idleBurnRate and this value."
                )
                .defineInRange("fullLoadBurnRate", 2.5D, 1.0D, 8.0D);

        BUILDER.pop();

        BUILDER.push("fanOreProcessing");

        FAN_ORE_ENABLED = BUILDER
                .comment(
                        "Slow Create fan processing (blasting and washing) of ores, raw ores, crushed",
                        "raw ores and dusts, so electric smelting has a reason to exist. Other fan",
                        "recipes keep Create's speed."
                )
                .define("enabled", true);

        FAN_ORE_TIME_MULTIPLIER = BUILDER
                .comment("How many times longer an ore item takes in a fan's air current than Create's normal time.")
                .defineInRange("timeMultiplier", 8.0D, 1.0D, 64.0D);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private CreateIndustryConfig() {
    }

    public static boolean steamFuelEnabled() { return STEAM_FUEL_ENABLED.getAsBoolean(); }
    public static double idleBurnRate() { return IDLE_BURN_RATE.getAsDouble(); }
    public static double fullLoadBurnRate() { return FULL_LOAD_BURN_RATE.getAsDouble(); }
    public static boolean fanOreEnabled() { return FAN_ORE_ENABLED.getAsBoolean(); }
    public static double fanOreTimeMultiplier() { return FAN_ORE_TIME_MULTIPLIER.getAsDouble(); }
}

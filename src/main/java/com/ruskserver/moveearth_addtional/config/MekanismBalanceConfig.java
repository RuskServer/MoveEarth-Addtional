package com.ruskserver.moveearth_addtional.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side balance controls for Mekanism beyond its own config: radiation and the MekaSuit. */
public final class MekanismBalanceConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue TERRITORY_RULES_ENABLED;
    private static final ModConfigSpec.DoubleValue WILDERNESS_SOURCE_DECAY_RATE;
    private static final ModConfigSpec.DoubleValue CORE_SOURCE_DECAY_RATE;
    private static final ModConfigSpec.IntValue CORE_RADIUS_BLOCKS;
    private static final ModConfigSpec.DoubleValue CROSS_BORDER_EXPOSURE_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue MEKASUIT_UNSPECIFIED_ABSORPTION;
    private static final ModConfigSpec.DoubleValue MEKASUIT_FALL_ABSORPTION;
    private static final ModConfigSpec.BooleanValue SHIELD_ENABLED;
    private static final ModConfigSpec.DoubleValue SHIELD_THRESHOLD;
    private static final ModConfigSpec.DoubleValue SHIELD_REDUCTION;
    private static final ModConfigSpec.IntValue SHIELD_CHARGES;
    private static final ModConfigSpec.IntValue SHIELD_COOLDOWN_SECONDS;
    private static final ModConfigSpec.IntValue SHIELD_ENERGY_FE;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("radiation");

        TERRITORY_RULES_ENABLED = BUILDER
                .comment(
                        "Apply MoveEarth's territory rules to Mekanism radiation: faster decay in the",
                        "wilderness and around territory cores, and weaker exposure across borders.",
                        "A decay rate is the factor a source keeps each decay step (about once a second):",
                        "Mekanism's default 0.9995 halves a source in about 23 minutes, 0.998 in about 6,",
                        "0.995 in about 2.3 and 0.99 in about 69 seconds. No rule decays slower than",
                        "Mekanism's own sourceDecayRate."
                )
                .define("territoryRulesEnabled", true);

        WILDERNESS_SOURCE_DECAY_RATE = BUILDER
                .comment("Decay rate for sources outside every nation's territory.")
                .defineInRange("wildernessSourceDecayRate", 0.995D, 0.0D, 1.0D);

        CORE_SOURCE_DECAY_RATE = BUILDER
                .comment("Decay rate for sources near any territory core, so a core cannot be walled off with radiation.")
                .defineInRange("coreSourceDecayRate", 0.99D, 0.0D, 1.0D);

        CORE_RADIUS_BLOCKS = BUILDER
                .comment("How far from a territory core, on each axis, the core decay rate applies.")
                .defineInRange("coreRadiusBlocks", 48, 0, 256);

        CROSS_BORDER_EXPOSURE_MULTIPLIER = BUILDER
                .comment(
                        "Share of a source's exposure that reaches a place under different control:",
                        "another nation's territory, or wilderness versus territory. 1.0 disables it."
                )
                .defineInRange("crossBorderExposureMultiplier", 0.25D, 0.0D, 1.0D);

        BUILDER.pop();

        BUILDER.push("mekaSuit");

        MEKASUIT_UNSPECIFIED_ABSORPTION = BUILDER
                .comment(
                        "Highest share of damage the MekaSuit absorbs for damage types with no entry in",
                        "Mekanism's mekasuit_absorption data map (MoveEarth's data map keeps only",
                        "explosions and player melee). Mekanism's own unspecifiedDamageReductionRatio is",
                        "used when it is lower."
                )
                .defineInRange("unspecifiedAbsorption", 0.0D, 0.0D, 1.0D);

        MEKASUIT_FALL_ABSORPTION = BUILDER
                .comment(
                        "Highest share of fall damage MekaSuit boots cancel. Mekanism's own",
                        "fallDamageReductionRatio is used when it is lower."
                )
                .defineInRange("fallAbsorption", 0.5D, 0.0D, 1.0D);

        BUILDER.push("heavyHitShield");

        SHIELD_ENABLED = BUILDER
                .comment(
                        "A full MekaSuit cuts a flat amount off a single TaCZ bullet hit at or above the",
                        "threshold, a few times before it must recharge. It answers snipers rather than",
                        "making the suit generally tougher."
                )
                .define("enabled", true);

        SHIELD_THRESHOLD = BUILDER
                .comment("Incoming damage of one hit, before armor, that triggers the shield.")
                .defineInRange("thresholdDamage", 16.0D, 0.0D, 1000.0D);

        SHIELD_REDUCTION = BUILDER
                .comment("Damage removed from a triggering hit.")
                .defineInRange("reductionDamage", 12.0D, 0.0D, 1000.0D);

        SHIELD_CHARGES = BUILDER
                .comment("Hits the shield can stop before it has to recharge.")
                .defineInRange("charges", 2, 1, 16);

        SHIELD_COOLDOWN_SECONDS = BUILDER
                .comment("Seconds after the last triggering hit before every charge is restored.")
                .defineInRange("cooldownSeconds", 20, 1, 600);

        SHIELD_ENERGY_FE = BUILDER
                .comment(
                        "Energy taken from the suit per triggering hit, in FE (1,000,000 J at Mekanism's",
                        "default 2.5 J per FE). The shield does not trigger when the suit cannot pay it."
                )
                .defineInRange("energyPerHitFE", 400_000, 0, Integer.MAX_VALUE);

        BUILDER.pop(2);
        SPEC = BUILDER.build();
    }

    private MekanismBalanceConfig() {
    }

    public static boolean territoryRulesEnabled() { return TERRITORY_RULES_ENABLED.getAsBoolean(); }
    public static double wildernessSourceDecayRate() { return WILDERNESS_SOURCE_DECAY_RATE.getAsDouble(); }
    public static double coreSourceDecayRate() { return CORE_SOURCE_DECAY_RATE.getAsDouble(); }
    public static int coreRadiusBlocks() { return CORE_RADIUS_BLOCKS.getAsInt(); }
    public static double crossBorderExposureMultiplier() { return CROSS_BORDER_EXPOSURE_MULTIPLIER.getAsDouble(); }
    public static double mekaSuitUnspecifiedAbsorption() { return MEKASUIT_UNSPECIFIED_ABSORPTION.getAsDouble(); }
    public static double mekaSuitFallAbsorption() { return MEKASUIT_FALL_ABSORPTION.getAsDouble(); }
    public static boolean heavyHitShieldEnabled() { return SHIELD_ENABLED.getAsBoolean(); }
    public static double heavyHitShieldThreshold() { return SHIELD_THRESHOLD.getAsDouble(); }
    public static double heavyHitShieldReduction() { return SHIELD_REDUCTION.getAsDouble(); }
    public static int heavyHitShieldCharges() { return SHIELD_CHARGES.getAsInt(); }
    public static int heavyHitShieldCooldownSeconds() { return SHIELD_COOLDOWN_SECONDS.getAsInt(); }
    public static int heavyHitShieldEnergyFE() { return SHIELD_ENERGY_FE.getAsInt(); }
}

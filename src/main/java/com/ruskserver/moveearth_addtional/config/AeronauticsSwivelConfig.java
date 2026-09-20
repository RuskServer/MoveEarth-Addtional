package com.ruskserver.moveearth_addtional.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side compatibility and balance settings for Create: Simulated. */
public final class AeronauticsSwivelConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue HINGE_CONSTRAINT_ENABLED;
    private static final ModConfigSpec.BooleanValue HORIZONTAL_ONLY;
    private static final ModConfigSpec.BooleanValue ALLOW_UNTESTED_VERSIONS;
    private static final ModConfigSpec.BooleanValue PORTABLE_ENGINE_BALANCE_ENABLED;
    private static final ModConfigSpec.DoubleValue PORTABLE_ENGINE_MAX_CAPACITY;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("swivelOptimization");

        HINGE_CONSTRAINT_ENABLED = BUILDER
                .comment(
                        "Replace supported swivel bearings' rotary constraints with a hinge-style generic constraint.",
                        "This is experimental. Existing swivels are changed only after they are reassembled."
                )
                .define("hingeConstraintEnabled", true);

        HORIZONTAL_ONLY = BUILDER
                .comment(
                        "Only optimize up/down-facing swivels, whose moving part rotates horizontally.",
                        "Side-facing swivels retain Create: Simulated's original constraint."
                )
                .define("horizontalOnly", true);

        ALLOW_UNTESTED_VERSIONS = BUILDER
                .comment(
                        "Allow the compatibility patch on untested Create: Simulated or Sable versions.",
                        "Keep this disabled unless the exact mod combination has been tested."
                )
                .define("allowUntestedVersions", false);

        BUILDER.pop();

        BUILDER.push("portableEngineBalance");

        PORTABLE_ENGINE_BALANCE_ENABLED = BUILDER
                .comment(
                        "Cap Create: Simulated portable-engine stress capacity.",
                        "This keeps compact vehicle engines useful without replacing fixed industrial power."
                )
                .define("enabled", true);

        PORTABLE_ENGINE_MAX_CAPACITY = BUILDER
                .comment(
                        "Maximum portable-engine stress capacity per RPM.",
                        "At the default 32 RPM this value of 32 produces 1024 SU; superheated 64 RPM produces 2048 SU.",
                        "A lower value from Simulated's own config is preserved."
                )
                .defineInRange("maxStressCapacityPerRpm", 32.0D, 0.0D, 64.0D);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private AeronauticsSwivelConfig() {
    }

    public static boolean hingeConstraintEnabled() {
        return HINGE_CONSTRAINT_ENABLED.getAsBoolean();
    }

    public static boolean horizontalOnly() {
        return HORIZONTAL_ONLY.getAsBoolean();
    }

    public static boolean allowUntestedVersions() {
        return ALLOW_UNTESTED_VERSIONS.getAsBoolean();
    }

    public static boolean portableEngineBalanceEnabled() {
        return PORTABLE_ENGINE_BALANCE_ENABLED.getAsBoolean();
    }

    public static double portableEngineMaxCapacity() {
        return PORTABLE_ENGINE_MAX_CAPACITY.getAsDouble();
    }
}

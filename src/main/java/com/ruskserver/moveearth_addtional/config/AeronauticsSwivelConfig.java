package com.ruskserver.moveearth_addtional.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Experimental compatibility settings for Create: Simulated swivel bearings.
 * Existing constraints must be disassembled and assembled again after changing
 * these values.
 */
public final class AeronauticsSwivelConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue HINGE_CONSTRAINT_ENABLED;
    private static final ModConfigSpec.BooleanValue HORIZONTAL_ONLY;
    private static final ModConfigSpec.BooleanValue ALLOW_UNTESTED_VERSIONS;

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
}

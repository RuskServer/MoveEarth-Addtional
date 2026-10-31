package com.ruskserver.moveearth_addtional.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side compatibility and balance settings for Create: Simulated and Absolute Kinematics. */
public final class AeronauticsSwivelConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue HINGE_CONSTRAINT_ENABLED;
    private static final ModConfigSpec.BooleanValue HORIZONTAL_ONLY;
    private static final ModConfigSpec.BooleanValue ALLOW_UNTESTED_VERSIONS;
    private static final ModConfigSpec.BooleanValue PORTABLE_ENGINE_BALANCE_ENABLED;
    private static final ModConfigSpec.DoubleValue PORTABLE_ENGINE_MAX_CAPACITY;
    private static final ModConfigSpec.BooleanValue JOINT_WINDUP_LIMIT_ENABLED;
    private static final ModConfigSpec.DoubleValue JOINT_MAX_LEAD_DEGREES;
    private static final ModConfigSpec.BooleanValue BALLOON_HOLES_ENABLED;
    private static final ModConfigSpec.DoubleValue BALLOON_LEAK_SECONDS;
    private static final ModConfigSpec.BooleanValue BODY_DENSITY_ENABLED;
    private static final ModConfigSpec.IntValue BODY_DENSITY_RADIUS;
    private static final ModConfigSpec.IntValue BODY_DENSITY_MAX;
    private static final ModConfigSpec.BooleanValue DEBRIS_CLEANUP_ENABLED;
    private static final ModConfigSpec.IntValue DEBRIS_CLEANUP_GRACE;
    private static final ModConfigSpec.IntValue DEBRIS_CLEANUP_BATCH;

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

        BUILDER.push("jointWindupLimit");

        JOINT_WINDUP_LIMIT_ENABLED = BUILDER
                .comment(
                        "Stop driven Absolute Kinematics hinges and Create: Simulated swivel bearings from building up force",
                        "while something holds them back. Without this their target angle keeps turning, and the stored push",
                        "snaps the moving part round when it is freed."
                )
                .define("enabled", true);

        JOINT_MAX_LEAD_DEGREES = BUILDER
                .comment(
                        "How far a joint's target may run ahead of its actual angle, in degrees.",
                        "Fast joints may lead by two ticks of their own motion if that is more."
                )
                .defineInRange("maxLeadDegrees", 10.0D, 1.0D, 180.0D);

        BUILDER.pop();

        BUILDER.push("balloonHoles");

        BALLOON_HOLES_ENABLED = BUILDER
                .comment(
                        "Let a holed Create Aeronautics balloon keep the hot air above the hole, and lose what it can no",
                        "longer hold slowly. Without this one broken envelope block empties the whole balloon within seconds."
                )
                .define("enabled", true);

        BALLOON_LEAK_SECONDS = BUILDER
                .comment(
                        "How slowly a holed balloon loses lift: the time, in seconds, for the excess hot air to fall to about",
                        "a third. It is mostly gone after three times this. Aeronautics itself takes 9 seconds; turning a",
                        "burner down still takes those 9 seconds."
                )
                .defineInRange("leakSeconds", 30.0D, 9.0D, 600.0D);

        BUILDER.pop();
        BUILDER.push("bodyDensityLimit");
        BODY_DENSITY_ENABLED = BUILDER.comment(
                "Limit new physics bodies from Simulated assemblers, Sable assembly commands and automatic splitting.",
                "Existing bodies are never deleted; blocked automatic splits retry once per second.",
                "This does not prevent existing bodies moving into the same area or limit other mods' direct API calls.")
                .define("enabled", true);
        BODY_DENSITY_RADIUS = BUILDER.comment(
                "Horizontal radius in world blocks; height is ignored. Each body's world anchor counts once.")
                .defineInRange("radius", 128, 16, 2048);
        BODY_DENSITY_MAX = BUILDER.comment("Maximum bodies around proposed and affected existing world anchors, including new bodies.")
                .defineInRange("maxBodies", 64, 1, 4096);
        DEBRIS_CLEANUP_ENABLED = BUILDER.comment(
                "Remove excess loaded bodies after a warning and grace period, only while still over the density limit.",
                "A physical vehicle core anywhere in the connected chain, an active raid, or a nearby/onboard player protects the chain.")
                .define("debrisCleanupEnabled", true);
        DEBRIS_CLEANUP_GRACE = BUILDER.comment("Continuous seconds eligible before removal; resets when no longer over budget or protected.")
                .defineInRange("debrisCleanupGraceSeconds", 60, 10, 3600);
        DEBRIS_CLEANUP_BATCH = BUILDER.comment("Maximum excess bodies removed per five-second sweep.")
                .defineInRange("debrisCleanupMaxPerSweep", 8, 1, 64);
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

    public static boolean jointWindupLimitEnabled() {
        return JOINT_WINDUP_LIMIT_ENABLED.getAsBoolean();
    }

    public static double jointMaxLeadDegrees() {
        return JOINT_MAX_LEAD_DEGREES.getAsDouble();
    }

    public static boolean balloonHolesEnabled() {
        return BALLOON_HOLES_ENABLED.getAsBoolean();
    }

    public static double balloonLeakSeconds() {
        return BALLOON_LEAK_SECONDS.getAsDouble();
    }

    public static double portableEngineMaxCapacity() {
        return PORTABLE_ENGINE_MAX_CAPACITY.getAsDouble();
    }

    public static boolean bodyDensityEnabled() { return BODY_DENSITY_ENABLED.getAsBoolean(); }
    public static int bodyDensityRadius() { return BODY_DENSITY_RADIUS.getAsInt(); }
    public static int bodyDensityMax() { return BODY_DENSITY_MAX.getAsInt(); }
    public static boolean debrisCleanupEnabled() { return DEBRIS_CLEANUP_ENABLED.getAsBoolean(); }
    public static int debrisCleanupGraceSeconds() { return DEBRIS_CLEANUP_GRACE.getAsInt(); }
    public static int debrisCleanupMaxPerSweep() { return DEBRIS_CLEANUP_BATCH.getAsInt(); }
}

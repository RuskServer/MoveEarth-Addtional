package com.ruskserver.moveearth_addtional.client.scope;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ScopePipConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER.define("enabled", false);
    public static final ModConfigSpec.BooleanValue DEBUG = BUILDER.define("debugOverlay", false);
    public static final ModConfigSpec.BooleanValue IRIS_EXPERIMENTAL = BUILDER.define("irisExperimental", false);
    public static final ModConfigSpec.DoubleValue RESOLUTION = BUILDER.defineInRange("resolutionScale", 0.5, 0.25, 1);
    public static final ModConfigSpec.DoubleValue MINIMUM_MAGNIFICATION = BUILDER.defineInRange("minimumMagnification", 2.0, 1.01, 32);
    public static final ModConfigSpec.BooleanValue SMOOTH_LENS = BUILDER
            .comment("Bilinear filtering when the lens resolution is below the screen's; off keeps sharp pixels.")
            .define("smoothLens", false);
    public static final ModConfigSpec.DoubleValue ABERRATION = BUILDER
            .comment("Colour fringe at the lens edge. 0 is none.")
            .defineInRange("lensAberration", 0.3, 0.0, 1.0);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private ScopePipConfig() { }
}

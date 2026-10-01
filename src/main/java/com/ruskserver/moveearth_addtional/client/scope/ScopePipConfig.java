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
    public static final ModConfigSpec.IntValue UPDATE_INTERVAL = BUILDER
            .comment("Redraw the lens every N frames. Each redraw renders the whole world a second time,",
                    "so 1 roughly doubles world rendering cost while scoped. Higher is lighter, but the",
                    "lens image lags while turning.")
            .defineInRange("lensUpdateInterval", 2, 1, 3);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private ScopePipConfig() { }
}

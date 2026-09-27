package com.ruskserver.moveearth_addtional.client.scope;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ScopePipConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER.define("enabled", false);
    public static final ModConfigSpec.BooleanValue DEBUG = BUILDER.define("debugOverlay", false);
    public static final ModConfigSpec.BooleanValue IRIS_EXPERIMENTAL = BUILDER.define("irisExperimental", false);
    public static final ModConfigSpec.DoubleValue RESOLUTION = BUILDER.defineInRange("resolutionScale", 0.5, 0.25, 1);
    public static final ModConfigSpec.DoubleValue MINIMUM_MAGNIFICATION = BUILDER.defineInRange("minimumMagnification", 2.0, 1.01, 32);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private ScopePipConfig() { }
}

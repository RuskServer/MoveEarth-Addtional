package com.ruskserver.moveearth_addtional.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Market is active by default; operators can pause transactions in an emergency. */
public final class MarketConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.BooleanValue DISABLED = BUILDER
            .comment("Emergency stop for market transactions. False by default; browsing and waypoints remain available.")
            .define("disabled", false);
    public static final ModConfigSpec SPEC = BUILDER.build();
    private MarketConfig() { }
    public static boolean enabled() { return !DISABLED.getAsBoolean(); }
}

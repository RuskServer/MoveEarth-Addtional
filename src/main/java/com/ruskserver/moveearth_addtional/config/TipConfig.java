package com.ruskserver.moveearth_addtional.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned pacing for periodic player tips. */
public final class TipConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Enables periodic MoveEarth tips. Individual players can still opt out.")
            .define("enabled", true);
    private static final ModConfigSpec.IntValue INITIAL_DELAY_MINUTES = BUILDER
            .comment("Online minutes before a player's first tip is shown.")
            .defineInRange("initialDelayMinutes", 5, 0, 120);
    private static final ModConfigSpec.IntValue INTERVAL_MINUTES = BUILDER
            .comment("Online minutes between tips. Due tips wait until combat and captivity end.")
            .defineInRange("intervalMinutes", 30, 15, 120);
    private static final ModConfigSpec.IntValue HISTORY_SIZE = BUILDER
            .comment("Number of recently displayed tips retained per player.")
            .defineInRange("historySize", 20, 5, 100);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private TipConfig() { }

    public static boolean enabled() { return ENABLED.getAsBoolean(); }
    public static int initialDelaySeconds() { return INITIAL_DELAY_MINUTES.getAsInt() * 60; }
    public static int intervalSeconds() { return INTERVAL_MINUTES.getAsInt() * 60; }
    public static int historySize() { return HISTORY_SIZE.getAsInt(); }
}

package com.ruskserver.moveearth_addtional.territory.raid;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TerritoryRaidConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue REFRESH_INTERVAL = BUILDER
            .comment("Ticks between mobile raid emitter updates.")
            .defineInRange("refreshIntervalTicks", 10, 1, 200);
    public static final ModConfigSpec.IntValue STALE_AFTER = BUILDER
            .comment("Ticks after the last update before an emitter is discarded.")
            .defineInRange("staleAfterTicks", 40, 2, 1200);
    public static final ModConfigSpec.DoubleValue RADIUS = BUILDER
            .comment("Radius in blocks of a mobile raid emitter.")
            .defineInRange("radius", 64.0D, 8.0D, 512.0D);
    public static final ModConfigSpec.DoubleValue STRESS_IMPACT = BUILDER
            .comment("Raid block stress impact at 1 RPM.")
            .defineInRange("stressImpact", 64.0D, 1.0D, 16_384.0D);
    public static final ModConfigSpec.DoubleValue MINIMUM_SPEED = BUILDER
            .comment("Minimum absolute RPM required to activate a raid emitter.")
            .defineInRange("minimumSpeed", 16.0D, 1.0D, 256.0D);
    public static final ModConfigSpec.DoubleValue STRENGTH_SCALE = BUILDER
            .comment("Multiplier in raid strength = multiplier * sqrt(valid used SU).")
            .defineInRange("strengthScale", 2.0D, 0.0D, 100.0D);
    public static final ModConfigSpec.DoubleValue MAX_STRENGTH = BUILDER
            .comment("Maximum suppression strength contributed by one raid block.")
            .defineInRange("maxStrength", 128.0D, 0.0D, 1024.0D);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private TerritoryRaidConfig() {
    }
}

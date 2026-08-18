package com.ruskserver.moveearth_addtional.territory.create;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TerritoryCreateConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue SCAN_RADIUS = BUILDER
            .comment("Horizontal distance in blocks around a territory core where generators are counted.")
            .defineInRange("scanRadius", CreateStressBalance.SCAN_RADIUS_BLOCKS, 16, 512);
    public static final ModConfigSpec.IntValue REFRESH_INTERVAL = BUILDER
            .comment("Ticks between Create stress snapshots.")
            .defineInRange("refreshIntervalTicks", CreateStressBalance.REFRESH_INTERVAL_TICKS, 20, 1200);
    public static final ModConfigSpec.DoubleValue MAX_COUNTED_STRESS = BUILDER
            .comment("Per-owner used SU cap applied before conversion to industrial score.")
            .defineInRange("maxCountedStress", CreateStressBalance.MAX_COUNTED_STRESS, 1.0D, 16_777_216.0D);
    public static final ModConfigSpec.DoubleValue SCORE_SCALE = BUILDER
            .comment("Multiplier in score = multiplier * sqrt(used SU).")
            .defineInRange("scoreScale", CreateStressBalance.SCORE_SCALE, 0.0D, 100.0D);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private TerritoryCreateConfig() {
    }
}

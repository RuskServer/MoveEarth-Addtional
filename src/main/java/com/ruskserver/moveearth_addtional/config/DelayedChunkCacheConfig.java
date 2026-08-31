package com.ruskserver.moveearth_addtional.config;

import com.ruskserver.moveearth_addtional.handler.dcc.DelayedChunkTrackingView;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side settings for the Delayed Chunk Cache (DCC).
 */
public final class DelayedChunkCacheConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.IntValue SIZE_LIMIT;
    private static final ModConfigSpec.IntValue EXTRA_DISTANCE;
    private static final ModConfigSpec.IntValue TIMEOUT_SECONDS;
    private static final ModConfigSpec.IntValue CHECK_INTERVAL_TICKS;

    public static final ModConfigSpec SPEC;
    private static volatile DelayedChunkTrackingView.Settings cachedSettings;

    static {
        BUILDER.push("delayedChunkCache");

        ENABLED = BUILDER
                .comment("Keep recently departed chunks in the client cache to avoid resending full chunk data.")
                .define("enabled", true);

        SIZE_LIMIT = BUILDER
                .comment("Maximum number of delayed chunks retained per player.")
                .defineInRange("sizeLimit", 64, 1, 4096);

        EXTRA_DISTANCE = BUILDER
                .comment("Maximum cache distance beyond the player's normal view distance, in chunks.")
                .defineInRange("extraDistance", 2, 0, 32);

        TIMEOUT_SECONDS = BUILDER
                .comment("Maximum time that a departed chunk remains cached by the client.")
                .defineInRange("timeoutSeconds", 30, 1, 3600);

        CHECK_INTERVAL_TICKS = BUILDER
                .comment("Interval between timeout eviction checks. Distance and capacity are enforced immediately.")
                .defineInRange("checkIntervalTicks", 10, 1, 200);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private DelayedChunkCacheConfig() {
    }

    public static boolean enabled() {
        return ENABLED.getAsBoolean();
    }

    public static DelayedChunkTrackingView.Settings settings() {
        int sizeLimit = SIZE_LIMIT.getAsInt();
        int extraDistance = EXTRA_DISTANCE.getAsInt();
        long timeoutTicks = TIMEOUT_SECONDS.getAsInt() * 20L;
        int checkIntervalTicks = CHECK_INTERVAL_TICKS.getAsInt();

        DelayedChunkTrackingView.Settings current = cachedSettings;
        if (current == null
                || current.sizeLimit() != sizeLimit
                || current.extraDistance() != extraDistance
                || current.timeoutTicks() != timeoutTicks
                || current.checkIntervalTicks() != checkIntervalTicks) {
            current = new DelayedChunkTrackingView.Settings(
                    sizeLimit,
                    extraDistance,
                    timeoutTicks,
                    checkIntervalTicks
            );
            cachedSettings = current;
        }
        return current;
    }
}

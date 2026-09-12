package com.ruskserver.moveearth_addtional.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Non-synced startup settings for the dedicated-server JDA bot. */
public final class DiscordBotConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.ConfigValue<String> BOT_TOKEN;
    private static final ModConfigSpec.IntValue LINK_CODE_EXPIRY_SECONDS;
    private static final ModConfigSpec.IntValue DELIVERY_INTERVAL_TICKS;
    private static final ModConfigSpec.IntValue DELIVERY_BATCH_SIZE;
    private static final ModConfigSpec.IntValue OUTBOX_RETENTION_HOURS;
    private static final ModConfigSpec.IntValue DEDUPLICATION_WINDOW_SECONDS;
    private static final ModConfigSpec.IntValue AUDIT_LOG_ENTRIES;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment("Dedicated-server-only Discord bot settings. Never commit this file.")
                .push("discordBot");
        ENABLED = BUILDER.comment("Start the embedded JDA bot when a dedicated server starts.")
                .define("enabled", false);
        BOT_TOKEN = BUILDER.comment("Discord bot token. This startup config is never synchronized to clients.")
                .define("botToken", "", value -> value instanceof String);
        LINK_CODE_EXPIRY_SECONDS = BUILDER.defineInRange("linkCodeExpirySeconds", 600, 60, 3600);
        DELIVERY_INTERVAL_TICKS = BUILDER.defineInRange("deliveryIntervalTicks", 20, 1, 1200);
        DELIVERY_BATCH_SIZE = BUILDER.defineInRange("deliveryBatchSize", 25, 1, 100);
        OUTBOX_RETENTION_HOURS = BUILDER.comment("Discard undeliverable notifications older than this.")
                .defineInRange("outboxRetentionHours", 72, 1, 720);
        DEDUPLICATION_WINDOW_SECONDS = BUILDER.comment("Suppress identical queued/delivered events in this window.")
                .defineInRange("deduplicationWindowSeconds", 900, 0, 86400);
        AUDIT_LOG_ENTRIES = BUILDER.comment("Number of Discord operation/delivery audit entries retained in world data.")
                .defineInRange("auditLogEntries", 256, 32, 2048);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private DiscordBotConfig() { }

    public static boolean enabled() { return ENABLED.getAsBoolean(); }
    public static String botToken() { return BOT_TOKEN.get(); }
    public static int linkCodeExpirySeconds() { return LINK_CODE_EXPIRY_SECONDS.getAsInt(); }
    public static int deliveryIntervalTicks() { return DELIVERY_INTERVAL_TICKS.getAsInt(); }
    public static int deliveryBatchSize() { return DELIVERY_BATCH_SIZE.getAsInt(); }
    public static int outboxRetentionHours() { return OUTBOX_RETENTION_HOURS.getAsInt(); }
    public static int deduplicationWindowSeconds() { return DEDUPLICATION_WINDOW_SECONDS.getAsInt(); }
    public static int auditLogEntries() { return AUDIT_LOG_ENTRIES.getAsInt(); }
}

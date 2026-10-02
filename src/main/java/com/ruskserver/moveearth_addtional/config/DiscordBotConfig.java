package com.ruskserver.moveearth_addtional.config;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;

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

    /**
     * Keeps the file holding the bot token readable by the server's own user only
     * (600), as the operations guide promises. Runs on every load and reload,
     * since a rewritten file comes back with the default permissions.
     */
    public static void restrictOwnerAccess(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        Path file = event.getConfig().getFullPath();
        if (file == null || !Files.exists(file)) return;
        ownerOnly(file);
        // FML keeps a .bak copy when it corrects a broken file; it holds the token too.
        Path directory = file.getParent();
        String name = file.getFileName().toString();
        String stem = name.endsWith(".toml") ? name.substring(0, name.length() - ".toml".length()) : name;
        if (directory == null) return;
        try (var backups = Files.newDirectoryStream(directory, stem + "*.bak")) {
            for (Path backup : backups) ownerOnly(backup);
        } catch (Exception exception) {
            Moveearth_addtional.LOGGER.warn("Could not check {} for config backups: {}", directory, exception.getMessage());
        }
    }

    private static void ownerOnly(Path file) {
        try {
            Files.setPosixFilePermissions(file, EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Windows and some mounted filesystems do not expose POSIX permissions.
        } catch (Exception exception) {
            Moveearth_addtional.LOGGER.warn("Could not restrict permissions on {}: {}", file, exception.getMessage());
        }
    }
}

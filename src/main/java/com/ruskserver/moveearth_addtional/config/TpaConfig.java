package com.ruskserver.moveearth_addtional.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side balancing settings for player-requested teleports.
 */
public final class TpaConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.IntValue TRAVELER_COOLDOWN_MINUTES;
    private static final ModConfigSpec.IntValue HOST_COOLDOWN_MINUTES;
    private static final ModConfigSpec.IntValue BEGINNER_HOST_COOLDOWN_MINUTES;
    private static final ModConfigSpec.IntValue BEGINNER_PLAY_TIME_HOURS;
    private static final ModConfigSpec.IntValue BEGINNER_FREE_TELEPORTS;
    private static final ModConfigSpec.IntValue WARMUP_SECONDS;
    private static final ModConfigSpec.IntValue COMBAT_LOCK_SECONDS;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("tpa");

        TRAVELER_COOLDOWN_MINUTES = BUILDER
                .comment("Cooldown applied to a normally teleported player after a successful TPA.")
                .defineInRange("travelerCooldownMinutes", 60, 0, 1440);

        HOST_COOLDOWN_MINUTES = BUILDER
                .comment("Cooldown before a destination host can receive another normal TPA.")
                .defineInRange("hostCooldownMinutes", 15, 0, 1440);

        BEGINNER_HOST_COOLDOWN_MINUTES = BUILDER
                .comment("Destination-host cooldown for a beginner allowance teleport.")
                .defineInRange("beginnerHostCooldownMinutes", 3, 0, 1440);

        BEGINNER_PLAY_TIME_HOURS = BUILDER
                .comment("Players below this total play time may use the beginner TPA allowance.")
                .defineInRange("beginnerPlayTimeHours", 6, 0, 1000);

        BEGINNER_FREE_TELEPORTS = BUILDER
                .comment("Lifetime beginner teleports that do not consume daily uses or traveler cooldown.")
                .defineInRange("beginnerFreeTeleports", 3, 0, 100);

        WARMUP_SECONDS = BUILDER
                .comment("Both players must remain still and safe for this long before teleporting.")
                .defineInRange("warmupSeconds", 20, 0, 300);

        COMBAT_LOCK_SECONDS = BUILDER
                .comment("Time after dealing or receiving damage during which TPA cannot be used.")
                .defineInRange("combatLockSeconds", 15, 0, 300);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private TpaConfig() {
    }

    public static int travelerCooldownMinutes() {
        return TRAVELER_COOLDOWN_MINUTES.getAsInt();
    }

    public static int hostCooldownMinutes() {
        return HOST_COOLDOWN_MINUTES.getAsInt();
    }

    public static int beginnerHostCooldownMinutes() {
        return BEGINNER_HOST_COOLDOWN_MINUTES.getAsInt();
    }

    public static int beginnerPlayTimeHours() {
        return BEGINNER_PLAY_TIME_HOURS.getAsInt();
    }

    public static int beginnerFreeTeleports() {
        return BEGINNER_FREE_TELEPORTS.getAsInt();
    }

    public static int warmupSeconds() {
        return WARMUP_SECONDS.getAsInt();
    }

    public static int combatLockSeconds() {
        return COMBAT_LOCK_SECONDS.getAsInt();
    }
}

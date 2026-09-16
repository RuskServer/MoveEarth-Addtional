package com.ruskserver.moveearth_addtional.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned rollout and balance settings for post-war recovery and dispatch contracts. */
public final class RecoveryDispatchConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.BooleanValue RECOVERY_ENABLED;
    private static final ModConfigSpec.IntValue ELIGIBILITY_OPEN_HOURS;
    private static final ModConfigSpec.IntValue REPEAT_COOLDOWN_OPEN_HOURS;
    private static final ModConfigSpec.IntValue RESEAL_OPEN_MINUTES;
    private static final ModConfigSpec.IntValue WALL_TARGET_CAP;
    private static final ModConfigSpec.BooleanValue FUND_ENABLED;
    private static final ModConfigSpec.LongValue FUND_EPISODE_CAP;
    private static final ModConfigSpec.LongValue FUND_NATION_WINDOW_CAP;
    private static final ModConfigSpec.LongValue FUND_GLOBAL_WINDOW_CAP;
    private static final ModConfigSpec.BooleanValue DISPATCH_ENABLED;
    private static final ModConfigSpec.BooleanValue DISPATCH_MONEY_ENABLED;
    private static final ModConfigSpec.IntValue DISPATCH_MAX_OPEN_MINUTES;
    private static final ModConfigSpec.IntValue DISPATCH_BATTLEFIELD_RADIUS;
    private static final ModConfigSpec.BooleanValue DISPATCH_SUBSIDY_APPROVAL;
    private static final ModConfigSpec.BooleanValue RIVAL_ENABLED;
    private static final ModConfigSpec.IntValue HISTORY_RETENTION;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("recovery");
        RECOVERY_ENABLED = BUILDER.define("enabled", true);
        ELIGIBILITY_OPEN_HOURS = BUILDER.defineInRange("eligibilityOpenHours", 42, 1, 720);
        REPEAT_COOLDOWN_OPEN_HOURS = BUILDER.defineInRange("repeatCooldownOpenHours", 84, 0, 2160);
        RESEAL_OPEN_MINUTES = BUILDER.defineInRange("resealOpenMinutes", 10, 1, 1440);
        WALL_TARGET_CAP = BUILDER.defineInRange("wallTargetCap", 4096, 0, 65536);
        BUILDER.pop();

        BUILDER.push("fund");
        FUND_ENABLED = BUILDER.define("enabled", false);
        FUND_EPISODE_CAP = BUILDER.defineInRange("lossCapPerEpisode", 1000L, 0L, 1_000_000_000L);
        FUND_NATION_WINDOW_CAP = BUILDER.defineInRange("nationWindowCap", 2000L, 0L, 1_000_000_000L);
        FUND_GLOBAL_WINDOW_CAP = BUILDER.defineInRange("globalWindowCap", 10000L, 0L, 1_000_000_000L);
        BUILDER.pop();

        BUILDER.push("dispatch");
        DISPATCH_ENABLED = BUILDER.define("enabled", false);
        DISPATCH_MONEY_ENABLED = BUILDER.define("moneyEnabled", false);
        DISPATCH_MAX_OPEN_MINUTES = BUILDER.defineInRange("maxOpenMinutes", 60, 1, 1440);
        DISPATCH_BATTLEFIELD_RADIUS = BUILDER.defineInRange("battlefieldRadius", 192, 16, 2048);
        DISPATCH_SUBSIDY_APPROVAL = BUILDER.define("requireSubsidyAdminApproval", true);
        BUILDER.pop();

        RIVAL_ENABLED = BUILDER.define("rival.enabled", true);
        HISTORY_RETENTION = BUILDER.defineInRange("history.retentionEntries", 512, 16, 10000);
        SPEC = BUILDER.build();
    }

    private RecoveryDispatchConfig() { }

    public static boolean recoveryEnabled() { return RECOVERY_ENABLED.getAsBoolean(); }
    public static long eligibilityOpenTicks() { return ELIGIBILITY_OPEN_HOURS.getAsInt() * 72_000L; }
    public static long repeatCooldownOpenTicks() { return REPEAT_COOLDOWN_OPEN_HOURS.getAsInt() * 72_000L; }
    public static long resealOpenTicks() { return RESEAL_OPEN_MINUTES.getAsInt() * 1_200L; }
    public static int wallTargetCap() { return WALL_TARGET_CAP.getAsInt(); }
    public static boolean fundEnabled() { return FUND_ENABLED.getAsBoolean(); }
    public static long fundEpisodeCap() { return FUND_EPISODE_CAP.getAsLong(); }
    public static long fundNationWindowCap() { return FUND_NATION_WINDOW_CAP.getAsLong(); }
    public static long fundGlobalWindowCap() { return FUND_GLOBAL_WINDOW_CAP.getAsLong(); }
    public static boolean dispatchEnabled() { return DISPATCH_ENABLED.getAsBoolean(); }
    public static boolean dispatchMoneyEnabled() { return DISPATCH_MONEY_ENABLED.getAsBoolean(); }
    public static long dispatchMaxOpenTicks() { return DISPATCH_MAX_OPEN_MINUTES.getAsInt() * 1_200L; }
    public static int dispatchBattlefieldRadius() { return DISPATCH_BATTLEFIELD_RADIUS.getAsInt(); }
    public static boolean dispatchSubsidyApprovalRequired() { return DISPATCH_SUBSIDY_APPROVAL.getAsBoolean(); }
    public static boolean rivalEnabled() { return RIVAL_ENABLED.getAsBoolean(); }
    public static int historyRetention() { return HISTORY_RETENTION.getAsInt(); }
}

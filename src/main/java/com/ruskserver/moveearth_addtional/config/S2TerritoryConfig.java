package com.ruskserver.moveearth_addtional.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned balance values for the S2 territory and reinforcement systems. */
public final class S2TerritoryConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.IntValue CHUNKS_PER_COIN;
    private static final ModConfigSpec.LongValue OUTPOST_BASE_COST;
    private static final ModConfigSpec.IntValue UPKEEP_CYCLE_HOURS;
    private static final ModConfigSpec.IntValue UPKEEP_RETRY_MINUTES;
    private static final ModConfigSpec.IntValue UPKEEP_WEAKEN_AFTER_HOURS;
    private static final ModConfigSpec.IntValue UPKEEP_DISABLE_AFTER_HOURS;
    private static final ModConfigSpec.IntValue CAPITAL_CORE_HEALTH;
    private static final ModConfigSpec.IntValue OUTPOST_CORE_HEALTH;
    private static final ModConfigSpec.IntValue CORE_REGEN_DELAY_SECONDS;
    private static final ModConfigSpec.IntValue CORE_REGEN_INTERVAL_SECONDS;
    private static final ModConfigSpec.DoubleValue CORE_REGEN_PERCENT;
    private static final ModConfigSpec.DoubleValue OVERDUE_DAMAGE_MULTIPLIER;
    private static final ModConfigSpec.IntValue CBC_SHOT_DAMAGE;
    private static final ModConfigSpec.IntValue CBC_AP_SHOT_DAMAGE;
    private static final ModConfigSpec.IntValue CBC_HE_SHELL_DAMAGE;
    private static final ModConfigSpec.IntValue CBC_AP_SHELL_DAMAGE;
    private static final ModConfigSpec.IntValue CBC_MORTAR_DAMAGE;
    private static final ModConfigSpec.IntValue CBC_FRAGMENTATION_DAMAGE;
    private static final ModConfigSpec.IntValue CBC_AUTOCANNON_DAMAGE;
    private static final ModConfigSpec.IntValue CBC_UTILITY_DAMAGE;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("s2Territory");
        BUILDER.push("upkeep");
        CHUNKS_PER_COIN = BUILDER.defineInRange("chunksPerGoldCoin", 8, 1, 4096);
        OUTPOST_BASE_COST = BUILDER.defineInRange("outpostBaseGoldCost", 8L, 0L, 1_000_000L);
        UPKEEP_CYCLE_HOURS = BUILDER.defineInRange("cycleHours", 24, 1, 720);
        UPKEEP_RETRY_MINUTES = BUILDER.defineInRange("retryMinutes", 60, 1, 1440);
        UPKEEP_WEAKEN_AFTER_HOURS = BUILDER
                .comment("Hours overdue before reinforcement takes increased siege damage.")
                .defineInRange("weakenAfterHours", 24, 0, 2160);
        UPKEEP_DISABLE_AFTER_HOURS = BUILDER
                .comment("Hours overdue before reinforcement protection and Bastion stop working.")
                .defineInRange("disableAfterHours", 72, 1, 4320);
        OVERDUE_DAMAGE_MULTIPLIER = BUILDER.defineInRange("weakenedSiegeDamageMultiplier", 2.0D, 1.0D, 100.0D);
        BUILDER.pop();

        BUILDER.push("core");
        CAPITAL_CORE_HEALTH = BUILDER.defineInRange("capitalMaxHealth", 2000, 1, 10_000_000);
        OUTPOST_CORE_HEALTH = BUILDER.defineInRange("outpostMaxHealth", 1000, 1, 10_000_000);
        CORE_REGEN_DELAY_SECONDS = BUILDER.defineInRange("regenDelaySeconds", 300, 0, 86400);
        CORE_REGEN_INTERVAL_SECONDS = BUILDER.defineInRange("regenIntervalSeconds", 60, 1, 3600);
        CORE_REGEN_PERCENT = BUILDER.defineInRange("regenPercentPerInterval", 1.0D, 0.0D, 100.0D);
        BUILDER.pop();

        BUILDER.push("cbcDamage");
        CBC_SHOT_DAMAGE = BUILDER.defineInRange("solidShot", 24, 0, 100000);
        CBC_AP_SHOT_DAMAGE = BUILDER.defineInRange("armorPiercingShot", 48, 0, 100000);
        CBC_HE_SHELL_DAMAGE = BUILDER.defineInRange("highExplosiveShell", 20, 0, 100000);
        CBC_AP_SHELL_DAMAGE = BUILDER.defineInRange("armorPiercingShell", 40, 0, 100000);
        CBC_MORTAR_DAMAGE = BUILDER.defineInRange("mortar", 18, 0, 100000);
        CBC_FRAGMENTATION_DAMAGE = BUILDER.defineInRange("fragmentation", 8, 0, 100000);
        CBC_AUTOCANNON_DAMAGE = BUILDER.defineInRange("autocannon", 4, 0, 100000);
        CBC_UTILITY_DAMAGE = BUILDER.defineInRange("utilityProjectile", 1, 0, 100000);
        BUILDER.pop();
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private S2TerritoryConfig() { }

    public static int chunksPerCoin() { return CHUNKS_PER_COIN.getAsInt(); }
    public static long outpostBaseCost() { return OUTPOST_BASE_COST.getAsLong(); }
    public static long upkeepCycleMillis() { return UPKEEP_CYCLE_HOURS.getAsInt() * 3_600_000L; }
    public static long upkeepRetryMillis() { return UPKEEP_RETRY_MINUTES.getAsInt() * 60_000L; }
    public static long upkeepWeakenMillis() { return UPKEEP_WEAKEN_AFTER_HOURS.getAsInt() * 3_600_000L; }
    public static long upkeepDisableMillis() { return UPKEEP_DISABLE_AFTER_HOURS.getAsInt() * 3_600_000L; }
    public static double overdueDamageMultiplier() { return OVERDUE_DAMAGE_MULTIPLIER.getAsDouble(); }
    public static int capitalCoreHealth() { return CAPITAL_CORE_HEALTH.getAsInt(); }
    public static int outpostCoreHealth() { return OUTPOST_CORE_HEALTH.getAsInt(); }
    public static long coreRegenDelayTicks() { return CORE_REGEN_DELAY_SECONDS.getAsInt() * 20L; }
    public static long coreRegenIntervalTicks() { return CORE_REGEN_INTERVAL_SECONDS.getAsInt() * 20L; }
    public static double coreRegenPercent() { return CORE_REGEN_PERCENT.getAsDouble(); }
    public static int cbcShotDamage() { return CBC_SHOT_DAMAGE.getAsInt(); }
    public static int cbcApShotDamage() { return CBC_AP_SHOT_DAMAGE.getAsInt(); }
    public static int cbcHeShellDamage() { return CBC_HE_SHELL_DAMAGE.getAsInt(); }
    public static int cbcApShellDamage() { return CBC_AP_SHELL_DAMAGE.getAsInt(); }
    public static int cbcMortarDamage() { return CBC_MORTAR_DAMAGE.getAsInt(); }
    public static int cbcFragmentationDamage() { return CBC_FRAGMENTATION_DAMAGE.getAsInt(); }
    public static int cbcAutocannonDamage() { return CBC_AUTOCANNON_DAMAGE.getAsInt(); }
    public static int cbcUtilityDamage() { return CBC_UTILITY_DAMAGE.getAsInt(); }
}

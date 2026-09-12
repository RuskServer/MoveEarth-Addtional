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
    private static final ModConfigSpec.IntValue CBC_MACHINE_GUN_DAMAGE;
    private static final ModConfigSpec.IntValue CBC_UTILITY_DAMAGE;
    private static final ModConfigSpec.DoubleValue CBC_CORE_DAMAGE_MULTIPLIER;
    private static final ModConfigSpec.IntValue CBC_PROTECTED_BLAST_RADIUS;
    private static final ModConfigSpec.IntValue SIEGE_INITIAL_LOCK_SECONDS;
    private static final ModConfigSpec.IntValue SIEGE_ROLLING_SECONDS;
    private static final ModConfigSpec.IntValue SIEGE_RETRY_COOLDOWN_SECONDS;
    private static final ModConfigSpec.IntValue SIEGE_DUPLICATE_LOG_SECONDS;
    private static final ModConfigSpec.IntValue SIEGE_POST_FALL_SECONDS;
    private static final ModConfigSpec.IntValue SIEGE_FALL_STAGE_SECONDS;
    private static final ModConfigSpec.IntValue SIEGE_COUNTER_CAPTURE_SECONDS;
    private static final ModConfigSpec.IntValue SIEGE_COUNTER_RADIUS_BLOCKS;
    private static final ModConfigSpec.DoubleValue SIEGE_COUNTER_RECOVERY_PERCENT;
    private static final ModConfigSpec.IntValue SIEGE_SETTLEMENT_TRUCE_SECONDS;
    private static final ModConfigSpec.DoubleValue SIEGE_SETTLEMENT_RECOVERY_PERCENT;
    private static final ModConfigSpec.IntValue PEACE_PROPOSAL_SECONDS;
    private static final ModConfigSpec.IntValue PEACE_TRUCE_SECONDS;
    private static final ModConfigSpec.IntValue VAULT_CHANGE_COOLDOWN_SECONDS;
    private static final ModConfigSpec.IntValue OFFLINE_DEFENSE_GRACE_MINUTES;
    private static final ModConfigSpec.IntValue OFFLINE_DEFENSE_DAMAGE_DIVISOR;
    private static final ModConfigSpec.IntValue LONG_ABSENCE_FULL_DAYS;
    private static final ModConfigSpec.IntValue LONG_ABSENCE_HALF_DAYS;
    private static final ModConfigSpec.IntValue LONG_ABSENCE_QUARTER_DAYS;
    private static final ModConfigSpec.IntValue LONG_ABSENCE_DISABLE_DAYS;

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
        CBC_SHOT_DAMAGE = BUILDER.defineInRange("solidShotImpact", 48, 0, 100000);
        CBC_AP_SHOT_DAMAGE = BUILDER.defineInRange("armorPiercingShotImpact", 96, 0, 100000);
        CBC_HE_SHELL_DAMAGE = BUILDER.defineInRange("highExplosiveShellImpact", 40, 0, 100000);
        CBC_AP_SHELL_DAMAGE = BUILDER.defineInRange("armorPiercingShellImpact", 72, 0, 100000);
        CBC_MORTAR_DAMAGE = BUILDER.defineInRange("mortarImpact", 30, 0, 100000);
        CBC_FRAGMENTATION_DAMAGE = BUILDER.comment(
                "Damage per individual fragment hit. Fragment bursts are never treated as repeated area blasts.")
                .defineInRange("fragmentHit", 1, 0, 100000);
        CBC_AUTOCANNON_DAMAGE = BUILDER.comment(
                "Damage per AP autocannon hit. Rapid-fire ammunition cannot damage territory cores.")
                .defineInRange("apAutocannonHit", 3, 0, 100000);
        CBC_MACHINE_GUN_DAMAGE = BUILDER.comment(
                "Damage per machine-gun bullet hit. Machine-gun bullets cannot damage territory cores.")
                .defineInRange("machineGunHit", 1, 0, 100000);
        CBC_UTILITY_DAMAGE = BUILDER.defineInRange("utilityProjectile", 1, 0, 100000);
        CBC_CORE_DAMAGE_MULTIPLIER = BUILDER.comment(
                "Multiplier applied only when a heavy CBC projectile directly damages an exposed territory core.")
                .defineInRange("coreDamageMultiplier", 1.0D, 0.0D, 100.0D);
        CBC_PROTECTED_BLAST_RADIUS = BUILDER.comment(
                "Radius scanned before CBC explosive terrain transforms; protected hits are handled by MoveEarth.")
                .defineInRange("protectedBlastRadiusBlocks", 8, 1, 32);
        BUILDER.pop();

        BUILDER.push("siege");
        SIEGE_INITIAL_LOCK_SECONDS = BUILDER.defineInRange("initialLockSeconds", 300, 1, 86400);
        SIEGE_ROLLING_SECONDS = BUILDER.defineInRange("rollingSeconds", 1800, 1, 604800);
        SIEGE_RETRY_COOLDOWN_SECONDS = BUILDER.defineInRange("retryCooldownSeconds", 3600, 0, 604800);
        SIEGE_DUPLICATE_LOG_SECONDS = BUILDER.defineInRange("duplicateLogCooldownSeconds", 60, 0, 3600);
        SIEGE_POST_FALL_SECONDS = BUILDER.defineInRange("postFallSeconds", 1800, 60, 604800);
        SIEGE_FALL_STAGE_SECONDS = BUILDER.defineInRange("fallStageSeconds", 600, 1, 604800);
        SIEGE_COUNTER_CAPTURE_SECONDS = BUILDER.defineInRange("counterCaptureSeconds", 180, 1, 86400);
        SIEGE_COUNTER_RADIUS_BLOCKS = BUILDER.defineInRange("counterCaptureRadiusBlocks", 12, 1, 128);
        SIEGE_COUNTER_RECOVERY_PERCENT = BUILDER.defineInRange("counterRecoveryPercent", 25.0D, 1.0D, 100.0D);
        SIEGE_SETTLEMENT_TRUCE_SECONDS = BUILDER.comment(
                "Server-open seconds before a rebuilt capital or occupied outpost can be attacked again.")
                .defineInRange("settlementTruceSeconds", 21600, 0, 2592000);
        SIEGE_SETTLEMENT_RECOVERY_PERCENT = BUILDER.comment(
                "Core health restored when a capital enters rebuilding or an outpost is occupied.")
                .defineInRange("settlementRecoveryPercent", 25.0D, 1.0D, 100.0D);
        PEACE_PROPOSAL_SECONDS = BUILDER.defineInRange("peaceProposalSeconds", 600, 30, 86400);
        PEACE_TRUCE_SECONDS = BUILDER.defineInRange("peaceTruceSeconds", 21600, 0, 2592000);
        VAULT_CHANGE_COOLDOWN_SECONDS = BUILDER.comment(
                "Server-open seconds before an already configured vault chunk can be changed again.")
                .defineInRange("vaultChangeCooldownSeconds", 3600, 0, 2592000);
        BUILDER.pop();

        BUILDER.push("offlineDefense");
        OFFLINE_DEFENSE_GRACE_MINUTES = BUILDER.comment(
                "Minutes after the final nation member logs out before offline defense activates.")
                .defineInRange("graceMinutes", 20, 0, 10080);
        OFFLINE_DEFENSE_DAMAGE_DIVISOR = BUILDER.comment(
                "Incoming reinforcement and core damage is divided by this value while offline defense is active.")
                .defineInRange("damageDivisor", 3, 1, 100);
        BUILDER.pop();

        BUILDER.push("longAbsence");
        LONG_ABSENCE_FULL_DAYS = BUILDER.defineInRange("fullStrengthDays", 7, 0, 3650);
        LONG_ABSENCE_HALF_DAYS = BUILDER.defineInRange("halfStrengthAfterDays", 14, 0, 3650);
        LONG_ABSENCE_QUARTER_DAYS = BUILDER.defineInRange("quarterStrengthAfterDays", 21, 0, 3650);
        LONG_ABSENCE_DISABLE_DAYS = BUILDER.defineInRange("disableProtectionAfterDays", 30, 1, 3650);
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
    public static int cbcMachineGunDamage() { return CBC_MACHINE_GUN_DAMAGE.getAsInt(); }
    public static int cbcUtilityDamage() { return CBC_UTILITY_DAMAGE.getAsInt(); }
    public static double cbcCoreDamageMultiplier() { return CBC_CORE_DAMAGE_MULTIPLIER.getAsDouble(); }
    public static int cbcProtectedBlastRadius() { return CBC_PROTECTED_BLAST_RADIUS.getAsInt(); }
    public static long siegeInitialLockTicks() { return SIEGE_INITIAL_LOCK_SECONDS.getAsInt() * 20L; }
    public static long siegeRollingTicks() { return SIEGE_ROLLING_SECONDS.getAsInt() * 20L; }
    public static long siegeRetryCooldownTicks() { return SIEGE_RETRY_COOLDOWN_SECONDS.getAsInt() * 20L; }
    public static long siegeDuplicateLogTicks() { return SIEGE_DUPLICATE_LOG_SECONDS.getAsInt() * 20L; }
    public static long siegePostFallTicks() { return SIEGE_POST_FALL_SECONDS.getAsInt() * 20L; }
    public static long siegeFallStageTicks() { return SIEGE_FALL_STAGE_SECONDS.getAsInt() * 20L; }
    public static long siegeCounterCaptureTicks() { return SIEGE_COUNTER_CAPTURE_SECONDS.getAsInt() * 20L; }
    public static int siegeCounterRadiusBlocks() { return SIEGE_COUNTER_RADIUS_BLOCKS.getAsInt(); }
    public static double siegeCounterRecoveryPercent() { return SIEGE_COUNTER_RECOVERY_PERCENT.getAsDouble(); }
    public static long siegeSettlementTruceTicks() { return SIEGE_SETTLEMENT_TRUCE_SECONDS.getAsInt() * 20L; }
    public static double siegeSettlementRecoveryPercent() { return SIEGE_SETTLEMENT_RECOVERY_PERCENT.getAsDouble(); }
    public static long peaceProposalTicks() { return PEACE_PROPOSAL_SECONDS.getAsInt() * 20L; }
    public static long peaceTruceTicks() { return PEACE_TRUCE_SECONDS.getAsInt() * 20L; }
    public static long vaultChangeCooldownTicks() { return VAULT_CHANGE_COOLDOWN_SECONDS.getAsInt() * 20L; }
    public static long offlineDefenseGraceMillis() { return OFFLINE_DEFENSE_GRACE_MINUTES.getAsInt() * 60_000L; }
    public static int offlineDefenseDamageDivisor() { return OFFLINE_DEFENSE_DAMAGE_DIVISOR.getAsInt(); }
    public static long longAbsenceFullStrengthMillis() { return daysToMillis(LONG_ABSENCE_FULL_DAYS.getAsInt()); }
    public static long longAbsenceHalfStrengthMillis() { return daysToMillis(LONG_ABSENCE_HALF_DAYS.getAsInt()); }
    public static long longAbsenceQuarterStrengthMillis() { return daysToMillis(LONG_ABSENCE_QUARTER_DAYS.getAsInt()); }
    public static long longAbsenceDisableMillis() { return daysToMillis(LONG_ABSENCE_DISABLE_DAYS.getAsInt()); }

    private static long daysToMillis(int days) { return days * 86_400_000L; }
}

package com.ruskserver.moveearth_addtional.nether;

import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code serverconfig/moveearth/nether-gate.toml}: the gate generator, its fight and the blaze rod refiner. */
public final class NetherGateConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.DoubleValue GATE_STRESS;
    private static final ModConfigSpec.IntValue GATE_MIN_RPM;
    private static final ModConfigSpec.DoubleValue GATE_CHARGE_MINUTES;
    private static final ModConfigSpec.IntValue GATE_OPEN_RADIUS;
    private static final ModConfigSpec.IntValue FIGHT_SECONDS;
    private static final ModConfigSpec.DoubleValue FIGHT_MOB_COUNT;
    private static final ModConfigSpec.DoubleValue FIGHT_HEALTH;
    private static final ModConfigSpec.DoubleValue FIGHT_DAMAGE;
    private static final ModConfigSpec.IntValue FIGHT_LEASH;
    private static final ModConfigSpec.IntValue FIGHT_SHARDS;
    private static final ModConfigSpec.IntValue LIMIT_PER_NATION;
    private static final ModConfigSpec.IntValue LIMIT_SERVER;
    private static final ModConfigSpec.IntValue LIMIT_MIN_DISTANCE;
    private static final ModConfigSpec.IntValue LIMIT_DESERTED_SECONDS;
    private static final ModConfigSpec.DoubleValue REFINER_STRESS;
    private static final ModConfigSpec.IntValue REFINER_MIN_RPM;
    private static final ModConfigSpec.DoubleValue REFINER_MINUTES;

    public static final ModConfigSpec SPEC;

    static final double DEFAULT_GATE_STRESS = 64.0D;
    static final double DEFAULT_REFINER_STRESS = 64.0D;
    static final int DEFAULT_GATE_MIN_RPM = 128;
    static final double DEFAULT_CHARGE_MINUTES = 5.0D;
    static final int DEFAULT_FIGHT_SECONDS = 300;
    static final int DEFAULT_SHARDS = 4;
    static final int DEFAULT_REFINER_MIN_RPM = 48;
    static final double DEFAULT_REFINER_MINUTES = 3.0D;

    static {
        BUILDER.push("gateGenerator");
        GATE_STRESS = BUILDER.comment("Stress impact per RPM while the generator turns.")
                .defineInRange("stressImpact", DEFAULT_GATE_STRESS, 0.0D, 1024.0D);
        GATE_MIN_RPM = BUILDER.comment("The generator charges only at or above this speed.")
                .defineInRange("minimumRpm", DEFAULT_GATE_MIN_RPM, 1, 256);
        GATE_CHARGE_MINUTES = BUILDER.comment("Minutes to charge fully at 256 RPM; slower speeds take proportionally longer.")
                .defineInRange("chargeMinutesAt256Rpm", DEFAULT_CHARGE_MINUTES, 0.1D, 600.0D);
        GATE_OPEN_RADIUS = BUILDER.comment("A charged gate opens only while a member of its nation is this close.")
                .defineInRange("openRadius", 16, 4, 64);
        BUILDER.pop();

        BUILDER.push("fight");
        FIGHT_SECONDS = BUILDER.comment("Time to clear every wave. When it runs out the gate closes, the remaining",
                        "enemies vanish and no shards drop.")
                .defineInRange("timeLimitSeconds", DEFAULT_FIGHT_SECONDS, 30, 3600);
        FIGHT_MOB_COUNT = BUILDER.comment("Multiplier on the number of enemies in each wave.")
                .defineInRange("enemyCountMultiplier", 1.0D, 0.1D, 10.0D);
        FIGHT_HEALTH = BUILDER.comment("Multiplier on the enemies' maximum health.")
                .defineInRange("healthMultiplier", 2.0D, 0.1D, 20.0D);
        FIGHT_DAMAGE = BUILDER.comment("Multiplier on the enemies' melee damage.")
                .defineInRange("damageMultiplier", 1.5D, 0.1D, 20.0D);
        FIGHT_LEASH = BUILDER.comment("Enemies that stray further than this from the generator are pulled back.")
                .defineInRange("leashRadius", 24, 8, 64);
        FIGHT_SHARDS = BUILDER.comment("Nether shards dropped on the generator when every wave is cleared in time.")
                .defineInRange("shardsOnSuccess", DEFAULT_SHARDS, 0, 64);
        BUILDER.pop();

        BUILDER.push("limits");
        LIMIT_PER_NATION = BUILDER.comment("Gate battles one nation may have running at once. A further charged gate",
                        "waits, fully charged, until one ends.")
                .defineInRange("maxBattlesPerNation", 1, 1, 8);
        LIMIT_SERVER = BUILDER.comment("Gate battles the whole server may have running at once.")
                .defineInRange("maxBattlesServerWide", 4, 1, 64);
        LIMIT_MIN_DISTANCE = BUILDER.comment("A gate does not open within this many blocks (horizontally) of a running",
                        "battle in the same dimension, whichever nation owns it. 0 turns the rule off.")
                .defineInRange("minimumDistanceBetweenBattles", 96, 0, 1024);
        LIMIT_DESERTED_SECONDS = BUILDER.comment("A battle closes without reward once no player has been within 32 blocks",
                        "of the generator for this many seconds.")
                .defineInRange("closeWhenDesertedSeconds", 30, 10, 600);
        BUILDER.pop();

        BUILDER.push("blazeRodRefiner");
        REFINER_STRESS = BUILDER.comment("Stress impact per RPM while the refiner turns.")
                .defineInRange("stressImpact", DEFAULT_REFINER_STRESS, 0.0D, 1024.0D);
        REFINER_MIN_RPM = BUILDER.comment("The refiner makes progress only at or above this speed.")
                .defineInRange("minimumRpm", DEFAULT_REFINER_MIN_RPM, 1, 256);
        REFINER_MINUTES = BUILDER.comment("Minutes per blaze rod at 256 RPM; slower speeds take proportionally longer",
                        "(3 minutes at 256 RPM is 16 minutes at 48 RPM).")
                .defineInRange("minutesPerRodAt256Rpm", DEFAULT_REFINER_MINUTES, 0.1D, 600.0D);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private NetherGateConfig() { }

    /** Read by Create's item tooltips on the title screen too, before any world config is loaded. */
    public static double gateStressImpact() {
        return SPEC.isLoaded() ? GATE_STRESS.getAsDouble() : DEFAULT_GATE_STRESS;
    }

    public static double refinerStressImpact() {
        return SPEC.isLoaded() ? REFINER_STRESS.getAsDouble() : DEFAULT_REFINER_STRESS;
    }

    /**
     * Values for JEI and goggles, which also run on a client: server configs are
     * synced to it in a world, and before that the defaults stand in.
     */
    public record Display(int gateMinRpm, double chargeMinutes, int fightSeconds, int shards, int waves,
                          double refinerStress, int refinerMinRpm, double refinerMinutes) {
        /** Minutes one rod takes at the refiner's minimum speed. */
        public double refinerMinutesAtMinimum() {
            return refinerMinutes * KineticWork.MAX_RPM / refinerMinRpm;
        }
    }

    public static Display display() {
        boolean loaded = SPEC.isLoaded();
        return new Display(
                loaded ? GATE_MIN_RPM.getAsInt() : DEFAULT_GATE_MIN_RPM,
                loaded ? GATE_CHARGE_MINUTES.getAsDouble() : DEFAULT_CHARGE_MINUTES,
                loaded ? FIGHT_SECONDS.getAsInt() : DEFAULT_FIGHT_SECONDS,
                loaded ? FIGHT_SHARDS.getAsInt() : DEFAULT_SHARDS,
                NetherGateWaves.count(),
                refinerStressImpact(),
                loaded ? REFINER_MIN_RPM.getAsInt() : DEFAULT_REFINER_MIN_RPM,
                loaded ? REFINER_MINUTES.getAsDouble() : DEFAULT_REFINER_MINUTES);
    }

    public static int gateMinRpm() { return GATE_MIN_RPM.getAsInt(); }
    public static long gateChargeWork() { return KineticWork.required(GATE_CHARGE_MINUTES.getAsDouble()); }
    public static int gateOpenRadius() { return GATE_OPEN_RADIUS.getAsInt(); }
    public static int fightSeconds() { return FIGHT_SECONDS.getAsInt(); }
    public static double fightEnemyCount() { return FIGHT_MOB_COUNT.getAsDouble(); }
    public static double fightHealth() { return FIGHT_HEALTH.getAsDouble(); }
    public static double fightDamage() { return FIGHT_DAMAGE.getAsDouble(); }
    public static int fightLeash() { return FIGHT_LEASH.getAsInt(); }
    public static int fightShards() { return FIGHT_SHARDS.getAsInt(); }
    public static int maxBattlesPerNation() { return LIMIT_PER_NATION.getAsInt(); }
    public static int maxBattlesServerWide() { return LIMIT_SERVER.getAsInt(); }
    public static int minimumBattleDistance() { return LIMIT_MIN_DISTANCE.getAsInt(); }
    public static int desertedSeconds() { return LIMIT_DESERTED_SECONDS.getAsInt(); }
    public static int refinerMinRpm() { return REFINER_MIN_RPM.getAsInt(); }
    public static long refinerWork() { return KineticWork.required(REFINER_MINUTES.getAsDouble()); }
}

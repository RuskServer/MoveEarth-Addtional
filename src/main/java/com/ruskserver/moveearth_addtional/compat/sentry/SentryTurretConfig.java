package com.ruskserver.moveearth_addtional.compat.sentry;

import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code serverconfig/moveearth/sentry.toml}: limits on Create: Sentry Mechanical Arm turrets. */
public final class SentryTurretConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.DoubleValue MAX_DEPRESSION;
    private static final ModConfigSpec.DoubleValue STRESS;
    private static final ModConfigSpec.DoubleValue MAX_GUN_DAMAGE;

    public static final ModConfigSpec SPEC;

    static final double DEFAULT_MAX_DEPRESSION = 20.0D;
    static final double DEFAULT_STRESS = 16.0D;
    static final double DEFAULT_MAX_GUN_DAMAGE = 10.0D;

    static {
        BUILDER.push("turret");
        MAX_DEPRESSION = BUILDER.comment("Steepest downward angle, in degrees below horizontal, a turret may aim or fire at.",
                        "The ground closer to a raised turret than this angle allows is left unguarded.")
                .defineInRange("maxDepressionDegrees", DEFAULT_MAX_DEPRESSION, 0.0D, 90.0D);
        STRESS = BUILDER.comment("Stress impact per RPM. The mod's own value is 3.")
                .defineInRange("stressImpact", DEFAULT_STRESS, 0.0D, 1024.0D);
        MAX_GUN_DAMAGE = BUILDER.comment("Highest per-hit damage of a gun a turret accepts. Only semi-automatic guns firing",
                        "one non-explosive projectile per shot are accepted at all.")
                .defineInRange("maxGunDamage", DEFAULT_MAX_GUN_DAMAGE, 0.0D, 1000.0D);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private SentryTurretConfig() { }

    public static double maxDepressionDegrees() {
        return SPEC.isLoaded() ? MAX_DEPRESSION.getAsDouble() : DEFAULT_MAX_DEPRESSION;
    }

    /** Read by Create's item tooltips on the title screen too, before any world config is loaded. */
    public static double stressImpact() {
        return SPEC.isLoaded() ? STRESS.getAsDouble() : DEFAULT_STRESS;
    }

    public static double maxGunDamage() {
        return SPEC.isLoaded() ? MAX_GUN_DAMAGE.getAsDouble() : DEFAULT_MAX_GUN_DAMAGE;
    }
}

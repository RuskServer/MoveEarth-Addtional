package com.ruskserver.moveearth_addtional.s2.reinforcement;

import java.util.Locale;

/** CBC registry-name classification kept independent from the optional CBC binary. */
public final class CbcMunitionDamage {
    public enum Kind {
        SHOT, AP_SHOT, HE_SHELL, AP_SHELL, MORTAR, FRAGMENTATION, AUTOCANNON, MACHINE_GUN, UTILITY
    }

    private CbcMunitionDamage() { }

    public static Kind classify(String entityPath) {
        String id = entityPath == null ? "" : entityPath.toLowerCase(Locale.ROOT);
        return switch (id) {
            case "ap_shot" -> Kind.AP_SHOT;
            case "ap_shell" -> Kind.AP_SHELL;
            case "he_shell" -> Kind.HE_SHELL;
            case "mortar_stone", "drop_mortar_shell" -> Kind.MORTAR;
            case "shrapnel_shell", "shrapnel_burst", "bag_of_grapeshot", "grapeshot_burst",
                    "flak_burst", "flak_autocannon" -> Kind.FRAGMENTATION;
            case "ap_autocannon" -> Kind.AUTOCANNON;
            case "machine_gun_bullet" -> Kind.MACHINE_GUN;
            case "shot" -> Kind.SHOT;
            default -> Kind.UTILITY;
        };
    }

    /** Classifies CBC custom explosions whose direct source entity is intentionally null. */
    public static Kind classifyExplosionClass(String simpleClassName) {
        String name = simpleClassName == null ? "" : simpleClassName.toLowerCase(Locale.ROOT);
        if (name.contains("mortar")) return Kind.MORTAR;
        if (name.contains("armor") && name.contains("piercing") && name.contains("shell")) {
            return Kind.AP_SHELL;
        }
        if (name.contains("shell")) return Kind.HE_SHELL;
        return Kind.UTILITY;
    }

    /**
     * Only the carrier projectile owns an explosion area. Burst entities report one terrain event per
     * fragment, so treating their shared FRAGMENTATION kind as an area hit multiplies damage heavily.
     */
    public static boolean usesBlastArea(String entityPath) {
        String id = entityPath == null ? "" : entityPath.toLowerCase(Locale.ROOT);
        return switch (id) {
            case "he_shell", "ap_shell", "mortar_stone", "drop_mortar_shell", "shrapnel_shell",
                    "flak_autocannon" -> true;
            default -> false;
        };
    }

    /** Strategic cores require a heavy CBC projectile; rapid-fire and fragment spam cannot damage them. */
    public static boolean canDamageCore(Kind kind) {
        return kind == Kind.SHOT || kind == Kind.AP_SHOT || kind == Kind.HE_SHELL
                || kind == Kind.AP_SHELL || kind == Kind.MORTAR;
    }

    public static int coreDamage(Kind kind, int reinforcementDamage, double multiplier) {
        if (!canDamageCore(kind) || reinforcementDamage <= 0 || multiplier <= 0.0D) return 0;
        return (int) Math.min(Integer.MAX_VALUE,
                Math.max(1L, Math.round(reinforcementDamage * multiplier)));
    }

    /** AP autocannon fire can destroy an exposed vehicle core, never a strategic territory core. */
    public static int vehicleCoreDamage(Kind kind, int heavyCoreDamage, int apAutocannonDamage,
                                        boolean pointHit) {
        if (kind == Kind.AUTOCANNON) return pointHit ? Math.max(0, apAutocannonDamage) : 0;
        return canDamageCore(kind) ? Math.max(0, heavyCoreDamage) : 0;
    }
}

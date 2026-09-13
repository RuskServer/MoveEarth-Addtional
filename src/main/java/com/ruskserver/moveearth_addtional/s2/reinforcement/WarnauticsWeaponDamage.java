package com.ruskserver.moveearth_addtional.s2.reinforcement;

import java.util.Locale;

/** Warnautics classification kept independent from the optional mod binary. */
public final class WarnauticsWeaponDamage {
    public enum Kind {
        SMALL_BOMB,
        SEA_BOMB,
        MEDIUM_BOMB,
        LARGE_BOMB,
        MOAB,
        CRUISE_MISSILE,
        C4,
        LARGE_MINE,
        UNKNOWN
    }

    private WarnauticsWeaponDamage() { }

    public static Kind classify(String sizeName, String sourceEntityPath) {
        String source = normalize(sourceEntityPath);
        if ("cruise_missile".equals(source)) return Kind.CRUISE_MISSILE;
        if ("c4".equals(source)) return Kind.C4;

        String size = normalize(sizeName);
        if (source.isEmpty()) {
            // Placed C4 and anti-vehicle mines intentionally create their blast without
            // a source entity. Their distinct fixed BombSize values are the public hook.
            if ("medium".equals(size)) return Kind.C4;
            if ("large".equals(size)) return Kind.LARGE_MINE;
        }
        return switch (size) {
            case "small" -> Kind.SMALL_BOMB;
            case "sea" -> Kind.SEA_BOMB;
            case "medium" -> Kind.MEDIUM_BOMB;
            case "large" -> Kind.LARGE_BOMB;
            case "moab" -> Kind.MOAB;
            default -> Kind.UNKNOWN;
        };
    }

    public static boolean isMine(Kind kind) {
        return kind == Kind.LARGE_MINE;
    }

    public static boolean canDamageCore(Kind kind) {
        return kind != Kind.LARGE_MINE && kind != Kind.CRUISE_MISSILE && kind != Kind.UNKNOWN;
    }

    /** Linear falloff which is deterministic and easy to tune in server tests. */
    public static int distanceScaledDamage(int maximum, double distance, double radius) {
        if (maximum <= 0 || !Double.isFinite(distance) || !Double.isFinite(radius)
                || distance < 0.0D || radius <= 0.0D || distance > radius) return 0;
        double scale = 1.0D - distance / radius;
        return (int) Math.floor(maximum * scale);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

package com.ruskserver.moveearth_addtional.s2.reinforcement;

import java.util.Locale;

/** CBC registry-name classification kept independent from the optional CBC binary. */
public final class CbcMunitionDamage {
    public enum Kind { SHOT, AP_SHOT, HE_SHELL, AP_SHELL, MORTAR, FRAGMENTATION, AUTOCANNON, UTILITY }

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
            case "ap_autocannon", "machine_gun_bullet" -> Kind.AUTOCANNON;
            case "shot" -> Kind.SHOT;
            default -> Kind.UTILITY;
        };
    }
}

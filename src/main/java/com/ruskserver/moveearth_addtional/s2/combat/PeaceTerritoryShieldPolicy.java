package com.ruskserver.moveearth_addtional.s2.combat;

import java.util.UUID;

/** Peace protection is positional and asymmetric; firing back never revokes it. */
public final class PeaceTerritoryShieldPolicy {
    private PeaceTerritoryShieldPolicy() { }

    public static boolean blocks(UUID victim, UUID homeNation, UUID landNation,
                                 UUID attacker, UUID attackerNation,
                                 boolean ranged, boolean mobOrNatural, boolean siegeAtPosition, boolean arena) {
        if (!ranged || mobOrNatural || arena || siegeAtPosition
                || homeNation == null || !homeNation.equals(landNation)) return false;
        if (victim.equals(attacker)) return false;
        // Nationless and unidentified artificial shots cannot bypass home-territory protection.
        return !homeNation.equals(attackerNation);
    }

    public static boolean insideChunks(int x, int z, int centerX, int centerZ, int radius) {
        return radius >= 0 && Math.abs((long) x - centerX) <= radius
                && Math.abs((long) z - centerZ) <= radius;
    }

    public static boolean rangedType(boolean projectileTag, boolean explosionTag, boolean projectileEntity,
                                     boolean gunTag, String namespace, String path) {
        if (projectileTag || explosionTag || projectileEntity || gunTag) return true;
        if ("cbc_more_content".equals(namespace)) return true;
        if ("mekanism".equals(namespace) && ("laser".equals(path) || "flamethrower".equals(path))) return true;
        return "createbigcannons".equals(namespace) && switch (path) {
            case "cannon_projectile", "big_cannon_projectile", "machine_gun_fire", "machine_gun_fire_in_water",
                    "shrapnel", "grapeshot", "flak" -> true;
            default -> false;
        };
    }
}

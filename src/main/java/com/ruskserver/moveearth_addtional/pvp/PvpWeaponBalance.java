package com.ruskserver.moveearth_addtional.pvp;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * PvP-only body-damage scaling for the selectable TaCZ standard-gun loadouts.
 *
 * <p>The targets assume full Protection IV iron armor, TaCZ Tweaks armor
 * penetration set to zero, a 1.5x raw headshot multiplier, close-range gun
 * damage and 20 health. TTK is measured from the first landed body shot:</p>
 *
 * <pre>
 * SCAR-L 5 hits @ 650 RPM = 369.2 ms
 * MP5A5  6 hits @ 820 RPM = 365.9 ms
 * AA12   3 full-pellet hits @ 350 RPM = 342.9 ms (cadence exception)
 * SKS    4 semi-auto hits @ 510 RPM = 352.9 ms
 * P320   4 hits @ 450 RPM = 400.0 ms
 * </pre>
 *
 * <p>Multipliers preserve each gun's native distance falloff. AA12 damage is
 * divided across ten projectiles by TaCZ. The targets rely on the server's
 * TaCZ Tweaks configuration forcing armor penetration to zero.</p>
 */
final class PvpWeaponBalance {
    static final float HEADSHOT_MULTIPLIER = 1.5F;

    private static final Map<ResourceLocation, Float> DAMAGE_MULTIPLIERS = Map.of(
            tacz("scar_l"), 16.15F / 7.5F,
            tacz("hk_mp5a5"), 14.2F / 6.0F,
            tacz("aa12"), 41.4F / 30.0F,
            tacz("sks_tactical"), 18.4F / 11.0F,
            tacz("p320"), 19.4F / 10.0F
    );

    private PvpWeaponBalance() {
    }

    static Float damageMultiplier(ResourceLocation gunId) {
        return DAMAGE_MULTIPLIERS.get(gunId);
    }

    private static ResourceLocation tacz(String path) {
        return ResourceLocation.fromNamespaceAndPath("tacz", path);
    }
}

package com.ruskserver.moveearth_addtional.pvp;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * PvP-only body-damage scaling for the selectable FMIC loadouts.
 *
 * <p>The targets assume full Protection IV iron armor, TaCZ Tweaks armor
 * penetration set to zero, a 1.5x raw headshot multiplier, close-range gun
 * damage and 20 health. TTK is measured from the first landed body shot:</p>
 *
 * <pre>
 * RA39   4 hits @ 680 RPM = 264.7 ms
 * EF_SMG 5 hits @ 900 RPM = 266.7 ms
 * EF_SG  3 full-pellet hits @ 400 RPM = 300.0 ms
 * NSR20  3 hits @ 360 RPM = 333.3 ms (precision weapon exception)
 * G45    2 hits @ 300 RPM = 200.0 ms
 * </pre>
 *
 * <p>Multipliers preserve each gun's native distance falloff. The shotgun's
 * native 38 damage is already divided across eight projectiles by TaCZ.</p>
 */
final class PvpWeaponBalance {
    static final float HEADSHOT_MULTIPLIER = 1.5F;

    private static final Map<ResourceLocation, Float> DAMAGE_MULTIPLIERS = Map.of(
            fmic("ra39"), 18.7F / 8.3F,
            fmic("ef_smg"), 16.15F / 7.2F,
            fmic("ef_sg"), 1.0F,
            fmic("nsr20"), 22.7F / 22.0F,
            fmic("g45"), 31.6F / 11.4F
    );

    private PvpWeaponBalance() {
    }

    static Float damageMultiplier(ResourceLocation gunId) {
        return DAMAGE_MULTIPLIERS.get(gunId);
    }

    private static ResourceLocation fmic(String path) {
        return ResourceLocation.fromNamespaceAndPath("fmic", path);
    }
}

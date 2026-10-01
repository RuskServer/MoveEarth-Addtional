package com.ruskserver.moveearth_addtional.compat.sentry;

import com.ruskserver.moveearth_addtional.CompatEventHandler;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.ExplosionData;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Enforces {@link SentryTurretRules} on every turret shot. Both of the mod's fire
 * paths, a placed turret and one riding a contraption, shoot through a fake
 * player whose pitch is set to the shot's, so one shoot event covers both, and
 * covers targets the aim limit cannot see, such as target blocks and marked
 * positions.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class SentryTurretGuard {
    private static final String SENTRY_PACKAGE = "euphy.upo.sentrymechanicalarm.";

    private SentryTurretGuard() { }

    @SubscribeEvent
    public static void onShoot(GunShootEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER || !isTurret(event.getShooter())) return;
        if (!allowed(event.getGunItemStack())
                || SentryTurretRules.tooSteep(event.getShooter().getXRot(), SentryTurretConfig.maxDepressionDegrees())) {
            event.setCanceled(true);
        }
    }

    /**
     * A downed PlayerRevive player is left to be revived or finished by a person;
     * a turret would otherwise keep firing at a body that cannot move.
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (ignoredTarget(event.getEntity()) && event.getSource().getEntity() instanceof LivingEntity shooter
                && isTurret(shooter)) {
            event.setCanceled(true);
        }
    }

    /** Targets a turret treats as out of sight. */
    public static boolean ignoredTarget(Entity target) {
        return target instanceof Player player && CompatEventHandler.isPlayerDown(player);
    }

    /** Whether a turret may carry {@code gun}; anything that is not a known TaCZ gun is refused. */
    public static boolean allowed(ItemStack gun) {
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) return false;
        return TimelessAPI.getCommonGunIndex(iGun.getGunId(gun))
                .map(index -> allowed(index.getGunData()))
                .orElse(false);
    }

    private static boolean allowed(GunData data) {
        BulletData bullet = data.getBulletData();
        if (bullet == null) return false;
        ExplosionData explosion = bullet.getExplosionData();
        boolean semiAutoOnly = !data.getFireModeSet().isEmpty()
                && data.getFireModeSet().stream().allMatch(mode -> mode == FireMode.SEMI);
        return SentryTurretRules.gunAllowed(semiAutoOnly, bullet.getBulletAmount(), peakDamage(bullet),
                explosion != null && explosion.isExplode(), SentryTurretConfig.maxGunDamage());
    }

    /**
     * The most a hit can deal. A distance table replaces the base damage where it
     * applies, and its close-range entry is often the higher one: the Glock 17's
     * base is 6 but it hits for 7 within 18 blocks.
     */
    private static float peakDamage(BulletData bullet) {
        float peak = bullet.getDamageAmount();
        ExtraDamage extra = bullet.getExtraDamage();
        if (extra != null && extra.getDamageAdjust() != null) {
            for (ExtraDamage.DistanceDamagePair pair : extra.getDamageAdjust()) {
                peak = Math.max(peak, pair.getDamage());
            }
        }
        return peak;
    }

    private static boolean isTurret(LivingEntity shooter) {
        return shooter instanceof FakePlayer && shooter.getClass().getName().startsWith(SENTRY_PACKAGE);
    }
}

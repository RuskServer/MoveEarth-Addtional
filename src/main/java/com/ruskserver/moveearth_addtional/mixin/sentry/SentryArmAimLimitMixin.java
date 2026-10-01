package com.ruskserver.moveearth_addtional.mixin.sentry;

import com.ruskserver.moveearth_addtional.compat.sentry.SentryTurretConfig;
import com.ruskserver.moveearth_addtional.compat.sentry.SentryTurretGuard;
import com.ruskserver.moveearth_addtional.compat.sentry.SentryTurretRules;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A placed turret treats a downed player (see {@link SentryTurretGuard#ignoredTarget})
 * or a target below its depression limit as out of sight, so it neither picks
 * nor keeps one, at the foot of its own wall for instance, and moves on to the
 * next. {@code getBestTargetPos} is the turret's single visibility check for
 * scanning, focused and marked targets and keeping a lock.
 */
@Pseudo
@Mixin(targets = "euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity", remap = false)
public abstract class SentryArmAimLimitMixin {
    @Shadow public abstract Vec3 getActualMuzzlePos();

    @Inject(method = "getBestTargetPos", at = @At("RETURN"), cancellable = true)
    private void moveearth$limitTargets(LivingEntity target, CallbackInfoReturnable<Vec3> cir) {
        Vec3 aim = cir.getReturnValue();
        if (aim == null) return;
        if (SentryTurretGuard.ignoredTarget(target)) {
            cir.setReturnValue(null);
            return;
        }
        Vec3 muzzle = getActualMuzzlePos();
        double depression = SentryTurretRules.depressionDegrees(muzzle.x, muzzle.y, muzzle.z, aim.x, aim.y, aim.z);
        if (SentryTurretRules.tooSteep(depression, SentryTurretConfig.maxDepressionDegrees())) {
            cir.setReturnValue(null);
        }
    }
}

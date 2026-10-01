package com.ruskserver.moveearth_addtional.mixin.sentry;

import com.ruskserver.moveearth_addtional.compat.sentry.SentryTurretConfig;
import com.ruskserver.moveearth_addtional.compat.sentry.SentryTurretGuard;
import com.ruskserver.moveearth_addtional.compat.sentry.SentryTurretRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The same target limits as {@link SentryArmAimLimitMixin}, for a turret riding a contraption. */
@Pseudo
@Mixin(targets = "euphy.upo.sentrymechanicalarm.content.SentryMovementBehaviour", remap = false)
public abstract class SentryContraptionAimLimitMixin {
    @Inject(method = "getBestTargetPos", at = @At("RETURN"), cancellable = true)
    private void moveearth$limitTargets(Level level, LivingEntity target, Vec3 muzzle, Entity shooter,
                                           CallbackInfoReturnable<Vec3> cir) {
        Vec3 aim = cir.getReturnValue();
        if (aim == null) return;
        if (SentryTurretGuard.ignoredTarget(target)) {
            cir.setReturnValue(null);
            return;
        }
        double depression = SentryTurretRules.depressionDegrees(muzzle.x, muzzle.y, muzzle.z, aim.x, aim.y, aim.z);
        if (SentryTurretRules.tooSteep(depression, SentryTurretConfig.maxDepressionDegrees())) {
            cir.setReturnValue(null);
        }
    }
}

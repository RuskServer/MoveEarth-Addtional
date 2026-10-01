package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.compat.aeronautics.JointWindupLimit;
import com.ruskserver.moveearth_addtional.compat.kinematics.KinematicsJointView;
import com.ruskserver.moveearth_addtional.config.AeronauticsSwivelConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops an Absolute Kinematics 1.0.1 hinge from storing force while it is held
 * back; see {@link JointWindupLimit}. Applies only when that mod is installed.
 */
@Pseudo
@Mixin(targets = "com.rcx.absolutekinematics.blockentity.HingeBlockEntity", remap = false)
public abstract class KinematicsHingeWindupMixin {
    @Shadow public double lastTargetAngleDegrees;
    @Shadow public double targetAngleDegrees;
    @Shadow public double sequencedAngleLimit;

    @Shadow public abstract double getCurrentAngle();

    @Inject(method = "tick", at = @At("TAIL"))
    private void moveearth$limitWindup(CallbackInfo ci) {
        double proposed = targetAngleDegrees;
        if (proposed == lastTargetAngleDegrees || !AeronauticsSwivelConfig.jointWindupLimitEnabled()) return;
        if (!(((Object) this) instanceof KinematicsJointView joint) || !joint.moveearth$drivesAttachedBody()) return;
        double limited = JointWindupLimit.limitAdvance(lastTargetAngleDegrees, proposed, getCurrentAngle(),
                AeronauticsSwivelConfig.jointMaxLeadDegrees());
        if (limited == proposed) return;
        targetAngleDegrees = limited;
        // A sequenced turn was charged for the whole step; give back what was held so
        // the hinge still completes the instructed angle once it is free.
        if (sequencedAngleLimit >= 0.0) sequencedAngleLimit += Math.abs(proposed - limited);
    }
}

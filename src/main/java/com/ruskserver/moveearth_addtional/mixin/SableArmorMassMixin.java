package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.compat.vehicle.SableArmorMassCache;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs after Sable reconstructs base properties, before change detection/upload to the solver. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.api.physics.mass.MergedMassTracker", remap = false)
public abstract class SableArmorMassMixin {
    @Shadow @Final private ServerSubLevel subLevel;
    @Shadow private double mass;
    @Shadow private double inverseMass;
    @Shadow private Vector3d centerOfMass;
    @Shadow @Final private Matrix3d inertiaTensor;
    @Shadow @Final private Matrix3d inverseInertiaTensor;
    @Unique private SableArmorMassCache moveearth$armorMass;

    @Inject(method = "update", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/physics/mass/MergedMassTracker;uploadData()V"))
    private void moveearth$mergeArmor(float partialPhysicsTick, CallbackInfo callback) {
        if (centerOfMass == null || !(mass > 0.0)) return;
        if (moveearth$armorMass == null) moveearth$armorMass = new SableArmorMassCache();
        double combined = moveearth$armorMass.merge(subLevel, mass, centerOfMass, inertiaTensor);
        if (combined == mass) return;
        mass = combined;
        inverseMass = 1.0 / mass;
        inertiaTensor.invert(inverseInertiaTensor);
    }
}

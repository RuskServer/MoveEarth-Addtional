package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.compat.cbc.CbcShotAttribution;
import com.simibubi.create.content.contraptions.Contraption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Opens a CBC firing frame around every mounted autocannon round so it can be attributed (CBC sets no owner). */
@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.cannon_control.contraption.MountedAutocannonContraption", remap = false)
public abstract class CbcAutocannonFireAttributionMixin {
    @Inject(method = "fireShot(Lnet/minecraft/server/level/ServerLevel;Lrbasamoyai/createbigcannons/cannon_control/contraption/PitchOrientedContraptionEntity;)V",
            at = @At("HEAD"), remap = false)
    private void moveearth$beginShot(CallbackInfo callback) {
        CbcShotAttribution.beginFiring(((Contraption) (Object) this).entity);
    }

    @Inject(method = "fireShot(Lnet/minecraft/server/level/ServerLevel;Lrbasamoyai/createbigcannons/cannon_control/contraption/PitchOrientedContraptionEntity;)V",
            at = @At("RETURN"), remap = false)
    private void moveearth$endShot(CallbackInfo callback) {
        CbcShotAttribution.end();
    }
}

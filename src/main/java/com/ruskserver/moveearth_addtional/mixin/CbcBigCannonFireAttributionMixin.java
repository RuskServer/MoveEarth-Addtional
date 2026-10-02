package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.compat.cbc.CbcShotAttribution;
import com.simibubi.create.content.contraptions.Contraption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Opens a CBC firing frame around every big-cannon discharge so the round can be attributed (CBC sets no
 * owner). The drop mortar launches its round later from {@code tick}, outside {@code fireShot}.
 */
@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption", remap = false)
public abstract class CbcBigCannonFireAttributionMixin {
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

    @Inject(method = "actuallyFireDropMortar()V", at = @At("HEAD"), remap = false)
    private void moveearth$beginDropMortar(CallbackInfo callback) {
        CbcShotAttribution.beginFiring(((Contraption) (Object) this).entity);
    }

    @Inject(method = "actuallyFireDropMortar()V", at = @At("RETURN"), remap = false)
    private void moveearth$endDropMortar(CallbackInfo callback) {
        CbcShotAttribution.end();
    }
}

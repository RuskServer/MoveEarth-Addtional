package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.compat.cbc.CbcShotAttribution;
import com.simibubi.create.content.contraptions.Contraption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CBC Modern Warfare's cannons extend CBC's mounted-cannon contraption and, like CBC, fire without an
 * owner; their shots open the same CBC firing frame. Optional add-on: a changed method is skipped
 * (require = 0) rather than failing the game, which leaves those shots unattributed.
 */
@Pseudo
@Mixin(targets = {
        "riftyboi.cbcmodernwarfare.cannon_control.contraption.MountedMediumcannonContraption",
        "riftyboi.cbcmodernwarfare.cannon_control.contraption.MountedRotarycannonContraption",
        "riftyboi.cbcmodernwarfare.cannon_control.contraption.MountedMunitionsLauncherContraption"
}, remap = false)
public abstract class CbcAddonFireAttributionMixin {
    @Inject(method = "fireShot(Lnet/minecraft/server/level/ServerLevel;Lrbasamoyai/createbigcannons/cannon_control/contraption/PitchOrientedContraptionEntity;)V",
            at = @At("HEAD"), remap = false, require = 0)
    private void moveearth$beginShot(CallbackInfo callback) {
        CbcShotAttribution.beginFiring(((Contraption) (Object) this).entity);
    }

    @Inject(method = "fireShot(Lnet/minecraft/server/level/ServerLevel;Lrbasamoyai/createbigcannons/cannon_control/contraption/PitchOrientedContraptionEntity;)V",
            at = @At("RETURN"), remap = false, require = 0)
    private void moveearth$endShot(CallbackInfo callback) {
        CbcShotAttribution.end();
    }
}

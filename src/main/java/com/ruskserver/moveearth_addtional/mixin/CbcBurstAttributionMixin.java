package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.compat.cbc.CbcShotAttribution;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shells that burst into fragments (shrapnel, fluid, flak) spawn the burst from {@code detonate}; the
 * burst inherits the shell's attribution through a CBC detonation frame.
 */
@Pseudo
@Mixin(targets = {
        "rbasamoyai.createbigcannons.munitions.big_cannon.shrapnel.ShrapnelShellProjectile",
        "rbasamoyai.createbigcannons.munitions.big_cannon.fluid_shell.FluidShellProjectile",
        "rbasamoyai.createbigcannons.munitions.autocannon.flak.FlakAutocannonProjectile"
}, remap = false)
public abstract class CbcBurstAttributionMixin {
    @Inject(method = "detonate(Lnet/minecraft/core/Position;)V", at = @At("HEAD"), remap = false)
    private void moveearth$beginDetonation(CallbackInfo callback) {
        CbcShotAttribution.beginDetonation((Entity) (Object) this);
    }

    @Inject(method = "detonate(Lnet/minecraft/core/Position;)V", at = @At("RETURN"), remap = false)
    private void moveearth$endDetonation(CallbackInfo callback) {
        CbcShotAttribution.end();
    }
}

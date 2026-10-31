package com.ruskserver.moveearth_addtional.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stops secondary shell/propellant spawning while retaining ordinary explosion block destruction. */
@Pseudo
@Mixin(targets = {
        "rbasamoyai.createbigcannons.munitions.big_cannon.ProjectileBlock",
        "rbasamoyai.createbigcannons.munitions.big_cannon.propellant.PowderChargeBlock",
        "rbasamoyai.createbigcannons.munitions.big_cannon.propellant.BigCartridgeBlock"
}, remap = false)
public abstract class CbcMunitionChainExplosionMixin {
    @Inject(method = "createbigcannons$onBlockExplode", at = @At("HEAD"), cancellable = true, remap = false)
    private void moveearth$stopSecondaryExplosion(Level level, BlockPos pos, BlockState state,
                                                 Explosion explosion, CallbackInfo callback) {
        callback.cancel();
    }
}

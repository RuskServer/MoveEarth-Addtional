package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.compat.cbc.CbcShotAttribution;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A grapeshot bag releases its burst from {@code disintegrate}; the burst inherits the bag's attribution. */
@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.munitions.big_cannon.grapeshot.GrapeshotBagProjectile", remap = false)
public abstract class CbcGrapeshotAttributionMixin {
    @Inject(method = "disintegrate()V", at = @At("HEAD"), remap = false)
    private void moveearth$beginDisintegration(CallbackInfo callback) {
        CbcShotAttribution.beginDetonation((Entity) (Object) this);
    }

    @Inject(method = "disintegrate()V", at = @At("RETURN"), remap = false)
    private void moveearth$endDisintegration(CallbackInfo callback) {
        CbcShotAttribution.end();
    }
}

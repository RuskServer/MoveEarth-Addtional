package com.ruskserver.moveearth_addtional.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ruskserver.moveearth_addtional.client.particles.CbcParticlePerformance;
import com.ruskserver.moveearth_addtional.client.particles.CbcParticleConfig;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public abstract class CbcParticlePerformanceMixin {
    @Inject(method = "makeParticle", at = @At("RETURN"), cancellable = true)
    private void moveearth$budgetSmoke(ParticleOptions data, double x, double y, double z,
                                       double dx, double dy, double dz, CallbackInfoReturnable<Particle> callback) {
        if (CbcParticlePerformance.discard(callback.getReturnValue(), x, y, z)) callback.setReturnValue(null);
    }

    @WrapOperation(method = "tickParticle", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/Particle;tick()V"))
    private void moveearth$measureUpdate(Particle particle, Operation<Void> original) {
        if (CbcParticleConfig.PROFILE.get() || CbcParticleConfig.COLLISION_LOD.get())
            CbcParticlePerformance.tick(particle, () -> original.call(particle));
        else original.call(particle);
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void moveearth$resetMeasurements(CallbackInfo callback) {
        CbcParticlePerformance.reset();
    }
}

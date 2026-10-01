package com.ruskserver.moveearth_addtional.mixin.sentry;

import com.ruskserver.moveearth_addtional.compat.sentry.SentryTurretConfig;
import com.simibubi.create.api.stress.BlockStressValues;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers the turret's stress from {@link SentryTurretConfig} in place of the
 * mod's fixed 3 SU per RPM. Create refuses a second registration for the same
 * block, so the mod's own is skipped rather than overridden afterwards.
 */
@Pseudo
@Mixin(targets = "euphy.upo.sentrymechanicalarm.registry.SentryRegistry", remap = false)
public abstract class SentryStressMixin {
    private static final ResourceLocation MOVEEARTH$TURRET =
            ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", "sentry_mechanical_arm");

    @Inject(method = "registerAllStressValues", at = @At("HEAD"), cancellable = true)
    private static void moveearth$configuredStress(CallbackInfo ci) {
        Block turret = BuiltInRegistries.BLOCK.getOptional(MOVEEARTH$TURRET).orElse(null);
        if (turret == null) return;
        BlockStressValues.IMPACTS.register(turret, SentryTurretConfig::stressImpact);
        ci.cancel();
    }
}

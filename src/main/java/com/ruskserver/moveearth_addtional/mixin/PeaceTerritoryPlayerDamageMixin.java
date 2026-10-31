package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.s2.combat.PeaceTerritoryShieldService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class PeaceTerritoryPlayerDamageMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void moveearth$peaceTerritoryBeforeCombatCosts(DamageSource source, float amount,
                                                          CallbackInfoReturnable<Boolean> callback) {
        if (PeaceTerritoryShieldService.blockDamage((ServerPlayer) (Object) this, source, amount)) {
            callback.setReturnValue(false);
        }
    }
}

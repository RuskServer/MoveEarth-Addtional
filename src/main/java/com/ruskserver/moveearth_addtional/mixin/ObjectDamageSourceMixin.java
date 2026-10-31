package com.ruskserver.moveearth_addtional.mixin;

import static com.ruskserver.moveearth_addtional.analytics.provenance.MutationRejectionAudit.rejected;

import com.ruskserver.moveearth_addtional.s2.provenance.WorldMutationGuard;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Object damage only: living players and mobs keep their existing combat rules. */
@Mixin(targets = {"net.minecraft.world.entity.decoration.ArmorStand",
        "net.minecraft.world.entity.decoration.BlockAttachedEntity", "net.minecraft.world.entity.decoration.ItemFrame",
        "net.minecraft.world.entity.vehicle.VehicleEntity", "net.minecraft.world.entity.item.ItemEntity"})
public abstract class ObjectDamageSourceMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void moveearth$requireObjectAttackSource(DamageSource source, float damage,
                                                     CallbackInfoReturnable<Boolean> callback) {
        if (!((Entity) (Object) this).level().isClientSide && !WorldMutationGuard.allowsObjectDamage(source, (Entity) (Object) this)) {
            var target = (Entity) (Object) this;
            rejected("object.damage", target.level(), target.blockPosition());
            callback.setReturnValue(false);
        }
    }
}

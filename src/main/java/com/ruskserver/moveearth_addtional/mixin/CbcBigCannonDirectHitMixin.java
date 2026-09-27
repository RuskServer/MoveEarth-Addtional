package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.compat.cbc.CbcReinforcementCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Supplies the actual projectile to CBC's terrain-damage hook for direct, unfuzed hits. */
@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile", remap = false)
public abstract class CbcBigCannonDirectHitMixin {
    @Redirect(method = "calculateBlockPenetration", remap = false,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lrbasamoyai/createbigcannons/munitions/ProjectileDamageHooks;canDamageTerrain(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean moveearth$protectDirectHit(Level level, BlockPos pos) {
        return CbcReinforcementCompat.canDamageTerrain((Entity) (Object) this, level, pos);
    }
}

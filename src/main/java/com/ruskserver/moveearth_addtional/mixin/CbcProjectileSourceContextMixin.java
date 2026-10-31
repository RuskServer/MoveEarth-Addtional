package com.ruskserver.moveearth_addtional.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.ruskserver.moveearth_addtional.s2.provenance.ActionContext;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Keeps callback-based fragmentation/fluid effects attached to the exact ticking round. */
@Pseudo
@Mixin(targets = {
        "rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile",
        "rbasamoyai.createbigcannons.munitions.fragment_burst.CBCProjectileBurst"
}, remap = false)
public abstract class CbcProjectileSourceContextMixin {
    @WrapMethod(method = "tick", remap = false)
    private void moveearth$exactProjectileFrame(Operation<Void> original) {
        try (var scope = ActionContext.push((Entity) (Object) this)) { original.call(); }
    }
}

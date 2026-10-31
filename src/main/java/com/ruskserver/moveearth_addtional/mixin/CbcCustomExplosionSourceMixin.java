package com.ruskserver.moveearth_addtional.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.ruskserver.moveearth_addtional.s2.provenance.ActionContext;
import com.ruskserver.moveearth_addtional.s2.provenance.WorldMutationGuard;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.CreateBigCannons", remap = false)
public abstract class CbcCustomExplosionSourceMixin {
    @WrapMethod(method = "handleCustomExplosion", remap = false)
    private static void moveearth$exactExplosionFrame(Level level, Explosion explosion, Operation<Void> original) {
        try (var scope = ActionContext.push(WorldMutationGuard.explosionOrigin(explosion))) {
            original.call(level, explosion);
        }
    }
}
